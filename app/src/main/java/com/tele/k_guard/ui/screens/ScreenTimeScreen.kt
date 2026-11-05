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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.ScreenTimeConfig
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.ScreenTimeViewModel

@Composable
fun ScreenTimeScreen(
    viewModel: ScreenTimeViewModel,
    activeAgeProfile: AgeProfile,
    onNavigateBack: () -> Unit
) {
    val config by viewModel.screenTimeConfig.collectAsState()

    ScreenTimeContent(
        config = config,
        activeAgeProfile = activeAgeProfile,
        onApplyAgeRecommendation = { viewModel.applyAgeRecommendation(activeAgeProfile) },
        onUpdateDailyLimit = { viewModel.updateDailyLimit(it) },
        onToggleSchoolTime = { viewModel.toggleSchoolTime(it) },
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun ScreenTimeContent(
    config: ScreenTimeConfig,
    activeAgeProfile: AgeProfile,
    onApplyAgeRecommendation: () -> Unit,
    onUpdateDailyLimit: (Int) -> Unit,
    onToggleSchoolTime: (Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            KGuardTopAppBar(
                title = "Smart Screen Time Management",
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
                text = "Pillar 1: Manage Digital Habits",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Screen Time & Schedules",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Today's Usage",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${config.usedTodayMinutes} / ${config.dailyLimitMinutes} mins",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val progress = (config.usedTodayMinutes.toFloat() / config.dailyLimitMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "K-Guard Age-Aware Guidance",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = ScreenTimeConfig.getRecommendationForAge(activeAgeProfile),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onApplyAgeRecommendation,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Apply Recommended Limit")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Daily Limit: ${config.dailyLimitMinutes} Minutes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = config.dailyLimitMinutes.toFloat(),
                onValueChange = { onUpdateDailyLimit(it.toInt()) },
                valueRange = 15f..360f,
                steps = 22
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Bedtime Downtime Schedule", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${config.bedtimeStart} to ${config.bedtimeEnd} (Locks device apps)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "School Hours Focus Mode", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${config.schoolTimeStart} to ${config.schoolTimeEnd} (Restricts non-educational apps)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.schoolTimeEnabled,
                        onCheckedChange = onToggleSchoolTime
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ScreenTimeScreenPreview() {
    KGuardTheme {
        ScreenTimeContent(
            config = ScreenTimeConfig(dailyLimitMinutes = 120, usedTodayMinutes = 45, schoolTimeEnabled = true),
            activeAgeProfile = AgeProfile.PRETEENS_8_12,
            onApplyAgeRecommendation = {},
            onUpdateDailyLimit = {},
            onToggleSchoolTime = {},
            onNavigateBack = {}
        )
    }
}
