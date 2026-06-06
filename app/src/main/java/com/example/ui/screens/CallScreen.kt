package com.example.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.Call
import com.example.model.User
import com.example.ui.theme.RedReject
import com.example.ui.theme.SlateDarkBlue
import com.example.ui.theme.SlateSurface
import com.example.viewmodel.CallViewModel
import com.example.viewmodel.ChatViewModel
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlin.math.roundToInt

@Composable
fun CallScreen(
    callViewModel: CallViewModel,
    chatViewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val TAG = "CallScreen"
    val activeCall by callViewModel.activeCall.collectAsState()
    val localVideoTrack by callViewModel.localVideoTrack.collectAsState()
    val remoteVideoTrack by callViewModel.remoteVideoTrack.collectAsState()
    val connectionState by callViewModel.connectionState.collectAsState()

    val contacts by chatViewModel.contacts.collectAsState()

    var isMicMuted by remember { mutableStateOf(false) }
    var isCameraOff by remember { mutableStateOf(false) }

    // Repositionable Picture-In-Picture container coordinates
    var pipOffsetX by remember { mutableFloatStateOf(20f) }
    var pipOffsetY by remember { mutableFloatStateOf(20f) }

    // When the call terminates, automatically nav-back
    LaunchedEffect(activeCall) {
        if (activeCall == null || activeCall?.callStatus == "ended" || activeCall?.callStatus == "rejected") {
            Log.d(TAG, "Call closed. Going back to contact panel.")
            onNavigateBack()
        }
    }

    if (activeCall == null) return

    val currentCall = activeCall!!
    val otherUserId = if (currentCall.callerId == currentCall.callerId) currentCall.receiverId else currentCall.callerId
    val otherUser = contacts.find { it.userId == otherUserId }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDarkBlue)
    ) {
        // --- 1. REMOTE STREAM SCREEN RENDER ---
        if (currentCall.callType == "video" && remoteVideoTrack != null && !isCameraOff) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(callViewModel.rootEglBaseContext, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        setMirror(false)
                        remoteVideoTrack?.addSink(this)
                    }
                },
                update = { view ->
                    // Auto recalculates on composition state refreshes
                },
                modifier = Modifier.fillMaxSize().testTag("remote_video_view")
            )
        } else {
            // Audio-only call profile background showing stunning pulsating concentric graphics
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(SlateDarkBlue, SlateSurface, SlateDarkBlue)
                        )
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Pulsating rings simulation
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    )
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    )
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(otherUser?.avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde")
                                .crossfade(true)
                                .build(),
                            contentDescription = "Contact Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = otherUser?.name ?: currentCall.callerName,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                val label = when (currentCall.callStatus) {
                    "ringing" -> "Ringing..."
                    "accepted" -> "Connected • WebRTC NAT Peer"
                    else -> "Setting connection up..."
                }
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // --- 2. PIP LOCAL CAPTURE DRAGGABLE OVERLAY ---
        if (currentCall.callType == "video" && localVideoTrack != null && !isCameraOff) {
            Card(
                modifier = Modifier
                    .offset { IntOffset(pipOffsetX.roundToInt(), pipOffsetY.roundToInt()) }
                    .size(width = 110.dp, height = 150.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            pipOffsetX += dragAmount.x
                            pipOffsetY += dragAmount.y
                        }
                    }
                    .border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(16.dp))
                    .testTag("local_video_pip_card"),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(callViewModel.rootEglBaseContext, null)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            setMirror(true)
                            localVideoTrack?.addSink(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // --- 3. CALL STATUS DISPLAY HEADER ---
        if (currentCall.callType == "video") {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE • ${currentCall.callType.uppercase()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // --- 4. FLOATING CONTROL BUTTONS CAPSULE ---
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 50.dp)
                .width(280.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurface.copy(alpha = 0.92f)),
            shape = RoundedCornerShape(32.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic Mute toggle
                IconButton(
                    onClick = {
                        isMicMuted = !isMicMuted
                        callViewModel.localVideoTrack.value?.setEnabled(!isMicMuted)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isMicMuted) RedReject.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        .testTag("mute_mic_button")
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute Microphone",
                        tint = if (isMicMuted) RedReject else Color.White
                    )
                }

                // Hang Up Button (Red)
                IconButton(
                    onClick = { callViewModel.endCall() },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(RedReject)
                        .testTag("hang_up_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Hang Up Session",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Camera Toggle (Available for video calling)
                if (currentCall.callType == "video") {
                    IconButton(
                        onClick = {
                            isCameraOff = !isCameraOff
                            localVideoTrack?.setEnabled(!isCameraOff)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isCameraOff) RedReject.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            .testTag("toggle_camera_button")
                    ) {
                        Icon(
                            imageVector = if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            contentDescription = "Toggle Video",
                            tint = if (isCameraOff) RedReject else Color.White
                        )
                    }
                }
            }
        }
    }
}
