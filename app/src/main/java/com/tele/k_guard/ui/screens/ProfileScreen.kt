package com.tele.k_guard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseUser
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.ui.components.KGuardTopAppBar
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
        onSignOut = {
            session.signOut()
            onSignOut()
        },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Avatar & Name Card
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isChildDevice) Icons.Default.Person else Icons.Default.SupervisorAccount,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = if (isChildDevice) selectedChild?.name ?: "Child Account" else user?.email?.substringBefore('@')?.replaceFirstChar { it.uppercase() } ?: "Parent Account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Role Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isChildDevice) Color(0xFF1E88E5).copy(alpha = 0.15f) else Color(0xFF8E24AA).copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isChildDevice) "ROLE: CHILD / SUPERVISED DEVICE" else "ROLE: FAMILY PARENT ADMIN",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isChildDevice) Color(0xFF1E88E5) else Color(0xFF8E24AA)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileDetailRow(label = "Email Account", value = user?.email ?: "Not signed in")
                    ProfileDetailRow(label = "Device Mode", value = if (isChildDevice) "Child Protection Mode" else "Parent Administration Mode")
                    if (isChildDevice && selectedChild != null) {
                        ProfileDetailRow(label = "Linked Child", value = "${selectedChild.name} (${selectedChild.age.title})")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actions: Edit / Logout
            Button(
                onClick = { /* Edit profile action */ },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Profile Information", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828))
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out", fontWeight = FontWeight.Bold)
            }
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
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
