package com.tele.k_guard.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.tele.k_guard.R
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.ModerationDecision
import com.tele.k_guard.data.model.ModerationResponse

/**
 * Requests a runtime permission and reports the outcome via [onResult] (true = granted, false = denied).
 * Returns the zero-arg action to invoke from a click handler.
 * [onResult] defaults to a no-op so existing call sites that ignore the result keep compiling.
 */
@Composable
fun RequestPermission(
    permission: String,
    onResult: (Boolean) -> Unit = {}
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onResult(granted)
    }
    return { launcher.launch(permission) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KGuardTopAppBar(
    title: String,
    canNavigateBack: Boolean = false,
    onNavigateBack: () -> Unit = {},
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate Back"
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

private data class DecisionStyle(
    val backgroundColor: Color,
    val textColor: Color,
    val label: String,
    val icon: ImageVector
)

@Composable
fun DecisionBadge(decision: ModerationDecision) {
    val style = when (decision) {
        ModerationDecision.ALLOW -> DecisionStyle(Color(0xFF2E7D32), Color.White, "ALLOW", Icons.Default.CheckCircle)
        ModerationDecision.WARN -> DecisionStyle(Color(0xFFE65100), Color.White, "WARN", Icons.Default.Warning)
        ModerationDecision.BLUR -> DecisionStyle(Color(0xFF0277BD), Color.White, "BLUR", Icons.Default.Info)
        ModerationDecision.BLOCK -> DecisionStyle(Color(0xFFC62828), Color.White, "BLOCK", Icons.Default.Lock)
        ModerationDecision.REVIEW -> DecisionStyle(Color(0xFF6A1B9A), Color.White, "REVIEW", Icons.Default.Info)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(style.backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = style.label,
            tint = style.textColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = style.label,
            color = style.textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun ModerationResultCard(
    response: ModerationResponse,
    activeAgeProfile: AgeProfile,
    originalContent: String? = null,
    isImage: Boolean = false
) {
    var isBlurRevealed by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Category: ${response.category}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Evaluated for ${activeAgeProfile.title} (${activeAgeProfile.ageRange})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DecisionBadge(decision = response.parsedDecision)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (response.parsedDecision == ModerationDecision.REVIEW) {
                    // Nothing was scored, so don't show a risk number or a suitability verdict.
                    Text(
                        text = "Not assessed yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6A1B9A),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Risk Score: ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${(response.riskScore * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (response.riskScore > 0.6f) Color(0xFFC62828) else Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (response.ageAppropriate) "Suitable for Age" else "Unsuitable for Age",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (response.ageAppropriate) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Explanation: ${response.reason}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (response.parsedDecision) {
                ModerationDecision.ALLOW -> {
                    if (!isImage && originalContent != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE8F5E9))
                                .padding(12.dp)
                        ) {
                            Text(text = originalContent, color = Color(0xFF1B5E20))
                        }
                    }
                }
                ModerationDecision.WARN -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF3E0))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Safety Warning",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This content contains sensitive terms. Please exercise discretion.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFBF360C)
                        )
                        if (!isImage && originalContent != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = originalContent, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                ModerationDecision.BLUR -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE1F5FE))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Content Blurred for Safety",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0277BD)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isImage && originalContent != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = Uri.parse(originalContent),
                                    contentDescription = "Blurred image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .blur(if (isBlurRevealed) 0.dp else 25.dp)
                                )
                                if (!isBlurRevealed && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                                    // Modifier.blur is a no-op before Android 12, so hide the image completely instead.
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .background(Color(0xFF455A64))
                                    )
                                }
                                if (!isBlurRevealed && response.canReveal) {
                                    Button(
                                        onClick = { isBlurRevealed = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0277BD))
                                    ) {
                                        Text("Tap to Reveal Preview")
                                    }
                                }
                            }
                        } else if (originalContent != null) {
                            Text(
                                text = if (isBlurRevealed) originalContent else "••••••••••••••••••••••••••••••••••••",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            if (response.canReveal) {
                                Button(
                                    onClick = { isBlurRevealed = !isBlurRevealed },
                                    modifier = Modifier.padding(top = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0277BD))
                                ) {
                                    Text(if (isBlurRevealed) "Hide Content" else "Reveal Content")
                                }
                            } else {
                                Text(
                                    text = "A parent has set this profile so blurred items stay hidden.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF01579B)
                                )
                            }
                        }
                    }
                }
                ModerationDecision.BLOCK -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFEBEE))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Blocked",
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Content Restricted",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                        Text(
                            text = "This item cannot be viewed under ${activeAgeProfile.title} settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB71C1C)
                        )
                    }
                }
                ModerationDecision.REVIEW -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF3E5F5))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "This item is held until a parent reviews it. It has not been approved.",
                            color = Color(0xFF4A148C),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AgeProfileCard(
    profile: AgeProfile,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onSelect() }
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = profile.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${profile.ageRange})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = profile.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The K-Guard brand mark: the launcher padlock artwork centred on a white disc.
 * Use [modifier] to size it, e.g. `KGuardLogo(Modifier.size(120.dp))`.
 */
@Composable
fun KGuardLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = "K-Guard logo",
            modifier = Modifier.fillMaxSize()
        )
    }
}
