package com.maloy.muzza.listentogether

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.maloy.muzza.constants.ListenTogetherServerUrlKey
import com.maloy.muzza.constants.ListenTogetherSessionTokenKey
import com.maloy.muzza.constants.ListenTogetherRoomCodeKey
import com.maloy.muzza.constants.ListenTogetherUserIdKey
import com.maloy.muzza.constants.ListenTogetherIsHostKey
import com.maloy.muzza.constants.ListenTogetherSessionTimestampKey
import com.maloy.muzza.utils.dataStore
import com.maloy.muzza.utils.get
import androidx.datastore.preferences.core.edit
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.OkHttpClient
import android.widget.Toast
import androidx.annotation.RequiresPermission
import com.maloy.muzza.R
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.maloy.muzza.utils.isInternetAvailable

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

enum class RoomRole {
    HOST,
    GUEST,
    NONE
}

sealed class PendingAction {
    data class CreateRoom(val username: String) : PendingAction()
    data class JoinRoom(val roomCode: String, val username: String) : PendingAction()
}

sealed class ListenTogetherEvent {
    data object Disconnected : ListenTogetherEvent()
    data class ConnectionError(val error: String) : ListenTogetherEvent()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ListenTogetherEvent()

    data class RoomCreated(val roomCode: String, val userId: String) : ListenTogetherEvent()
    data class JoinRequestReceived(val userId: String, val username: String) : ListenTogetherEvent()
    data class JoinApproved(val roomCode: String, val userId: String, val state: RoomState) : ListenTogetherEvent()
    data class JoinRejected(val reason: String) : ListenTogetherEvent()
    data class UserJoined(val userId: String, val username: String) : ListenTogetherEvent()
    data class UserLeft(val userId: String, val username: String) : ListenTogetherEvent()
    data class HostChanged(val newHostId: String, val newHostName: String) : ListenTogetherEvent()
    data class Kicked(val reason: String) : ListenTogetherEvent()
    data class Reconnected(val roomCode: String, val userId: String, val state: RoomState, val isHost: Boolean) : ListenTogetherEvent()
    data class UserReconnected(val userId: String, val username: String) : ListenTogetherEvent()
    data class UserDisconnected(val userId: String, val username: String) : ListenTogetherEvent()

    data class PlaybackSync(val action: PlaybackActionPayload) : ListenTogetherEvent()
    data class BufferWait(val trackId: String, val waitingFor: List<String>) : ListenTogetherEvent()
    data class BufferComplete(val trackId: String) : ListenTogetherEvent()
    data class SyncStateReceived(val state: SyncStatePayload) : ListenTogetherEvent()

    data class ChatReceived(
        val userId: String,
        val username: String,
        val message: String,
        val timestamp: Long
    ) : ListenTogetherEvent()

    data class ServerError(val code: String, val message: String) : ListenTogetherEvent()
}


