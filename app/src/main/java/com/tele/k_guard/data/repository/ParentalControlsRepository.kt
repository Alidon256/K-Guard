package com.tele.k_guard.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.tele.k_guard.data.model.AppAccessInfo
import com.tele.k_guard.data.model.AppApprovalStatus
import com.tele.k_guard.data.model.AppUsageItem
import com.tele.k_guard.data.model.ChildDevice
import com.tele.k_guard.data.model.DeviceLockState
import com.tele.k_guard.data.model.RemoteAction
import com.tele.k_guard.data.model.RemoteCommand
import com.tele.k_guard.data.model.ScreenTimeConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

object ParentalControlsRepository {

    private val auth get() = runCatching { FirebaseAuth.getInstance() }.getOrNull()
    private val db get() = runCatching { FirebaseFirestore.getInstance() }.getOrNull()

    // ---------------- Legacy config support ----------------
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

    // ---------------- Connected Child Devices & Remote Management ----------------

    private val initialDevices = mapOf(
        "c1" to ChildDevice(
            deviceId = "dev_ama_01",
            childId = "c1",
            childName = "Ama",
            deviceName = "Ama's Galaxy A14",
            deviceModel = "Samsung Galaxy A14 5G",
            androidVersion = "Android 14 (One UI 6.0)",
            isOnline = true,
            lastSeenMinutesAgo = 0,
            batteryPercent = 78,
            isCharging = false,
            networkName = "Home Wi-Fi (5 GHz)",
            isScreenOn = true,
            currentForegroundApp = "YouTube Kids",
            lockState = DeviceLockState.ACTIVE_UNLOCKED,
            usedScreenTimeMinutes = 45,
            dailyLimitMinutes = 90,
            pickupsCount = 26,
            bedtimeStart = "20:30",
            bedtimeEnd = "07:00",
            bedtimeEnabled = true,
            schoolStart = "08:30",
            schoolEnd = "15:30",
            schoolEnabled = true,
            appUsages = listOf(
                AppUsageItem(
                    packageName = "com.google.android.apps.youtube.kids",
                    appName = "YouTube Kids",
                    category = "Entertainment",
                    usedMinutes = 25,
                    dailyLimitMinutes = 45,
                    isCurrentlyActive = true
                ),
                AppUsageItem(
                    packageName = "com.duolingo.abc",
                    appName = "Duolingo ABC",
                    category = "Education",
                    usedMinutes = 15,
                    dailyLimitMinutes = 60,
                    isCurrentlyActive = false
                ),
                AppUsageItem(
                    packageName = "org.scratchjr.android",
                    appName = "ScratchJr",
                    category = "Education",
                    usedMinutes = 5,
                    dailyLimitMinutes = 30,
                    isCurrentlyActive = false
                )
            ),
            hourlyUsageMinutes = listOf(0, 0, 5, 10, 0, 15, 10, 5, 0, 0, 0, 0)
        ),
        "c2" to ChildDevice(
            deviceId = "dev_kofi_01",
            childId = "c2",
            childName = "Kofi",
            deviceName = "Kofi's Pixel 6a",
            deviceModel = "Google Pixel 6a",
            androidVersion = "Android 15",
            isOnline = true,
            lastSeenMinutesAgo = 1,
            batteryPercent = 42,
            isCharging = true,
            networkName = "MTN Uganda 4G",
            isScreenOn = true,
            currentForegroundApp = "Roblox",
            lockState = DeviceLockState.ACTIVE_UNLOCKED,
            usedScreenTimeMinutes = 110,
            dailyLimitMinutes = 150,
            pickupsCount = 42,
            bedtimeStart = "22:00",
            bedtimeEnd = "06:30",
            bedtimeEnabled = true,
            schoolStart = "08:30",
            schoolEnd = "15:30",
            schoolEnabled = true,
            appUsages = listOf(
                AppUsageItem(
                    packageName = "com.roblox.client",
                    appName = "Roblox",
                    category = "Gaming",
                    usedMinutes = 45,
                    dailyLimitMinutes = 60,
                    isCurrentlyActive = true
                ),
                AppUsageItem(
                    packageName = "org.khanacademy.android",
                    appName = "Khan Academy",
                    category = "Education",
                    usedMinutes = 30,
                    dailyLimitMinutes = 90,
                    isCurrentlyActive = false
                ),
                AppUsageItem(
                    packageName = "com.whatsapp",
                    appName = "WhatsApp",
                    category = "Social & Messaging",
                    usedMinutes = 20,
                    dailyLimitMinutes = 30,
                    isCurrentlyActive = false
                ),
                AppUsageItem(
                    packageName = "com.spotify.music",
                    appName = "Spotify",
                    category = "Entertainment",
                    usedMinutes = 15,
                    dailyLimitMinutes = 60,
                    isCurrentlyActive = false
                )
            ),
            hourlyUsageMinutes = listOf(0, 10, 15, 20, 5, 10, 20, 30, 0, 0, 0, 0)
        )
    )

