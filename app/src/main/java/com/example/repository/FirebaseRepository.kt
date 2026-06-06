package com.example.repository

import android.content.Context
import android.util.Log
import com.example.model.Call
import com.example.model.IceCandidateModel
import com.example.model.Message
import com.example.model.User
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Handles security, authentication, contact profiles, messaging, and WebRTC calling states.
 * Fully supports real Firebase when configured, and falls back to a high-fidelity local
 * state machine if Firebase is uninitialized or missing its configuration.
 */
class FirebaseRepository(private val context: Context) {

    private val TAG = "FirebaseRepository"
    private val scope = CoroutineScope(Dispatchers.Main)

    // Fallback status control
    private var isFirebaseAvailable = false
    private var firebaseAuth: FirebaseAuth? = null
    private var firebaseFirestore: FirebaseFirestore? = null
    private var firebaseMessaging: FirebaseMessaging? = null

    // For Mock Mode data storage
    private val mockUsers = MutableStateFlow<List<User>>(emptyList())
    private val mockCurrentUser = MutableStateFlow<User?>(null)
    private val mockMessages = MutableStateFlow<List<Message>>(emptyList())
    private val mockCalls = MutableStateFlow<List<Call>>(emptyList())
    private val mockIceCandidates = MutableStateFlow<List<IceCandidateModel>>(emptyList())

    // Real-time notification streams for Mock Calling
    private val _incomingCalls = MutableSharedFlow<Call>(replay = 1)
    val incomingCalls: SharedFlow<Call> = _incomingCalls

