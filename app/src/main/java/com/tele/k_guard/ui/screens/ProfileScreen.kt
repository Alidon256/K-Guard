package com.tele.k_guard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseUser
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.ui.components.KGCard
import com.tele.k_guard.ui.components.KGDangerButton
import com.tele.k_guard.ui.components.KGStatusTag
import com.tele.k_guard.ui.components.KGuardLogo
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGColors
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.SessionViewModel

@Composable
fun ProfileScreen(
    session: SessionViewModel,
    isChildDevice: Boolean,
    onSignOut: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val user by session.user.collectAsState()
    val selectedChild by session.selectedChild.collectAsState()

    ProfileContent(
        user = user,
        selectedChild = selectedChild,
        isChildDevice = isChildDevice,
        onSignOut = { session.signOut(onDone = onSignOut) },
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun ProfileContent(
    user: FirebaseUser?,
    selectedChild: ChildProfile?,
    isChildDevice: Boolean,
    onSignOut: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val displayName = if (isChildDevice) selectedChild?.name ?: "Child Account"
    else user?.email ?: "Parent Account"
    val roleLabel = if (isChildDevice) "Supervised device" else "Parent admin"
    val roleColor = if (isChildDevice) KGColors.accent else KGColors.success

    Scaffold(
        topBar = {
            KGuardTopAppBar(
                title = "Account Profile",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Identity header: logo, display name, role chip.
            KGCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KGuardLogo(modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        KGStatusTag(text = roleLabel, color = roleColor)
                    }
                }
            }

            // Account details.
            KGCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileDetailRow(label = "Email Account", value = user?.email ?: "Not signed in")
                    ProfileDetailRow(label = "Device Mode", value = if (isChildDevice) "Child Protection Mode" else "Parent Administration Mode")
                    if (isChildDevice && selectedChild != null) {
                        ProfileDetailRow(label = "Linked Child", value = "${selectedChild.name} (${selectedChild.age.title})")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            KGDangerButton(
                text = "Sign out",
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Preview(showBackground = true)
@Composable
fun ProfilePreview() {
    KGuardTheme {
        ProfileContent(
            user = null,
            selectedChild = null,
            isChildDevice = false,
            onSignOut = {},
            onNavigateBack = {}
        )
    }
}
