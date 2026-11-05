package com.tele.k_guard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.tele.k_guard.ui.navigation.AppNavigation
import com.tele.k_guard.ui.theme.KGuardTheme

class MainActivity : ComponentActivity() {

    private val targetRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DebugPreview.init(this, intent.getStringExtra("demo_route"), intent.getIntExtra("demo_tab", 0))
        com.tele.k_guard.service.KGuardMessagingService.ensureChannel(this)

        targetRoute.value = intent.getStringExtra("navigate_to")

        setContent {
            KGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(targetRoute = targetRoute.value)
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
