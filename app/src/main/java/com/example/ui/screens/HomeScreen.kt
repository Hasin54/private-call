package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.Call
import com.example.model.User
import com.example.ui.theme.*
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.CallViewModel
import com.example.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    callViewModel: CallViewModel,
    onNavigateToChat: (otherUserId: String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToCall: () -> Unit
) {
    val currentUser by authViewModel.currentUser.collectAsState()
    val contacts by chatViewModel.contacts.collectAsState()
    val historicCalls by chatViewModel.historicCalls.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Chats, 1: Contacts, 2: History
    var showAddContactDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Global Call",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier.testTag("home_profile_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(currentUser?.avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Active Profile Icon",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(if (selectedTab == 0) Icons.Filled.Chat else Icons.Outlined.Chat, contentDescription = "ChatsTab") },
                    label = { Text("Chats", fontSize = 12.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(if (selectedTab == 1) Icons.Filled.People else Icons.Outlined.People, contentDescription = "ContactsTab") },
                    label = { Text("Contacts", fontSize = 12.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(if (selectedTab == 2) Icons.Filled.Call else Icons.Outlined.Call, contentDescription = "HistoryTab") },
                    label = { Text("Call Logs", fontSize = 12.sp) }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showAddContactDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_contact_fab")
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Add Contact")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> ChatsTab(
                    contacts = contacts,
                    currentUserId = currentUser?.userId ?: "",
                    onNavigateToChat = onNavigateToChat
                )
                1 -> ContactsTab(
                    contacts = contacts,
                    currentUserId = currentUser?.userId ?: "",
                    onNavigateToChat = onNavigateToChat,
                    onStartAudioCall = { receiverId ->
                        chatViewModel.recordCallHistoryLogInChat(receiverId, "audio", "Outgoing WebRTC Audio Call placed")
                        callViewModel.startCall(receiverId, "audio")
                        onNavigateToCall()
                    },
                    onStartVideoCall = { receiverId ->
                        chatViewModel.recordCallHistoryLogInChat(receiverId, "video", "Outgoing WebRTC Video Call placed")
                        callViewModel.startCall(receiverId, "video")
                        onNavigateToCall()
                    }
                )
                2 -> CallHistoryTab(
                    historicCalls = historicCalls,
                    currentUserId = currentUser?.userId ?: "",
                    contacts = contacts,
                    onRedial = { receiverId, callType ->
                        chatViewModel.recordCallHistoryLogInChat(receiverId, callType, "Outgoing Redial WebRTC $callType Call placed")
                        callViewModel.startCall(receiverId, callType)
                        onNavigateToCall()
                    }
                )
            }

            if (showAddContactDialog) {
                AddContactDialog(
                    chatViewModel = chatViewModel,
                    onDismiss = { showAddContactDialog = false }
                )
            }
        }
    }
}

// ---------------- SUB TAB VIEWS ----------------

