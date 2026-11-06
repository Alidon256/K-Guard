package com.tele.k_guard.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import com.tele.k_guard.R
import com.tele.k_guard.data.repository.UserPreferencesRepository
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.SessionViewModel

@Composable
fun SignInScreen(
    session: SessionViewModel,
    userPreferencesRepository: UserPreferencesRepository,
    onParentSignedIn: () -> Unit = {},
    onChildPaired: () -> Unit = {}
) {
    val busy by session.busy.collectAsState()
    val error by session.error.collectAsState()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val clientId = remember(appContext) { appContext.getString(R.string.default_web_client_id) }
    val scope = rememberCoroutineScope()

    /** Asks Google for an ID token; returns null (after showing why) if the person cancelled or it failed. */
    suspend fun googleCredential(): com.google.firebase.auth.AuthCredential? = try {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
            )
            .build()
        val credential = CredentialManager.create(context).getCredential(context, request).credential
        if (credential is androidx.credentials.CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(credential.data).idToken, null)
        } else {
            session.showError("Google sign-in returned an unexpected result. Please try again.")
            null
        }
    } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
        null // the person closed the account picker; nothing to report
    } catch (e: androidx.credentials.exceptions.NoCredentialException) {
        session.showError("No Google account is set up on this phone. Add one in Settings › Accounts, then try again.")
        null
    } catch (e: androidx.credentials.exceptions.GetCredentialException) {
        Log.e("KGuard", "Google sign-in failed", e)
        session.showError("Google sign-in isn't available right now. Check the internet connection and try again.")
        null
    }

    SignInContent(
        busy = busy,
        error = error,
        onParentGoogleSignIn = {
            session.clearError()
            scope.launch {
                val credential = googleCredential() ?: return@launch
                userPreferencesRepository.saveChildDeviceMode(false)
                session.signInParent(credential) { onParentSignedIn() }
            }
        },
        onChildGoogleSignIn = { otpCode ->
            session.clearError()
            scope.launch {
                val credential = googleCredential() ?: return@launch
                session.signInChildAndPair(credential, otpCode) {
                    scope.launch {
                        session.familyId.value?.let { userPreferencesRepository.saveFamilyId(it) }
                        userPreferencesRepository.saveChildDeviceMode(true, session.selectedChild.value?.id)
                        onChildPaired()
                    }
                }
            }
        }
    )
}

@Composable
fun SignInContent(
    busy: Boolean,
    error: String?,
    onParentGoogleSignIn: () -> Unit = {},
    onChildGoogleSignIn: (String) -> Unit = {}
) {
    var isChildMode by remember { mutableStateOf(false) }
    var childStep by remember { mutableIntStateOf(1) } // Step 1: OTP Code, Step 2: Google Sign-In
    var otpCode by remember { mutableStateOf("") }
    val validOtp = otpCode.trim().length == 6

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_inapp),
            contentDescription = "K-Guard Logo",
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.height(8.dp))
        Text("K-Guard", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            if (isChildMode) "Child Device Setup (Step $childStep of 2)" else "Parent Sign-In",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = !isChildMode,
                onClick = {
                    isChildMode = false
                    childStep = 1
                },
                label = { Text("Parent Mode") },
                leadingIcon = { Icon(Icons.Default.SupervisorAccount, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = isChildMode,
                onClick = {
                    isChildMode = true
                    childStep = 1
                    otpCode = ""
                },
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
                Text(
                    "Sign in with your Google administrator account to manage family protection, screen time, and app approvals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onParentGoogleSignIn,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign in with Google", fontWeight = FontWeight.Bold)
                }
            } else {
                if (childStep == 1) {
                    Text(
                        "Step 1 of 2: Enter the 6-digit pairing OTP code generated in your Parent Dashboard:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
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
                        onClick = { if (validOtp) childStep = 2 },
                        enabled = validOtp,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Next: Sign in with Google", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        "Step 2 of 2: Sign in with the child's Google account to authorize and complete protection on this device:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onChildGoogleSignIn(otpCode.trim()) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Sign in with Google (Child & Authorize)", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { childStep = 1 },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Back to Step 1 (Change Code)")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Your family's alerts and settings are stored securely and protected with age-aware controls.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
            onParentGoogleSignIn = {},
            onChildGoogleSignIn = {}
        )
    }
}
