package com.tele.k_guard.viewmodel

import android.util.Log
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

    var isChildDeviceSetup: Boolean = false

    // ---- Production state flows ----
    private val demoChildren = MutableStateFlow<List<ChildProfile>>(emptyList())
    private val demoEvents = MutableStateFlow<List<SafetyEvent>>(emptyList())
    private val demoPrivacy = MutableStateFlow(FamilyPrivacy(includeSnippet = true))
    private val _demo = MutableStateFlow(DebugPreview.demo)

    private val _user = MutableStateFlow(repo.currentUser)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

    /** True when signed in, or the debug demo is active. */
    val signedIn: StateFlow<Boolean> = combine(_user, _demo) { u, d -> u != null || d }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DebugPreview.demo || repo.currentUser != null)

    fun enterDemo() {
        Log.d("KGuard", "SessionViewModel: enterDemo activated")
        if (DebugPreview.demo) _demo.value = true
    }

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

    /** True when the signed-in account is a paired child; it may read only its own profile. */
    private val _isChildAccount = MutableStateFlow(false)
    val isChildAccount: StateFlow<Boolean> = _isChildAccount.asStateFlow()

    val children: StateFlow<List<ChildProfile>> = combine(_familyId, _isChildAccount) { id, child -> id to child }
        .flatMapLatest { (id, child) ->
            when {
                id == "demo" -> demoChildren
                id == null -> flowOf(emptyList())
                child -> repo.linkedChild(id, repo.currentUser?.uid.orEmpty()).catch { reportError(it); emit(emptyList()) }
                else -> repo.children(id).catch { reportError(it); emit(emptyList()) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Safety alerts and privacy settings are for parents; a child account is not allowed to read them.
    val events: StateFlow<List<SafetyEvent>> = combine(_familyId, _isChildAccount) { id, child -> id to child }
        .flatMapLatest { (id, child) -> if (id == "demo") demoEvents else if (id == null || child) flowOf(emptyList()) else repo.events(id).catch { reportError(it); emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val privacy: StateFlow<FamilyPrivacy> = combine(_familyId, _isChildAccount) { id, child -> id to child }
        .flatMapLatest { (id, child) -> if (id == "demo") demoPrivacy else if (id == null || child) flowOf(FamilyPrivacy()) else repo.privacy(id).catch { reportError(it); emit(FamilyPrivacy()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FamilyPrivacy())

    /** The child currently being protected/tested; defaults to the first child. */
    val selectedChild: StateFlow<ChildProfile?> = combine(children, _selectedChildId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val openAlertCount: StateFlow<Int> = events
        .flatMapLatest { flowOf(it.count { e -> e.isOpen }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        Log.d("KGuard", "SessionViewModel init: demo=${DebugPreview.demo}, currentUser=${repo.currentUser?.email}")
        if (DebugPreview.demo) {
            _familyId.value = "demo"
            _authChecked.value = true
        }
        viewModelScope.launch {
            repo.authState().collect { u ->
                Log.d("KGuard", "SessionViewModel: authState collected, user=${u?.email}")
                _user.value = u
                _authChecked.value = true
                if (_demo.value) return@collect
                if (u == null) {
                    _familyId.value = null
                    _selectedChildId.value = null
                    _isChildAccount.value = false
                } else if (signInFlowActive) {
                    // signInParent / signInChildAndPair finish the setup in order; doing it here as well would race.
                    Log.d("KGuard", "SessionViewModel: sign-in flow in progress; skipping automatic setup")
                } else {
                    val userDoc = runCatching { repo.getUserDoc(u.uid) }.getOrNull()
                    val role = userDoc?.get("role") as? String
                    _isChildAccount.value = role == "child"
                    if (role == "child") {
                        _familyId.value = userDoc["familyId"] as? String
                        _selectedChildId.value = userDoc["childId"] as? String
                    } else if (role == "parent") {
                        runCatching { repo.ensureFamily() }
                            .onSuccess {
                                Log.d("KGuard", "SessionViewModel: ensureFamily success, familyId=$it")
                                _familyId.value = it
                            }
                            .onFailure {
                                Log.e("KGuard", "SessionViewModel: ensureFamily failed", it)
                                reportError(it)
                            }
                    } else if (isChildDeviceSetup) {
                        Log.d("KGuard", "SessionViewModel: Child device setup in progress, waiting for OTP pairing code redemption.")
                    } else {
                        runCatching { repo.ensureFamily() }
                            .onSuccess {
                                Log.d("KGuard", "SessionViewModel: ensureFamily success, familyId=$it")
                                _familyId.value = it
                            }
                            .onFailure {
                                Log.e("KGuard", "SessionViewModel: ensureFamily failed", it)
                                reportError(it)
                            }
                    }
                    runCatching { repo.registerPushToken() }
                }
            }
        }
    }

    fun selectChild(id: String) {
        Log.d("KGuard", "SessionViewModel: selectChild id=$id")
        _selectedChildId.value = id
    }
    fun clearError() { _error.value = null }
    fun clearPairingCode() { _pairingCode.value = null }

    fun signIn(email: String, password: String) = launchBusy {
        Log.d("KGuard", "SessionViewModel: signIn email=$email")
        repo.signIn(email, password)
        Log.d("KGuard", "SessionViewModel: signIn success for $email")
    }

    fun signUp(email: String, password: String) = launchBusy {
        Log.d("KGuard", "SessionViewModel: signUp email=$email")
        repo.signUp(email, password)
        Log.d("KGuard", "SessionViewModel: signUp success for $email")
    }

    fun signInWithCredential(credential: AuthCredential) = launchBusy {
        Log.d("KGuard", "SessionViewModel: signInWithCredential")
        repo.signInWithCredential(credential)
        Log.d("KGuard", "SessionViewModel: signInWithCredential success")
    }

    @Volatile private var signInFlowActive = false

    /**
     * Parent sign-in, in order: Firebase sign-in, refuse an account already paired as a child, set up the family,
     * register this phone for alerts. [onDone] runs only when all of that succeeded.
     */
    fun signInParent(credential: AuthCredential, onDone: () -> Unit) = launchBusy {
        signInFlowActive = true
        try {
            repo.signInWithCredential(credential)
            val uid = repo.currentUser?.uid ?: error("Sign-in did not complete. Please try again.")
            val role = repo.getUserDoc(uid)?.get("role") as? String
            if (role == "child") {
                repo.signOut()
                error("This Google account is paired as a child's phone. Sign in with a parent's account.")
            }
            _familyId.value = repo.ensureFamily()
            _isChildAccount.value = false
            runCatching { repo.registerPushToken() }
            onDone()
        } finally {
            signInFlowActive = false
        }
    }

    /**
     * Child phone setup, in order: Firebase sign-in, redeem the parent's code, register for alerts. No family is
     * ever created for the child's account. If pairing fails the account is signed out again, so the phone is
     * never left half set up, and the reason is shown.
     */
    fun signInChildAndPair(credential: AuthCredential, code: String, onDone: () -> Unit) = launchBusy {
        signInFlowActive = true
        isChildDeviceSetup = true
        try {
            repo.signInWithCredential(credential)
            val res = try {
                repo.redeemPairingCode(code)
            } catch (e: Exception) {
                repo.signOut()
                throw e
            }
            _isChildAccount.value = true
            _familyId.value = res["familyId"] as? String
            _selectedChildId.value = res["childId"] as? String
            runCatching { repo.registerPushToken() }
            onDone()
        } finally {
            signInFlowActive = false
            isChildDeviceSetup = false
        }
    }

    /** For errors raised by the UI itself (for example the Google account picker). */
    fun showError(message: String) { _error.value = message }

    /** Signs out after removing this phone from the account's alerts, so it stops receiving them. */
    fun signOut(onDone: () -> Unit = {}) {
        Log.d("KGuard", "SessionViewModel: signOut")
        viewModelScope.launch {
            _busy.value = true
            try {
                repo.unregisterPushToken()
            } finally {
                isChildDeviceSetup = false
                repo.signOut()
                _familyId.value = null
                _selectedChildId.value = null
                _isChildAccount.value = false
                _busy.value = false
                onDone()
            }
        }
    }

    /** Saves the profile, then uploads [newPhoto] (already shrunk, see ChildPhotoRepository) and stores its URL. */
    fun saveChild(child: ChildProfile, newPhoto: ByteArray?, onDone: () -> Unit = {}) = launchBusy {
        val fid = _familyId.value ?: error("Your family is not ready yet.")
        require(child.name.isNotBlank()) { "Please enter a name." }
        // A content:// address only ever worked on the phone that picked it; never store one.
        val clean = child.copy(photoUrl = child.photoUrl.takeIf { it.startsWith("http") }.orEmpty())
        val id = repo.saveChild(fid, clean)
        if (newPhoto != null) {
            val url = runCatching { com.tele.k_guard.data.repository.ChildPhotoRepository.upload(id, newPhoto) }
                .getOrElse { throw IllegalStateException("The profile was saved, but the photo couldn't be uploaded. Check the connection and try again.") }
            repo.updateChildPhoto(fid, id, url)
        }
        if (_selectedChildId.value == null) _selectedChildId.value = id
        onDone()
    }

    fun saveChild(child: ChildProfile, onDone: () -> Unit = {}) = launchBusy {
        Log.d("KGuard", "SessionViewModel: saveChild name=${child.name}, id=${child.id}")
        if (_familyId.value == "demo") {
            val c = child.copy(id = child.id.ifEmpty { "c${System.nanoTime()}" }, createdAt = child.createdAt ?: Timestamp.now())
            demoChildren.value = if (demoChildren.value.any { it.id == c.id }) demoChildren.value.map { if (it.id == c.id) c else it } else demoChildren.value + c
            Log.d("KGuard", "SessionViewModel: saveChild demo mode success")
            onDone(); return@launchBusy
        }
        val fid = _familyId.value ?: error("Your family is not ready yet.")
        require(child.name.isNotBlank()) { "Please enter a name." }
        val id = repo.saveChild(fid, child)
        if (_selectedChildId.value == null) _selectedChildId.value = id
        Log.d("KGuard", "SessionViewModel: saveChild Firestore success, childId=$id")
        onDone()
    }

    fun deleteChild(childId: String, onDone: () -> Unit = {}) = launchBusy {
        Log.d("KGuard", "SessionViewModel: deleteChild childId=$childId")
        val fid = _familyId.value ?: return@launchBusy
        repo.deleteChild(fid, childId)
        if (_selectedChildId.value == childId) _selectedChildId.value = null
        Log.d("KGuard", "SessionViewModel: deleteChild success")
        onDone()
    }

    fun createPairingCode(childId: String) = launchBusy {
        Log.d("KGuard", "SessionViewModel: createPairingCode childId=$childId")
        _pairingCode.value = repo.createPairingCode(childId)
    }

    fun sendChildOtp(childId: String) = createPairingCode(childId)

    fun redeemPairingCode(code: String, onDone: () -> Unit = {}) = launchBusy {
        Log.d("KGuard", "SessionViewModel: redeemPairingCode code=$code")
        val res = repo.redeemPairingCode(code)
        val fid = res["familyId"] as? String
        val cid = res["childId"] as? String
        _isChildAccount.value = true
        runCatching { repo.registerPushToken() }
        if (fid != null) _familyId.value = fid
        if (cid != null) _selectedChildId.value = cid
        isChildDeviceSetup = false
        Log.d("KGuard", "SessionViewModel: redeemPairingCode success, familyId=$fid, childId=$cid")
        onDone()
    }

    fun review(eventId: String, action: ParentAction, note: String) = launchBusy {
        Log.d("KGuard", "SessionViewModel: review eventId=$eventId, action=${action.id}")
        if (_familyId.value == "demo") {
            demoEvents.value = demoEvents.value.map { if (it.id == eventId) it.copy(status = "reviewed", parentAction = action.id, parentNote = note) else it }
            return@launchBusy
        }
        val fid = _familyId.value ?: return@launchBusy
        repo.reviewEvent(fid, eventId, action, note)
    }

    fun savePrivacy(p: FamilyPrivacy) = launchBusy {
        Log.d("KGuard", "SessionViewModel: savePrivacy")
        if (_familyId.value == "demo") { demoPrivacy.value = p; return@launchBusy }
        val fid = _familyId.value ?: return@launchBusy
        repo.savePrivacy(fid, p)
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                block()
            } catch (e: Exception) {
                Log.e("KGuard", "SessionViewModel: operation failed", e)
                reportError(e)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun reportError(t: Throwable) {
        Log.e("KGuard", "SessionViewModel: error reported", t)
        _error.value = friendlyMessage(t)
    }

    /** Plain-language errors; never raw exception text from Firebase or Google Play services. */
    private fun friendlyMessage(t: Throwable): String {
        val msg = t.localizedMessage.orEmpty()
        val offline = generateSequence(t) { it.cause }.take(6).any {
            it is java.io.IOException || it is com.google.firebase.FirebaseNetworkException
        }
        return when {
            offline -> "This phone isn't connected to the internet. Turn on Wi-Fi or mobile data and try again."
            t is com.google.firebase.functions.FirebaseFunctionsException -> when (t.code) {
                com.google.firebase.functions.FirebaseFunctionsException.Code.NOT_FOUND,
                com.google.firebase.functions.FirebaseFunctionsException.Code.INVALID_ARGUMENT,
                com.google.firebase.functions.FirebaseFunctionsException.Code.FAILED_PRECONDITION,
                com.google.firebase.functions.FirebaseFunctionsException.Code.PERMISSION_DENIED -> msg.ifBlank { "That didn't work. Please try again." }
                com.google.firebase.functions.FirebaseFunctionsException.Code.UNAVAILABLE,
                com.google.firebase.functions.FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> "No connection to K-Guard. Check the internet and try again."
                else -> "Something went wrong. Please try again."
            }
            t is com.google.firebase.FirebaseNetworkException -> "No internet connection. Connect and try again."
            t is com.google.firebase.auth.FirebaseAuthException -> "Google sign-in failed. Please try again."
            t is IllegalStateException && msg.isNotBlank() -> msg
            else -> "Something went wrong. Please try again."
        }
    }
}
