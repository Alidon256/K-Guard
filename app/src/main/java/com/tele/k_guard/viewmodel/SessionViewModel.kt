package com.tele.k_guard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.Timestamp
import com.tele.k_guard.DebugPreview
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.data.model.FamilyPrivacy
import com.tele.k_guard.data.model.ParentAction
import com.tele.k_guard.data.model.SafetyEvent
import com.tele.k_guard.data.repository.FamilyRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds the signed-in parent/child, their family, children, safety events and privacy settings. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel : ViewModel() {

    private val repo = FamilyRepository

    // ---- debug-only demo data (see DebugPreview); never used when demo is false ----
    private val demoChildren = MutableStateFlow(
        listOf(
            ChildProfile("c1", "Ama", "ama@example.com", "PRETEENS_8_12", mapOf("profanity" to "block"), false, null, Timestamp.now()),
            ChildProfile("c2", "Kofi", "kofi@example.com", "TEENS_13_17", mapOf("violence" to "warn", "drugs" to "block"), true, null, Timestamp.now())
        )
    )
    private val demoEvents = MutableStateFlow(
        listOf(
            SafetyEvent("e1", "c1", "Ama", "PRETEENS_8_12", "text", "cyberbullying", 0.72, "WARN", "harmful",
                "Likely contains bullying or harassment, which is not suitable for ages 8-12.", "rules", "you are so ugly", "open", null, null, Timestamp.now()),
            SafetyEvent("e2", "c1", "Ama", "PRETEENS_8_12", "image", "violence", 0.81, "BLUR", "news",
                "Contains violent content in a news setting, so the response was reduced for ages 8-12.", "ai", null, "open", null, null, Timestamp.now()),
            SafetyEvent("e3", "c2", "Kofi", "TEENS_13_17", "web", "drugs", 1.0, "BLOCK", "harmful",
                "Web Protection blocked a visit to drug-related content.", "dns", "example-drugs.com", "open", null, null, Timestamp.now()),
            SafetyEvent("e4", "c1", "Ama", "PRETEENS_8_12", "text", "threat", 0.92, "BLOCK", "harmful",
                "Likely contains threatening language, which is not suitable for ages 8-12.", "rules", null, "reviewed", "keep_blocked", "Spoke to her about it", Timestamp.now())
        )
    )
    private val demoPrivacy = MutableStateFlow(FamilyPrivacy(includeSnippet = true))
    private val _demo = MutableStateFlow(DebugPreview.demo)

    private val _user = MutableStateFlow(repo.currentUser)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    /** True when signed in, or the debug demo is active. */
    val signedIn: StateFlow<Boolean> = combine(_user, _demo) { u, d -> u != null || d }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DebugPreview.demo || repo.currentUser != null)

    fun enterDemo() { if (DebugPreview.demo) _demo.value = true }

    private val _authChecked = MutableStateFlow(false)
    val authChecked: StateFlow<Boolean> = _authChecked.asStateFlow()

    private val _familyId = MutableStateFlow<String?>(null)
    val familyId: StateFlow<String?> = _familyId.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedChildId = MutableStateFlow<String?>(null)

    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()

    val children: StateFlow<List<ChildProfile>> = _familyId
        .flatMapLatest { id -> if (id == "demo") demoChildren else if (id == null) flowOf(emptyList()) else repo.children(id).catch { reportError(it); emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val events: StateFlow<List<SafetyEvent>> = _familyId
        .flatMapLatest { id -> if (id == "demo") demoEvents else if (id == null) flowOf(emptyList()) else repo.events(id).catch { reportError(it); emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val privacy: StateFlow<FamilyPrivacy> = _familyId
        .flatMapLatest { id -> if (id == "demo") demoPrivacy else if (id == null) flowOf(FamilyPrivacy()) else repo.privacy(id).catch { reportError(it); emit(FamilyPrivacy()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FamilyPrivacy())

    /** The child currently being protected/tested; defaults to the first child. */
    val selectedChild: StateFlow<ChildProfile?> = combine(children, _selectedChildId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val openAlertCount: StateFlow<Int> = events
        .flatMapLatest { flowOf(it.count { e -> e.isOpen }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        if (DebugPreview.demo) {
            _familyId.value = "demo"
            _authChecked.value = true
        }
        viewModelScope.launch {
            repo.authState().collect { u ->
                _user.value = u
                _authChecked.value = true
                if (_demo.value) return@collect
                if (u == null) {
                    _familyId.value = null
                    _selectedChildId.value = null
                } else {
                    runCatching { repo.ensureFamily() }
                        .onSuccess { _familyId.value = it }
                        .onFailure { reportError(it) }
                    runCatching { repo.registerPushToken() }
                }
            }
        }
    }

    fun selectChild(id: String) { _selectedChildId.value = id }
    fun clearError() { _error.value = null }
    fun clearPairingCode() { _pairingCode.value = null }

    fun signIn(email: String, password: String) = launchBusy { repo.signIn(email, password) }
    fun signUp(email: String, password: String) = launchBusy { repo.signUp(email, password) }
    fun signInWithCredential(credential: AuthCredential) = launchBusy { repo.signInWithCredential(credential) }
    fun signOut() = repo.signOut()

    fun saveChild(child: ChildProfile, onDone: () -> Unit = {}) = launchBusy {
        if (_familyId.value == "demo") {
            val c = child.copy(id = child.id.ifEmpty { "c${System.nanoTime()}" }, createdAt = child.createdAt ?: Timestamp.now())
            demoChildren.value = if (demoChildren.value.any { it.id == c.id }) demoChildren.value.map { if (it.id == c.id) c else it } else demoChildren.value + c
            onDone(); return@launchBusy
        }
        val fid = _familyId.value ?: error("Your family is not ready yet.")
        require(child.name.isNotBlank()) { "Please enter a name." }
        val id = repo.saveChild(fid, child)
        if (_selectedChildId.value == null) _selectedChildId.value = id
        onDone()
    }

    fun deleteChild(childId: String, onDone: () -> Unit = {}) = launchBusy {
        val fid = _familyId.value ?: return@launchBusy
        repo.deleteChild(fid, childId)
        if (_selectedChildId.value == childId) _selectedChildId.value = null
        onDone()
    }

    fun createPairingCode(childId: String) = launchBusy { _pairingCode.value = repo.createPairingCode(childId) }
    fun sendChildOtp(childId: String) = createPairingCode(childId)

    fun redeemPairingCode(code: String, onDone: () -> Unit = {}) = launchBusy {
        val res = repo.redeemPairingCode(code)
        val fid = res["familyId"] as? String
        val cid = res["childId"] as? String
        if (fid != null) _familyId.value = fid
        if (cid != null) _selectedChildId.value = cid
        onDone()
    }

    fun review(eventId: String, action: ParentAction, note: String) = launchBusy {
        if (_familyId.value == "demo") {
            demoEvents.value = demoEvents.value.map { if (it.id == eventId) it.copy(status = "reviewed", parentAction = action.id, parentNote = note) else it }
            return@launchBusy
        }
        val fid = _familyId.value ?: return@launchBusy
        repo.reviewEvent(fid, eventId, action, note)
    }

    fun savePrivacy(p: FamilyPrivacy) = launchBusy {
        if (_familyId.value == "demo") { demoPrivacy.value = p; return@launchBusy }
        val fid = _familyId.value ?: return@launchBusy
        repo.savePrivacy(fid, p)
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try { block() } catch (e: Exception) { reportError(e) } finally { _busy.value = false }
        }
    }

    private fun reportError(t: Throwable) {
        _error.value = t.localizedMessage ?: "Something went wrong. Please try again."
    }
}
