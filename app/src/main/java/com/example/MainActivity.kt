package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.SwapMoneyViewModel
import com.example.ui.auth.AuthFlowScreen
import com.example.ui.exchange.ExchangeDetailScreen
import com.example.ui.history.HistoryScreen
import com.example.ui.home.HomeScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.score.ScoreScreen
import com.example.ui.theme.SwapMoneyTheme

private const val ROUTE_HOME = "home"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_SCORE = "score"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_EXCHANGE_DETAIL = "exchange_detail/{requestId}"

private data class PrimaryTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val PrimaryTabs = listOf(
    PrimaryTab(ROUTE_HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    PrimaryTab(ROUTE_HISTORY, "History", Icons.Filled.History, Icons.Outlined.History, "nav_tab_history"),
    PrimaryTab(ROUTE_SCORE, "Score", Icons.Filled.Assessment, Icons.Outlined.Assessment, "nav_tab_score"),
    PrimaryTab(ROUTE_PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person, "nav_tab_profile")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: SwapMoneyViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            SwapMoneyTheme(darkTheme = uiState.preferences.darkMode) {
                SwapMoneyAppRoot(
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun SwapMoneyAppRoot(viewModel: SwapMoneyViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusBannerMessage) {
        val msg = uiState.statusBannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = "Dismiss",
                duration = SnackbarDuration.Short
            )
            viewModel.clearBannerMessage()
        }
    }

    if (uiState.activeUser == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            AuthFlowScreen(
                uiState = uiState,
                onLogin = { id, pwd -> viewModel.login(id, pwd) },
                onSendOtp = { ch, target, onSent -> viewModel.sendOtp(ch, target, onSent) },
                onVerifyOtp = { ch, target, code, onVerified -> viewModel.verifyOtp(ch, target, code, onVerified) },
                onRegister = { name, mobile, email, addr, pwd ->
                    viewModel.registerCustomer(name, mobile, email, addr, pwd) {}
                },
                onRecoverPassword = { id, newPwd, onDone ->
                    viewModel.recoverPassword(id, newPwd, onDone)
                },
                onUpdateBackendConfig = { mode, url ->
                    viewModel.updatePreferences { it.copy(backendMode = mode, fastApiBaseUrl = url) }
                }
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            ) { data ->
                Snackbar(
                    containerColor = if (uiState.isErrorBanner) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    action = {
                        data.visuals.actionLabel?.let { actionLabel ->
                            TextButton(onClick = { data.dismiss() }) {
                                Text(actionLabel, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                ) {
                    Text(data.visuals.message)
                }
            }
        }
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_HOME
    val showPrimaryNav = currentRoute in PrimaryTabs.map { it.route }

    fun navigateToPrimaryTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        containerColor = if (uiState.isErrorBanner) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        action = {
                            data.visuals.actionLabel?.let { actionLabel ->
                                TextButton(onClick = { data.dismiss() }) {
                                    Text(actionLabel, color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    ) {
                        Text(data.visuals.message)
                    }
                }
            },
            bottomBar = {
                if (showPrimaryNav && !isWideScreen) {
                    NavigationBar {
                        PrimaryTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToPrimaryTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showPrimaryNav && isWideScreen) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        PrimaryTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToPrimaryTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_HOME,
                        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()
                    ) {
                        composable(ROUTE_HOME) {
                            HomeScreen(
                                uiState = uiState,
                                onRefreshLocation = { viewModel.refreshApproximateLocation() },
                                onSwitchPersona = { viewModel.switchSandboxPersona(it) },
                                onOpenProfileTab = { navigateToPrimaryTab(ROUTE_PROFILE) },
                                onOpenRequestDetail = { reqId ->
                                    navController.navigate("exchange_detail/$reqId")
                                },
                                onCreateRequest = { wanted, offered, amount, radius, expiry, onDone ->
                                    viewModel.createRequest(wanted, offered, amount, radius, expiry, onDone)
                                },
                                onMarkNotificationsRead = { viewModel.markNotificationsRead() },
                                onAcceptNotification = { notifId, address ->
                                    viewModel.acceptNotification(notifId, address) { reqId ->
                                        navController.navigate("exchange_detail/$reqId")
                                    }
                                },
                                onIgnoreNotification = { notifId ->
                                    viewModel.ignoreNotification(notifId)
                                },
                                onMarkReceived = { reqId ->
                                    viewModel.markReceiverReceived(reqId)
                                },
                                onSubmitProof = { rId, ref, uri, mime, size ->
                                    viewModel.submitEvidence(rId, ref, uri, mime, size)
                                }
                            )
                        }
                        composable(ROUTE_HISTORY) {
                            HistoryScreen(uiState = uiState)
                        }
                        composable(ROUTE_SCORE) {
                            ScoreScreen(
                                uiState = uiState,
                                onReportReview = { viewModel.reportReview(it) }
                            )
                        }
                        composable(ROUTE_PROFILE) {
                            ProfileScreen(
                                uiState = uiState,
                                onMfaAction = { action, code, newVal, onDone ->
                                    viewModel.performMfaAction(action, code, newVal, onDone)
                                },
                                onUpdatePreferences = { viewModel.updatePreferences(it) },
                                onToggleSimulatedOffline = { viewModel.toggleSimulatedOffline() },
                                onLogout = { viewModel.logout() }
                            )
                        }
                        composable(
                            route = ROUTE_EXCHANGE_DETAIL,
                            arguments = listOf(navArgument("requestId") { type = NavType.StringType })
                        ) { entry ->
                            val reqId = entry.arguments?.getString("requestId").orEmpty()
                            ExchangeDetailScreen(
                                requestId = reqId,
                                uiState = uiState,
                                onBack = { navController.popBackStack() },
                                onSubmitInterest = { reqIdArg, addr -> viewModel.submitInterest(reqIdArg, addr) },
                                onSendChatMessage = { convId, txt -> viewModel.sendChatMessage(convId, txt) },
                                onBlockUser = { viewModel.blockUser(it) },
                                onReportUser = { uid, reason -> viewModel.reportUser(uid, reason) },
                                onStage1Decision = { intId, approve -> viewModel.submitStage1Approval(intId, approve) },
                                onStage2ConfirmPartner = { intId -> viewModel.submitStage2PartnerConfirmation(intId) },
                                onGrantConsent = { viewModel.grantContactConsent(it) },
                                onProposeMeeting = { rId, loc, time -> viewModel.proposeMeeting(rId, loc, time) },
                                onApproveMeeting = { viewModel.approveMeeting(it) },
                                onSubmitEvidence = { rId, ref, uri, mime, size ->
                                    viewModel.submitEvidence(rId, ref, uri, mime, size)
                                },
                                onMarkReceiverReceived = { rId ->
                                    viewModel.markReceiverReceived(rId)
                                },
                                onConfirmExchange = { rId, ok, note -> viewModel.submitConfirmation(rId, ok, note) },
                                onCancelRequest = { viewModel.cancelRequest(it) },
                                onSubmitRating = { rId, comm, punct, coop, overall, comment ->
                                    viewModel.submitRating(rId, comm, punct, coop, overall, comment)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
