package com.tele.k_guard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tele.k_guard.data.model.AppAccessInfo
import com.tele.k_guard.data.model.AppApprovalStatus
import com.tele.k_guard.data.repository.ParentalControlsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppManagementViewModel(
    private val repository: ParentalControlsRepository = ParentalControlsRepository
) : ViewModel() {

    private val _familyId = MutableStateFlow<String?>("demo")
    val familyId: StateFlow<String?> = _familyId.asStateFlow()

    private val _childId = MutableStateFlow<String?>(null)
    val childId: StateFlow<String?> = _childId.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val managedApps: StateFlow<List<AppAccessInfo>> = combine(_familyId, _childId) { fid, cid ->
        fid to cid
    }.flatMapLatest { (fid, cid) ->
        val currentFid = fid ?: "demo"
        repository.appsStream(currentFid, cid).catch { e ->
            _error.value = e.localizedMessage
            emit(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setFamilyAndChild(familyId: String?, childId: String?) {
        if (familyId != null && familyId != _familyId.value) {
            _familyId.value = familyId
        }
        _childId.value = childId
    }

    fun clearError() { _error.value = null }
    fun clearMessage() { _message.value = null }

    fun approveApp(familyId: String? = null, appId: String) {
        val fid = familyId ?: _familyId.value ?: "demo"
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                repository.respondToAppApproval(fid, appId, AppApprovalStatus.APPROVED)
                _message.value = "App approved successfully."
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Failed to approve application."
            } finally {
                _busy.value = false
            }
        }
    }

    fun rejectApp(familyId: String? = null, appId: String) {
        val fid = familyId ?: _familyId.value ?: "demo"
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                repository.respondToAppApproval(fid, appId, AppApprovalStatus.REJECTED)
                _message.value = "App install request rejected."
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Failed to reject application."
            } finally {
                _busy.value = false
            }
        }
    }

    fun updateAppLimit(familyId: String? = null, appId: String, limitMinutes: Int) {
        val fid = familyId ?: _familyId.value ?: "demo"
        viewModelScope.launch {
            try {
                repository.updateAppLimit(fid, appId, limitMinutes)
                _message.value = "Daily limit updated to $limitMinutes mins."
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Failed to update daily limit."
            }
        }
    }

    fun requestAppInstall(
        familyId: String? = null,
        childId: String,
        appName: String,
        packageName: String,
        category: String,
        minAgeRating: Int,
        dailyLimitMinutes: Int = 30,
        onSuccess: () -> Unit = {}
    ) {
        val fid = familyId ?: _familyId.value ?: "demo"
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                repository.requestAppApproval(
                    familyId = fid,
                    childId = childId,
                    appName = appName,
                    packageName = packageName,
                    category = category,
                    minAgeRating = minAgeRating,
                    dailyLimitMinutes = dailyLimitMinutes
                )
                _message.value = "Install request submitted for $appName! Parent will be notified."
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Failed to request app install."
            } finally {
                _busy.value = false
            }
        }
    }
}
