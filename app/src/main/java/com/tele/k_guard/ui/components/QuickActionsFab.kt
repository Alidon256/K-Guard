package com.tele.k_guard.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One shortcut in the [QuickActionsFab] menu. */
data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0,
    val onClick: () -> Unit
)

/** The parent dashboard's shortcuts, in the order they appear above the + button (top first). */
fun parentQuickActions(
    pendingApprovals: Int,
    onWebProtection: () -> Unit,
    onAppApprovals: () -> Unit,
    onDigitalHabits: () -> Unit,
    onAddChild: () -> Unit,
    onAskAssistant: (() -> Unit)? = null
) = listOfNotNull(
    onAskAssistant?.let { QuickAction("Ask K-Guard", Icons.Default.AutoAwesome, onClick = it) },
    QuickAction("Web protection", Icons.Default.VpnLock, onClick = onWebProtection),
    QuickAction("App approvals", Icons.Default.Apps, badge = pendingApprovals, onClick = onAppApprovals),
    QuickAction("Digital habits", Icons.Default.Timer, onClick = onDigitalHabits),
    QuickAction("Add a child", Icons.Default.PersonAdd, onClick = onAddChild)
)

/**
 * A + button that opens a column of labelled shortcuts above it. The page behind dims and closes the menu
 * when tapped; Back closes it too. Use [QuickActionsScrim] as a full-screen layer and [QuickActionsFab] as
 * the Scaffold's floating action button, sharing one [expanded] state.
 */
@Composable
fun QuickActionsFab(
    actions: List<QuickAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "fab-rotation")

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        actions.forEachIndexed { index, action ->
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + slideOutVertically { it / 2 } + scaleOut(targetScale = 0.8f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp,
                        modifier = Modifier.clickable {
                            onExpandedChange(false)
                            action.onClick()
                        }
                    ) {
                        Text(
                            action.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    SmallFloatingActionButton(
                        onClick = {
                            onExpandedChange(false)
                            action.onClick()
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        if (action.badge > 0) {
                            BadgedBox(badge = { Badge { Text(if (action.badge > 99) "99+" else "${action.badge}") } }) {
                                Icon(action.icon, contentDescription = action.label)
                            }
                        } else {
                            Icon(action.icon, contentDescription = action.label)
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.semantics { contentDescription = if (expanded) "Close quick actions" else "Quick actions" }
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(28.dp).rotate(rotation))
        }
    }
}

/** Dims the page while the quick actions are open; tapping it closes them. */
@Composable
fun QuickActionsScrim(expanded: Boolean, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        )
    }
}

/** Remembers whether the quick actions are open across configuration changes. */
@Composable
fun rememberQuickActionsState() = rememberSaveable { mutableStateOf(false) }