@Composable
fun ChatsTab(
    contacts: List<User>,
    currentUserId: String,
    onNavigateToChat: (String) -> Unit
) {
    val listToShow = contacts.filter { it.userId != currentUserId }

    if (listToShow.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.ChatBubbleOutline,
            title = "No Conversations Yet",
            tip = "Go to the 'Contacts' tab, add friends and tap on their profiles to start texting!"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(listToShow) { contact ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToChat(contact.userId) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileImageWithPresenceIndicator(
                            avatarUrl = contact.avatarUrl,
                            isOnline = contact.isOnline,
                            size = 52.dp
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.name,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = contact.status,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Chat",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactsTab(
    contacts: List<User>,
    currentUserId: String,
    onNavigateToChat: (String) -> Unit,
    onStartAudioCall: (String) -> Unit,
    onStartVideoCall: (String) -> Unit
) {
    val listToShow = contacts.filter { it.userId != currentUserId }

    if (listToShow.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.PeopleOutline,
            title = "No Contacts Found",
            tip = "Click the '+' Floating Action Button below to search and add registered users by their email!"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(listToShow) { contact ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToChat(contact.userId) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileImageWithPresenceIndicator(
                            avatarUrl = contact.avatarUrl,
                            isOnline = contact.isOnline,
                            size = 52.dp
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = contact.name,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (contact.isOnline) "ONLINE" else "OFFLINE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (contact.isOnline) GreenActive else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = contact.status,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Direct call actions right inside contacts lists!
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { onStartAudioCall(contact.userId) }) {
                                Icon(Icons.Default.Call, contentDescription = "Audio Call", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onStartVideoCall(contact.userId) }) {
                                Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallHistoryTab(
    historicCalls: List<Call>,
    currentUserId: String,
    contacts: List<User>,
    onRedial: (receiverId: String, callType: String) -> Unit
) {
    if (historicCalls.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.PhoneCallback,
            title = "No Incoming/Outgoing Calls",
            tip = "Your calling stats will list automatically as soon as WebRTC connection SDP packets synchronize!"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(historicCalls) { call ->
                val otherParticipantId = if (call.callerId == currentUserId) call.receiverId else call.callerId
                val otherContact = contacts.find { it.userId == otherParticipantId }

                val isOutgoing = call.callerId == currentUserId
                val displayState = when (call.callStatus) {
                    "accepted" -> "Connected"
                    "rejected" -> if (isOutgoing) "Declined" else "Missed"
                    "ended" -> "Completed"
                    else -> "No answer"
                }
                
                val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                val formattedTime = sdf.format(Date(call.timestamp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileImageWithPresenceIndicator(
                            avatarUrl = otherContact?.avatarUrl ?: "",
                            isOnline = otherContact?.isOnline ?: false,
                            size = 46.dp
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = otherContact?.name ?: call.callerName,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isOutgoing) Icons.Default.ArrowOutward else Icons.Default.CallReceived,
                                    contentDescription = "Call Type",
                                    tint = if (call.callStatus == "rejected") RedReject else if (isOutgoing) IndigoLight else GreenActive,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$displayState • $formattedTime",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // WebRTC instant call action
                        IconButton(onClick = { onRedial(otherParticipantId, call.callType) }) {
                            Icon(
                                imageVector = if (call.callType == "video") Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = "Redial Call",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------- ADDITIONAL CORE RE-USABLE COMPONENTS ----------------

@Composable
fun ProfileImageWithPresenceIndicator(
    avatarUrl: String,
    isOnline: Boolean,
    size: androidx.compose.ui.unit.Dp = 50.dp
) {
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "User Avatar Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Online/Offline status badge dots representing the live state
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(13.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface) // outer border
                .padding(1.5.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(if (isOnline) GreenActive else Color(0xFF94A3B8))
            )
        }
    }
}

@Composable
fun EmptyStateView(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Call,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tip: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Placeholder Icon",
            modifier = Modifier.size(68.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = tip,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

// Add Contacts search Dialog representing contact search & add friend as requested
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactDialog(
    chatViewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val searchResult by chatViewModel.searchResult.collectAsState()
    val searchLoading by chatViewModel.searchLoading.collectAsState()
    val searchError by chatViewModel.searchError.collectAsState()

    var emailInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        chatViewModel.clearSearch()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Add Friend",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "Enter email to search for user accounts",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Friend's Email") },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_email_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { chatViewModel.searchAndAddContact(emailInput) },
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("dialog_search_button"),
                    enabled = emailInput.trim().isNotEmpty() && !searchLoading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (searchLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Search Profile")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Shows search result status nicely
                if (searchError != null) {
                    Text(
                        text = searchError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                if (searchResult != null) {
                    val matchingContact = searchResult!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProfileImageWithPresenceIndicator(
                                avatarUrl = matchingContact.avatarUrl,
                                isOnline = matchingContact.isOnline,
                                size = 44.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = matchingContact.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = matchingContact.email,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("dialog_dismiss_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save & Open Chat", color = MaterialTheme.colorScheme.onSecondary)
                    }
                }
            }
        }
    }
}
