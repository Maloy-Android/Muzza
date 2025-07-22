package com.maloy.muzza.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import com.maloy.muzza.R
import com.maloy.muzza.utils.makeTimeString

val ListMenuItemHeight = 56.dp

@Composable
fun ListMenu(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun ListMenuDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    startIndent: Dp = 16.dp
) {
    HorizontalDivider(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startIndent),
        thickness = thickness,
        color = color
    )
}

fun LazyListScope.ListMenuItem(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    tint: @Composable () -> Color = { LocalContentColor.current },
    @StringRes title: Int,
    enabled: Boolean = true,
    onClick: () -> Unit
) = ListMenuItem(
    modifier = modifier,
    icon = {
        Icon(
            painter = painterResource(icon),
            tint = tint(),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    },
    title = title,
    enabled = enabled,
    onClick = onClick
)

fun LazyListScope.ListMenuItem(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    @StringRes title: Int,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    item {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(ListMenuItemHeight)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

fun LazyListScope.DownloadListMenu(
    @Download.State state: Int?,
    onRemoveDownload: () -> Unit,
    onDownload: () -> Unit,
) {
    when (state) {
        Download.STATE_COMPLETED -> {
            ListMenuItem(
                icon = R.drawable.offline,
                title = R.string.remove_download,
                onClick = onRemoveDownload
            )
        }
        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
            ListMenuItem(
                icon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                },
                title = R.string.downloading,
                onClick = onRemoveDownload
            )
        }
        else -> {
            ListMenuItem(
                icon = R.drawable.download,
                title = R.string.download,
                onClick = onDownload
            )
        }
    }
}

fun LazyListScope.SleepTimerListMenu(
    modifier: Modifier = Modifier,
    sleepTimerTimeLeft: Long,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    item {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(ListMenuItemHeight)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.bedtime),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = if (enabled) makeTimeString(sleepTimerTimeLeft) else stringResource(id = R.string.sleep_timer),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
