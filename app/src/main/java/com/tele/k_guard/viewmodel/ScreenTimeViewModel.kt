package com.tele.k_guard.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.ChildDevice
import com.tele.k_guard.data.model.RemoteAction
import com.tele.k_guard.data.model.RemoteCommand
import com.tele.k_guard.data.model.ScreenTimeConfig
import com.tele.k_guard.data.repository.ParentalControlsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A one-shot UI feedback event. The id lets identical consecutive messages re-trigger snackbars. */
data class ScreenTimeFeedback(val id: Long, val message: String)

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenTimeViewModel(
    private val repository: ParentalControlsRepository = ParentalControlsRepository,
    /**
     * Optional external scope for the stateIn/launched coroutines. Falls back to [viewModelScope]
     * when null. Exists so unit tests can drive the ViewModel on a synchronous test dispatcher.
     */
    private val externalScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope get() = externalScope ?: viewModelScope

    private val _familyId = MutableStateFlow<String>("demo")
    private val _selectedChildId = MutableStateFlow<String>("c1")
    private val _selectedChildName = MutableStateFlow<String>("Ama")

    val selectedChildId: StateFlow<String> = _selectedChildId.asStateFlow()
    val selectedChildName: StateFlow<String> = _selectedChildName.asStateFlow()

    private val _actionFeedback = MutableStateFlow<ScreenTimeFeedback?>(null)
    val actionFeedback: StateFlow<ScreenTimeFeedback?> = _actionFeedback.asStateFlow()

    private var nextFeedbackId = 0L

    fun setFamilyAndChild(familyId: String?, childId: String, childName: String) {
        _familyId.value = familyId ?: "demo"
        _selectedChildId.value = childId
        _selectedChildName.value = childName
    }

    val currentDevice: StateFlow<ChildDevice> = kotlinx.coroutines.flow.combine(
        _familyId,
        _selectedChildId,
        _selectedChildName
    ) { fid, cid, cname -> fid to Pair(cid, cname) }
        .flatMapLatest { (fid, pair) ->
            repository.deviceStream(fid, pair.first, pair.second)
        }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            ChildDevice(childId = "c1", childName = "Ama", deviceName = "Ama's Phone")
        )

    val commandHistory: StateFlow<List<RemoteCommand>> = kotlinx.coroutines.flow.combine(
        _familyId,
        _selectedChildId
    ) { fid, cid -> fid to cid }
        .flatMapLatest { (fid, cid) ->
            repository.commandsStream(fid, cid)
        }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            emptyList()
        )

    fun selectChild(id: String, name: String) {
        _selectedChildId.value = id
        _selectedChildName.value = name
    }

    private fun showFeedback(message: String) {
        _actionFeedback.value = ScreenTimeFeedback(nextFeedbackId++, message)
    }

    fun remoteSwitchOff(childId: String, reason: String) {
        val cname = _selectedChildName.value
        val fid = _familyId.value
        scope.launch {
            try {
                repository.sendRemoteCommand(
                    familyId = fid,
                    childId = childId,
                    childName = cname,
                    action = RemoteAction.SWITCH_OFF_LOCK,
                    description = "Remotely switched off screen & locked device",
                    reason = reason,
                    updates = mapOf("lockState" to "remote_locked", "lockReason" to reason)
                )
                showFeedback("🔴 Switched off & locked $cname's phone ($reason)")
            } catch (e: Exception) {
                Log.e("KGuard", "Failed to switch off phone", e)
                showFeedback("Failed to switch off phone: ${e.localizedMessage}")
            }
        }
    }

    fun remoteUnlock(childId: String) {
        val cname = _selectedChildName.value
        val fid = _familyId.value
        scope.launch {
            try {
                repository.sendRemoteCommand(
                    familyId = fid,
                    childId = childId,
                    childName = cname,
                    action = RemoteAction.UNLOCK_DEVICE,
                    description = "Unlocked and resumed device",
                    updates = mapOf("lockState" to "active_unlocked", "lockReason" to null)
                )
                showFeedback("🟢 Unlocked & resumed $cname's phone")
            } catch (e: Exception) {
                Log.e("KGuard", "Failed to unlock phone", e)
                showFeedback("Failed to unlock phone: ${e.localizedMessage}")
            }
        }
    }

    fun remoteSoundAlarm(childId: String) {
        val cname = _selectedChildName.value
        val fid = _familyId.value
        scope.launch {
            try {
                repository.sendRemoteCommand(
                    familyId = fid,
                    childId = childId,
                    childName = cname,
                    action = RemoteAction.SOUND_ALARM,
                    description = "Played loud ring alert on device"
                )
                showFeedback("🔊 Sounding loud chime on $cname's phone")
            } catch (e: Exception) {
                Log.e("KGuard", "Failed to sound alarm", e)
            }
        }
    }

    fun addBonusTime(childId: String, bonusMinutes: Int) {
        val cname = _selectedChildName.value
        val fid = _familyId.value
        scope.launch {
            try {
                // No client-side newLimit: the repository applies an atomic FieldValue.increment,
                // so two rapid taps each add their increment.
                repository.sendRemoteCommand(
                    familyId = fid,
                    childId = childId,
                    childName = cname,
                    action = RemoteAction.ADD_BONUS_TIME,
                    description = "Added +$bonusMinutes minutes bonus screen time",
                    updates = mapOf("bonusMinutes" to bonusMinutes)
                )
                showFeedback("⭐ +$bonusMinutes min bonus screen time granted to $cname")
            } catch (e: Exception) {
                Log.e("KGuard", "Failed to add bonus time", e)
            }
        }
    }

    fun updateDailyLimit(childId: String, minutes: Int, feedbackMessage: String? = null) {
        val cname = _selectedChildName.value
        val fid = _familyId.value
        scope.launch {
            try {
                repository.sendRemoteCommand(
                    familyId = fid,
                    childId = childId,
                    childName = cname,
                    action = RemoteAction.SET_DAILY_LIMIT,
                    description = "Updated daily screen time limit to $minutes mins",
                    updates = mapOf("dailyLimitMinutes" to minutes)
                )
                showFeedback(feedbackMessage ?: "Updated daily limit to $minutes mins for $cname")
            } catch (e: Exception) {
                Log.e("KGuard", "Failed to update daily limit", e)
            }
        }
    }

    fun applyAgeRecommendation(childId: String, profile: AgeProfile) {
        val recommendedLimit = ScreenTimeConfig.getDefaultLimitForAge(profile)
        // The recommendation's own message is passed through so the spawned
        // updateDailyLimit can no longer overwrite it with the generic one.
        updateDailyLimit(
            childId,
            recommendedLimit,
            feedbackMessage = "Applied recommended limit of $recommendedLimit mins for ${profile.title}"
        )
    }

    fun updateBedtimeSchedule(childId: String, start: String, end: String, enabled: Boolean) {
        val cname = _selectedChildName.value
        val fid = _familyId.value
        scope.launch {
            try {
                repository.sendRemoteCommand(
                    familyId = fid,
                    childId = childId,
                    childName = cname,
                    action = RemoteAction.TOGGLE_BEDTIME,
                    description = "Updated bedtime schedule ($start - $end, enabled=$enabled)",
                    updates = mapOf("bedtimeStart" to start, "bedtimeEnd" to end, "bedtimeEnabled" to enabled)
                )
                showFeedback("Bedtime schedule updated for $cname")
            } catch (e: Exception) {
                Log.e("KGuard", "Failed to update bedtime", e)
            }
        }
    }

    fun updateSchoolSchedule(childId: String, start: String, end: String, enabled: Boolean) {
        val cname = _selectedChildName.value
        val fid = _familyId.value
        scope.launch {
            try {
                repository.sendRemoteCommand(
                    familyId = fid,
                    childId = childId,
                    childName = cname,
                    action = RemoteAction.TOGGLE_SCHOOL_MODE,
                    description = "Updated school focus hours ($start - $end, enabled=$enabled)",
                    updates = mapOf("schoolStart" to start, "schoolEnd" to end, "schoolEnabled" to enabled)
                )
                showFeedback("School focus schedule updated for $cname")
            } catch (e: Exception) {
                Log.e("KGuard", "Failed to update school schedule", e)
            }
        }
    }

    fun clearFeedback() {
        _actionFeedback.value = null
    }

    // Legacy overloads
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
