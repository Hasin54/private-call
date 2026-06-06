package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.Message
import com.example.model.User
import com.example.ui.theme.GreenActive
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.CallViewModel
import com.example.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    otherUserId: String,
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    callViewModel: CallViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCall: () -> Unit
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    val contacts by chatViewModel.contacts.collectAsState()
    val otherUser = contacts.find { it.userId == otherUserId }

    val chatId = chatViewModel.getChatId(otherUserId)
    val messages by chatViewModel.activeMessages.collectAsState()

    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Subscribe to firestore collection real time updates
    LaunchedEffect(chatId) {
        chatViewModel.loadMessagesForChat(chatId)
    }

    // Auto-scroll to the newest chats
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(otherUser?.avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Active Profile Icon",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = otherUser?.name ?: "Chat Partner",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (otherUser?.isOnline == true) "Online" else "Offline",
                                fontSize = 11.sp,
                                color = if (otherUser?.isOnline == true) GreenActive else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Call triggering buttons inside Header
                    IconButton(
                        onClick = {
                            chatViewModel.recordCallHistoryLogInChat(otherUserId, "audio", "Outgoing WebRTC Audio Call placed")
                            callViewModel.startCall(otherUserId, "audio")
                            onNavigateToCall()
                        },
                        modifier = Modifier.testTag("chat_audio_call_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Audio Calling Link", tint = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(
                        onClick = {
                            chatViewModel.recordCallHistoryLogInChat(otherUserId, "video", "Outgoing WebRTC Video Call placed")
                            callViewModel.startCall(otherUserId, "video")
                            onNavigateToCall()
                        },
                        modifier = Modifier.testTag("chat_video_call_button")
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Calling Link", tint = MaterialTheme.colorScheme.secondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Chat bubble feeds
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { message ->
                    val isMine = message.senderId == currentUser?.userId

                    if (message.messageType == "audio_call_log" || message.messageType == "video_call_log") {
                        CallHistoryMessageBadge(
                            message = message,
                            isMine = isMine,
                            partnerName = otherUser?.name ?: "Partner"
                        )
                    } else {
                        MessageBubble(
                            message = message,
                            isMine = isMine
                        )
                    }
                }
            }

            // Message writing section
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Write high-fidelity messages...", fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    IconButton(
                        onClick = {
                            if (textInput.trim().isNotEmpty()) {
                                chatViewModel.sendMessage(otherUserId, textInput)
                                textInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("message_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------- SUB RENDERING CARDS ----------------

@Composable
fun MessageBubble(message: Message, isMine: Boolean) {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = sdf.format(Date(message.timestamp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 2.dp,
                    bottomEnd = if (isMine) 2.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMine) IndigoPrimary else SlateSurface
                ),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.text,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = timeString,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun CallHistoryMessageBadge(message: Message, isMine: Boolean, partnerName: String) {
    val isVideo = message.messageType == "video_call_log"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isMine) Icons.Default.PhoneForwarded else Icons.Default.PhoneCallback,
                    contentDescription = "Incoming Event",
                    tint = if (isMine) MaterialTheme.colorScheme.primary else GreenActive,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${if (isMine) "You initiated" else "$partnerName placed"} a ${if (isVideo) "video" else "voice"} call",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
