package com.tele.k_guard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AgeProfileViewModel(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val selectedAgeProfile: StateFlow<AgeProfile> = preferencesRepository.ageProfileFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AgeProfile.DEFAULT
        )

    fun selectAgeProfile(profile: AgeProfile) {
        viewModelScope.launch {
            preferencesRepository.saveAgeProfile(profile)
        }
    }
}
