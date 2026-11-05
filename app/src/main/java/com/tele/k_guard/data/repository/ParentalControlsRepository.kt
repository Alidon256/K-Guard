package com.tele.k_guard.data.repository

import com.tele.k_guard.data.model.AppAccessInfo
import com.tele.k_guard.data.model.AppApprovalStatus
import com.tele.k_guard.data.model.ScreenTimeConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ParentalControlsRepository {

    private val _screenTimeConfig = MutableStateFlow(ScreenTimeConfig())
    val screenTimeConfig: StateFlow<ScreenTimeConfig> = _screenTimeConfig.asStateFlow()

    private val initialApps = listOf(
        AppAccessInfo(
            id = "app_1",
            appName = "YouTube Kids",
            packageName = "com.google.android.apps.youtube.kids",
            category = "Video & Entertainment",
            minAgeRating = 3,
            safetyScore = 94,
            approvalStatus = AppApprovalStatus.APPROVED,
            dailyLimitMinutes = 60,
            safetyReason = "Curated content for young children with strict parental controls."
        ),
        AppAccessInfo(
            id = "app_2",
            appName = "TikTok",
            packageName = "com.zhiliaoapp.musically",
            category = "Social Media",
            minAgeRating = 13,
            safetyScore = 42,
            approvalStatus = AppApprovalStatus.PENDING_REVIEW,
            dailyLimitMinutes = 30,
            safetyReason = "High volume of uncurated user-generated videos; potential exposure to inappropriate trends."
        ),
        AppAccessInfo(
            id = "app_3",
            appName = "Roblox",
            packageName = "com.roblox.client",
            category = "Gaming & Community",
            minAgeRating = 10,
            safetyScore = 65,
            approvalStatus = AppApprovalStatus.APPROVED,
            dailyLimitMinutes = 45,
            safetyReason = "Multiplayer gaming. Open chat features require monitoring for younger players."
        ),
        AppAccessInfo(
            id = "app_4",
            appName = "Duolingo ABC",
            packageName = "com.duolingo.abc",
            category = "Education",
            minAgeRating = 3,
            safetyScore = 98,
            approvalStatus = AppApprovalStatus.APPROVED,
            dailyLimitMinutes = 120,
            safetyReason = "100% child-safe educational reading app with zero ads or external communication."
        )
    )

    private val _managedApps = MutableStateFlow(initialApps)
    val managedApps: StateFlow<List<AppAccessInfo>> = _managedApps.asStateFlow()

    fun updateScreenTimeLimits(newLimitMinutes: Int, schoolTimeEnabled: Boolean) {
        _screenTimeConfig.value = _screenTimeConfig.value.copy(
            dailyLimitMinutes = newLimitMinutes,
            schoolTimeEnabled = schoolTimeEnabled
        )
    }

    fun updateAppStatus(appId: String, status: AppApprovalStatus) {
        _managedApps.value = _managedApps.value.map { app ->
            if (app.id == appId) app.copy(approvalStatus = status) else app
        }
    }

    fun updateAppLimit(appId: String, limitMinutes: Int) {
        _managedApps.value = _managedApps.value.map { app ->
            if (app.id == appId) app.copy(dailyLimitMinutes = limitMinutes) else app
        }
    }
}
