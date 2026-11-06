package com.tele.k_guard.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.ChildDevice
import com.tele.k_guard.data.model.DeviceLockState
import com.tele.k_guard.data.model.RemoteCommand
import com.tele.k_guard.data.model.ScreenTimeConfig
import com.tele.k_guard.data.repository.ScreenTimeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Parent's phone: one child's screen time and device controls. Every action writes rules to Firestore
 * (see [ScreenTimeRepository]); the child's phone applies them and reports back, and [currentDevice] shows
 * what the phone actually reports.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenTimeViewModel(
    private val repository: ScreenTimeRepository = ScreenTimeRepository
) : ViewModel() {

    private val _familyId = MutableStateFlow<String?>(null)
    private val _selectedChildId = MutableStateFlow<String?>(null)
    private val _selectedChildName = MutableStateFlow("")

    val selectedChildId: StateFlow<String?> = _selectedChildId.asStateFlow()
    val selectedChildName: StateFlow<String> = _selectedChildName.asStateFlow()

    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback: StateFlow<String?> = _actionFeedback.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val currentDevice: StateFlow<ChildDevice> = combine(_familyId, _selectedChildId, _selectedChildName) { f, c, n -> Triple(f, c, n) }
        .flatMapLatest { (fid, cid, name) ->
            if (fid == null || cid == null) flowOf(ChildDevice()) else repository.device(fid, cid, name)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChildDevice())

    val commandHistory: StateFlow<List<RemoteCommand>> = combine(_familyId, _selectedChildId) { f, c -> f to c }
        .flatMapLatest { (fid, cid) -> if (fid == null || cid == null) flowOf(emptyList()) else repository.commands(fid, cid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val appActivity: StateFlow<List<com.tele.k_guard.data.repository.AppActivity>> = combine(_familyId, _selectedChildId) { f, c -> f to c }
        .flatMapLatest { (fid, cid) -> if (fid == null || cid == null) flowOf(emptyList()) else repository.appActivity(fid, cid) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setFamilyAndChild(familyId: String?, childId: String, childName: String) {
        _familyId.value = familyId
        _selectedChildId.value = childId
        _selectedChildName.value = childName
    }

    fun selectChild(id: String, name: String) {
        _selectedChildId.value = id
        _selectedChildName.value = name
    }

    /** Writes age-appropriate starting rules the first time a parent opens screen time for this child. */
    fun initializeDefaultsIfNeeded(profile: AgeProfile) {
        val fid = _familyId.value ?: return
        val cid = _selectedChildId.value ?: return
        viewModelScope.launch {
            runCatching { repository.initializeDefaults(fid, cid, profile) }
                .onFailure { Log.e(TAG, "Could not write starting rules", it) }
        }
    }

    private fun act(success: String, block: suspend (fid: String, cid: String, name: String) -> Unit) {
        val fid = _familyId.value
        val cid = _selectedChildId.value
        if (fid == null || cid == null) {
            _actionFeedback.value = "Choose a child first."
            return
        }
        val name = _selectedChildName.value
        viewModelScope.launch {
            _busy.value = true
            try {
                block(fid, cid, name)
                val dev = currentDevice.value
                _actionFeedback.value = if (dev.isOnline) success
                else "$success It applies as soon as $name's phone is back online."
            } catch (e: Exception) {
                Log.e(TAG, "Screen time action failed", e)
                _actionFeedback.value = "Couldn't save that: ${e.localizedMessage ?: "check the connection and try again."}"
            } finally {
                _busy.value = false
            }
        }
    }

    // The childId parameters below match the screen's existing calls; the selected child is always used.

    fun remoteSwitchOff(@Suppress("UNUSED_PARAMETER") childId: String, reason: String) =
        act("${_selectedChildName.value}'s phone is locked.") { f, c, n -> repository.lock(f, c, n, reason.ifBlank { "Your parent has locked this phone." }, null) }

    fun remoteTimedLock(@Suppress("UNUSED_PARAMETER") childId: String, hours: Int, minutes: Int) {
        val total = hours * 60 + minutes
        if (total <= 0) { _actionFeedback.value = "Choose how long to lock the phone for."; return }
        act("${_selectedChildName.value}'s phone is locked for ${repository.formatDuration(total)}.") { f, c, n ->
            repository.lock(f, c, n, "Locked by your parent for ${repository.formatDuration(total)}.", total)
        }
    }

    /**
     * Unlocks now. If bedtime or the daily limit is what is locking the phone, those are paused for an hour
     * so the unlock actually takes effect; after that the normal rules apply again.
     */
    fun remoteUnlock(@Suppress("UNUSED_PARAMETER") childId: String, pauseMinutes: Int? = null) {
        val state = currentDevice.value.lockState
        val pause = pauseMinutes ?: if (state == DeviceLockState.BEDTIME_LOCKED || state == DeviceLockState.LIMIT_REACHED) 60 else 0
        val note = if (pause > 0) " Bedtime and the daily limit are paused for ${repository.formatDuration(pause)}." else ""
        act("${_selectedChildName.value}'s phone is unlocked.$note") { f, c, n -> repository.unlock(f, c, n, pause) }
    }

    fun remoteSoundAlarm(@Suppress("UNUSED_PARAMETER") childId: String) =
        act("Playing an alarm on ${_selectedChildName.value}'s phone for 30 seconds.") { f, c, n -> repository.soundAlarm(f, c, n) }

    fun addBonusTime(@Suppress("UNUSED_PARAMETER") childId: String, bonusMinutes: Int) {
        val childToday = currentDevice.value.usageDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        act("Added ${repository.formatDuration(bonusMinutes)} for today.") { f, c, n -> repository.addBonus(f, c, n, bonusMinutes, childToday) }
    }

    fun updateDailyLimit(@Suppress("UNUSED_PARAMETER") childId: String, minutes: Int) =
        act(if (minutes <= 0) "Daily limit removed." else "Daily limit set to ${repository.formatDuration(minutes)}.") { f, c, n ->
            repository.setDailyLimit(f, c, n, minutes)
        }

    fun applyAgeRecommendation(childId: String, profile: AgeProfile) =
        updateDailyLimit(childId, ScreenTimeConfig.getDefaultLimitForAge(profile))

    fun updateBedtimeSchedule(@Suppress("UNUSED_PARAMETER") childId: String, start: String, end: String, enabled: Boolean) =
        act(if (enabled) "Bedtime set to $start–$end." else "Bedtime turned off.") { f, c, n -> repository.setBedtime(f, c, n, start, end, enabled) }

    fun updateSchoolSchedule(@Suppress("UNUSED_PARAMETER") childId: String, start: String, end: String, enabled: Boolean) =
        act(if (enabled) "School hours set to $start–$end on weekdays." else "School hours turned off.") { f, c, n ->
            repository.setSchoolHours(f, c, n, start, end, enabled)
        }

    fun clearFeedback() { _actionFeedback.value = null }

    private companion object { const val TAG = "KGuardScreenTime" }
}
