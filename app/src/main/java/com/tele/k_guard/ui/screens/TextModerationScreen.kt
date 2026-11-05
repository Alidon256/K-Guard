package com.tele.k_guard.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.ModerationUiState
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.components.ModerationResultCard
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.TextModerationViewModel

@Composable
fun TextModerationScreen(
    viewModel: TextModerationViewModel,
    activeAgeProfile: AgeProfile,
    activeChildId: String?,
    onNavigateBack: () -> Unit
) {
    val inputText by viewModel.inputText.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    TextModerationContent(
        inputText = inputText,
        uiState = uiState,
        activeAgeProfile = activeAgeProfile,
        onInputTextChange = { viewModel.updateInputText(it) },
        onSubmit = { viewModel.submitTextForModeration(activeChildId) },
        onReset = { viewModel.resetState() },
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun TextModerationContent(
    inputText: String,
    uiState: ModerationUiState,
    activeAgeProfile: AgeProfile,
    onInputTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onReset: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            KGuardTopAppBar(
                title = "Text Content Moderation",
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
                text = "Enter Text for Safety Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Evaluating against active profile: ${activeAgeProfile.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                label = { Text("Input message or comment") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = uiState !is ModerationUiState.Loading && inputText.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState is ModerationUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Text("Analyzing...")
                } else {
                    Text("Evaluate Content Safety", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (val state = uiState) {
                is ModerationUiState.Idle -> {
                    Text(
                        text = "Submit text above to view safety classification and decision interventions.",
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
                        isImage = false
                    )
                    TextButton(
                        onClick = onReset,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Clear & Evaluate New Text")
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
fun TextModerationScreenPreview() {
    KGuardTheme {
        TextModerationContent(
            inputText = "You are ugly and nobody likes you",
            uiState = ModerationUiState.Idle,
            activeAgeProfile = AgeProfile.PRETEENS_8_12,
            onInputTextChange = {},
            onSubmit = {},
            onReset = {},
            onNavigateBack = {}
        )
    }
}