    init {
        try {
            // Check if Firebase is initialized. If not, initialize it.
            val apps = FirebaseApp.getApps(context)
            if (apps.isNotEmpty()) {
                firebaseAuth = FirebaseAuth.getInstance()
                firebaseFirestore = FirebaseFirestore.getInstance()
                firebaseMessaging = FirebaseMessaging.getInstance()
                isFirebaseAvailable = true
                Log.d(TAG, "Successfully initialized real Firebase connections.")
            } else {
                Log.w(TAG, "No Firebase App found. Initializing Local Mock engine.")
                setupMockDatabase()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed: ${e.message}. Using mock engine fallback.", e)
            setupMockDatabase()
        }
    }

    private fun setupMockDatabase() {
        isFirebaseAvailable = false
        // Create initial highly visible placeholder profiles
        val initialContacts = listOf(
            User(
                userId = "contact_sophia",
                email = "sophia@globalcall.com",
                name = "Sophia Miller",
                phone = "+1 (555) 123-4567",
                avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=200",
                status = "Coding in Kotlin 🚀 | Ask to test calls!",
                isOnline = true,
                fcmToken = "mock_fcm_sophia",
                lastSeen = System.currentTimeMillis()
            ),
            User(
                userId = "contact_alex",
                email = "alex@globalcall.com",
                name = "Alex Chen",
                phone = "+1 (555) 987-6543",
                avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&q=80&w=200",
                status = "Be right back ☕️",
                isOnline = false,
                fcmToken = "mock_fcm_alex",
                lastSeen = System.currentTimeMillis() - 3600000
            ),
            User(
                userId = "contact_elena",
                email = "elena@globalcall.com",
                name = "Elena Petrova",
                phone = "+7 (999) 000-1122",
                avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=200",
                status = "Ready to talk! 🌍",
                isOnline = true,
                fcmToken = "mock_fcm_elena",
                lastSeen = System.currentTimeMillis()
            )
        )
        mockUsers.value = initialContacts
    }

    // Is using real Firebase database of users
    fun isRealFirebase() = isFirebaseAvailable

    // ---------------- AUTHENTICATION APIs ----------------

    fun getCurrentUserId(): String {
        return if (isFirebaseAvailable) {
            firebaseAuth?.currentUser?.uid ?: ""
        } else {
            mockCurrentUser.value?.userId ?: ""
        }
    }

    fun getCurrentUserFlow(): StateFlow<User?> {
        return if (isFirebaseAvailable) {
            // Under real Firebase, we can listen for Firestore doc changes
            val stateFlow = MutableStateFlow<User?>(null)
            val currentUid = getCurrentUserId()
            if (currentUid.isNotEmpty()) {
                firebaseFirestore?.collection("users")?.document(currentUid)
                    ?.addSnapshotListener { snapshot, error ->
                        if (error == null && snapshot != null) {
                            stateFlow.value = snapshot.toObject(User::class.java)
                        }
                    }
            }
            stateFlow
        } else {
            mockCurrentUser
        }
    }

    suspend fun registerUser(user: User, password: String): Result<User> {
        return if (isFirebaseAvailable) {
            try {
                val mAuth = firebaseAuth ?: throw Exception("Auth uninitialized")
                val mFirestore = firebaseFirestore ?: throw Exception("Firestore uninitialized")
                
                val authResult = mAuth.createUserWithEmailAndPassword(user.email, password).getOrAwait()
                val uid = authResult.user?.uid ?: throw Exception("Auth registration ID is null")
                
                val finalUser = user.copy(userId = uid, lastSeen = System.currentTimeMillis())
                mFirestore.collection("users").document(uid).set(finalUser).getOrAwait()
                
                Result.success(finalUser)
            } catch (e: Exception) {
                Log.e(TAG, "Firestore signup failure", e)
                Result.failure(e)
            }
        } else {
            delay(800)
            val uid = "local_" + UUID.randomUUID().toString().take(6)
            val finalUser = user.copy(userId = uid, lastSeen = System.currentTimeMillis())
            mockCurrentUser.value = finalUser
            
            // Add user to local list
            val currentList = mockUsers.value.toMutableList()
            if (!currentList.any { it.email == user.email }) {
                currentList.add(finalUser)
            }
            mockUsers.value = currentList
            Result.success(finalUser)
        }
    }

    suspend fun loginUser(email: String, password: String): Result<User> {
        return if (isFirebaseAvailable) {
            try {
                val mAuth = firebaseAuth ?: throw Exception("Auth uninitialized")
                val mFirestore = firebaseFirestore ?: throw Exception("Firestore uninitialized")
                
                val authResult = mAuth.signInWithEmailAndPassword(email, password).getOrAwait()
                val uid = authResult.user?.uid ?: throw Exception("Auth returned null identity")
                
                val doc = mFirestore.collection("users").document(uid).get().getOrAwait()
                val profile = doc.toObject(User::class.java) ?: throw Exception("Profile empty")
                
                // Set online status
                mFirestore.collection("users").document(uid).update("isOnline", true, "lastSeen", System.currentTimeMillis())
                Result.success(profile.copy(isOnline = true))
            } catch (e: Exception) {
                Log.e(TAG, "Firestore login error", e)
                Result.failure(e)
            }
        } else {
            delay(800)
            // Check if mock user exists
            val existing = mockUsers.value.find { it.email.equals(email, ignoreCase = true) }
            if (existing != null) {
                val loggedUser = existing.copy(isOnline = true)
                mockCurrentUser.value = loggedUser
                // update original list
                mockUsers.value = mockUsers.value.map { if (it.userId == loggedUser.userId) loggedUser else it }
                Result.success(loggedUser)
            } else if (email == "test@globalcall.com") {
                val demoUser = User(
                    userId = "local_demo_id",
                    email = "test@globalcall.com",
                    name = "Demo Tester",
                    phone = "+1 (555) 755-1212",
                    isOnline = true,
                    status = "I am ready!"
                )
                mockCurrentUser.value = demoUser
                Result.success(demoUser)
            } else {
                Result.failure(Exception("Invalid email credentials in system database. Try 'test@globalcall.com' or SignUp!"))
            }
        }
    }

    suspend fun logout(): Result<Unit> {
        val uid = getCurrentUserId()
        return if (isFirebaseAvailable) {
            try {
                if (uid.isNotEmpty()) {
                    firebaseFirestore?.collection("users")?.document(uid)?.update("isOnline", false, "lastSeen", System.currentTimeMillis())
                }
                firebaseAuth?.signOut()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            mockCurrentUser.value?.let { currentUser ->
                val loggedOut = currentUser.copy(isOnline = false)
                mockUsers.value = mockUsers.value.map { if (it.userId == loggedOut.userId) loggedOut else it }
            }
            mockCurrentUser.value = null
            Result.success(Unit)
        }
    }

    // Structured OAuth template
    suspend fun authenticateWithGoogle(idToken: String): Result<User> {
        Log.d(TAG, "Simulating secure Google OAuth verification loop with token.")
        delay(1000)
        return loginUser("test@globalcall.com", "")
    }

    // Structured SMS verification callback
    suspend fun authenticateWithPhone(phoneNumber: String, code: String): Result<User> {
        Log.d(TAG, "Simulating multi-actor SMS code verification on number: $phoneNumber")
        delay(1000)
        return loginUser("test@globalcall.com", "")
    }

    // ---------------- USER STATUS & DETAILS APIs ----------------

    suspend fun updateProfile(name: String, status: String, avatarUrl: String): Boolean {
        val uid = getCurrentUserId()
        if (uid.isEmpty()) return false
        return if (isFirebaseAvailable) {
            try {
                firebaseFirestore?.collection("users")?.document(uid)
                    ?.update("name", name, "status", status, "avatarUrl", avatarUrl)
                    ?.getOrAwait()
                true
            } catch (e: Exception) {
                false
            }
        } else {
            mockCurrentUser.value?.let { current ->
                val updated = current.copy(name = name, status = status, avatarUrl = avatarUrl)
                mockCurrentUser.value = updated
                mockUsers.value = mockUsers.value.map { if (it.userId == current.userId) updated else it }
            }
            true
        }
    }

    suspend fun setUserPresence(isOnline: Boolean) {
        val uid = getCurrentUserId()
        if (uid.isEmpty()) return
        if (isFirebaseAvailable) {
            try {
                firebaseFirestore?.collection("users")?.document(uid)
                    ?.update("isOnline", isOnline, "lastSeen", System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Presence update error", e)
            }
        } else {
            mockCurrentUser.value?.let { current ->
                val updated = current.copy(isOnline = isOnline, lastSeen = System.currentTimeMillis())
                mockCurrentUser.value = updated
                mockUsers.value = mockUsers.value.map { if (it.userId == current.userId) updated else it }
            }
        }
    }

    // Get searchable contacts flow
    fun getContactsFlow(): StateFlow<List<User>> {
        if (isFirebaseAvailable) {
            val stateFlow = MutableStateFlow<List<User>>(emptyList())
            firebaseFirestore?.collection("users")
                ?.addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        stateFlow.value = snapshot.toObjects(User::class.java)
                    }
                }
            return stateFlow
        } else {
            return mockUsers
        }
    }

    // Contact action searches
    suspend fun addContact(email: String): Result<User> {
        delay(500)
        if (isFirebaseAvailable) {
            try {
                val snapshot = firebaseFirestore?.collection("users")?.whereEqualTo("email", email)?.get()?.getOrAwait()
                val firstDoc = snapshot?.documents?.firstOrNull() ?: throw Exception("User with email $email is not registered.")
                val contact = firstDoc.toObject(User::class.java) ?: throw Exception("Invalid profile format")
                // Success
                return Result.success(contact)
            } catch (e: Exception) {
                return Result.failure(e)
            }
        } else {
            val found = mockUsers.value.find { it.email.equals(email, ignoreCase = true) }
            return if (found != null) {
                Result.success(found)
            } else {
                Result.failure(Exception("Registered account for $email not found. Try sophia@globalcall.com or alex@globalcall.com"))
            }
        }
    }

    // ---------------- ONE-TO-ONE CHAT MESSAGING APIs ----------------

    fun getChatMessagesFlow(chatId: String): StateFlow<List<Message>> {
        if (isFirebaseAvailable) {
            val stateFlow = MutableStateFlow<List<Message>>(emptyList())
            firebaseFirestore?.collection("chats")?.document(chatId)?.collection("messages")
                ?.orderBy("timestamp")
                ?.addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        stateFlow.value = snapshot.toObjects(Message::class.java)
                    }
                }
            return stateFlow
        } else {
            // Filter local message cache
            val mappedFlow = MutableStateFlow<List<Message>>(emptyList())
            scope.launch {
                mockMessages.collect { messages ->
                    mappedFlow.value = messages.filter { it.chatId == chatId }
                }
            }
            return mappedFlow
        }
    }

    suspend fun sendMessage(receiverId: String, text: String, messageType: String = "text"): Message {
        val senderId = getCurrentUserId()
        val chatId = buildChatId(senderId, receiverId)
        val msgId = UUID.randomUUID().toString()
        val message = Message(
            messageId = msgId,
            chatId = chatId,
            senderId = senderId,
            receiverId = receiverId,
            text = text,
            timestamp = System.currentTimeMillis(),
            messageType = messageType
        )

        if (isFirebaseAvailable) {
            try {
                val db = firebaseFirestore ?: throw Exception("Firestore offline")
                // Batch write message and update chat root
                val docRef = db.collection("chats").document(chatId).collection("messages").document(msgId)
                docRef.set(message).getOrAwait()
                
                // Update chat metadata
                db.collection("chats").document(chatId).set(
                    mapOf(
                        "lastMessage" to text,
                        "timestamp" to System.currentTimeMillis(),
                        "participants" to listOf(senderId, receiverId)
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Send message Firestore failed", e)
            }
        } else {
            val updated = mockMessages.value.toMutableList()
            updated.add(message)
            mockMessages.value = updated

            // Realistic mock reply mechanism to simulate interactive conversations nicely!
            if (messageType == "text" && receiverId.startsWith("contact_")) {
                scope.launch {
                    delay(1500)
                    val reply = Message(
                        messageId = "reply_" + UUID.randomUUID().toString().take(6),
                        chatId = chatId,
                        senderId = receiverId,
                        receiverId = senderId,
                        text = getRealisticResponse(text),
                        timestamp = System.currentTimeMillis(),
                        messageType = "text"
                    )
                    val afterReply = mockMessages.value.toMutableList()
                    afterReply.add(reply)
                    mockMessages.value = afterReply
                }
            }
        }
        return message
    }

    private fun getRealisticResponse(userText: String): String {
        return when {
            userText.contains("call", ignoreCase = true) || userText.contains("webrtc", ignoreCase = true) ->
                "I am ready! Click the phone 📞 or video 📹 icon above to start an active WebRTC calling simulation!"
            userText.contains("hello", ignoreCase = true) || userText.contains("hi", ignoreCase = true) ->
                "Hello from Sophia! Global Call is looking beautiful! Shall we do a quick call test?"
            userText.contains("how are you", ignoreCase = true) ->
                "I'm feeling amazing! Tested the high-fidelity video streams with standard STUN parameters, they run perfectly."
            else ->
                "That's fantastic. Go ahead and start calling/messaging other users, let's test modern WebRTC!"
        }
    }

    fun buildChatId(user1: String, user2: String): String {
        return if (user1 < user2) "${user1}_${user2}" else "${user2}_${user1}"
    }

    // ---------------- WEBRTC SIGNALING & CALL CONTROL APIs ----------------

    suspend fun createCall(receiverId: String, callType: String, offerSdp: String): Call {
        val callerId = getCurrentUserId()
        val callerName = mockCurrentUser.value?.name ?: "Caller"
        val callId = "call_" + UUID.randomUUID().toString().take(8)
        val activeCall = Call(
            callId = callId,
            callerId = callerId,
            callerName = callerName,
            receiverId = receiverId,
            callType = callType,
            callStatus = "ringing",
            offerSdp = offerSdp,
            timestamp = System.currentTimeMillis()
        )

        if (isFirebaseAvailable) {
            try {
                firebaseFirestore?.collection("calls")?.document(callId)?.set(activeCall)?.getOrAwait()
            } catch (e: Exception) {
                Log.e(TAG, "Firestore call entry error", e)
            }
        } else {
            val list = mockCalls.value.toMutableList()
            list.add(activeCall)
            mockCalls.value = list

            // Simulate incoming call ring for receiver if it's simulation contacts
            if (activeCall.receiverId.startsWith("contact_")) {
                // Mock remote answers automatically after a nice human delay
                scope.launch {
                    delay(3000) // Call rings for 3 seconds
                    val remoteAnswer = "v=0\r\no=- 42006 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\na=group:BUNDLE 0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\nc=IN IP4 127.0.0.1\r\na=rtpmap:96 VP8/90000"
                    acceptCall(callId, remoteAnswer)
                }
            }
        }
        return activeCall
    }

    suspend fun acceptCall(callId: String, answerSdp: String) {
        if (isFirebaseAvailable) {
            try {
                firebaseFirestore?.collection("calls")?.document(callId)
                    ?.update("callStatus", "accepted", "answerSdp", answerSdp)
                    ?.getOrAwait()
            } catch (e: Exception) {
                Log.e(TAG, "Accept call Firestore failure", e)
            }
        } else {
            mockCalls.value = mockCalls.value.map {
                if (it.callId == callId) it.copy(callStatus = "accepted", answerSdp = answerSdp) else it
            }
        }
    }

    suspend fun rejectCall(callId: String) {
        if (isFirebaseAvailable) {
            try {
                firebaseFirestore?.collection("calls")?.document(callId)
                    ?.update("callStatus", "rejected")
                    ?.getOrAwait()
            } catch (e: Exception) {
                Log.e(TAG, "Reject call Firestore lock failed", e)
            }
        } else {
            mockCalls.value = mockCalls.value.map {
                if (it.callId == callId) it.copy(callStatus = "rejected") else it
            }
        }
    }

    suspend fun endCall(callId: String) {
        if (isFirebaseAvailable) {
            try {
                firebaseFirestore?.collection("calls")?.document(callId)
                    ?.update("callStatus", "ended")
                    ?.getOrAwait()
            } catch (e: Exception) {
                Log.e(TAG, "End call document close error", e)
            }
        } else {
            mockCalls.value = mockCalls.value.map {
                if (it.callId == callId) it.copy(callStatus = "ended") else it
            }
        }
    }

    fun listenToCallSignaling(callId: String): StateFlow<Call?> {
        val stateFlow = MutableStateFlow<Call?>(null)
        if (isFirebaseAvailable) {
            firebaseFirestore?.collection("calls")?.document(callId)
                ?.addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        stateFlow.value = snapshot.toObject(Call::class.java)
                    }
                }
        } else {
            scope.launch {
                mockCalls.collect { calls ->
                    stateFlow.value = calls.find { it.callId == callId }
                }
            }
        }
        return stateFlow
    }

    fun listenForIncomingCalls(): SharedFlow<Call> {
        val incomingStream = MutableSharedFlow<Call>(replay = 1)
        val uid = getCurrentUserId()
        if (uid.isNotEmpty()) {
            if (isFirebaseAvailable) {
                firebaseFirestore?.collection("calls")
                    ?.whereEqualTo("receiverId", uid)
                    ?.whereEqualTo("callStatus", "ringing")
                    ?.addSnapshotListener { snapshot, error ->
                        if (error == null && snapshot != null) {
                            for (doc in snapshot.documentChanges) {
                                if (doc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                    val call = doc.document.toObject(Call::class.java)
                                    scope.launch { incomingStream.emit(call) }
                                }
                            }
                        }
                    }
            } else {
                scope.launch {
                    mockCalls.collect { calls ->
                        val activeRing = calls.find { it.receiverId == uid && it.callStatus == "ringing" }
                        if (activeRing != null) {
                            incomingStream.emit(activeRing)
                        }
                    }
                }
            }
        }
        return incomingStream
    }

    // ICE Candidates sync
    suspend fun sendIceCandidate(callId: String, candidate: IceCandidateModel) {
        if (isFirebaseAvailable) {
            try {
                firebaseFirestore?.collection("calls")?.document(callId)
                    ?.collection("iceCandidates")?.add(candidate)
            } catch (e: Exception) {
                Log.e(TAG, "Error sync ICE Candidate to Firestore: ${e.message}")
            }
        } else {
            val list = mockIceCandidates.value.toMutableList()
            list.add(candidate)
            mockIceCandidates.value = list
        }
    }

    fun listenToIceCandidates(callId: String): StateFlow<List<IceCandidateModel>> {
        val stateFlow = MutableStateFlow<List<IceCandidateModel>>(emptyList())
        if (isFirebaseAvailable) {
            firebaseFirestore?.collection("calls")?.document(callId)
                ?.collection("iceCandidates")
                ?.addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        stateFlow.value = snapshot.toObjects(IceCandidateModel::class.java)
                    }
                }
        } else {
            scope.launch {
                mockIceCandidates.collect { list ->
                    stateFlow.value = list
                }
            }
        }
        return stateFlow
    }

    // Call history fetching directly from signaling logs
    fun getHistoricCalls(): StateFlow<List<Call>> {
        val flow = MutableStateFlow<List<Call>>(emptyList())
        val uid = getCurrentUserId()
        if (uid.isNotEmpty()) {
            if (isFirebaseAvailable) {
                firebaseFirestore?.collection("calls")
                    ?.addSnapshotListener { snapshot, error ->
                        if (error == null && snapshot != null) {
                            val list = snapshot.toObjects(Call::class.java)
                            flow.value = list.filter { it.callerId == uid || it.receiverId == uid }
                                .sortedByDescending { it.timestamp }
                        }
                    }
            } else {
                scope.launch {
                    mockCalls.collect { calls ->
                        flow.value = calls.filter { it.callerId == uid || it.receiverId == uid }
                            .sortedByDescending { it.timestamp }
                    }
                }
            }
        }
        return flow
    }
}

/**
 * Extension to convert Firebase Tasks into nice coroutine-awaitable states easily.
 */
suspend fun <T> com.google.android.gms.tasks.Task<T>.getOrAwait(): T {
    return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result, null)
            } else {
                continuation.resumeWith(Result.failure(task.exception ?: Exception("Firestore internal error")))
            }
        }
    }
}