@Singleton
class ListenTogetherClient @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val DEFAULT_SERVER_URL = "https://metroserver.meowery.eu/ws"
        private const val MAX_RECONNECT_ATTEMPTS = 15
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 120000L
        private const val PING_INTERVAL_MS = 25000L
        private const val SESSION_GRACE_PERIOD_MS = 10 * 60 * 1000L

        private const val NOTIFICATION_CHANNEL_ID = "listen_together_channel"
        const val ACTION_APPROVE_JOIN = "com.maloy.muzza.LISTEN_TOGETHER_APPROVE_JOIN"
        const val ACTION_REJECT_JOIN = "com.maloy.muzza.LISTEN_TOGETHER_REJECT_JOIN"
        const val ACTION_APPROVE_SUGGESTION = "com.maloy.muzza.LISTEN_TOGETHER_APPROVE_SUGGESTION"
        const val ACTION_REJECT_SUGGESTION = "com.maloy.muzza.LISTEN_TOGETHER_REJECT_SUGGESTION"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_SUGGESTION_ID = "extra_suggestion_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ListenTogetherClient? = null

        fun getInstance(): ListenTogetherClient? = instance

        fun setInstance(client: ListenTogetherClient) {
            instance = client
        }
    }

    init {
        setInstance(this)
        ensureNotificationChannel()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            loadPersistedSession()
            observeNetworkChanges()
        }
    }

    private fun observeNetworkChanges() {
        scope.launch {
            if (isNetworkAvailable) {
                if (_connectionState.value == ConnectionState.ERROR ||
                    _connectionState.value == ConnectionState.DISCONNECTED
                ) {
                    if (sessionToken != null || _roomState.value != null || pendingAction != null) {
                        reconnectAttempts = 0
                        connect()
                    }
                }
            }
        }
    }

    private fun loadPersistedSession() {
        val token = context.dataStore.get(ListenTogetherSessionTokenKey, "")
        val roomCode = context.dataStore.get(ListenTogetherRoomCodeKey, "")
        val userId = context.dataStore.get(ListenTogetherUserIdKey, "")
        val isHost = context.dataStore.get(ListenTogetherIsHostKey, false)
        val timestamp = context.dataStore.get(ListenTogetherSessionTimestampKey, 0L)
        if (token.isNotEmpty() && roomCode.isNotEmpty() &&
            (System.currentTimeMillis() - timestamp < SESSION_GRACE_PERIOD_MS)
        ) {
            sessionToken = token
            storedRoomCode = roomCode
            _userId.value = userId.ifEmpty { null }
            wasHost = isHost
            sessionStartTime = timestamp
        } else if (token.isNotEmpty()) {
            clearPersistedSession()
        }
    }

    private fun savePersistedSession() {
        scope.launch {
            context.dataStore.edit { preferences ->
                if (sessionToken != null) {
                    preferences[ListenTogetherSessionTokenKey] = sessionToken!!
                    preferences[ListenTogetherRoomCodeKey] = storedRoomCode ?: ""
                    preferences[ListenTogetherUserIdKey] = _userId.value ?: ""
                    preferences[ListenTogetherIsHostKey] = wasHost
                    preferences[ListenTogetherSessionTimestampKey] = System.currentTimeMillis()
                }
            }
        }
    }

    private fun clearPersistedSession() {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences.remove(ListenTogetherSessionTokenKey)
                preferences.remove(ListenTogetherRoomCodeKey)
                preferences.remove(ListenTogetherUserIdKey)
                preferences.remove(ListenTogetherIsHostKey)
                preferences.remove(ListenTogetherSessionTimestampKey)
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectAttempts = 0

    private var sessionToken: String? = null
    private var storedUsername: String? = null
    private var storedRoomCode: String? = null
    private var wasHost: Boolean = false
    private var sessionStartTime: Long = 0

    private var pendingAction: PendingAction? = null

    private var wakeLock: PowerManager.WakeLock? = null

    private val joinRequestNotifications = mutableMapOf<String, Int>()

    private val suggestionNotifications = mutableMapOf<String, Int>()

    private val isNetworkAvailable = isInternetAvailable(context)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _roomState = MutableStateFlow<RoomState?>(null)
    val roomState: StateFlow<RoomState?> = _roomState.asStateFlow()

    private val _role = MutableStateFlow(RoomRole.NONE)
    val role: StateFlow<RoomRole> = _role.asStateFlow()

    private val _userId = MutableStateFlow<String?>(null)

    private val _pendingJoinRequests = MutableStateFlow<List<JoinRequestPayload>>(emptyList())
    val pendingJoinRequests: StateFlow<List<JoinRequestPayload>> = _pendingJoinRequests.asStateFlow()

    private val _bufferingUsers = MutableStateFlow<List<String>>(emptyList())
    val bufferingUsers: StateFlow<List<String>> = _bufferingUsers.asStateFlow()

    private val _pendingSuggestions = MutableStateFlow<List<SuggestionReceivedPayload>>(emptyList())
    val pendingSuggestions: StateFlow<List<SuggestionReceivedPayload>> = _pendingSuggestions.asStateFlow()

    private val _events = MutableSharedFlow<ListenTogetherEvent>()
    val events: SharedFlow<ListenTogetherEvent> = _events.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private fun getServerUrl(): String {
        return context.dataStore.get(ListenTogetherServerUrlKey, DEFAULT_SERVER_URL)
    }

    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = INITIAL_RECONNECT_DELAY_MS * (2 shl (minOf(attempt - 1, 4)))
        val cappedDelay = minOf(exponentialDelay, MAX_RECONNECT_DELAY_MS)
        val jitter = (cappedDelay * 0.2 * Math.random()).toLong()
        return cappedDelay + jitter
    }

    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) {
            return
        }
        _connectionState.value = ConnectionState.CONNECTING
        val request = Request.Builder()
            .url(getServerUrl())
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempts = 0
                startPingJob()
                if (sessionToken != null && storedRoomCode != null) {
                    sendMessage(MessageTypes.RECONNECT, ReconnectPayload(sessionToken!!))
                } else {
                    executePendingAction()
                }
            }

            @SuppressLint("MissingPermission")
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleConnectionFailure(t)
            }
        })
    }

    private fun executePendingAction() {
        val action = pendingAction ?: return
        pendingAction = null

        when (action) {
            is PendingAction.CreateRoom -> {
                sendMessage(MessageTypes.CREATE_ROOM, CreateRoomPayload(action.username))
            }
            is PendingAction.JoinRoom -> {
                sendMessage(MessageTypes.JOIN_ROOM, JoinRoomPayload(action.roomCode.uppercase(), action.username))
            }
        }
    }

    fun disconnect() {
        releaseWakeLock()
        pingJob?.cancel()
        pingJob = null
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        sessionToken = null
        storedRoomCode = null
        storedUsername = null
        pendingAction = null
        _roomState.value = null
        _role.value = RoomRole.NONE
        _userId.value = null
        _pendingJoinRequests.value = emptyList()
        _bufferingUsers.value = emptyList()
        clearPersistedSession()
        reconnectAttempts = 0

        scope.launch { _events.emit(ListenTogetherEvent.Disconnected) }
    }

    private fun startPingJob() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (true) {
                delay(PING_INTERVAL_MS)
                sendMessageNoPayload(MessageTypes.PING)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = context.getSystemService<PowerManager>()
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Muzza:ListenTogether"
            )
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(30 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    @SuppressLint("NewApi")
    private fun ensureNotificationChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        val existing = nm?.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.listen_together_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description =
                context.getString(R.string.listen_together_notification_channel_desc)
            nm?.createNotificationChannel(channel)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showJoinRequestNotification(payload: JoinRequestPayload) {
        val notifId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        joinRequestNotifications[payload.userId] = notifId

        val approveIntent = Intent(context, ListenTogetherActionReceiver::class.java).apply {
            action = ACTION_APPROVE_JOIN
            putExtra(EXTRA_USER_ID, payload.userId)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val rejectIntent = Intent(context, ListenTogetherActionReceiver::class.java).apply {
            action = ACTION_REJECT_JOIN
            putExtra(EXTRA_USER_ID, payload.userId)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }

        val approvePI = PendingIntent.getBroadcast(context, payload.userId.hashCode(), approveIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val rejectPI = PendingIntent.getBroadcast(context, payload.userId.hashCode().inv(), rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val content = context.getString(R.string.listen_together_join_request_notification, payload.username)

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.share)
            .setContentTitle(context.getString(R.string.listen_together))
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.approve), approvePI)
            .addAction(0, context.getString(R.string.reject), rejectPI)

        NotificationManagerCompat.from(context).notify(notifId, builder.build())
    }

    @SuppressLint("MissingPermission")
    private fun showSuggestionNotification(payload: SuggestionReceivedPayload) {
        val notifId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        suggestionNotifications[payload.suggestionId] = notifId
        val approveIntent = Intent(context, ListenTogetherActionReceiver::class.java).apply {
            action = ACTION_APPROVE_SUGGESTION
            putExtra(EXTRA_SUGGESTION_ID, payload.suggestionId)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val rejectIntent = Intent(context, ListenTogetherActionReceiver::class.java).apply {
            action = ACTION_REJECT_SUGGESTION
            putExtra(EXTRA_SUGGESTION_ID, payload.suggestionId)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val approvePI = PendingIntent.getBroadcast(context, payload.suggestionId.hashCode(), approveIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val rejectPI = PendingIntent.getBroadcast(context, payload.suggestionId.hashCode().inv(), rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val content = context.getString(R.string.listen_together_suggestion_received, payload.fromUsername, payload.trackInfo.title)
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.share)
            .setContentTitle(context.getString(R.string.listen_together))
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.approve), approvePI)
            .addAction(0, context.getString(R.string.reject), rejectPI)

        NotificationManagerCompat.from(context).notify(notifId, builder.build())
    }

    private fun handleDisconnect() {
        pingJob?.cancel()
        pingJob = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _pendingJoinRequests.value = emptyList()
        _bufferingUsers.value = emptyList()
        if (sessionToken != null && _roomState.value != null) {
            handleConnectionFailure(Exception("Connection lost"))
        } else {
            scope.launch { _events.emit(ListenTogetherEvent.Disconnected) }
        }
    }

    private fun handleConnectionFailure(t: Throwable) {
        pingJob?.cancel()
        pingJob = null
        val shouldReconnect = sessionToken != null || _roomState.value != null || pendingAction != null

        if (!isNetworkAvailable) {
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS && shouldReconnect) {
            reconnectAttempts++
            _connectionState.value = ConnectionState.RECONNECTING

            val delayMs = calculateBackoffDelay(reconnectAttempts)
            scope.launch {
                _events.emit(ListenTogetherEvent.Reconnecting(reconnectAttempts, MAX_RECONNECT_ATTEMPTS))
                delay(delayMs)
                if (_connectionState.value == ConnectionState.RECONNECTING || _connectionState.value == ConnectionState.DISCONNECTED) {
                    connect()
                }
            }
        } else {
            _connectionState.value = ConnectionState.ERROR
            if (sessionToken != null) {
                scope.launch {
                    _events.emit(ListenTogetherEvent.ConnectionError(
                        "Connection failed after $MAX_RECONNECT_ATTEMPTS attempts. ${t.message ?: "Unknown error"}"
                    ))
                }
            } else {
                sessionToken = null
                storedRoomCode = null
                storedUsername = null
                _roomState.value = null
                _role.value = RoomRole.NONE
                clearPersistedSession()
                scope.launch {
                    _events.emit(ListenTogetherEvent.ConnectionError(t.message ?: "Unknown error"))
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun handleMessage(text: String) {
            val message = json.decodeFromString<Message>(text)
            when (message.type) {
                MessageTypes.ROOM_CREATED -> {
                    val payload = json.decodeFromJsonElement<RoomCreatedPayload>(message.payload!!)
                    _userId.value = payload.userId
                    _role.value = RoomRole.HOST
                    sessionToken = payload.sessionToken
                    storedRoomCode = payload.roomCode
                    wasHost = true
                    sessionStartTime = System.currentTimeMillis()
                    _roomState.value = RoomState(
                        roomCode = payload.roomCode,
                        hostId = payload.userId,
                        users = listOf(UserInfo(payload.userId, storedUsername ?: "", true)),
                        isPlaying = false,
                        position = 0,
                        lastUpdate = System.currentTimeMillis()
                    )
                    savePersistedSession()

                    acquireWakeLock()
                    scope.launch { _events.emit(ListenTogetherEvent.RoomCreated(payload.roomCode, payload.userId)) }
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.listen_together_room_created, payload.roomCode),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                MessageTypes.JOIN_REQUEST -> {
                    val payload = json.decodeFromJsonElement<JoinRequestPayload>(message.payload!!)
                    _pendingJoinRequests.value += payload
                    if (_role.value == RoomRole.HOST) {
                        showJoinRequestNotification(payload)
                    }
                    scope.launch { _events.emit(ListenTogetherEvent.JoinRequestReceived(payload.userId, payload.username)) }
                }

                MessageTypes.JOIN_APPROVED -> {
                    val payload = json.decodeFromJsonElement<JoinApprovedPayload>(message.payload!!)
                    _userId.value = payload.userId
                    _role.value = RoomRole.GUEST
                    sessionToken = payload.sessionToken
                    storedRoomCode = payload.roomCode
                    wasHost = false
                    sessionStartTime = System.currentTimeMillis()
                    _roomState.value = payload.state
                    savePersistedSession()
                    acquireWakeLock()
                    scope.launch { _events.emit(ListenTogetherEvent.JoinApproved(payload.roomCode, payload.userId, payload.state)) }
                }

                MessageTypes.JOIN_REJECTED -> {
                    val payload = json.decodeFromJsonElement<JoinRejectedPayload>(message.payload!!)
                    scope.launch { _events.emit(ListenTogetherEvent.JoinRejected(payload.reason)) }
                }

                MessageTypes.USER_JOINED -> {
                    val payload = json.decodeFromJsonElement<UserJoinedPayload>(message.payload!!)
                    _roomState.value = _roomState.value?.copy(
                        users = _roomState.value!!.users + UserInfo(payload.userId, payload.username, false)
                    )
                    _pendingJoinRequests.value = _pendingJoinRequests.value.filter { it.userId != payload.userId }
                    joinRequestNotifications.remove(payload.userId)?.let { notifId ->
                        NotificationManagerCompat.from(context).cancel(notifId)
                    }

                    scope.launch { _events.emit(ListenTogetherEvent.UserJoined(payload.userId, payload.username)) }
                }

                MessageTypes.USER_LEFT -> {
                    val payload = json.decodeFromJsonElement<UserLeftPayload>(message.payload!!)
                    _roomState.value = _roomState.value?.copy(
                        users = _roomState.value!!.users.filter { it.userId != payload.userId }
                    )
                    scope.launch { _events.emit(ListenTogetherEvent.UserLeft(payload.userId, payload.username)) }
                }

                MessageTypes.HOST_CHANGED -> {
                    val payload = json.decodeFromJsonElement<HostChangedPayload>(message.payload!!)
                    _roomState.value = _roomState.value?.copy(
                        hostId = payload.newHostId,
                        users = _roomState.value!!.users.map {
                            it.copy(isHost = it.userId == payload.newHostId)
                        }
                    )
                    if (payload.newHostId == _userId.value) {
                        _role.value = RoomRole.HOST
                    }
                    scope.launch { _events.emit(ListenTogetherEvent.HostChanged(payload.newHostId, payload.newHostName)) }
                }

                MessageTypes.KICKED -> {
                    val payload = json.decodeFromJsonElement<KickedPayload>(message.payload!!)
                    releaseWakeLock()
                    sessionToken = null
                    _roomState.value = null
                    _role.value = RoomRole.NONE
                    scope.launch { _events.emit(ListenTogetherEvent.Kicked(payload.reason)) }
                }

                MessageTypes.SYNC_PLAYBACK -> {
                    val payload = json.decodeFromJsonElement<PlaybackActionPayload>(message.payload!!)
                    when (payload.action) {
                        PlaybackActions.PLAY -> {
                            _roomState.value = _roomState.value?.copy(
                                isPlaying = true,
                                position = payload.position ?: _roomState.value!!.position
                            )
                        }
                        PlaybackActions.PAUSE -> {
                            _roomState.value = _roomState.value?.copy(
                                isPlaying = false,
                                position = payload.position ?: _roomState.value!!.position
                            )
                        }
                        PlaybackActions.SEEK -> {
                            _roomState.value = _roomState.value?.copy(
                                position = payload.position ?: _roomState.value!!.position
                            )
                        }
                        PlaybackActions.CHANGE_TRACK -> {
                            _roomState.value = _roomState.value?.copy(
                                currentTrack = payload.trackInfo,
                                isPlaying = false,
                                position = 0
                            )
                        }
                        PlaybackActions.QUEUE_ADD -> {
                            val ti = payload.trackInfo
                            if (ti != null) {
                                val currentQueue = _roomState.value?.queue ?: emptyList()
                                _roomState.value = _roomState.value?.copy(
                                    queue = if (payload.insertNext == true) listOf(ti) + currentQueue else currentQueue + ti
                                )
                            }
                        }
                        PlaybackActions.QUEUE_REMOVE -> {
                            val id = payload.trackId
                            if (!id.isNullOrEmpty()) {
                                val currentQueue = _roomState.value?.queue ?: emptyList()
                                _roomState.value = _roomState.value?.copy(
                                    queue = currentQueue.filter { it.id != id }
                                )
                            }
                        }
                        PlaybackActions.QUEUE_CLEAR -> {
                            _roomState.value = _roomState.value?.copy(queue = emptyList())
                        }
                    }

                    scope.launch { _events.emit(ListenTogetherEvent.PlaybackSync(payload)) }
                }

                MessageTypes.BUFFER_WAIT -> {
                    val payload = json.decodeFromJsonElement<BufferWaitPayload>(message.payload!!)
                    _bufferingUsers.value = payload.waitingFor
                    scope.launch { _events.emit(ListenTogetherEvent.BufferWait(payload.trackId, payload.waitingFor)) }
                }

                MessageTypes.BUFFER_COMPLETE -> {
                    val payload = json.decodeFromJsonElement<BufferCompletePayload>(message.payload!!)
                    _bufferingUsers.value = emptyList()
                    scope.launch { _events.emit(ListenTogetherEvent.BufferComplete(payload.trackId)) }
                }

                MessageTypes.SYNC_STATE -> {
                    val payload = json.decodeFromJsonElement<SyncStatePayload>(message.payload!!)
                    scope.launch { _events.emit(ListenTogetherEvent.SyncStateReceived(payload)) }
                }

                MessageTypes.CHAT_MESSAGE -> {
                    val payload = json.decodeFromJsonElement<ChatMessagePayload>(message.payload!!)
                    scope.launch {
                        _events.emit(ListenTogetherEvent.ChatReceived(
                            payload.userId,
                            payload.username,
                            payload.message,
                            payload.timestamp
                        ))
                    }
                }

                MessageTypes.SUGGESTION_RECEIVED -> {
                    val payload = json.decodeFromJsonElement<SuggestionReceivedPayload>(message.payload!!)
                    if (_role.value == RoomRole.HOST) {
                        _pendingSuggestions.value += payload
                        showSuggestionNotification(payload)
                    }
                }

                MessageTypes.SUGGESTION_APPROVED -> {
                    val payload = json.decodeFromJsonElement<SuggestionApprovedPayload>(message.payload!!)
                    suggestionNotifications.remove(payload.suggestionId)?.let { notifId ->
                        NotificationManagerCompat.from(context).cancel(notifId)
                    }
                }

                MessageTypes.SUGGESTION_REJECTED -> {
                    val payload = json.decodeFromJsonElement<SuggestionRejectedPayload>(message.payload!!)
                    suggestionNotifications.remove(payload.suggestionId)?.let { notifId ->
                        NotificationManagerCompat.from(context).cancel(notifId)
                    }
                }

                MessageTypes.ERROR -> {
                    val payload = json.decodeFromJsonElement<ErrorPayload>(message.payload!!)
                    when (payload.code) {
                        "session_not_found" -> {
                            if (storedRoomCode != null && storedUsername != null && !wasHost) {
                                scope.launch {
                                    delay(500)
                                    joinRoom(storedRoomCode!!, storedUsername!!)
                                }
                            } else if (storedRoomCode != null && storedUsername != null) {
                                clearPersistedSession()
                                sessionToken = null
                            } else {
                                clearPersistedSession()
                                sessionToken = null
                            }
                        }
                        else -> {}
                    }

                    scope.launch { _events.emit(ListenTogetherEvent.ServerError(payload.code, payload.message)) }
                }

                MessageTypes.RECONNECTED -> {
                    val payload = json.decodeFromJsonElement<ReconnectedPayload>(message.payload!!)
                    _userId.value = payload.userId
                    _role.value = if (payload.isHost) RoomRole.HOST else RoomRole.GUEST
                    _roomState.value = payload.state

                    wasHost = payload.isHost
                    sessionStartTime = System.currentTimeMillis()
                    savePersistedSession()

                    reconnectAttempts = 0

                    acquireWakeLock()
                    scope.launch { _events.emit(ListenTogetherEvent.Reconnected(payload.roomCode, payload.userId, payload.state, payload.isHost)) }
                }

                MessageTypes.USER_RECONNECTED -> {
                    val payload = json.decodeFromJsonElement<UserReconnectedPayload>(message.payload!!)
                    _roomState.value = _roomState.value?.copy(
                        users = _roomState.value!!.users.map { user ->
                            if (user.userId == payload.userId) user.copy(isConnected = true) else user
                        }
                    )
                    scope.launch { _events.emit(ListenTogetherEvent.UserReconnected(payload.userId, payload.username)) }
                }

                MessageTypes.USER_DISCONNECTED -> {
                    val payload = json.decodeFromJsonElement<UserDisconnectedPayload>(message.payload!!)
                    _roomState.value = _roomState.value?.copy(
                        users = _roomState.value!!.users.map { user ->
                            if (user.userId == payload.userId) user.copy(isConnected = false) else user
                        }
                    )
                    scope.launch { _events.emit(ListenTogetherEvent.UserDisconnected(payload.userId, payload.username)) }
                }
            }
    }

    private inline fun <reified T> sendMessage(type: String, payload: T?) {
        val message = if (payload != null) {
            Message(type, json.encodeToJsonElement(payload))
        } else {
            Message(type, null)
        }
        val text = json.encodeToString(message)
        val success = webSocket?.send(text) ?: false
        if (!success) {
            return
        }
    }

    private fun sendMessageNoPayload(type: String) {
        val message = Message(type, null)
        val text = json.encodeToString(message)
        val success = webSocket?.send(text) ?: false
        if (!success) {
            return
        }
    }

    fun createRoom(username: String) {
        clearPersistedSession()
        sessionToken = null
        storedRoomCode = null
        wasHost = false

        storedUsername = username

        if (_connectionState.value == ConnectionState.CONNECTED) {
            sendMessage(MessageTypes.CREATE_ROOM, CreateRoomPayload(username))
        } else {
            pendingAction = PendingAction.CreateRoom(username)
            if (_connectionState.value == ConnectionState.DISCONNECTED ||
                _connectionState.value == ConnectionState.ERROR) {
                connect()
            }
        }
    }

    fun joinRoom(roomCode: String, username: String) {
        clearPersistedSession()
        sessionToken = null
        storedRoomCode = null
        wasHost = false
        storedUsername = username
        if (_connectionState.value == ConnectionState.CONNECTED) {
            sendMessage(MessageTypes.JOIN_ROOM, JoinRoomPayload(roomCode.uppercase(), username))
        } else {
            pendingAction = PendingAction.JoinRoom(roomCode, username)
            if (_connectionState.value == ConnectionState.DISCONNECTED ||
                _connectionState.value == ConnectionState.ERROR) {
                connect()
            }
        }
    }

    fun leaveRoom() {
        sendMessageNoPayload(MessageTypes.LEAVE_ROOM)
        sessionToken = null
        storedRoomCode = null
        storedUsername = null
        pendingAction = null
        _roomState.value = null
        _role.value = RoomRole.NONE
        _userId.value = null
        _pendingJoinRequests.value = emptyList()
        _bufferingUsers.value = emptyList()
        clearPersistedSession()

        releaseWakeLock()
    }

    fun approveJoin(userId: String) {
        if (_role.value != RoomRole.HOST) {
            return
        }
        sendMessage(MessageTypes.APPROVE_JOIN, ApproveJoinPayload(userId))
        joinRequestNotifications.remove(userId)?.let { notifId ->
            NotificationManagerCompat.from(context).cancel(notifId)
        }
    }

    fun rejectJoin(userId: String, reason: String? = null) {
        if (_role.value != RoomRole.HOST) {
            return
        }
        sendMessage(MessageTypes.REJECT_JOIN, RejectJoinPayload(userId, reason))
        _pendingJoinRequests.value = _pendingJoinRequests.value.filter { it.userId != userId }
        joinRequestNotifications.remove(userId)?.let { notifId ->
            NotificationManagerCompat.from(context).cancel(notifId)
        }
    }

    fun kickUser(userId: String, reason: String? = null) {
        if (_role.value != RoomRole.HOST) {
            return
        }
        sendMessage(MessageTypes.KICK_USER, KickUserPayload(userId, reason))
    }

    fun sendPlaybackAction(
        action: String,
        trackId: String? = null,
        position: Long? = null,
        trackInfo: TrackInfo? = null,
        insertNext: Boolean? = null,
        queue: List<TrackInfo>? = null,
        queueTitle: String? = null
    ) {
        if (_role.value != RoomRole.HOST) {
            return
        }
        sendMessage(MessageTypes.PLAYBACK_ACTION, PlaybackActionPayload(action, trackId, position, trackInfo, insertNext, queue, queueTitle))
    }

    fun sendBufferReady(trackId: String) {
        sendMessage(MessageTypes.BUFFER_READY, BufferReadyPayload(trackId))
    }

    fun suggestTrack(trackInfo: TrackInfo) {
        if (!isInRoom) {
            return
        }
        if (_role.value == RoomRole.HOST) {
            return
        }
        sendMessage(MessageTypes.SUGGEST_TRACK, SuggestTrackPayload(trackInfo))
        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.listen_together_suggestion_sent), Toast.LENGTH_SHORT).show()
        }
    }

    fun approveSuggestion(suggestionId: String) {
        if (_role.value != RoomRole.HOST) {
            return
        }
        sendMessage(MessageTypes.APPROVE_SUGGESTION, ApproveSuggestionPayload(suggestionId))
        _pendingSuggestions.value = _pendingSuggestions.value.filter { it.suggestionId != suggestionId }
        suggestionNotifications.remove(suggestionId)?.let { notifId ->
            NotificationManagerCompat.from(context).cancel(notifId)
        }
    }

    fun rejectSuggestion(suggestionId: String, reason: String? = null) {
        if (_role.value != RoomRole.HOST) {
            return
        }
        sendMessage(MessageTypes.REJECT_SUGGESTION, RejectSuggestionPayload(suggestionId, reason))
        _pendingSuggestions.value = _pendingSuggestions.value.filter { it.suggestionId != suggestionId }
        suggestionNotifications.remove(suggestionId)?.let { notifId ->
            NotificationManagerCompat.from(context).cancel(notifId)
        }
    }

    fun requestSync() {
        if (_roomState.value == null) {
            return
        }
        sendMessageNoPayload(MessageTypes.REQUEST_SYNC)
    }

    val isInRoom: Boolean
        get() = _roomState.value != null

    val isHost: Boolean
        get() = _role.value == RoomRole.HOST
}