    private val _devices = MutableStateFlow<Map<String, ChildDevice>>(initialDevices)
    val devices: StateFlow<Map<String, ChildDevice>> = _devices.asStateFlow()

    private val initialCommands = listOf(
        RemoteCommand(
            id = "cmd_1",
            childId = "c1",
            childName = "Ama",
            deviceId = "dev_ama_01",
            action = RemoteAction.ADD_BONUS_TIME,
            description = "+15 minutes bonus screen time awarded for completing homework",
            reason = "Completed homework",
            timestamp = System.currentTimeMillis() - 7200000L,
            status = "Delivered & Applied"
        ),
        RemoteCommand(
            id = "cmd_2",
            childId = "c2",
            childName = "Kofi",
            deviceId = "dev_kofi_01",
            action = RemoteAction.TOGGLE_SCHOOL_MODE,
            description = "School focus mode enabled",
            reason = "School morning schedule",
            timestamp = System.currentTimeMillis() - 28800000L,
            status = "Delivered & Applied"
        )
    )

    private val _commandHistory = MutableStateFlow<List<RemoteCommand>>(initialCommands)
    val commandHistory: StateFlow<List<RemoteCommand>> = _commandHistory.asStateFlow()

    /**
     * Retrieves or lazily creates a device profile for any child in the family.
     */
    fun getDeviceForChild(childId: String, childName: String = "Child"): ChildDevice {
        return _devices.value[childId] ?: ChildDevice(
            deviceId = "dev_${childId}",
            childId = childId,
            childName = childName,
            deviceName = "$childName's Phone",
            deviceModel = "Android Phone",
            androidVersion = "Android 14",
            isOnline = true,
            lastSeenMinutesAgo = 0,
            batteryPercent = 90,
            isCharging = false,
            networkName = "Wi-Fi",
            isScreenOn = true,
            currentForegroundApp = "Home Screen",
            lockState = DeviceLockState.ACTIVE_UNLOCKED,
            usedScreenTimeMinutes = 30,
            dailyLimitMinutes = 120,
            pickupsCount = 18,
            appUsages = listOf(
                AppUsageItem("com.google.android.youtube", "YouTube", "Entertainment", 20, 60, false, true),
                AppUsageItem("com.google.android.apps.docs", "Docs", "Education", 10, 60, false, false)
            ),
            hourlyUsageMinutes = listOf(0, 0, 5, 10, 15, 0, 0, 0, 0, 0, 0, 0)
        ).also { newDevice ->
            _devices.value = _devices.value + (childId to newDevice)
        }
    }

    /**
     * REMOTELY SWITCH OFF / LOCK CHILD'S PHONE
     * Turns off the screen and enforces remote lockdown on the child device.
     */
    fun switchOffPhoneRemotely(childId: String, reason: String = "Parental Screen Lock") {
        val current = getDeviceForChild(childId)
        val now = System.currentTimeMillis()
        val updated = current.copy(
            isScreenOn = false,
            currentForegroundApp = "Screen Turned Off · Locked by Parent",
            lockState = DeviceLockState.REMOTE_LOCKED,
            lockedAtMillis = now,
            lockReason = reason
        )
        _devices.value = _devices.value + (childId to updated)

        // Log remote command
        val cmd = RemoteCommand(
            id = "cmd_${System.currentTimeMillis()}",
            childId = childId,
            childName = current.childName,
            deviceId = current.deviceId,
            action = RemoteAction.SWITCH_OFF_LOCK,
            description = "Switched off phone screen remotely: $reason",
            reason = reason,
            timestamp = now,
            status = "Delivered & Applied"
        )
        _commandHistory.value = listOf(cmd) + _commandHistory.value

        // Sync with Firestore if signed in
        syncRemoteCommandToFirestore(childId, updated, cmd)
    }

