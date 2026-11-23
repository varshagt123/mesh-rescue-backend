package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsListScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SosScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedSoft
import com.example.ui.theme.NavySecondary
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RescueLinkTheme
import com.example.ui.theme.TealContainer
import com.example.ui.theme.TealPrimary
import com.example.viewmodel.RescueLinkViewModel

enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    MESSAGES("Messages", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    SOS("SOS", Icons.Filled.Warning, Icons.Outlined.WarningAmber),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.PersonOutline)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RescueLinkTheme {
                RescueLinkApp()
            }
        }
    }
}

@Composable
fun RescueLinkApp(
    viewModel: RescueLinkViewModel = viewModel()
) {
    val sessionLoaded by viewModel.sessionLoaded.collectAsStateWithLifecycle()
    val loggedInUserId by viewModel.loggedInUserId.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val emergencyContacts by viewModel.emergencyContacts.collectAsStateWithLifecycle()
    val isOfflineMode by viewModel.isOfflineMode.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val sosAlerts by viewModel.sosAlerts.collectAsStateWithLifecycle()
    val activeSosProgress by viewModel.activeSosProgress.collectAsStateWithLifecycle()
    val pendingMessageCount by viewModel.pendingMessageCount.collectAsStateWithLifecycle()
    val bleRadioState by viewModel.bleRadioState.collectAsStateWithLifecycle()
    val discoveredPeers by viewModel.discoveredPeers.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val blePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.onBluetoothPermissionsOrStateChanged()
    }
    val enableBluetoothLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onBluetoothPermissionsOrStateChanged()
    }

    var showSplash by rememberSaveable { mutableStateOf(true) }
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var activeConversationId by rememberSaveable { mutableStateOf<String?>(null) }

    if (showSplash || !sessionLoaded) {
        SplashScreen(onFinished = { showSplash = false })
        return
    }

    if (loggedInUserId.isNullOrBlank()) {
        AuthScreen(
            authError = authError,
            onLogin = viewModel::login,
            onRegister = viewModel::register,
            onContinueDemo = viewModel::continueAsDemoUser,
            onClearError = viewModel::clearAuthError
        )
        return
    }

    // Individual Chat Detail Screen
    val openConversation = activeConversationId?.let { id ->
        conversations.firstOrNull { it.id == id }
    }
    if (openConversation != null) {
        LaunchedEffect(openConversation.id) {
            viewModel.markConversationRead(openConversation.id)
        }
        val messages by viewModel.observeMessages(openConversation.id)
            .collectAsStateWithLifecycle(initialValue = emptyList())

        ChatDetailScreen(
            conversation = openConversation,
            messages = messages,
            isOfflineMode = isOfflineMode || bleRadioState != com.example.data.mesh.BleRadioState.READY || discoveredPeers.isEmpty(),
            onBack = { activeConversationId = null },
            onSendMessage = { text -> viewModel.sendMessage(openConversation.id, text) }
        )
        return
    }

    Scaffold(
        containerColor = PureWhite,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = PureWhite,
                tonalElevation = 4.dp
            ) {
                MainTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val isSosTab = tab == MainTab.SOS
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (isSosTab) EmergencyRed else TealPrimary,
                            selectedTextColor = if (isSosTab) EmergencyRed else DeepNavy,
                            indicatorColor = if (isSosTab) EmergencyRedSoft else TealContainer,
                            unselectedIconColor = if (isSosTab) EmergencyRed.copy(alpha = 0.7f) else NavySecondary,
                            unselectedTextColor = NavySecondary
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            when (selectedTab) {
                MainTab.HOME -> HomeScreen(
                    userName = currentUser?.name ?: "Alex Rivera",
                    isOfflineMode = isOfflineMode,
                    bleRadioState = bleRadioState,
                    discoveredPeers = discoveredPeers,
                    pendingMessageCount = pendingMessageCount,
                    recentConversations = conversations,
                    onRequestBluetoothAccess = {
                        when (bleRadioState) {
                            com.example.data.mesh.BleRadioState.PERMISSION_REQUIRED -> {
                                blePermissionLauncher.launch(viewModel.requiredBluetoothPermissions())
                            }
                            com.example.data.mesh.BleRadioState.BLUETOOTH_DISABLED -> {
                                val intent = android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                enableBluetoothLauncher.launch(intent)
                            }
                            else -> viewModel.onBluetoothPermissionsOrStateChanged()
                        }
                    },
                    onOpenSos = { selectedTab = MainTab.SOS },
                    onOpenAllMessages = { selectedTab = MainTab.MESSAGES },
                    onOpenConversation = { convId -> activeConversationId = convId }
                )
                MainTab.MESSAGES -> ChatsListScreen(
                    conversations = conversations,
                    onOpenConversation = { convId -> activeConversationId = convId }
                )
                MainTab.SOS -> SosScreen(
                    activeProgress = activeSosProgress,
                    sosAlerts = sosAlerts,
                    onSendSos = viewModel::sendSosAlert
                )
                MainTab.PROFILE -> ProfileScreen(
                    user = currentUser,
                    emergencyContacts = emergencyContacts,
                    isOfflineMode = isOfflineMode,
                    onToggleOfflineMode = viewModel::toggleOfflineMode,
                    onUpdateProfile = viewModel::updateProfile,
                    onAddEmergencyContact = viewModel::addEmergencyContact,
                    onDeleteEmergencyContact = viewModel::deleteEmergencyContact,
                    onLogout = {
                        activeConversationId = null
                        selectedTab = MainTab.HOME
                        viewModel.logout()
                    }
                )
            }
        }
    }
}
