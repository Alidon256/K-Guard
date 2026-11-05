package com.tele.k_guard.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.CategoryRule
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.data.model.SafetyCategory
import com.tele.k_guard.service.KGuardMessagingService
import com.tele.k_guard.ui.components.KGCard
import com.tele.k_guard.ui.components.KGDangerButton
import com.tele.k_guard.ui.components.KGEmptyState
import com.tele.k_guard.ui.components.KGPrimaryButton
import com.tele.k_guard.ui.components.KGSecondaryButton
import com.tele.k_guard.ui.components.KGSectionHeader
import com.tele.k_guard.ui.components.KGStatusTag
import com.tele.k_guard.ui.components.KGTextField
import com.tele.k_guard.ui.components.KGToggleRow
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGColors
import com.tele.k_guard.ui.theme.KGShapes
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.SessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Lists child profiles; the selected one drives which age rules apply in the checkers. */
@Composable
fun ChildrenScreen(
    session: SessionViewModel,
    onEditChild: (String?) -> Unit,
    onNavigateBack: () -> Unit
) {
    val children by session.children.collectAsState()
    val selected by session.selectedChild.collectAsState()
    val familyId by session.familyId.collectAsState()

    ChildrenContent(
        children = children,
        selected = selected,
        familyId = familyId,
        onSelectChild = { session.selectChild(it) },
        onEditChild = onEditChild,
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun ChildrenContent(
    children: List<ChildProfile>,
    selected: ChildProfile?,
    familyId: String?,
    onSelectChild: (String) -> Unit,
    onEditChild: (String?) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = { KGuardTopAppBar("Children & Protection", canNavigateBack = true, onNavigateBack = onNavigateBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEditChild(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add child") }
            )
        }
    ) { padding ->
        when {
            children.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center
            ) {
                KGEmptyState(
                    icon = Icons.Default.Person,
                    title = "No child profiles yet",
                    description = "Add a child to set their age group and choose which categories " +
                        "K-Guard should block, blur or warn about.",
                    action = { KGPrimaryButton("Add a child", onClick = { onEditChild(null) }) }
                )
            }

            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(children, key = { it.id }) { child ->
                    val isSelected = child.id == selected?.id
                    ChildRowCard(
                        child = child,
                        isSelected = isSelected,
                        onClick = {
                            // Selecting the child keeps it active in the checkers; the row then
                            // opens its editor, replacing the old separate Edit affordance.
                            onSelectChild(child.id)
                            onEditChild(child.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChildRowCard(
    child: ChildProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    KGCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChildAvatar(child = child, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(child.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${child.age.title} · ${child.age.ageRange}" +
                        if (child.email.isNotBlank()) " · ${child.email}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = KGColors.inkMuted
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isSelected) KGStatusTag("Active", KGColors.info)
                if (child.linkedUid != null) {
                    KGStatusTag("Paired", KGColors.success)
                } else {
                    KGStatusTag("Not linked", KGColors.warning)
                }
            }
        }
    }
}

/** Circular child photo: the picked image when present, otherwise the initial letter on a surface disc. */
@Composable
private fun ChildAvatar(child: ChildProfile, size: androidx.compose.ui.unit.Dp) {
    if (child.photoUrl.isNotBlank()) {
        AsyncImage(
            model = child.photoUrl,
            contentDescription = null,
            modifier = Modifier.size(size).clip(KGShapes.circle),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(KGShapes.circle)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                child.name.firstOrNull()?.uppercase() ?: "•",
                style = if (size >= 96.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                color = KGColors.inkMuted
            )
        }
    }
}

/** Create or edit a child: name, email, photoUrl from device gallery, age group, per-category rules and reveal permission. */
@Composable
fun ChildEditorScreen(
    session: SessionViewModel,
    childId: String?,
    onNavigateBack: () -> Unit
) {
    val children by session.children.collectAsState()
    val busy by session.busy.collectAsState()
    val error by session.error.collectAsState()
    val pairingCode by session.pairingCode.collectAsState()
    val existing = children.find { it.id == childId }

    ChildEditorContent(
        existing = existing,
        childId = childId,
        busy = busy,
        error = error,
        pairingCode = pairingCode,
        onGenerateCode = { id -> session.createPairingCode(id) },
        onClearCode = { session.clearPairingCode() },
        onSaveChild = { updatedChild ->
            session.saveChild(updatedChild, onDone = onNavigateBack)
        },
        onDeleteChild = { id ->
            session.deleteChild(id, onNavigateBack)
        },
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun ChildEditorContent(
    existing: ChildProfile?,
    childId: String? = null,
    busy: Boolean,
    error: String?,
    pairingCode: String? = null,
    onGenerateCode: (String) -> Unit = {},
    onClearCode: () -> Unit = {},
    onSaveChild: (ChildProfile) -> Unit,
    onDeleteChild: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    // Editing an existing child requires the children stream to have actually emitted: before that
    // `existing` is null and a naive Save would create a DUPLICATE child. Block the whole form (and
    // therefore all input) until the record is loaded, so form state is only ever keyed/seeded from
    // the resolved child and typed input is never wiped by a late emission.
    val childrenLoaded = childId == null || existing != null
    if (!childrenLoaded) {
        Scaffold(
            topBar = { KGuardTopAppBar("Loading…", canNavigateBack = true, onNavigateBack = onNavigateBack) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val scope = rememberCoroutineScope()
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var email by remember(existing?.id) { mutableStateOf(existing?.email ?: "") }
    var photoUrl by remember(existing?.id) { mutableStateOf(existing?.photoUrl ?: "") }
    var ageId by remember(existing?.id) { mutableStateOf(existing?.ageProfile ?: AgeProfile.PRETEENS_8_12.id) }
    var rules by remember(existing?.id) { mutableStateOf(existing?.categoryRules ?: emptyMap()) }
    var allowReveal by remember(existing?.id) { mutableStateOf(existing?.allowReveal ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val age = AgeProfile.fromId(ageId)
    val revealAllowed = age == AgeProfile.TEENS_13_17 || age == AgeProfile.ADULTS_18_PLUS

    val imagePicker = rememberLauncherFilePicker { uri ->
        if (uri != null) {
            // The picker's content:// URI is transient; copy the bytes into app-private storage so
            // the photo still shows after a restart. Fall back to the raw URI if the copy fails.
            scope.launch {
                val stored = copyPickedPhoto(context, uri)
                photoUrl = stored ?: uri.toString()
            }
        }
    }

    Scaffold(
        topBar = {
            KGuardTopAppBar(
                if (existing == null) "Add child" else "Edit ${existing.name}",
                canNavigateBack = true, onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
        ) {
            // -------------------------------------------------------------- Photo
            KGSectionHeader("Photo", hint = "A face your child recognises. Optional.")
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(KGShapes.circle)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Child Avatar",
                            modifier = Modifier.fillMaxSize().clip(KGShapes.circle),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Pick Photo",
                            tint = KGColors.inkMuted,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
            Text(
                "Tap to pick photo from device",
                style = MaterialTheme.typography.bodySmall,
                color = KGColors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            // -------------------------------------------------------------- Name
            KGSectionHeader("Name", hint = "How this child appears across K-Guard.")
            KGTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = "Child's name",
                isError = name.isBlank()
            )
            Spacer(Modifier.height(12.dp))
            KGTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = "Child's Google Email",
                keyboardType = KeyboardType.Email,
                supportingText = "Used when pairing your child's Google account."
            )

            // -------------------------------------------------------------- Age group
            KGSectionHeader("Age group", hint = "Sets the default protection level for every category below.")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AgeProfile.entries.forEach { p ->
                    val isChosen = p.id == ageId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (isChosen) MaterialTheme.colorScheme.surfaceContainerHigh
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                                KGShapes.cardSmall
                            )
                            .selectable(selected = isChosen, role = Role.RadioButton) { ageId = p.id }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isChosen, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("${p.title} (${p.ageRange})", style = MaterialTheme.typography.titleSmall)
                            Text(
                                p.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = KGColors.inkMuted
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------- Web categories
            KGSectionHeader(
                "Web categories",
                hint = "Per-category overrides. \"Default\" follows the age group; rules can make " +
                    "K-Guard stricter. They can relax it only for lower-risk categories and moderate " +
                    "scores. Sexual content, threats and self-harm are always handled strictly."
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SafetyCategory.entries.forEach { cat ->
                    KGCard(Modifier.fillMaxWidth()) {
                        Text(cat.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            cat.hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = KGColors.inkMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val current = CategoryRule.fromId(rules[cat.id])
                            FilterChip(
                                selected = current == null,
                                onClick = { rules = rules - cat.id },
                                label = { Text("Default") }
                            )
                            CategoryRule.entries
                                .filter { !(cat.lockedAgainstRelaxing && it != CategoryRule.BLOCK) }
                                .forEach { r ->
                                    FilterChip(
                                        selected = current == r,
                                        onClick = { rules = rules + (cat.id to r.id) },
                                        label = { Text(r.label) }
                                    )
                                }
                        }
                    }
                }
            }

            // -------------------------------------------------------------- Reveal permission
            KGSectionHeader("Revealing blurred content")
            KGToggleRow(
                title = "Allow tap-to-reveal on blurred items",
                description = if (!revealAllowed)
                    "Not available for this age group; blurred items stay hidden."
                else "Lets this child choose to view blurred items.",
                checked = allowReveal && revealAllowed,
                onCheckedChange = { if (revealAllowed) allowReveal = it },
                modifier = Modifier.fillMaxWidth().alpha(if (revealAllowed) 1f else 0.6f)
            )

            // -------------------------------------------------------------- Device linking
            KGSectionHeader("Device linking", hint = "Pair your child's phone so K-Guard protects it directly.")
            if (existing != null) {
                KGCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Link status", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (existing.linkedUid != null)
                                    "A device is paired with this child's Google account."
                                else
                                    "No device is paired yet. Generate a code and enter it on your child's phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = KGColors.inkMuted
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        if (existing.linkedUid != null) {
                            KGStatusTag("Paired", KGColors.success)
                        } else {
                            KGStatusTag("Not linked", KGColors.warning)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    KGSecondaryButton(
                        text = "Generate pairing code",
                        onClick = { onGenerateCode(existing.id) },
                        leading = { Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                KGCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Save the profile first — pairing codes are generated once the child exists.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KGColors.inkMuted
                    )
                }
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = KGColors.danger, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            KGPrimaryButton(
                text = if (busy) "Saving…" else "Save",
                onClick = {
                    onSaveChild(
                        (existing ?: ChildProfile()).copy(
                            name = name, email = email, photoUrl = photoUrl, ageProfile = ageId, categoryRules = rules,
                            allowReveal = allowReveal && (age == AgeProfile.TEENS_13_17 || age == AgeProfile.ADULTS_18_PLUS)
                        )
                    )
                },
                enabled = name.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth()
            )

            if (existing != null) {
                KGDangerButton(
                    text = "Delete profile",
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    pairingCode?.let { code ->
        LaunchedEffect(code) {
            KGuardMessagingService.showAlert(
                context,
                "Device Pairing OTP Code",
                "Your 6-digit device pairing code is: $code. Enter this on your child's phone during setup.",
                "children"
            )
        }
        AlertDialog(
            onDismissRequest = onClearCode,
            title = { Text("6-Digit Device Pairing OTP") },
            text = {
                Column {
                    Text(
                        "Enter this code on your child's phone during setup to pair their device:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    KGCard(Modifier.fillMaxWidth()) {
                        Text(
                            code,
                            style = MaterialTheme.typography.displaySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This code expires in 15 minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KGColors.inkMuted
                    )
                }
            },
            confirmButton = { TextButton(onClick = onClearCode) { Text("Done") } }
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.name}?") },
            text = { Text("The profile and its rules are removed. Past alerts stay in the review queue until they expire.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleteChild(existing.id) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

/**
 * Copies a gallery image into app-private storage (filesDir/child_photos) so the child photo
 * survives restarts and permission revocation of the source provider. Returns a file URI string,
 * or null when the copy fails (caller falls back to the original URI).
 * Note: this persists only on this device; syncing the photo across devices still needs an upload.
 */
private suspend fun copyPickedPhoto(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val dir = File(context.filesDir, "child_photos").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return@withContext null
        Uri.fromFile(file).toString()
    } catch (e: Exception) {
        null
    }
}

@Composable
fun rememberLauncherFilePicker(onImageSelected: (Uri?) -> Unit) =
    rememberLauncherFilePickerInternal(onImageSelected)

@Composable
private fun rememberLauncherFilePickerInternal(onImageSelected: (Uri?) -> Unit) =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = onImageSelected
    )

@Preview(showBackground = true)
@Composable
fun ChildrenScreenPreview() {
    val child = ChildProfile(id = "1", name = "Alex", email = "alex@example.com", ageProfile = AgeProfile.PRETEENS_8_12.id)
    KGuardTheme {
        ChildrenContent(
            children = listOf(child),
            selected = child,
            familyId = "family_1",
            onSelectChild = {},
            onEditChild = {},
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ChildEditorScreenPreview() {
    val child = ChildProfile(id = "1", name = "Alex", email = "alex@example.com", ageProfile = AgeProfile.PRETEENS_8_12.id)
    KGuardTheme {
        ChildEditorContent(
            existing = child,
            busy = false,
            error = null,
            onSaveChild = {},
            onDeleteChild = {},
            onNavigateBack = {}
        )
    }
}