    /**
     * REMOTELY UNLOCK / RESUME CHILD'S PHONE
     */
    fun unlockPhoneRemotely(childId: String) {
        val current = getDeviceForChild(childId)
        val now = System.currentTimeMillis()
        val updated = current.copy(
            isScreenOn = true,
            currentForegroundApp = "Device Resumed",
            lockState = DeviceLockState.ACTIVE_UNLOCKED,
            lockedAtMillis = null,
            lockReason = null
        )
        _devices.value = _devices.value + (childId to updated)

        val cmd = RemoteCommand(
            id = "cmd_${System.currentTimeMillis()}",
            childId = childId,
            childName = current.childName,
            deviceId = current.deviceId,
            action = RemoteAction.UNLOCK_DEVICE,
            description = "Unlocked and resumed child phone remotely",
            reason = "Parent approved resume",
            timestamp = now,
            status = "Delivered & Applied"
        )
        _commandHistory.value = listOf(cmd) + _commandHistory.value

        syncRemoteCommandToFirestore(childId, updated, cmd)
    }

    /**
     * REMOTELY SOUND ALARM / FIND PHONE
     * Plays a high-volume alarm chime on the child's phone even if silenced.
     */
    fun soundAlarmRemotely(childId: String) {
        val current = getDeviceForChild(childId)
        val now = System.currentTimeMillis()

        val cmd = RemoteCommand(
            id = "cmd_${System.currentTimeMillis()}",
            childId = childId,
            childName = current.childName,
            deviceId = current.deviceId,
            action = RemoteAction.SOUND_ALARM,
            description = "Triggered loud audible chime on child's phone",
            reason = "Parent locate/alert request",
            timestamp = now,
            status = "Sounding on Device"
        )
        _commandHistory.value = listOf(cmd) + _commandHistory.value

        syncRemoteCommandToFirestore(childId, current, cmd)
    }

    /**
     * ADD BONUS SCREEN TIME
     */
    fun addBonusScreenTime(childId: String, bonusMinutes: Int) {
        val current = getDeviceForChild(childId)
        val now = System.currentTimeMillis()
        val newLimit = current.dailyLimitMinutes + bonusMinutes
        val updated = current.copy(
            dailyLimitMinutes = newLimit,
            lockState = if (current.lockState == DeviceLockState.LIMIT_REACHED) DeviceLockState.ACTIVE_UNLOCKED else current.lockState
        )
        _devices.value = _devices.value + (childId to updated)

        val cmd = RemoteCommand(
            id = "cmd_${System.currentTimeMillis()}",
            childId = childId,
            childName = current.childName,
            deviceId = current.deviceId,
            action = RemoteAction.ADD_BONUS_TIME,
            description = "+$bonusMinutes minutes bonus screen time granted (New limit: ${newLimit}m)",
            reason = "Bonus rewarded by parent",
            timestamp = now,
            status = "Delivered & Applied"
        )
        _commandHistory.value = listOf(cmd) + _commandHistory.value

        syncRemoteCommandToFirestore(childId, updated, cmd)
    }

    /**
     * UPDATE DAILY SCREEN TIME LIMIT
     */
    fun updateChildDailyLimit(childId: String, limitMinutes: Int) {
        val current = getDeviceForChild(childId)
        val updated = current.copy(dailyLimitMinutes = limitMinutes)
        _devices.value = _devices.value + (childId to updated)
        updateScreenTimeLimits(limitMinutes, current.schoolEnabled)
    }

    /**
     * UPDATE BEDTIME SCHEDULE
     */
    fun updateChildBedtime(childId: String, start: String, end: String, enabled: Boolean) {
        val current = getDeviceForChild(childId)
        val updated = current.copy(
            bedtimeStart = start,
            bedtimeEnd = end,
            bedtimeEnabled = enabled
        )
        _devices.value = _devices.value + (childId to updated)
    }

    /**
     * UPDATE SCHOOL HOURS SCHEDULE
     */
    fun updateChildSchoolHours(childId: String, start: String, end: String, enabled: Boolean) {
        val current = getDeviceForChild(childId)
        val updated = current.copy(
            schoolStart = start,
            schoolEnd = end,
            schoolEnabled = enabled
        )
        _devices.value = _devices.value + (childId to updated)
        updateScreenTimeLimits(current.dailyLimitMinutes, enabled)
    }

    private fun syncRemoteCommandToFirestore(childId: String, device: ChildDevice, command: RemoteCommand) {
        val firestore = db ?: return
        val user = auth?.currentUser ?: return
        runCatching {
            firestore.collection("commands").add(
                mapOf(
                    "childId" to childId,
                    "deviceId" to device.deviceId,
                    "action" to command.action.id,
                    "reason" to command.reason,
                    "senderUid" to user.uid,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "status" to "sent"
                )
            )
        }
    }
}
