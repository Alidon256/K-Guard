package com.tele.k_guard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.tele.k_guard.data.repository.UserPreferencesRepository
import com.tele.k_guard.service.KGuardCompanionService
import com.tele.k_guard.ui.navigation.AppNavigation
import com.tele.k_guard.ui.theme.KGuardTheme
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val targetRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DebugPreview.init(this, intent.getStringExtra("demo_route"), intent.getIntExtra("demo_tab", 0))
        com.tele.k_guard.service.KGuardMessagingService.ensureChannel(this)

        targetRoute.value = intent.getStringExtra("navigate_to")

        val userPreferencesRepository = UserPreferencesRepository(applicationContext)

        lifecycleScope.launch {
            userPreferencesRepository.isChildDeviceFlow.collect { isChild ->
                if (isChild) {
                    val famId = userPreferencesRepository.familyIdFlow.firstOrNull()
                    val childId = userPreferencesRepository.activeChildIdFlow.firstOrNull()
                    KGuardCompanionService.startCompanionService(this@MainActivity, famId, childId)
                }
            }
        }

        setContent {
            val themeMode by userPreferencesRepository.themeModeFlow.collectAsState(initial = "SYSTEM")
            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            KGuardTheme(darkTheme = isDark, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        targetRoute = targetRoute.value,
                        onTargetRouteHandled = { targetRoute.value = null },
                        userPreferencesRepository = userPreferencesRepository
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetRoute.value = intent.getStringExtra("navigate_to")
    }
}
