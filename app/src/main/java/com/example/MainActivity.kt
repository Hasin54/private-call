package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.CallViewModel
import com.example.viewmodel.ChatViewModel

/**
 * Main activity entry point.
 * Coordinates type-safe Jetpack Compose routing, manages system runtime permissions,
 * and declares active ViewModel lifecycles.
 */
class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()

                // Register central state viewmodel loops
                val authViewModel: AuthViewModel = viewModel()
                val chatViewModel: ChatViewModel = viewModel()
                val callViewModel: CallViewModel = viewModel()

                // Automated Request Permission contract (Android 10 to 15 support)
                val permissionsToLaunch = remember {
                    mutableListOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    ).apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    permissions.entries.forEach { entry ->
                        Log.d(TAG, "Permission status - ${entry.key}: ${entry.value}")
                    }
                }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(permissionsToLaunch.toTypedArray())
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Root Navigation Stack Container
                    NavHost(
                        navController = navController,
                        startDestination = "login"
                    ) {
                        composable("login") {
                            LoginScreen(
                                authViewModel = authViewModel,
                                onNavigateToRegister = { navController.navigate("register") },
                                onNavigateToHome = {
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("register") {
                            RegisterScreen(
                                authViewModel = authViewModel,
                                onNavigateToLogin = { navController.navigate("login") },
                                onNavigateToHome = {
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("home") {
                            HomeScreen(
                                authViewModel = authViewModel,
                                chatViewModel = chatViewModel,
                                callViewModel = callViewModel,
                                onNavigateToChat = { otherUserId ->
                                    navController.navigate("chat/$otherUserId")
                                },
                                onNavigateToProfile = { navController.navigate("profile") },
                                onNavigateToCall = { navController.navigate("call") }
                            )
                        }

                        composable(
                            route = "chat/{userId}",
                            arguments = listOf(navArgument("userId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId") ?: ""
                            ChatScreen(
                                otherUserId = userId,
                                authViewModel = authViewModel,
                                chatViewModel = chatViewModel,
                                callViewModel = callViewModel,
                                onNavigateBack = { navController.navigateUp() },
                                onNavigateToCall = { navController.navigate("call") }
                            )
                        }

                        composable("profile") {
                            ProfileScreen(
                                authViewModel = authViewModel,
                                onNavigateBack = { navController.navigateUp() },
                                onNavigateToLogin = {
                                    navController.navigate("login") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("call") {
                            CallScreen(
                                callViewModel = callViewModel,
                                chatViewModel = chatViewModel,
                                onNavigateBack = { navController.navigateUp() }
                            )
                        }
                    }

                    // --- GLOBAL MOUNT INCOMING CALL DETECTOR ---
                    // This makes sure ringing calls display overlays instantly on top of any active nav screen!
                    IncomingCallOverlay(
                        callViewModel = callViewModel,
                        chatViewModel = chatViewModel,
                        onAcceptNavigation = {
                            navController.navigate("call")
                        }
                    )
                }
            }
        }
    }
}
