package com.tele.k_guard.viewmodel

import androidx.lifecycle.ViewModel
import com.tele.k_guard.data.model.AppAccessInfo
import com.tele.k_guard.data.model.AppApprovalStatus
import com.tele.k_guard.data.repository.ParentalControlsRepository
import kotlinx.coroutines.flow.StateFlow

class AppManagementViewModel(
    private val repository: ParentalControlsRepository = ParentalControlsRepository
) : ViewModel() {

    val managedApps: StateFlow<List<AppAccessInfo>> = repository.managedApps

    fun approveApp(appId: String) {
        repository.updateAppStatus(appId, AppApprovalStatus.APPROVED)
    }

    fun rejectApp(appId: String) {
        repository.updateAppStatus(appId, AppApprovalStatus.REJECTED)
    }

    fun updateAppLimit(appId: String, limitMinutes: Int) {
        repository.updateAppLimit(appId, limitMinutes)
    }
}
