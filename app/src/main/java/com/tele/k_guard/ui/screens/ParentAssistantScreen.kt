package com.tele.k_guard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.AssistantMessage
import com.tele.k_guard.viewmodel.ParentAssistantUiState
import com.tele.k_guard.viewmodel.ParentAssistantViewModel

private val AssistantGradient = Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF0EA5E9)))

@Composable
fun ParentAssistantScreen(
    viewModel: ParentAssistantViewModel,
    parentName: String?,
    children: List<ChildProfile>,
    onNavigateBack: () -> Unit
) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(parentName, children) { viewModel.setFamily(parentName, children) }
    ParentAssistantContent(
        ui = ui,
        parentName = parentName,
        children = children,
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onRetry = viewModel::retry,
        onNewChat = viewModel::newChat,
        onNavigateBack = onNavigateBack
    )
}

private data class Suggestion(val icon: ImageVector, val title: String, val prompt: String)

private fun suggestions(children: List<ChildProfile>): List<Suggestion> {
    val first = children.firstOrNull()?.name?.substringBefore(' ')
    val who = first ?: "my child"
    return listOf(
        Suggestion(Icons.Default.Bedtime, "Set up a healthy bedtime", "How do I set a bedtime and a daily screen time limit that suits $who?"),
        Suggestion(Icons.Default.Apps, "How app approvals work", "What happens when $who installs a new app, and how do I approve or block it?"),
        Suggestion(Icons.Default.VpnLock, "Block harmful websites", "How do I turn on web protection and block specific sites for $who?"),
        Suggestion(Icons.Default.Shield, "If my child is bullied online", "What should I do if I think $who is being bullied online?"),
        Suggestion(Icons.Default.Groups, "Talking about screen time", "How can I talk to $who about screen time rules without it becoming a fight?")
    )
}

