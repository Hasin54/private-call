package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.Call
import com.example.model.IceCandidateModel
import com.example.repository.FirebaseRepository
import com.example.webrtc.WebRtcClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.*

/**
 * Orchestrates WebRTC peer connections, SDP negotiation, local media captures,
 * audio/video track binds, and Firebase Firestore signaling updates.
 */
class CallViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "CallViewModel"
    private val repository = FirebaseRepository(application)

    // Shared EglBase context for video rendering
    val rootEglBaseContext: EglBase.Context = EglBase.create().eglBaseContext

    private var webrtcClient: WebRtcClient? = null

    // Call status and tracks
    private val _activeCall = MutableStateFlow<Call?>(null)
    val activeCall: StateFlow<Call?> = _activeCall.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _connectionState = MutableStateFlow(PeerConnection.IceConnectionState.NEW)
    val connectionState: StateFlow<PeerConnection.IceConnectionState> = _connectionState.asStateFlow()

    private val _incomingRingingCalls = MutableSharedFlow<Call>(replay = 1)
    val incomingRingingCalls: SharedFlow<Call> = _incomingRingingCalls.asSharedFlow()

    private var lastObservedStatus: String? = null

    init {
        // Automatically register to listen for any incoming calls ringing for this user!
        viewModelScope.launch {
            repository.listenForIncomingCalls().collect { call ->
                Log.d(TAG, "Incoming ringing call detected: ${call.callId} from ${call.callerName}")
                _incomingRingingCalls.emit(call)
            }
        }
    }

    /**
     * Places an outgoing audio or video WebRTC call to another user.
     */
    fun startCall(receiverId: String, callType: String) {
        viewModelScope.launch {
            cleanupActiveSession()
            Log.d(TAG, "Placing $callType call to: $receiverId")

            val rtcClient = WebRtcClient(getApplication(), rootEglBaseContext, createRtcListener())
            webrtcClient = rtcClient

            // Capture local inputs
            rtcClient.startLocalCapture(audioOnly = callType == "audio")
            rtcClient.createPeerConnection()

            // SDP Handshake (Create Offer)
            rtcClient.createOffer { sdpOffer ->
                viewModelScope.launch {
                    val callDoc = repository.createCall(receiverId, callType, sdpOffer.description)
                    _activeCall.value = callDoc
                    listenToSignalingHandshake(callDoc.callId)
                }
            }
        }
    }

    /**
     * Answers an incoming ringing call and exchanges SDP connections.
     */
    fun answerCall(call: Call) {
        viewModelScope.launch {
            cleanupActiveSession()
            _activeCall.value = call
            Log.d(TAG, "Answering income call: ${call.callId}")

            val rtcClient = WebRtcClient(getApplication(), rootEglBaseContext, createRtcListener())
            webrtcClient = rtcClient

            rtcClient.startLocalCapture(audioOnly = call.callType == "audio")
            rtcClient.createPeerConnection()

            // SDP Handshake (Create Answer response to remote offer)
            call.offerSdp?.let { offerSdp ->
                rtcClient.createAnswer(offerSdp) { sdpAnswer ->
                    viewModelScope.launch {
                        repository.acceptCall(call.callId, sdpAnswer.description)
                        listenToSignalingHandshake(call.callId)
                    }
                }
            }
        }
    }

    /**
     * Rejects an incoming ringing call.
     */
    fun rejectCall(callId: String) {
        viewModelScope.launch {
            repository.rejectCall(callId)
            cleanupActiveSession()
        }
    }

    /**
     * Hangs up and ends the current active call.
     */
    fun endCall() {
        val currentCall = _activeCall.value
        if (currentCall != null) {
            viewModelScope.launch {
                repository.endCall(currentCall.callId)
                cleanupActiveSession()
            }
        } else {
            cleanupActiveSession()
        }
    }

    private fun listenToSignalingHandshake(callId: String) {
        // Sync active signaling state
        viewModelScope.launch {
            repository.listenToCallSignaling(callId).collect { call ->
                if (call == null) return@collect
                _activeCall.value = call

                // Case: Sender receives remote answer and binds connection
                if (call.callerId == repository.getCurrentUserId() &&
                    call.callStatus == "accepted" &&
                    call.answerSdp != null &&
                    lastObservedStatus != "accepted"
                ) {
                    webrtcClient?.setRemoteAnswer(call.answerSdp)
                }

                // Cases: remote side terminates or declines the call
                if (call.callStatus == "rejected" || call.callStatus == "ended") {
                    cleanupActiveSession()
                }

                lastObservedStatus = call.callStatus
            }
        }

        // Sync ICE candidates
        viewModelScope.launch {
            repository.listenToIceCandidates(callId).collect { list ->
                val myId = repository.getCurrentUserId()
                for (ice in list) {
                    // Inject remote ICE candidates only
                    if (ice.senderId != myId) {
                        webrtcClient?.addIceCandidate(ice.sdpMid, ice.sdpMLineIndex, ice.sdp)
                    }
                }
            }
        }
    }

    private fun createRtcListener() = object : WebRtcClient.WebRtcListener {
        override fun onLocalStreamReady(videoTrack: VideoTrack?, audioTrack: AudioTrack?) {
            Log.d(TAG, "Local capture stream is ready to render.")
            _localVideoTrack.value = videoTrack
        }

        override fun onRemoteStreamReady(videoTrack: VideoTrack) {
            Log.d(TAG, "Remote capture track is ready to render.")
            _remoteVideoTrack.value = videoTrack
        }

        override fun onIceCandidateGathered(candidate: IceCandidate) {
            val callId = _activeCall.value?.callId
            if (callId != null) {
                viewModelScope.launch {
                    val model = IceCandidateModel(
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex,
                        sdp = candidate.sdp,
                        senderId = repository.getCurrentUserId()
                    )
                    repository.sendIceCandidate(callId, model)
                }
            }
        }

        override fun onIceConnectionStateChanged(state: PeerConnection.IceConnectionState) {
            _connectionState.value = state
            if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                state == PeerConnection.IceConnectionState.FAILED ||
                state == PeerConnection.IceConnectionState.CLOSED
            ) {
                cleanupActiveSession()
            }
        }
    }

    private fun cleanupActiveSession() {
        Log.d(TAG, "Clearing WebRTC active session and clean parameters.")
        webrtcClient?.close()
        webrtcClient = null
        _activeCall.value = null
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _connectionState.value = PeerConnection.IceConnectionState.NEW
        lastObservedStatus = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanupActiveSession()
    }
}
