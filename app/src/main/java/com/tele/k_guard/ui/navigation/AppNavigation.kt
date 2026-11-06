package com.tele.k_guard.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tele.k_guard.data.model.AppApprovalStatus
import com.tele.k_guard.data.repository.UserPreferencesRepository
import com.tele.k_guard.ui.screens.AgeProfileScreen
import com.tele.k_guard.ui.screens.AppDetailScreen
import com.tele.k_guard.ui.screens.AppManagementScreen
import com.tele.k_guard.ui.screens.BlockedAppScreen
import com.tele.k_guard.ui.screens.ChildDashboardScreen
import com.tele.k_guard.ui.screens.ChildEditorScreen
import com.tele.k_guard.ui.screens.ChildPairingCodeScreen
import com.tele.k_guard.ui.screens.ChildPinSetupScreen
import com.tele.k_guard.ui.screens.ChildrenScreen
import com.tele.k_guard.ui.screens.HomeScreen
import com.tele.k_guard.ui.screens.ImageModerationScreen
import com.tele.k_guard.ui.screens.OnboardingScreen
import com.tele.k_guard.ui.screens.PrivacyScreen
import com.tele.k_guard.ui.screens.ProfileScreen
import com.tele.k_guard.ui.screens.ReviewQueueScreen
import com.tele.k_guard.ui.screens.ScreenTimeScreen
import com.tele.k_guard.ui.screens.SignInScreen
import com.tele.k_guard.ui.screens.TextModerationScreen
import com.tele.k_guard.ui.screens.WebProtectionScreen
import com.tele.k_guard.viewmodel.AgeProfileViewModel
import com.tele.k_guard.viewmodel.AgeProfileViewModelFactory
import com.tele.k_guard.viewmodel.AppManagementViewModel
import com.tele.k_guard.viewmodel.ImageModerationViewModel
import com.tele.k_guard.viewmodel.ScreenTimeViewModel
import com.tele.k_guard.viewmodel.SessionViewModel
import com.tele.k_guard.viewmodel.TextModerationViewModel
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    targetRoute: String? = null,
    /** Called once a notification's target has been opened, so it isn't opened again on recomposition. */
    onTargetRouteHandled: () -> Unit = {},
    userPreferencesRepository: UserPreferencesRepository = UserPreferencesRepository(LocalContext.current.applicationContext)
) {
    val scope = rememberCoroutineScope()
    val ageProfileViewModel: AgeProfileViewModel = viewModel(
        factory = AgeProfileViewModelFactory(userPreferencesRepository)
    )
    val devicePreferredProfile by ageProfileViewModel.selectedAgeProfile.collectAsState()

    val session: SessionViewModel = viewModel()
    val user by session.user.collectAsState()
    val signedIn by session.signedIn.collectAsState()
    val authChecked by session.authChecked.collectAsState()
    val context = LocalContext.current
    val selectedChild by session.selectedChild.collectAsState()
    val openAlerts by session.openAlertCount.collectAsState()
    val familyId by session.familyId.collectAsState()
    val children by session.children.collectAsState()

    val isChildDevicePref by userPreferencesRepository.isChildDeviceFlow.collectAsState(initial = false)
    val isChildAccount by session.isChildAccount.collectAsState()
    // The account's role in Firestore decides; the saved flag only helps before it has loaded.
    val isChildDevice = isChildAccount || isChildDevicePref
    val parentPinHash by userPreferencesRepository.parentPinHashFlow.collectAsState(initial = null)
    val storedFamilyId by userPreferencesRepository.familyIdFlow.collectAsState(initial = null)
    val storedChildId by userPreferencesRepository.activeChildIdFlow.collectAsState(initial = null)

    val effectiveFamilyId = familyId ?: storedFamilyId
    val effectiveChildId = selectedChild?.id ?: storedChildId

    // The selected child's age group drives every check; before any child exists, fall back to the device setting.
    val activeAgeProfile = selectedChild?.age ?: devicePreferredProfile
    val activeChildId = effectiveChildId

    val textModerationViewModel: TextModerationViewModel = viewModel()
    val imageModerationViewModel: ImageModerationViewModel = viewModel()
    val screenTimeViewModel: ScreenTimeViewModel = viewModel()
    val appManagementViewModel: AppManagementViewModel = viewModel()

    LaunchedEffect(effectiveFamilyId, activeChildId) {
        // Every child's apps: App Management filters by child itself, and the home badge counts all requests.
        appManagementViewModel.setFamilyAndChild(effectiveFamilyId, null)
    }

    val managedApps by appManagementViewModel.managedApps.collectAsState()
    val pendingApprovalsCount = managedApps.count { it.approvalStatus == AppApprovalStatus.PENDING_REVIEW }

    // Signing out (or an expired session) returns to the sign-in screen from anywhere.
    LaunchedEffect(signedIn, authChecked) {
        if (authChecked && !signedIn) {
            val current = navController.currentDestination?.route
            if (current != null && current != Screen.SignIn.route && current != Screen.Onboarding.route) {
                navController.navigate(Screen.SignIn.route) { popUpTo(0) }
            }
        }
    }

    // A child's account may only reach the child screens; anything else (parent dashboard, rules, approvals)
    // sends it to its own dashboard. Also remembers the phone as a child device for the next cold start.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(signedIn, isChildAccount, currentRoute) {
        if (!signedIn || !isChildAccount) return@LaunchedEffect
        if (!isChildDevicePref) {
            userPreferencesRepository.saveChildDeviceMode(true, session.selectedChild.value?.id)
            session.familyId.value?.let { userPreferencesRepository.saveFamilyId(it) }
        }
        val childRoutes = setOf(
            Screen.ChildDashboard.route, Screen.ChildPinSetup.route, Screen.ChildPairingCode.route,
            Screen.Profile.route
        )
        if (currentRoute != null && currentRoute !in childRoutes) {
            val target = if (parentPinHash.isNullOrBlank()) Screen.ChildPinSetup.route else Screen.ChildDashboard.route
            navController.navigate(target) { popUpTo(0) }
        }
    }

    // Open the screen a tapped notification points to. Waits until the navigation graph exists (on a cold
    // start it doesn't yet, and navigating then crashes), runs once per tap, and only for parent accounts:
    // the child guard above keeps child phones on their own screens.
    LaunchedEffect(targetRoute, signedIn, currentRoute, isChildAccount) {
        if (!signedIn || targetRoute == null || currentRoute == null || isChildAccount) return@LaunchedEffect
        val destination = when (targetRoute) {
            "app_management" -> Screen.AppManagement.route
            "review_queue" -> Screen.ReviewQueue.route
            "screen_time" -> Screen.ScreenTime.route
            "web_protection" -> Screen.WebProtection.route
            else -> null
        }
        onTargetRouteHandled()
        if (destination != null && destination != currentRoute) {
            runCatching { navController.navigate(destination) { launchSingleTop = true } }
                .onFailure { android.util.Log.w("KGuard", "Could not open $destination from a notification", it) }
        }
    }

    if (!authChecked) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val startDestination = com.tele.k_guard.DebugPreview.startRoute?.takeIf { signedIn } ?: if (signedIn) {
        if (isChildDevice) {
            if (effectiveFamilyId.isNullOrBlank()) {
                Screen.ChildPairingCode.route
            } else if (parentPinHash.isNullOrBlank()) {
                Screen.ChildPinSetup.route
            } else {
                Screen.ChildDashboard.route
            }
        } else {
            Screen.Home.route
        }
    } else {
        Screen.Onboarding.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onGetStarted = { navController.navigate(Screen.SignIn.route) }
            )
        }

        composable(Screen.SignIn.route) {
            SignInScreen(
                session = session,
                userPreferencesRepository = userPreferencesRepository,
                onParentSignedIn = {
                    navController.navigate(Screen.Home.route) { popUpTo(0) }
                },
                onChildPaired = {
                    navController.navigate(Screen.ChildPinSetup.route) { popUpTo(0) }
                }
            )
        }

        composable(Screen.ChildPairingCode.route) {
            ChildPairingCodeScreen(
                session = session,
                onPairingSuccess = {
                    scope.launch {
                        val currentFid = session.familyId.value
                        val currentCid = session.selectedChild.value?.id
                        if (!currentFid.isNullOrBlank()) {
                            userPreferencesRepository.saveFamilyId(currentFid)
                        }
                        userPreferencesRepository.saveChildDeviceMode(true, currentCid)
                        navController.navigate(Screen.ChildPinSetup.route) { popUpTo(0) }
                    }
                }
            )
        }

        composable(Screen.ChildPinSetup.route) {
            ChildPinSetupScreen(
                onPinSaved = { pin ->
                    scope.launch {
                        userPreferencesRepository.saveParentPinHash(pin)
                        val currentFid = session.familyId.value ?: storedFamilyId
                        val currentCid = session.selectedChild.value?.id ?: storedChildId
                        if (!currentFid.isNullOrBlank()) {
                            userPreferencesRepository.saveFamilyId(currentFid)
                        }
                        userPreferencesRepository.saveChildDeviceMode(true, currentCid)
                        navController.navigate(Screen.ChildDashboard.route) { popUpTo(0) }
                    }
                }
            )
        }

        composable(Screen.ChildDashboard.route) {
            ChildDashboardScreen(
                childProfile = selectedChild,
                familyId = familyId,
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onPairThisPhone = { navController.navigate(Screen.ChildPairingCode.route) },
                onLaunchLiveAi = { context.startActivity(android.content.Intent(context, com.tele.k_guard.live.LiveSessionActivity::class.java)) }
            )
        }

        composable(Screen.ParentAssistant.route) {
            val assistantViewModel: com.tele.k_guard.viewmodel.ParentAssistantViewModel = viewModel()
            val children by session.children.collectAsState()
            com.tele.k_guard.ui.screens.ParentAssistantScreen(
                viewModel = assistantViewModel,
                parentName = user?.displayName,
                children = children,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Profile.route) {
            val themeMode by userPreferencesRepository.themeModeFlow.collectAsState(initial = "SYSTEM")
            ProfileScreen(
                session = session,
                isChildDevice = isChildDevice,
                themeMode = themeMode,
                onThemeModeChange = { newMode ->
                    scope.launch {
                        userPreferencesRepository.saveThemeMode(newMode)
                    }
                },
                onSignOut = {
                    scope.launch {
                        userPreferencesRepository.saveChildDeviceMode(false)
                        navController.navigate(Screen.SignIn.route) { popUpTo(0) }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AgeProfile.route) {
            AgeProfileScreen(
                viewModel = ageProfileViewModel,
                onSaveAndContinue = { navController.popBackStack() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Home.route) {
            val children by session.children.collectAsState()
            val currentFamilyId by session.familyId.collectAsState()

            HomeScreen(
                activeAgeProfile = activeAgeProfile,
                user = user,
                activeChildName = selectedChild?.name,
                activeChildPhotoUrl = selectedChild?.photoUrl,
                children = children,
                familyId = currentFamilyId,
                selectedChild = selectedChild,
                onSelectChild = { child -> session.selectChild(child.id) },
                openAlertCount = openAlerts,
                pendingApprovalCount = pendingApprovalsCount,
                onNavigateToTextModeration = { navController.navigate(Screen.TextModeration.route) },
                onNavigateToImageModeration = { navController.navigate(Screen.ImageModeration.route) },
                onNavigateToAgeProfile = { navController.navigate(Screen.Children.route) },
                onNavigateToScreenTime = { navController.navigate(Screen.ScreenTime.route) },
                onNavigateToAppManagement = { navController.navigate(Screen.AppManagement.route) },
                onNavigateToFlaggedContent = { navController.navigate(Screen.ReviewQueue.route) },
                onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) },
                onNavigateToWebProtection = { navController.navigate(Screen.WebProtection.route) },
                onAddChild = { navController.navigate(Screen.ChildEditor.create(null)) },
                onOpenAssistant = { navController.navigate(Screen.ParentAssistant.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) }
            )
        }

        composable(Screen.Children.route) {
            ChildrenScreen(
                session = session,
                onEditChild = { navController.navigate(Screen.ChildEditor.create(it)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ChildEditor.route,
            arguments = listOf(navArgument("childId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { entry ->
            ChildEditorScreen(
                session = session,
                childId = entry.arguments?.getString("childId"),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.TextModeration.route) {
            TextModerationScreen(
                viewModel = textModerationViewModel,
                activeAgeProfile = activeAgeProfile,
                activeChildId = activeChildId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ImageModeration.route) {
            ImageModerationScreen(
                viewModel = imageModerationViewModel,
                activeAgeProfile = activeAgeProfile,
                activeChildId = activeChildId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ScreenTime.route) {
            ScreenTimeScreen(
                viewModel = screenTimeViewModel,
                session = session,
                activeAgeProfile = activeAgeProfile,
                onNavigateToChildProfile = { childId ->
                    navController.navigate(Screen.ChildEditor.create(childId))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AppManagement.route) {
            AppManagementScreen(
                viewModel = appManagementViewModel,
                activeAgeProfile = activeAgeProfile,
                activeChild = selectedChild,
                children = children,
                familyId = familyId,
                onNavigateToAppDetail = { appId -> navController.navigate(Screen.AppDetail.create(appId)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AppDetail.route,
            arguments = listOf(navArgument("appId") { type = NavType.StringType })
        ) { entry ->
            val appId = entry.arguments?.getString("appId") ?: ""
            AppDetailScreen(
                appId = appId,
                viewModel = appManagementViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ReviewQueue.route) {
            ReviewQueueScreen(session = session, onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.WebProtection.route) {
            WebProtectionScreen(session = session, onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Privacy.route) {
            PrivacyScreen(session = session, onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.BlockedApp.route,
            arguments = listOf(navArgument("appName") { type = NavType.StringType; defaultValue = "KidSafe App" })
        ) { entry ->
            val appName = entry.arguments?.getString("appName") ?: "KidSafe App"
            BlockedAppScreen(
                appName = appName,
                onReturnHome = { navController.popBackStack() }
            )
        }
    }
}
