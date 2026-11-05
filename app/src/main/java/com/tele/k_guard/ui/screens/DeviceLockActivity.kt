package com.tele.k_guard.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.tele.k_guard.data.repository.UserPreferencesRepository
import com.tele.k_guard.ui.theme.KGuardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.tele.k_guard.ui.components.KGPrimaryButton
import com.tele.k_guard.ui.components.KGTextLink
import com.tele.k_guard.ui.components.KGuardLogo
import com.tele.k_guard.ui.theme.KGColors
import com.tele.k_guard.ui.theme.KGShapes

/**
 * Fullscreen lock activity displayed on the child's phone when switched off or locked remotely.
 * Back navigation is swallowed so the lock cannot be dismissed without the parent PIN.
 *
 * Dark by design: a lock screen that may appear at night must not flood the room with light.
 * This is the app's one justified dark surface, and its palette is rebuilt from the token
 * rules rather than a slate scheme — pure black canvas, translucent-white cards, white /
 * white-60 text, and the single accent blue as signal. Translucent white/black alphas are
 * the sanctioned exception to the no-inline-hex rule on this surface.
 */

// Dark lock-screen palette (sanctioned inline alphas — see class doc).
private val LockSurface = Color(0x14FFFFFF)             // translucent white card fill
private val LockTextMuted = Color.White.copy(alpha = 0.6f)
private val LockBorder = Color.White.copy(alpha = 0.25f)

class DeviceLockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep the lock screen up: back navigation must never finish the activity.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally a no-op; the remote lock stays until a valid parent PIN unlocks it.
            }
        })

        // Ensure window displays above keyguard and turns screen on when triggered
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // Keep the screen awake while the remote lock is showing.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Parent Remote Screen Lock"
        val childName = intent.getStringExtra(EXTRA_CHILD_NAME) ?: "Child"
        val emergencyNumber = intent.getStringExtra(EXTRA_EMERGENCY_NUMBER) ?: "999"

        setContent {
            KGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    DeviceLockScreenContent(
                        childName = childName,
                        reason = reason,
                        onEmergencyCall = {
                            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyNumber")))
                        },
                        onParentPinUnlock = { pin, onResult ->
                            lifecycleScope.launch {
                                val prefs = UserPreferencesRepository(applicationContext)
                                if (!prefs.hasParentPin()) {
                                    onResult(false, "No parent PIN set up yet")
                                } else {
                                    val ok = prefs.verifyParentPin(pin)
                                    onResult(ok, if (ok) null else "Incorrect PIN. Please try again.")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_REASON = "extra_lock_reason"
        const val EXTRA_CHILD_NAME = "extra_child_name"
        const val EXTRA_EMERGENCY_NUMBER = "extra_emergency_number"

        fun createIntent(
            context: Context,
            childName: String,
            reason: String,
            emergencyNumber: String = "999"
        ): Intent {
            return Intent(context, DeviceLockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_CHILD_NAME, childName)
                putExtra(EXTRA_REASON, reason)
                putExtra(EXTRA_EMERGENCY_NUMBER, emergencyNumber)
            }
        }
    }
}

private const val MAX_FAILED_ATTEMPTS = 5
private const val LOCKOUT_MILLIS = 30_000L

@Composable
fun DeviceLockScreenContent(
    childName: String,
    reason: String,
    onEmergencyCall: () -> Unit,
    onParentPinUnlock: (String, (Boolean, String?) -> Unit) -> Unit
) {
    var showPinDialog by rememberSaveable { mutableStateOf(false) }
    var pinInput by rememberSaveable { mutableStateOf("") }
    var pinErrorText by rememberSaveable { mutableStateOf<String?>(null) }
    var failedAttempts by rememberSaveable { mutableIntStateOf(0) }
    var lockoutUntilMillis by rememberSaveable { mutableLongStateOf(0L) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val lockedOut = nowMillis < lockoutUntilMillis
    val secondsRemaining = ((lockoutUntilMillis - nowMillis + 999) / 1000).toInt().coerceAtLeast(0)

    // Tick the lockout countdown once per second while it is active.
    LaunchedEffect(lockoutUntilMillis) {
        while (lockoutUntilMillis > nowMillis) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top status
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            KGuardLogo(modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "K-Guard Child Protection",
                style = MaterialTheme.typography.labelLarge,
                color = LockTextMuted
            )
        }

        // Center Hero
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(KGShapes.circle)
                    .background(LockSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = KGColors.accent,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Screen Switched Off",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This phone has been switched off remotely by your parent.",
                style = MaterialTheme.typography.bodyMedium,
                color = LockTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = LockSurface),
                shape = KGShapes.card
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "NOTE FROM PARENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = KGColors.accent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "“$reason”",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Bottom Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Parent PIN unlock: the one white pill CTA on the dark canvas.
            Button(
                onClick = { showPinDialog = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = KGShapes.pill
            ) {
                Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Parent PIN Unlock")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Emergency Call: white-on-transparent outlined pill, danger text.
            OutlinedButton(
                onClick = onEmergencyCall,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = KGShapes.pill,
                border = BorderStroke(1.dp, LockBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = KGColors.danger
                )
            ) {
                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Emergency Call")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pinInput = ""
                pinErrorText = null
            },
            title = { Text("Parent PIN Unlock", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    Text(
                        "Enter your 4 to 6 digit Parent Security PIN to temporarily resume device access.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // KGTextField-style numeric field, restyled inline for the dialog.
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { Text("PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pinErrorText != null,
                        enabled = !lockedOut,
                        shape = KGShapes.input,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedBorderColor = KGColors.accent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedLabelColor = KGColors.inkMuted
                        ),
                        supportingText = {
                            when {
                                lockedOut -> Text("Too many incorrect attempts. Try again in ${secondsRemaining}s.")
                                pinErrorText != null -> Text(pinErrorText!!)
                                else -> {}
                            }
                        },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                KGPrimaryButton(
                    text = "Unlock",
                    onClick = {
                        onParentPinUnlock(pinInput) { success, message ->
                            if (success) {
                                showPinDialog = false
                                pinInput = ""
                                pinErrorText = null
                                failedAttempts = 0
                                lockoutUntilMillis = 0L
                            } else {
                                pinErrorText = message
                                failedAttempts++
                                if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                                    lockoutUntilMillis = System.currentTimeMillis() + LOCKOUT_MILLIS
                                    failedAttempts = 0
                                }
                            }
                        }
                    },
                    enabled = !lockedOut
                )
            },
            dismissButton = {
                KGTextLink(
                    text = "Cancel",
                    onClick = { showPinDialog = false }
                )
            }
        )
    }
}
