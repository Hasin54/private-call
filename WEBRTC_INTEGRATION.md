# WebRTC Calling Integration Architecture

This document describes the high-fidelity **WebRTC Calling Engine** integrated into **Global Call**, documenting how standard peer connections are established, and how Firestore acts as a real-time signaling channel.

---

## 1. High-Level Signaling Lifecycle

A full peer-to-peer connection requires exchanging connection parameters (SDP Session Descriptions) and network routes (ICE Candidates). The diagram below illustrates the exact handshake pattern driving the app:

```
[Caller Client]                                           [Receiver Client]
     |                                                            |
     |--- 1. Capture Micro/Camera Media                          |
     |--- 2. Create PeerConnection & Local SDP Offer              |
     |                                                            |
     |--- 3. Upload Offer to Firestore calls/ ------------------->| (Rings in overlay via
     |                                                            |  addedSnapshot listener!)
     |                                                            |
     |                                                            |--- 4. Capture Local Media
     |                                                            |--- 5. Create PeerConnection
     |                                                            |--- 6. Set Remote Offer Description
     |                                                            |--- 7. Generate Response SDP Answer
     |                                                            |
     |<-- 8. Write Answer SDP to Firestore calls/ ----------------|
     |                                                            |
     |--- 9. Set Remote Answer Description                        |
     |                                                            |
     |================== P2P DIRECT STREAM CONNECTED =============|
     |                                                            |
     |--- 10. Exchange ICE Candidates real-time (subcollections) -|
```

---

## 2. PeerConnection & STUN Configuration

The PeerConnection config is declared in `WebRtcClient.kt`, implementing the **Unified Plan** SDP semantics. We utilize Google's public STUN servers for NAT traversal:

```kotlin
val iceServers = ArrayList<PeerConnection.IceServer>().apply {
    add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
    add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
    add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer())
}
```

---

## 3. Media Gathering and Resilient Capture Fallbacks

Front camera recording is initialized through standard CameraEnumerator interfaces:

```kotlin
private fun createVideoCapturer(): VideoCapturer? {
    val cameraEnumerator = if (Camera2Enumerator.isSupported(context)) {
        Camera2Enumerator(context)
    } else {
        Camera1Enumerator(true)
    }
    // Safely prioritizes FRONT-FACING camera ...
}
```

If the hardware context fails or permissions are denied on systems (e.g. emulators), calling routines catch capture exceptions gracefully, disabling video tracks, and automatically falling back to **Audio-Only simulation modes** without crashing the app.

---

## 4. Jetpack Compose Integration

To render active remote video tracks safely, we bind native `SurfaceViewRenderer` containers inside Compose using `AndroidView`:

```kotlin
AndroidView(
    factory = { ctx ->
        SurfaceViewRenderer(ctx).apply {
            init(callViewModel.rootEglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            remoteVideoTrack?.addSink(this)
        }
    },
    modifier = Modifier.fillMaxSize()
)
```

During handshakes, Picture-in-Picture (PIP) blocks are fully draggable, designed with material elevation, customizable shape clips, and dynamic offsets allowing users to position caller streams anywhere on screen.
