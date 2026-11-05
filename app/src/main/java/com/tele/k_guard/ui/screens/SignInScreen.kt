package com.tele.k_guard.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import com.tele.k_guard.DebugPreview
import com.tele.k_guard.R
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.SessionViewModel

@Composable
fun SignInScreen(
    session: SessionViewModel,
    onChildPaired: () -> Unit = {}
) {
    val busy by session.busy.collectAsState()
    val error by session.error.collectAsState()
    val context = LocalContext.current
    val clientId = remember(context) { context.getString(R.string.default_web_client_id) }
    val scope = rememberCoroutineScope()
    val showDemo = remember(context) { runCatching { DebugPreview.isDebuggable(context) }.getOrDefault(false) }

    SignInContent(
        busy = busy,
        error = error,
        showDemoButton = showDemo,
        onSignIn = { email, password ->
            Log.d("KGuard", "UI: User clicked Sign In with Email: $email")
            session.signIn(email, password)
        },
        onSignUp = { email, password ->
            Log.d("KGuard", "UI: User clicked Create Account with Email: $email")
            session.signUp(email, password)
        },
        onParentGoogleSignIn = {
            Log.d("KGuard", "UI: User clicked Sign In with Google")
            scope.launch {
                try {
                    val credentialManager = CredentialManager.create(context)
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(clientId)
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    val result = credentialManager.getCredential(context, request)
                    val credential = result.credential
                    if (credential is androidx.credentials.CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                        Log.d("KGuard", "UI: Google credential retrieved successfully, signing in with Firebase...")
                        session.signInWithCredential(firebaseCredential)
                    } else {
                        Log.e("KGuard", "UI: Unexpected credential type received from Credential Manager")
                    }
                } catch (e: Exception) {
                    Log.e("KGuard", "UI: Google Sign-In error", e)
                }
            }
        },
        onRedeemOtp = { otp ->
            Log.d("KGuard", "UI: User clicked Redeem OTP: $otp")
            session.redeemPairingCode(otp, onDone = onChildPaired)
        },
        onDemoClick = {
            Log.d("KGuard", "UI: User clicked Explore Demo Mode")
            DebugPreview.demo = true
            session.enterDemo()
        }
    )
}

@Composable
fun SignInContent(
    busy: Boolean,
    error: String?,
    showDemoButton: Boolean = true,
    onSignIn: (String, String) -> Unit = { _, _ -> },
    onSignUp: (String, String) -> Unit = { _, _ -> },
    onParentGoogleSignIn: () -> Unit = {},
    onRedeemOtp: (String) -> Unit = {},
    onDemoClick: () -> Unit = {}
) {
    var isChildMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    val validParent = email.contains("@") && password.length >= 6
    val validOtp = otpCode.trim().length == 6

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(8.dp))
        Text("K-Guard", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            if (isChildMode) "Child Device Setup (OTP)" else "Parent Sign-In",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = !isChildMode,
                onClick = { isChildMode = false },
                label = { Text("Parent Mode") },
                leadingIcon = { Icon(Icons.Default.SupervisorAccount, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = isChildMode,
                onClick = { isChildMode = true },
                label = { Text("Child Device Setup") },
                leadingIcon = { Icon(Icons.Default.ChildCare, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))

        if (busy) {
            CircularProgressIndicator()
        } else {
            if (!isChildMode) {
                OutlinedTextField(
                    value = email, onValueChange = { email = it }, label = { Text("Email") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, label = { Text("Password (6+ characters)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onSignIn(email, password) },
                    enabled = validParent,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign in with Email", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onSignUp(email, password) },
                    enabled = validParent,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create Parent Account")
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onParentGoogleSignIn,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign in with Google")
                }
            } else {
                Text(
                    "Step 1: Sign in with the child's Google account to authorize this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onParentGoogleSignIn,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign in with Google (Child)")
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text(
                    "Step 2: Enter the 6-digit pairing OTP code generated in your Parent Dashboard:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = { if (it.length <= 6) otpCode = it.filter { c -> c.isDigit() } },
                    label = { Text("6-Digit Pairing OTP Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onRedeemOtp(otpCode.trim()) },
                    enabled = validOtp,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Pair & Lock Child Device", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showDemoButton) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDemoClick) { Text("Explore Demo Mode") }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Your family's alerts and settings are stored securely and protected with age-aware controls.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SignInScreenPreview() {
    KGuardTheme {
        SignInContent(
            busy = false,
            error = null,
            showDemoButton = true,
            onSignIn = { _, _ -> },
            onSignUp = { _, _ -> },
            onParentGoogleSignIn = {},
            onRedeemOtp = {},
            onDemoClick = {}
        )
    }
}
