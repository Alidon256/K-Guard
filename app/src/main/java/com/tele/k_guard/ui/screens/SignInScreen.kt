package com.tele.k_guard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tele.k_guard.DebugPreview
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.SessionViewModel

@Composable
fun SignInScreen(session: SessionViewModel) {
    val busy by session.busy.collectAsState()
    val error by session.error.collectAsState()
    val context = LocalContext.current
    val showDemo = remember(context) { runCatching { DebugPreview.isDebuggable(context) }.getOrDefault(false) }

    SignInContent(
        busy = busy,
        error = error,
        showDemoButton = showDemo,
        onSignIn = { email, password -> session.signIn(email, password) },
        onSignUp = { email, password -> session.signUp(email, password) },
        onDemoClick = {
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
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onDemoClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val valid = email.contains("@") && password.length >= 6

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
            "Parent sign-in",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text("Email") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Password (6+ characters)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        if (busy) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { onSignIn(email, password) }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
                Text("Sign in")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onSignUp(email, password) }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
                Text("Create parent account")
            }
        }
        if (showDemoButton) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDemoClick) { Text("Explore demo (debug build)") }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Your family's alerts and settings are stored securely and only visible to you.",
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
            onDemoClick = {}
        )
    }
}
