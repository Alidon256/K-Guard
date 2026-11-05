package com.tele.k_guard.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.ModerationUiState
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.components.ModerationResultCard
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.ImageModerationViewModel

@Composable
fun ImageModerationScreen(
    viewModel: ImageModerationViewModel,
    activeAgeProfile: AgeProfile,
    activeChildId: String?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.setSelectedImage(uri)
    }

    ImageModerationContent(
        selectedImageUri = selectedImageUri,
        uiState = uiState,
        activeAgeProfile = activeAgeProfile,
        onPickImage = { imagePickerLauncher.launch("image/*") },
        onSubmit = { viewModel.submitImageForModeration(context, activeChildId) },
        onReset = { viewModel.resetState() },
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun ImageModerationContent(
    selectedImageUri: Uri?,
    uiState: ModerationUiState,
    activeAgeProfile: AgeProfile,
    onPickImage: () -> Unit,
    onSubmit: () -> Unit,
    onReset: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            KGuardTopAppBar(
                title = "Image Content Moderation",
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Select Image for Safety Moderation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Evaluating under profile: ${activeAgeProfile.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onPickImage() },
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Selected Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Pick Image",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap to pick an image from gallery",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = uiState !is ModerationUiState.Loading && selectedImageUri != null,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState is ModerationUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Text("Scanning Image...")
                } else {
                    Text("Moderate Image Safety", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (val state = uiState) {
                is ModerationUiState.Idle -> {
                    Text(
                        text = "Pick an image and tap 'Moderate Image Safety' to run AI moderation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ModerationUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ModerationUiState.Success -> {
                    Text(
                        text = "Moderation Result",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ModerationResultCard(
                        response = state.response,
                        activeAgeProfile = activeAgeProfile,
                        originalContent = state.originalContent,
                        isImage = true
                    )
                    TextButton(
                        onClick = onReset,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Pick Another Image")
                    }
                }
                is ModerationUiState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ImageModerationScreenPreview() {
    KGuardTheme {
        ImageModerationContent(
            selectedImageUri = null,
            uiState = ModerationUiState.Idle,
            activeAgeProfile = AgeProfile.PRETEENS_8_12,
            onPickImage = {},
            onSubmit = {},
            onReset = {},
            onNavigateBack = {}
        )
    }
}
