package com.tele.k_guard.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tele.k_guard.data.model.AppApprovalStatus
import com.tele.k_guard.data.repository.UserPreferencesRepository
import com.tele.k_guard.ui.screens.AgeProfileScreen
import com.tele.k_guard.ui.screens.AppManagementScreen
import com.tele.k_guard.ui.screens.ChildEditorScreen
import com.tele.k_guard.ui.screens.ChildrenScreen
import com.tele.k_guard.ui.screens.HomeScreen
import com.tele.k_guard.ui.screens.ImageModerationScreen
import com.tele.k_guard.ui.screens.OnboardingScreen
import com.tele.k_guard.ui.screens.PrivacyScreen
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

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    targetRoute: String? = null
) {
    val context = LocalContext.current.applicationContext
    val userPreferencesRepository = UserPreferencesRepository(context)
    val ageProfileViewModel: AgeProfileViewModel = viewModel(
        factory = AgeProfileViewModelFactory(userPreferencesRepository)
    )
    val devicePreferredProfile by ageProfileViewModel.selectedAgeProfile.collectAsState()

    val session: SessionViewModel = viewModel()
    val user by session.user.collectAsState()
    val signedIn by session.signedIn.collectAsState()
    val authChecked by session.authChecked.collectAsState()
    val selectedChild by session.selectedChild.collectAsState()
    val openAlerts by session.openAlertCount.collectAsState()
    val familyId by session.familyId.collectAsState()
    val children by session.children.collectAsState()

    // The selected child's age group drives every check; before any child exists, fall back to the device setting.
    val activeAgeProfile = selectedChild?.age ?: devicePreferredProfile
    val activeChildId = selectedChild?.id

    val textModerationViewModel: TextModerationViewModel = viewModel()
    val imageModerationViewModel: ImageModerationViewModel = viewModel()
    val screenTimeViewModel: ScreenTimeViewModel = viewModel()
    val appManagementViewModel: AppManagementViewModel = viewModel()

    LaunchedEffect(familyId, activeChildId) {
        appManagementViewModel.setFamilyAndChild(familyId, activeChildId)
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

    // Handle deep navigation from FCM push notifications
    LaunchedEffect(targetRoute, signedIn) {
        if (signedIn && targetRoute != null) {
            when (targetRoute) {
                "app_management" -> navController.navigate(Screen.AppManagement.route)
                "review_queue" -> navController.navigate(Screen.ReviewQueue.route)
            }
        }
    }

    if (!authChecked) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    NavHost(
        navController = navController,
        startDestination = com.tele.k_guard.DebugPreview.startRoute?.takeIf { signedIn } ?: if (signedIn) Screen.Home.route else Screen.Onboarding.route
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onGetStarted = { navController.navigate(Screen.SignIn.route) }
            )
        }

        composable(Screen.SignIn.route) {
            SignInScreen(session)
            LaunchedEffect(signedIn) {
                if (signedIn) {
                    navController.navigate(Screen.Home.route) { popUpTo(0) }
                }
            }
        }

        composable(Screen.AgeProfile.route) {
            AgeProfileScreen(
                viewModel = ageProfileViewModel,
                onSaveAndContinue = { navController.popBackStack() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                activeAgeProfile = activeAgeProfile,
                activeChildName = selectedChild?.name,
                openAlertCount = openAlerts,
                pendingApprovalCount = pendingApprovalsCount,
                onNavigateToTextModeration = { navController.navigate(Screen.TextModeration.route) },
                onNavigateToImageModeration = { navController.navigate(Screen.ImageModeration.route) },
                onNavigateToAgeProfile = { navController.navigate(Screen.Children.route) },
                onNavigateToScreenTime = { navController.navigate(Screen.ScreenTime.route) },
                onNavigateToAppManagement = { navController.navigate(Screen.AppManagement.route) },
                onNavigateToFlaggedContent = { navController.navigate(Screen.ReviewQueue.route) },
                onNavigateToPrivacy = { navController.navigate(Screen.Privacy.route) },
                onNavigateToWebProtection = { navController.navigate(Screen.WebProtection.route) }
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
                activeAgeProfile = activeAgeProfile,
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
    }
}