@Composable
fun ParentAssistantContent(
    ui: ParentAssistantUiState,
    parentName: String?,
    children: List<ChildProfile>,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onNewChat: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val last = ui.messages.lastOrNull()

    // Follow the answer as it streams in.
    LaunchedEffect(ui.messages.size, last?.text?.length) {
        if (ui.messages.isNotEmpty()) listState.scrollToItem(ui.messages.lastIndex, Int.MAX_VALUE)
    }

    Scaffold(
        topBar = {
            KGuardTopAppBar(
                title = "K-Guard Assistant",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                actions = {
                    if (ui.messages.isNotEmpty()) {
                        IconButton(onClick = onNewChat) { Icon(Icons.Default.AddComment, contentDescription = "New chat") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (ui.messages.isEmpty()) {
                    EmptyState(parentName, children, onPick = onSend)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(ui.messages, key = { it.id }) { m ->
                            if (m.fromParent) ParentBubble(m.text)
                            else AssistantAnswer(m, isLast = m.id == last?.id, onRetry = onRetry)
                        }
                    }
                }
            }

            Composer(
                draft = draft,
                onDraftChange = { draft = it.take(2000) },
                busy = ui.busy,
                onSend = { onSend(draft); draft = "" },
                onStop = onStop
            )
        }
    }
}

@Composable
private fun EmptyState(parentName: String?, children: List<ChildProfile>, onPick: (String) -> Unit) {
    val first = parentName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    val childNames = children.map { it.name.substringBefore(' ') }.filter { it.isNotBlank() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(AssistantGradient),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (first != null) "Hi $first, how can I help?" else "How can I help?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        childNames.isEmpty() -> "Ask how any K-Guard feature works, or how to support your child online."
                        childNames.size == 1 -> "Ask how any K-Guard feature works, or how to support ${childNames[0]} online."
                        else -> "Ask how any K-Guard feature works, or how to support ${childNames.dropLast(1).joinToString(", ")} and ${childNames.last()} online."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(suggestions(children)) { s ->
            Surface(
                onClick = { onPick(s.prompt) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(s.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Text(s.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ParentBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            SelectionContainer {
                Text(
                    text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun AssistantAnswer(m: AssistantMessage, isLast: Boolean, onRetry: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(AssistantGradient),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            when {
                m.text.isBlank() && m.streaming -> TypingDots()
                m.text.isNotBlank() -> SelectionContainer { RichAnswer(m.text, streaming = m.streaming) }
            }
            m.error?.let { err ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.padding(top = if (m.text.isBlank()) 0.dp else 8.dp)
                ) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(err, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f, fill = false))
                        if (isLast) {
                            TextButton(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(visible = !m.streaming && m.text.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(plainText(m.text))) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy answer", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600, delayMillis = i * 160), RepeatMode.Reverse), label = "dot$i"
            )
            Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

/** Renders the small Markdown subset the assistant is asked to use: paragraphs, "-"/"*" bullets, numbered steps, **bold**. */
@Composable
private fun RichAnswer(text: String, streaming: Boolean) {
    val blocks = remember(text) { text.trim().split(Regex("\n{2,}")) }
    val bodyColor = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEachIndexed { bi, block ->
            val lines = block.lines().filter { it.isNotBlank() }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEachIndexed { li, raw ->
                    val line = raw.trim()
                    val tail = if (streaming && bi == blocks.lastIndex && li == lines.lastIndex) " ▍" else ""
                    val bullet = Regex("^[-*•]\\s+(.*)").find(line)
                    val numbered = Regex("^(\\d+)[.)]\\s+(.*)").find(line)
                    val heading = Regex("^#{1,6}\\s+(.*)").find(line)
                    when {
                        bullet != null -> ListLine("•", bullet.groupValues[1] + tail, bodyColor)
                        numbered != null -> ListLine("${numbered.groupValues[1]}.", numbered.groupValues[2] + tail, bodyColor)
                        heading != null -> Text(inline("**${heading.groupValues[1]}**$tail"), style = MaterialTheme.typography.titleSmall, color = bodyColor)
                        else -> Text(inline(line + tail), style = MaterialTheme.typography.bodyLarge, color = bodyColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun ListLine(marker: String, text: String, color: Color) {
    Row {
        Text(marker, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(22.dp), textAlign = TextAlign.Start)
        Text(inline(text), style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
    }
}

private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        val start = s.indexOf("**", i)
        if (start < 0) { append(s.substring(i)); break }
        val end = s.indexOf("**", start + 2)
        if (end < 0) { append(s.substring(i).replace("**", "")); break }
        append(s.substring(i, start))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(s.substring(start + 2, end)) }
        i = end + 2
    }
}

private fun plainText(s: String) = s.replace("**", "").replace(Regex("(?m)^#{1,6}\\s+"), "").replace(Regex("(?m)^[*]\\s+"), "- ")

@Composable
private fun Composer(draft: String, onDraftChange: (String) -> Unit, busy: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    Surface(tonalElevation = 2.dp, shadowElevation = 6.dp) {
        Column(Modifier.navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                TextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about K-Guard or your child…") },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                Spacer(Modifier.width(8.dp))
                if (busy) {
                    FilledIconButton(onClick = onStop, modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) { Icon(Icons.Default.Stop, contentDescription = "Stop answer") }
                } else {
                    FilledIconButton(onClick = onSend, enabled = draft.isNotBlank(), modifier = Modifier.size(52.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
            Text(
                "K-Guard Assistant can make mistakes. In an emergency, contact local emergency services.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ParentAssistantPreview() {
    KGuardTheme {
        ParentAssistantContent(
            ui = ParentAssistantUiState(
                messages = listOf(
                    AssistantMessage(fromParent = true, text = "How do I set a bedtime for Martha?"),
                    AssistantMessage(fromParent = false, text = "Open **Screen Time & Device Controls** and choose Martha.\n\n- Turn on **Bedtime Downtime**\n- Pick a start and end time\n\nHer phone locks at bedtime.")
                )
            ),
            parentName = "Anthony", children = emptyList(),
            onSend = {}, onStop = {}, onRetry = {}, onNewChat = {}, onNavigateBack = {}
        )
    }
}
