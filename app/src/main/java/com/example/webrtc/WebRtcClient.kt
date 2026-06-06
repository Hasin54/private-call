package com.example.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import java.util.ArrayList

/**
 * Robust, highly documented WebRTC engine for Global Call.
 * Incorporates camera/mic capture configurations, peer connection creation,
 * SDP observation, and standard Google STUN servers.
 */
class WebRtcClient(
    private val context: Context,
    private val eglBaseContext: EglBase.Context,
    private val listener: WebRtcListener
) {

    private val TAG = "WebRtcClient"

    private var peerConnectionFactory: PeerConnectionFactory? = null
    var peerConnection: PeerConnection? = null
        private set

    private var rootEglBase: EglBase? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    interface WebRtcListener {
        fun onLocalStreamReady(videoTrack: VideoTrack?, audioTrack: AudioTrack?)
        fun onRemoteStreamReady(videoTrack: VideoTrack)
        fun onIceCandidateGathered(candidate: IceCandidate)
        fun onIceConnectionStateChanged(state: PeerConnection.IceConnectionState)
    }

    init {
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        Log.d(TAG, "Initializing PeerConnectionFactory...")
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()
        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()

        Log.d(TAG, "PeerConnectionFactory successfully generated.")
    }

    /**
     * Prepares capturing from local hardware (Mic & Front Camera).
     */
    fun startLocalCapture(audioOnly: Boolean) {
        if (peerConnectionFactory == null) {
            Log.e(TAG, "PeerConnectionFactory is null! Cannot capture media.")
            return
        }

        // Initialize Audio Track
        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        
        val audioSource = peerConnectionFactory!!.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory!!.createAudioTrack("local_audio_track_id", audioSource)
        localAudioTrack?.setEnabled(true)

        // Initialize Video Track (if not audio-only and hardware permissions permit)
        if (!audioOnly) {
            val capturer = createVideoCapturer()
            if (capturer != null) {
                videoCapturer = capturer
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
                val videoSource = peerConnectionFactory!!.createVideoSource(capturer.isScreencast)
                capturer.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
                
                // Start capturing at 30 fps, 1280x720 HD format
                capturer.startCapture(1280, 720, 30)

                localVideoTrack = peerConnectionFactory!!.createVideoTrack("local_video_track_id", videoSource)
                localVideoTrack?.setEnabled(true)
                Log.d(TAG, "Video hardware capture running.")
            } else {
                Log.e(TAG, "Failed to initialize high-res front camera capturer. Falling back to audio-only.")
            }
        }

        listener.onLocalStreamReady(localVideoTrack, localAudioTrack)
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val cameraEnumerator = if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator(context)
        } else {
            Camera1Enumerator(true)
        }

        val deviceNames = cameraEnumerator.deviceNames

        // First attempt: Prefer Front Camera
        for (deviceName in deviceNames) {
            if (cameraEnumerator.isFrontFacing(deviceName)) {
                val capturer = cameraEnumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }

        // Second attempt: Fallback to any camera
        for (deviceName in deviceNames) {
            val capturer = cameraEnumerator.createCapturer(deviceName, null)
            if (capturer != null) return capturer
        }

        return null
    }

    /**
     * Generates a structural WebRTC PeerConnection with standard public Google STUN servers.
     */
    fun createPeerConnection() {
        val iceServers = ArrayList<PeerConnection.IceServer>()
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
        iceServers.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer())

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY

        val rtcObserver = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.e(TAG, "ICE Connection status: $state")
                state?.let { listener.onIceConnectionStateChanged(it) }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "ICE Candidate Gathered. SDP: ${it.sdp}")
                    listener.onIceCandidateGathered(it)
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                receiver?.track()?.let { track ->
                    if (track is VideoTrack) {
                        track.setEnabled(true)
                        Log.d(TAG, "Remote Video Track successfully registered.")
                        listener.onRemoteStreamReady(track)
                    }
                }
            }
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, rtcObserver)
        Log.d(TAG, "PeerConnection established.")

        // Add tracks to PeerConnection
        localAudioTrack?.let { peerConnection?.addTrack(it, listOf("local_media_stream")) }
        localVideoTrack?.let { peerConnection?.addTrack(it, listOf("local_media_stream")) }
    }

    /**
     * Generates a Session Description Offer to start the signal handshake.
     */
    fun createOffer(callback: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(s: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local offer setSuccess.")
                            callback(desc)
                        }
                        override fun onCreateFailure(s: String?) {}
                        override fun onSetFailure(s: String?) {
                            Log.e(TAG, "Failed set local description: $s")
                        }
                    }, desc)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed createOffer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    /**
     * Receives a Connection Offer and replies with an Answer.
     */
    fun createAnswer(remoteOfferSdp: String, callback: (SessionDescription) -> Unit) {
        val remoteOffer = SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(s: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set successfully, creating response Answer...")
                val constraints = MediaConstraints()
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        if (desc != null) {
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(s: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    callback(desc)
                                }
                                override fun onCreateFailure(s: String?) {}
                                override fun onSetFailure(s: String?) {}
                            }, desc)
                        }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(s: String?) {}
                    override fun onSetFailure(s: String?) {}
                }, constraints)
            }
            override fun onCreateFailure(s: String?) {}
            override fun onSetFailure(s: String?) {
                Log.e(TAG, "Failed set remote offer: $s")
            }
        }, remoteOffer)
    }

    /**
     * Completes handshake binding by setting the remote Answer.
     */
    fun setRemoteAnswer(remoteAnswerSdp: String) {
        val remoteAnswer = SessionDescription(SessionDescription.Type.ANSWER, remoteAnswerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(s: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote answer successfully bound.")
            }
            override fun onCreateFailure(s: String?) {}
            override fun onSetFailure(s: String?) {}
        }, remoteAnswer)
    }

    /**
     * Adds an incoming ICE Candidate from our signaling subscriber.
     */
    fun addIceCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        peerConnection?.addIceCandidate(candidate)
        Log.d(TAG, "Injected remote ICE Candidate: $sdpMid index $sdpMLineIndex")
    }

    /**
     * Closes the active session, freeing mic, camera capture thread, and peer resources neatly.
     */
    fun close() {
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Capturer stop error", e)
        }
        videoCapturer?.dispose()
        surfaceTextureHelper?.dispose()

        peerConnection?.close()
        peerConnection = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        Log.d(TAG, "WebRTC resources cleared.")
    }
}
