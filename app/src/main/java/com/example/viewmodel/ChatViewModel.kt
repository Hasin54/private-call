package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.Call
import com.example.model.Message
import com.example.model.User
import com.example.repository.FirebaseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Handles lists of contact friends, real-time message sending/receiving,
 * searching and adding contacts, and pulling call histories.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FirebaseRepository(application)

    private val _contacts = repository.getContactsFlow()
    val contacts: StateFlow<List<User>> = _contacts

    private val _historicCalls = repository.getHistoricCalls()
    val historicCalls: StateFlow<List<Call>> = _historicCalls

    private val _activeMessages = MutableStateFlow<List<Message>>(emptyList())
    val activeMessages: StateFlow<List<Message>> = _activeMessages.asStateFlow()

    private val _searchResult = MutableStateFlow<User?>(null)
    val searchResult: StateFlow<User?> = _searchResult.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private var activeMessageSubscriptionJob: Job? = null

    /**
     * Searches for a contact via email and returns their profile.
     */
    fun searchAndAddContact(email: String) {
        viewModelScope.launch {
            _searchLoading.value = true
            _searchError.value = null
            _searchResult.value = null

            repository.addContact(email)
                .onSuccess { contact ->
                    _searchResult.value = contact
                }
                .onFailure { t ->
                    _searchError.value = t.message ?: "Account not found"
                }
            _searchLoading.value = false
        }
    }

    /**
     * Subscribes to changes in a direct messaging feed.
     */
    fun loadMessagesForChat(chatId: String) {
        // Cancel any active polling/listen job first
        activeMessageSubscriptionJob?.cancel()
        activeMessageSubscriptionJob = viewModelScope.launch {
            repository.getChatMessagesFlow(chatId).collect { list ->
                _activeMessages.value = list
            }
        }
    }

    /**
     * Helper to load chats for a participant.
     */
    fun getChatId(otherUserId: String): String {
        return repository.buildChatId(repository.getCurrentUserId(), otherUserId)
    }

    /**
     * Sends a direct text message.
     */
    fun sendMessage(receiverId: String, text: String) {
        if (text.trim().isEmpty()) return
        viewModelScope.launch {
            repository.sendMessage(receiverId, text.trim())
        }
    }

    /**
     * Records a calling log inside chat logs, representing a connection attempt.
     */
    fun recordCallHistoryLogInChat(receiverId: String, callType: String, logText: String) {
        viewModelScope.launch {
            repository.sendMessage(
                receiverId = receiverId,
                text = logText,
                messageType = if (callType == "video") "video_call_log" else "audio_call_log"
            )
        }
    }

    fun clearSearch() {
        _searchResult.value = null
        _searchError.value = null
    }
}
