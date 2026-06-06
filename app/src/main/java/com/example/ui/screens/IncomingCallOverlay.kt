package com.example.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.Call
import com.example.ui.theme.GreenActive
import com.example.ui.theme.RedReject
import com.example.ui.theme.SlateSurface
import com.example.viewmodel.CallViewModel
import com.example.viewmodel.ChatViewModel

@Composable
fun IncomingCallOverlay(
    callViewModel: CallViewModel,
    chatViewModel: ChatViewModel,
    onAcceptNavigation: () -> Unit
) {
    val TAG = "IncomingCallOverlay"
    val contacts by chatViewModel.contacts.collectAsState()
    
    var incomingCallRing by remember { mutableStateOf<Call?>(null) }

    // Listen on the shared flow channel
    LaunchedEffect(Unit) {
        callViewModel.incomingRingingCalls.collect { call ->
            Log.d(TAG, "Triggering fullscreen incoming overlay for: ${call.callId}")
            incomingCallRing = call
        }
    }

    if (incomingCallRing == null) return

    val currentRing = incomingCallRing!!
    val callerUser = contacts.find { it.userId == currentRing.callerId }

    Dialog(
        onDismissRequest = {
            // Dismissing can reject by default or maintain ringing
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // Profile card
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        // Pulsing outer bounds
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                        )
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(callerUser?.avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Caller Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = callerUser?.name ?: currentRing.callerName,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "INCOMING ${currentRing.callType.uppercase()} CALL",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Control actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 60.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Click Reject action
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            callViewModel.rejectCall(currentRing.callId)
                            incomingCallRing = null
                        }.testTag("overlay_reject_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(RedReject),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Reject Call Button",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Decline", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }

                    // Click Accept action
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            callViewModel.answerCall(currentRing)
                            incomingCallRing = null
                            onAcceptNavigation()
                        }.testTag("overlay_accept_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(GreenActive),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Accept Call Button",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Accept", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
