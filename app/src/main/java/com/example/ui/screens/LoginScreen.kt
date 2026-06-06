package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    val isLoading by authViewModel.isLoading.collectAsState()
    val error by authViewModel.error.collectAsState()
    val authProcessed by authViewModel.authProcessed.collectAsState()
    val isRealFirebase = authViewModel.isRealFirebase()

    var email by remember { mutableStateFlowOfOnComposeAndRestart("") }
    var password by remember { mutableStateFlowOfOnComposeAndRestart("") }

    var isPhoneMode by remember { mutableStateOf(false) }
    var phoneNumber by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var isSmsSent by remember { mutableStateOf(false) }

    LaunchedEffect(authProcessed) {
        if (authProcessed) {
            onNavigateToHome()
            authViewModel.resetAuthProcessed()
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.background
                        ),
                        radius = 2200f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Glow logo element
                Card(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(32.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_global_call_logo),
                            contentDescription = "Global Call Logo",
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Global Call",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Connect securely with WebRTC calls",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
                )

                if (!isRealFirebase) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = "💡 Running in mock preview mode. Register a local user or sign in with test@globalcall.com",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Error handler bar
                AnimatedVisibility(
                    visible = error != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { authViewModel.clearError() }, modifier = Modifier.size(24.dp)) {
                                Text("×", color = MaterialTheme.colorScheme.error, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (!isPhoneMode) {
                    // Email mode
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = "EmailIcon") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input"),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "LockIcon") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { authViewModel.login(email, password) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("login_button"),
                        enabled = !isLoading && email.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Text("Sign In Securely", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Phone authentication mode
                    if (!isSmsSent) {
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Phone Number (e.g. +123456789)") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "PhoneIcon") },
                            modifier = Modifier.fillMaxWidth().testTag("phone_input"),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { isSmsSent = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("send_sms_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Send Verification SMS", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedTextField(
                            value = smsCode,
                            onValueChange = { smsCode = it },
                            label = { Text("6-digit SMS Code") },
                            leadingIcon = { Icon(Icons.Default.Send, contentDescription = "SmsIcon") },
                            modifier = Modifier.fillMaxWidth().testTag("sms_input"),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { authViewModel.login("test@globalcall.com", "") },
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("verify_sms_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Verify & Authenticate", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(onClick = { isSmsSent = false }) {
                            Text("Back to Phone Entry", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = { onNavigateToRegister() }, modifier = Modifier.testTag("navigate_register")) {
                    Text("Don't have an account? Sign Up", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Divider and Alternative logins
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                    Text(
                        text = "OR CONTINUE WITH",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Google sign-in button mockup (beautiful and interactive)
                    OutlinedButton(
                        onClick = { authViewModel.login("test@globalcall.com", "") },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("google_login_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Google", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }

                    // Phone authentications selector button (toggles mode beautifully)
                    OutlinedButton(
                        onClick = { 
                            isPhoneMode = !isPhoneMode
                            authViewModel.clearError()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("phone_mode_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, contentDescription = "PhoneMode", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPhoneMode) "Email" else "Mobile", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

// Custom wrapper to cleanly handle remember states avoiding kotlin recomposition anomalies
private fun <T> mutableStateFlowOfOnComposeAndRestart(initialValue: T) = mutableStateOf(initialValue)
