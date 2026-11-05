package com.tele.k_guard.viewmodel

import androidx.lifecycle.ViewModel
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.ScreenTimeConfig
import com.tele.k_guard.data.repository.ParentalControlsRepository
import kotlinx.coroutines.flow.StateFlow

class ScreenTimeViewModel(
    private val repository: ParentalControlsRepository = ParentalControlsRepository
) : ViewModel() {

    val screenTimeConfig: StateFlow<ScreenTimeConfig> = repository.screenTimeConfig

    fun updateDailyLimit(minutes: Int) {
        repository.updateScreenTimeLimits(minutes, screenTimeConfig.value.schoolTimeEnabled)
    }

    fun toggleSchoolTime(enabled: Boolean) {
        repository.updateScreenTimeLimits(screenTimeConfig.value.dailyLimitMinutes, enabled)
    }

    fun applyAgeRecommendation(profile: AgeProfile) {
        val recommendedLimit = ScreenTimeConfig.getDefaultLimitForAge(profile)
        repository.updateScreenTimeLimits(recommendedLimit, screenTimeConfig.value.schoolTimeEnabled)
    }
}
