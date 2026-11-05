package com.tele.k_guard.viewmodel

import com.tele.k_guard.data.model.AgeProfile
import com.tele.k_guard.data.model.DeviceLockState
import com.tele.k_guard.data.model.RemoteAction
import com.tele.k_guard.data.repository.ParentalControlsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScreenTimeViewModelTest {

    private lateinit var viewModel: ScreenTimeViewModel
    private val repository = ParentalControlsRepository

    @Before
    fun setUp() {
        // ParentalControlsRepository is a singleton object with mutable demo state and no reset —
        // restore the pristine demo data before each test so tests are order-independent.
        repository.resetDemoData()

        // Drive the ViewModel on a synchronous Unconfined scope so the Eagerly-started
        // stateIn streams and launched coroutines complete before assertions run.
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        viewModel = ScreenTimeViewModel(repository, testScope)
        viewModel.selectChild("c1", "Ama")
    }

    @Test
    fun initialDeviceHasCorrectAttributes() {
        val dev = viewModel.currentDevice.value
        assertEquals("c1", dev.childId)
        assertEquals("Ama", dev.childName)
        assertEquals("Pixel 7", dev.deviceModel)
        assertTrue(dev.isOnline)
        assertEquals(85, dev.batteryPercent)
        assertFalse(dev.isCharging)
        assertEquals(DeviceLockState.ACTIVE_UNLOCKED, dev.lockState)
    }

    @Test
    fun selectChildSwitchesDevice() {
        viewModel.selectChild("c2", "Kofi")
        val dev = viewModel.currentDevice.value
        assertEquals("c2", dev.childId)
        assertEquals("Kofi", dev.childName)
        assertEquals("Galaxy Tab", dev.deviceModel)
        assertEquals(85, dev.batteryPercent)
        assertFalse(dev.isCharging)
    }

    @Test
    fun remoteSwitchOffLocksDeviceAndLogsCommand() {
        viewModel.selectChild("c1", "Ama")
        val reason = "Dinner Time"
        viewModel.remoteSwitchOff("c1", reason)

        val dev = viewModel.currentDevice.value
        assertEquals(DeviceLockState.REMOTE_LOCKED, dev.lockState)
        assertFalse(dev.isScreenOn)
        assertEquals(reason, dev.lockReason)
        assertTrue(dev.isLocked)

        // Verify command log
        val latestCmd = viewModel.commandHistory.value.firstOrNull()
        assertNotNull(latestCmd)
        assertEquals(RemoteAction.SWITCH_OFF_LOCK, latestCmd?.action)
        assertEquals("c1", latestCmd?.childId)
        assertEquals(reason, latestCmd?.reason)
        assertEquals("Sent", latestCmd?.status)
    }

    @Test
    fun remoteUnlockRestoresDevice() {
        viewModel.selectChild("c1", "Ama")
        viewModel.remoteSwitchOff("c1", "Bedtime")
        assertTrue(viewModel.currentDevice.value.isLocked)

        viewModel.remoteUnlock("c1")
        val dev = viewModel.currentDevice.value
        assertEquals(DeviceLockState.ACTIVE_UNLOCKED, dev.lockState)
        assertTrue(dev.isScreenOn)
        assertFalse(dev.isLocked)

        val latestCmd = viewModel.commandHistory.value.firstOrNull()
        assertEquals(RemoteAction.UNLOCK_DEVICE, latestCmd?.action)
    }

    @Test
    fun addBonusTimeIncreasesLimit() {
        viewModel.selectChild("c1", "Ama")
        val initialLimit = viewModel.currentDevice.value.dailyLimitMinutes
        viewModel.addBonusTime("c1", 30)

        val updatedLimit = viewModel.currentDevice.value.dailyLimitMinutes
        assertEquals(initialLimit + 30, updatedLimit)

        val latestCmd = viewModel.commandHistory.value.firstOrNull()
        assertEquals(RemoteAction.ADD_BONUS_TIME, latestCmd?.action)
    }

    @Test
    fun updateDailyLimitUsesSetDailyLimitAction() {
        viewModel.selectChild("c1", "Ama")
        viewModel.updateDailyLimit("c1", 75)

        assertEquals(75, viewModel.currentDevice.value.dailyLimitMinutes)
        val latestCmd = viewModel.commandHistory.value.firstOrNull()
        assertEquals(RemoteAction.SET_DAILY_LIMIT, latestCmd?.action)
    }

    @Test
    fun soundAlarmDispatchesCommand() {
        viewModel.selectChild("c2", "Kofi")
        viewModel.remoteSoundAlarm("c2")

        val latestCmd = viewModel.commandHistory.value.firstOrNull()
        assertEquals(RemoteAction.SOUND_ALARM, latestCmd?.action)
        assertEquals("c2", latestCmd?.childId)
    }

    @Test
    fun applyAgeRecommendationSetsDefaultLimit() {
        viewModel.selectChild("c1", "Ama")
        viewModel.applyAgeRecommendation("c1", AgeProfile.KIDS_3_7)
        assertEquals(60, viewModel.currentDevice.value.dailyLimitMinutes)

        viewModel.applyAgeRecommendation("c1", AgeProfile.PRETEENS_8_12)
        assertEquals(90, viewModel.currentDevice.value.dailyLimitMinutes)
    }

    @Test
    fun schedulesUpdateCorrectly() {
        viewModel.selectChild("c1", "Ama")
        viewModel.updateBedtimeSchedule("c1", "21:30", "07:30", true)
        val dev = viewModel.currentDevice.value
        assertEquals("21:30", dev.bedtimeStart)
        assertEquals("07:30", dev.bedtimeEnd)
        assertTrue(dev.bedtimeEnabled)

        viewModel.updateSchoolSchedule("c1", "08:00", "15:00", false)
        val dev2 = viewModel.currentDevice.value
        assertEquals("08:00", dev2.schoolStart)
        assertEquals("15:00", dev2.schoolEnd)
        assertFalse(dev2.schoolEnabled)
    }

    @Test
    fun clearFeedbackEmitsNullAndIdenticalMessagesRefire() {
        viewModel.selectChild("c1", "Ama")
        viewModel.remoteUnlock("c1")
        val first = viewModel.actionFeedback.value
        assertNotNull(first)
        assertEquals("🟢 Unlocked & resumed Ama's phone", first?.message)

        viewModel.clearFeedback()
        assertEquals(null, viewModel.actionFeedback.value)

        // An identical action produces a NEW id, so the UI can re-show the same message.
        viewModel.remoteUnlock("c1")
        val second = viewModel.actionFeedback.value
        assertNotNull(second)
        assertEquals(first?.message, second?.message)
        assertTrue((second?.id ?: 0L) > (first?.id ?: 0L))
    }
}
