package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.User
import com.example.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Handles all user session credentials, registration, profiles, and online presence.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FirebaseRepository(application)

    private val _currentUser = repository.getCurrentUserFlow()
    val currentUser: StateFlow<User?> = _currentUser

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _authProcessed = MutableStateFlow(false)
    val authProcessed: StateFlow<Boolean> = _authProcessed.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun isRealFirebase(): Boolean {
        return repository.isRealFirebase()
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository.loginUser(email, password)
                .onSuccess {
                    repository.setUserPresence(true)
                    _authProcessed.value = true
                }
                .onFailure { t ->
                    _error.value = t.message ?: "Authentication failed"
                }
            _isLoading.value = false
        }
    }

    fun register(name: String, email: String, phone: String, avatarUrl: String, status: String, pword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            // Choose an elegant avatar photo if none chosen
            val finalAvatar = avatarUrl.ifEmpty {
                "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&q=80&w=200"
            }
            
            val user = User(
                name = name,
                email = email,
                phone = phone,
                avatarUrl = finalAvatar,
                status = status.ifEmpty { "Gladly online on Global Call!" },
                isOnline = true
            )
            
            repository.registerUser(user, pword)
                .onSuccess {
                    _authProcessed.value = true
                }
                .onFailure { t ->
                    _error.value = t.message ?: "Registration failed. Account might already exist."
                }
            _isLoading.value = false
        }
    }

    fun updateProfile(name: String, status: String, avatarUrl: String, onFinished: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val success = repository.updateProfile(name, status, avatarUrl)
            onFinished(success)
            _isLoading.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _authProcessed.value = false
        }
    }

    fun resetAuthProcessed() {
        _authProcessed.value = false
    }
}
