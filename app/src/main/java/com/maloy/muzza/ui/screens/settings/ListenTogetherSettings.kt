package com.maloy.muzza.ui.screens.settings

import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.ListenTogetherServerUrlKey
import com.maloy.muzza.constants.ListenTogetherUsernameKey
import com.maloy.muzza.listentogether.ConnectionState
import com.maloy.muzza.listentogether.ListenTogetherEvent
import com.maloy.muzza.listentogether.RoomRole
import com.maloy.muzza.ui.component.PreferenceEntry
import com.maloy.muzza.ui.component.PreferenceGroupTitle
import com.maloy.muzza.ui.component.TextFieldDialog
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.utils.rememberPreference
import com.maloy.muzza.viewmodels.ListenTogetherViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ListenTogetherViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val song by playerConnection.currentSong.collectAsState(null)
    val context = LocalContext.current
    val playbackState by playerConnection.playbackState.collectAsState()
    var position by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }
    val connectionState by viewModel.connectionState.collectAsState()
    val roomState by viewModel.roomState.collectAsState()
    val role by viewModel.role.collectAsState()
    val pendingJoinRequests by viewModel.pendingJoinRequests.collectAsState()
    val bufferingUsers by viewModel.bufferingUsers.collectAsState()
    var refetchIconDegree by remember { mutableFloatStateOf(0f) }

    val rotationAnimation by animateFloatAsState(
        targetValue = refetchIconDegree,
        animationSpec = tween(durationMillis = 800),
        label = ""
    )

    var serverUrl by rememberPreference(ListenTogetherServerUrlKey, "ws://metroserver.meowery.eu/ws")
    var username by rememberPreference(ListenTogetherUsernameKey, "")

    var showCreateRoomDialog by rememberSaveable { mutableStateOf(false) }
    var showJoinRoomDialog by rememberSaveable { mutableStateOf(false) }
    var roomCodeInput by rememberSaveable { mutableStateOf("") }

    var showUserNameEditDialog by rememberSaveable { mutableStateOf(false) }

    if (showUserNameEditDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.listen_together_username)) },
            icon = { Icon(painter = painterResource(R.drawable.person), null) },
            placeholder = { Text(stringResource(R.string.listen_together_username))},
            onDismiss = { showUserNameEditDialog = false },
            initialTextFieldValue = TextFieldValue(username),
            onDone = {
                if (roomState == null) {
                    username = it
                } else {
                    Toast.makeText(context, context.getString(R.string.listen_together_cannot_edit_username_in_room), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    var showServerLinkEditDialog by rememberSaveable { mutableStateOf(false) }

    if (showServerLinkEditDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.listen_together_server_url)) },
            icon = { Icon(painter = painterResource(R.drawable.add_link), null) },
            placeholder = { Text(stringResource(R.string.listen_together_server_url))},
            onDismiss = { showServerLinkEditDialog = false },
            initialTextFieldValue = TextFieldValue(serverUrl),
            onDone = { serverUrl = it }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ListenTogetherEvent.RoomCreated -> {
                    Toast.makeText(context, "Room created: ${event.roomCode}", Toast.LENGTH_SHORT).show()
                }
                is ListenTogetherEvent.JoinApproved -> {
                    Toast.makeText(context, "Joined room: ${event.roomCode}", Toast.LENGTH_SHORT).show()
                }
                is ListenTogetherEvent.JoinRejected -> {
                    Toast.makeText(context, "Join rejected: ${event.reason}", Toast.LENGTH_SHORT).show()
                }
                is ListenTogetherEvent.JoinRequestReceived -> {
                    Toast.makeText(context, "${event.username} wants to join", Toast.LENGTH_SHORT).show()
                }
                is ListenTogetherEvent.Kicked -> {
                    Toast.makeText(context, "Kicked: ${event.reason}", Toast.LENGTH_SHORT).show()
                }
                is ListenTogetherEvent.ConnectionError -> {
                    Toast.makeText(context, "Connection error: ${event.error}", Toast.LENGTH_SHORT).show()
                }
                is ListenTogetherEvent.ServerError -> {
                    Toast.makeText(context, "Error: ${event.message}", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(100)
                position = playerConnection.player.currentPosition
            }
        }
    }

    if (showCreateRoomDialog) {
        var createUsername by rememberSaveable(showCreateRoomDialog) { mutableStateOf(username) }
        AlertDialog(
            onDismissRequest = { showCreateRoomDialog = false },
            icon = { Icon(painter = painterResource(R.drawable.add), null)},
            title = { Text(stringResource(R.string.listen_together_create_room)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.listen_together_create_room_desc))
                    OutlinedTextField(
                        value = createUsername,
                        onValueChange = { createUsername = it },
                        label = { Text(stringResource(R.string.listen_together_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalUsername = createUsername.trim()
                        if (finalUsername.isNotBlank()) {
                            username = finalUsername
                            viewModel.createRoom(finalUsername)
                            showCreateRoomDialog = false
                        } else {
                            Toast.makeText(context, R.string.error_username_empty, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = createUsername.trim().isNotBlank()
                ) {
                    Text(stringResource(R.string.listen_together_create_room))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateRoomDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showJoinRoomDialog) {
        var joinUsername by rememberSaveable(showJoinRoomDialog) { mutableStateOf(username) }

        AlertDialog(
            onDismissRequest = { showJoinRoomDialog = false },
            icon = { Icon(painter = painterResource(R.drawable.login), null)},
            title = { Text(stringResource(R.string.listen_together_join_room)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = joinUsername,
                        onValueChange = { joinUsername = it },
                        label = { Text(stringResource(R.string.listen_together_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = roomCodeInput,
                        onValueChange = { roomCodeInput = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) },
                        label = { Text(stringResource(R.string.listen_together_room_code)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalUsername = joinUsername.trim()
                        if (finalUsername.isNotBlank() && roomCodeInput.length == 8) {
                            username = finalUsername
                            viewModel.joinRoom(roomCodeInput, finalUsername)
                            showJoinRoomDialog = false
                            roomCodeInput = ""
                        } else {
                            Toast.makeText(context, R.string.error_username_empty, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = joinUsername.trim().isNotBlank() && roomCodeInput.length == 8
                ) {
                    Text(stringResource(R.string.join_room))
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinRoomDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.listen_together)) },
                navigationIcon = {
                    com.maloy.muzza.ui.component.IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
        ) {
            PreferenceGroupTitle(
                title = stringResource(R.string.listen_together)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = when (connectionState) {
                                ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                                ConnectionState.CONNECTING, ConnectionState.RECONNECTING ->
                                    MaterialTheme.colorScheme.secondaryContainer
                                ConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
                                ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.errorContainer
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.RECONNECTING) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        painter = if (connectionState == ConnectionState.ERROR || connectionState == ConnectionState.DISCONNECTED) painterResource(R.drawable.close) else painterResource(R.drawable.check),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (connectionState) {
                                    ConnectionState.CONNECTED -> stringResource(R.string.listen_together_connected)
                                    ConnectionState.CONNECTING -> stringResource(R.string.listen_together_connecting)
                                    ConnectionState.RECONNECTING -> stringResource(R.string.listen_together_reconnecting)
                                    ConnectionState.ERROR -> stringResource(R.string.listen_together_error)
                                    ConnectionState.DISCONNECTED -> stringResource(R.string.listen_together_disconnected)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = when (connectionState) {
                                    ConnectionState.CONNECTED -> stringResource(R.string.listen_together_background_disconnect_note)
                                    ConnectionState.CONNECTING -> stringResource(R.string.listen_together_connecting_to_server)
                                    ConnectionState.RECONNECTING -> stringResource(R.string.listen_together_reconnecting_to_server)
                                    ConnectionState.ERROR -> stringResource(R.string.listen_together_connection_failed)
                                    ConnectionState.DISCONNECTED -> stringResource(R.string.listen_together_not_connected)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
                            FilledTonalButton(
                                onClick = { viewModel.connect() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painterResource(R.drawable.add_link),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.connect))
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    viewModel.disconnect()
                                    Toast.makeText(context, R.string.listen_together_disconnected, Toast.LENGTH_SHORT).show()
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painterResource(R.drawable.close),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.disconnect))
                            }

                            FilledTonalButton(
                                onClick = {
                                    viewModel.connect()
                                    refetchIconDegree -= 360
                                    Toast.makeText(context, R.string.listen_together_reconnecting, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    painterResource(R.drawable.sync),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .graphicsLayer(rotationZ = rotationAnimation)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.reconnect))
                            }
                        }
                    }
                }
            }

            roomState?.let { state ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painterResource(R.drawable.group),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (role == RoomRole.HOST)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = state.roomCode,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = if (role == RoomRole.HOST)
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        else
                                            MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = if (role == RoomRole.HOST)
                                                stringResource(R.string.listen_together_host)
                                            else
                                                stringResource(R.string.listen_together_guest),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(
                                        R.string.listen_together_users_count,
                                        state.users.count { it.isConnected }
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Room Code", state.roomCode)
                                    cm.setPrimaryClip(clip)
                                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    painterResource(R.drawable.content_copy),
                                    contentDescription = stringResource(R.string.copy)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val connectedUsers = state.users.filter { it.isConnected }
                        if (connectedUsers.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                connectedUsers.forEach { user ->
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (user.isHost)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                if (user.isHost) painterResource(R.drawable.star) else painterResource(R.drawable.person),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = user.username.ifEmpty { "User" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f)
                                            )

                                            if (bufferingUsers.contains(user.userId)) {
                                                CircularProgressIndicator(
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }

                                            if (!user.isHost && role == RoomRole.HOST) {
                                                IconButton(
                                                    onClick = {
                                                        viewModel.kickUser(user.userId)
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.close),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        OutlinedButton(
                            onClick = { viewModel.leaveRoom() },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                painterResource(R.drawable.logout),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.listen_together_leave_room))
                        }
                    }
                }

                if (role == RoomRole.HOST && pendingJoinRequests.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.listen_together_join_requests),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            pendingJoinRequests.forEach { request ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.person),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = request.username,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { viewModel.approveJoin(request.userId) },
                                        modifier = Modifier
                                            .size(30.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.check),
                                            contentDescription = stringResource(R.string.approve),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.rejectJoin(request.userId) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.close),
                                            contentDescription = stringResource(R.string.reject),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (connectionState == ConnectionState.CONNECTED && roomState == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.listen_together_room_actions),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = { showCreateRoomDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(painter = painterResource(R.drawable.add), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.listen_together_create_room))
                            }

                            OutlinedButton(
                                onClick = { showJoinRoomDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(painter = painterResource(R.drawable.login), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.listen_together_join_room))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                PreferenceGroupTitle(
                    title = stringResource(R.string.preview),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                RichPresence(song, position)
            }

            PreferenceGroupTitle(
                title = stringResource(R.string.settings)
            )

            PreferenceEntry(
                title = { Text(stringResource(R.string.listen_together_server_url)) },
                description = serverUrl,
                icon = { Icon(painter = painterResource(R.drawable.add_link), null) },
                onClick = { showServerLinkEditDialog = true }
            )

            PreferenceEntry(
                title = { Text(stringResource(R.string.listen_together_username)) },
                description = username,
                icon = { Icon(painter = painterResource(R.drawable.person), null) },
                onClick = { showUserNameEditDialog = true },
                isEnabled = roomState == null
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}