package com.maloy.muzza.ui.component.shimmer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ButtonRowPlaceHolder(
    modifier: Modifier = Modifier,
    buttonCount: Int = 2
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        repeat(buttonCount) {
            ButtonPlaceholder(modifier = Modifier.weight(1f))
        }
    }
}