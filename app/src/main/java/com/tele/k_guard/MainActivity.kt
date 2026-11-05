package com.tele.k_guard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.tele.k_guard.ui.navigation.AppNavigation
import com.tele.k_guard.ui.theme.KGuardTheme

class MainActivity : ComponentActivity() {

    // targetRouteId increments on every intent carrying a route, so AppNavigation's
    // LaunchedEffect re-fires even for repeat taps of the same route.
    private val targetRoute = mutableStateOf<String?>(null)
    private val targetRouteId = mutableIntStateOf(0)

    private fun applyIntentRoute(intent: Intent?) {
        val route = intent?.getStringExtra("navigate_to")
        if (route != null) {
            targetRoute.value = route
            targetRouteId.intValue++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.tele.k_guard.service.KGuardMessagingService.ensureChannel(this)

        applyIntentRoute(intent)

        setContent {
            KGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        targetRoute = targetRoute.value,
                        targetRouteId = targetRouteId.intValue,
                        onTargetRouteConsumed = { targetRoute.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentRoute(intent)
    }
}
