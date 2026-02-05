package com.maloy.muzza.viewmodels

import androidx.lifecycle.ViewModel
import com.maloy.muzza.listentogether.ListenTogetherManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ListenTogetherViewModel @Inject constructor(
    private val manager: ListenTogetherManager
) : ViewModel() {
    val connectionState = manager.connectionState
    val roomState = manager.roomState
    val role = manager.role
    val pendingJoinRequests = manager.pendingJoinRequests
    val bufferingUsers = manager.bufferingUsers
    val events = manager.events

    init {
        manager.initialize()
    }

    fun connect() {
        manager.connect()
    }

    fun disconnect() {
        manager.disconnect()
    }

    fun createRoom(username: String) {
        manager.createRoom(username)
    }

    fun joinRoom(roomCode: String, username: String) {
        manager.joinRoom(roomCode, username)
    }

    fun leaveRoom() {
        manager.leaveRoom()
    }

    fun approveJoin(userId: String) {
        manager.approveJoin(userId)
    }

    fun rejectJoin(userId: String, reason: String? = null) {
        manager.rejectJoin(userId, reason)
    }

    fun kickUser(userId: String, reason: String? = null) {
        manager.kickUser(userId, reason)
    }
}