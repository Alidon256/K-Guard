package com.tele.k_guard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.ChildDevice
import com.tele.k_guard.data.model.RemoteCommand
import com.tele.k_guard.data.model.ScreenTimeConfig
import com.tele.k_guard.data.repository.ParentalControlsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScreenTimeViewModel(
    private val repository: ParentalControlsRepository = ParentalControlsRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private val scope: kotlinx.coroutines.CoroutineScope = coroutineScope ?: runCatching { viewModelScope }.getOrDefault(kotlinx.coroutines.GlobalScope)

    // Legacy support for backward compatibility with existing components
    val screenTimeConfig: StateFlow<ScreenTimeConfig> = repository.screenTimeConfig

    private val _selectedChildId = MutableStateFlow("c1")
    val selectedChildId: StateFlow<String> = _selectedChildId.asStateFlow()

    private val _selectedChildName = MutableStateFlow("Ama")
    val selectedChildName: StateFlow<String> = _selectedChildName.asStateFlow()

    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback: StateFlow<String?> = _actionFeedback.asStateFlow()

    val devices: StateFlow<Map<String, ChildDevice>> = repository.devices
    val commandHistory: StateFlow<List<RemoteCommand>> = repository.commandHistory

    /**
     * Active device for the selected child.
     */
    val currentDevice: StateFlow<ChildDevice> = combine(
        repository.devices,
        _selectedChildId,
        _selectedChildName
    ) { devMap, id, name ->
        devMap[id] ?: repository.getDeviceForChild(id, name)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        repository.getDeviceForChild("c1", "Ama")
    )

    fun selectChild(id: String, name: String) {
        _selectedChildId.value = id
        _selectedChildName.value = name
        // Ensure device profile exists
        repository.getDeviceForChild(id, name)
    }

    /**
     * REMOTELY SWITCH OFF / LOCK CHILD'S PHONE
     */
    fun remoteSwitchOff(childId: String, reason: String) {
        repository.switchOffPhoneRemotely(childId, reason)
        val dev = repository.getDeviceForChild(childId)
        _actionFeedback.value = "🔴 Switched off & locked ${dev.childName}'s phone (${reason})"
    }

    /**
     * REMOTELY UNLOCK / RESUME CHILD'S PHONE
     */
    fun remoteUnlock(childId: String) {
        repository.unlockPhoneRemotely(childId)
        val dev = repository.getDeviceForChild(childId)
        _actionFeedback.value = "🟢 Unlocked & resumed ${dev.childName}'s phone"
    }

    /**
     * PLAY LOUD ALARM CHIME
     */
    fun remoteSoundAlarm(childId: String) {
        repository.soundAlarmRemotely(childId)
        val dev = repository.getDeviceForChild(childId)
        _actionFeedback.value = "🔊 Sounding loud chime on ${dev.childName}'s phone"
    }

    /**
     * AWARD BONUS SCREEN TIME TODAY
     */
    fun addBonusTime(childId: String, bonusMinutes: Int) {
        repository.addBonusScreenTime(childId, bonusMinutes)
        val dev = repository.getDeviceForChild(childId)
        _actionFeedback.value = "⭐ +$bonusMinutes min bonus screen time granted to ${dev.childName}"
    }

    /**
     * UPDATE DAILY LIMIT
     */
    fun updateDailyLimit(childId: String, minutes: Int) {
        repository.updateChildDailyLimit(childId, minutes)
    }

    /**
     * APPLY AGE-BASED GUIDANCE
     */
    fun applyAgeRecommendation(childId: String, profile: AgeProfile) {
        val recommendedLimit = ScreenTimeConfig.getDefaultLimitForAge(profile)
        repository.updateChildDailyLimit(childId, recommendedLimit)
        _actionFeedback.value = "Applied recommended limit of $recommendedLimit mins"
    }

    /**
     * UPDATE BEDTIME SCHEDULE
     */
    fun updateBedtimeSchedule(childId: String, start: String, end: String, enabled: Boolean) {
        repository.updateChildBedtime(childId, start, end, enabled)
    }

    /**
     * TOGGLE SCHOOL HOURS FOCUS
     */
    fun updateSchoolSchedule(childId: String, start: String, end: String, enabled: Boolean) {
        repository.updateChildSchoolHours(childId, start, end, enabled)
    }

    fun clearFeedback() {
        _actionFeedback.value = null
    }

    // Overloads for legacy ScreenTimeContent calls
    fun updateDailyLimit(minutes: Int) {
        updateDailyLimit(_selectedChildId.value, minutes)
    }

    fun toggleSchoolTime(enabled: Boolean) {
        val cur = currentDevice.value
        updateSchoolSchedule(_selectedChildId.value, cur.schoolStart, cur.schoolEnd, enabled)
    }

    fun applyAgeRecommendation(profile: AgeProfile) {
        applyAgeRecommendation(_selectedChildId.value, profile)
    }
}
