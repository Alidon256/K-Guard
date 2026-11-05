package com.tele.k_guard.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.CategoryRule
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.data.model.SafetyCategory
import com.tele.k_guard.service.KGuardMessagingService
import com.tele.k_guard.ui.components.KGuardTopAppBar
import com.tele.k_guard.ui.theme.KGuardTheme
import com.tele.k_guard.viewmodel.SessionViewModel

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
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No child profiles yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Add a child to set their age group and choose which categories K-Guard should block, blur or warn about.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(children, key = { it.id }) { child ->
                    val isSelected = child.id == selected?.id
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectChild(child.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (child.photoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = child.photoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(child.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${child.age.title} · ${child.age.ageRange}" +
                                        if (child.email.isNotBlank()) " · ${child.email}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isSelected) Text(
                                    "Active in checkers",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(onClick = { onEditChild(child.id) }) {
                                Text("Edit")
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }
                }
            }
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
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var email by remember(existing?.id) { mutableStateOf(existing?.email ?: "") }
    var photoUrl by remember(existing?.id) { mutableStateOf(existing?.photoUrl ?: "") }
    var ageId by remember(existing?.id) { mutableStateOf(existing?.ageProfile ?: AgeProfile.PRETEENS_8_12.id) }
    var rules by remember(existing?.id) { mutableStateOf(existing?.categoryRules ?: emptyMap()) }
    var allowReveal by remember(existing?.id) { mutableStateOf(existing?.allowReveal ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val age = AgeProfile.fromId(ageId)

    val imagePicker = rememberLauncherFilePicker { uri ->
        if (uri != null) {
            photoUrl = uri.toString()
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
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Child Photo Picker Avatar
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { imagePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Child Avatar",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Pick Photo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap to pick photo from device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, label = { Text("Child's name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = email, onValueChange = { email = it.trim() }, label = { Text("Child's Google Email") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp)
                )

                if (existing != null) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { onGenerateCode(existing.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate Device Pairing OTP Code")
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Age group", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                AgeProfile.entries.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = p.id == ageId, role = Role.RadioButton) { ageId = p.id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = p.id == ageId, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("${p.title} (${p.ageRange})", fontWeight = FontWeight.SemiBold)
                            Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("What should K-Guard do about each category?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "\"Default\" follows the age group. Rules can make K-Guard stricter. They can relax it only for lower-risk categories and moderate scores. " +
                        "Sexual content, threats and self-harm are always handled strictly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                SafetyCategory.entries.forEach { cat ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(cat.label, fontWeight = FontWeight.SemiBold)
                        Text(cat.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow tap-to-reveal on blurred items", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (age == AgeProfile.KIDS_3_7 || age == AgeProfile.PRETEENS_8_12)
                                "Not available for this age group; blurred items stay hidden."
                            else "Lets this child choose to view blurred items.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = allowReveal && age != AgeProfile.KIDS_3_7 && age != AgeProfile.PRETEENS_8_12,
                        onCheckedChange = { allowReveal = it },
                        enabled = age == AgeProfile.TEENS_13_17 || age == AgeProfile.ADULTS_18_PLUS
                    )
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(16.dp))
                Button(
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
                ) { Text(if (busy) "Saving…" else "Save") }

                if (existing != null) {
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Delete profile", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
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
                    Text("Enter this code on your child's phone during setup to pair their device:")
                    Spacer(Modifier.height(12.dp))
                    Text(code, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("This code expires in 15 minutes.", style = MaterialTheme.typography.bodySmall)
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
