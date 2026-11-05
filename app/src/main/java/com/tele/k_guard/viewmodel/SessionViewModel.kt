package com.tele.k_guard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
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

/** Holds the signed-in parent, their family, children, safety events and privacy settings. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel : ViewModel() {

    private val repo = FamilyRepository

    private val _user = MutableStateFlow(repo.currentUser)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

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
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.children(id).catch { reportError(it); emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val events: StateFlow<List<SafetyEvent>> = _familyId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.events(id).catch { reportError(it); emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val privacy: StateFlow<FamilyPrivacy> = _familyId
        .flatMapLatest { id -> if (id == null) flowOf(FamilyPrivacy()) else repo.privacy(id).catch { reportError(it); emit(FamilyPrivacy()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FamilyPrivacy())

    /** The child currently being protected/tested; defaults to the first child. */
    val selectedChild: StateFlow<ChildProfile?> = combine(children, _selectedChildId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val openAlertCount: StateFlow<Int> = events
        .flatMapLatest { flowOf(it.count { e -> e.isOpen }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        viewModelScope.launch {
            repo.authState().collect { u ->
                _user.value = u
                _authChecked.value = true
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
    fun signOut() = repo.signOut()

    fun saveChild(child: ChildProfile, onDone: () -> Unit = {}) = launchBusy {
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

    fun review(eventId: String, action: ParentAction, note: String) = launchBusy {
        val fid = _familyId.value ?: return@launchBusy
        repo.reviewEvent(fid, eventId, action, note)
    }

    fun savePrivacy(p: FamilyPrivacy) = launchBusy {
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
