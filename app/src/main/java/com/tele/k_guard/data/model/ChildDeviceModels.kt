package com.tele.k_guard.data.model

import kotlinx.serialization.Serializable

/**
 * State of lock or restrictions applied to a child's device.
 */
@Serializable
enum class DeviceLockState(val id: String, val label: String) {
    ACTIVE_UNLOCKED("active_unlocked", "Active & Unlocked"),
    REMOTE_LOCKED("remote_locked", "Screen Turned Off & Locked"),
    BEDTIME_LOCKED("bedtime_locked", "Bedtime Lockdown"),
    SCHOOL_LOCKED("school_locked", "School Focus Mode"),
    LIMIT_REACHED("limit_reached", "Daily Limit Reached")
}

/**
 * Type of remote action sent by parent to child phone.
 */
@Serializable
enum class RemoteAction(val id: String, val label: String) {
    SWITCH_OFF_LOCK("switch_off_lock", "Turn Off Screen & Lock"),
    UNLOCK_DEVICE("unlock_device", "Unlock & Resume Device"),
    SOUND_ALARM("sound_alarm", "Play Loud Ring Alert"),
    ADD_BONUS_TIME("add_bonus_time", "Add Bonus Screen Time"),
    SET_DAILY_LIMIT("set_daily_limit", "Set Daily Limit"),
    TOGGLE_BEDTIME("toggle_bedtime", "Toggle Bedtime Schedule"),
    TOGGLE_SCHOOL_MODE("toggle_school_mode", "Toggle School Mode")
}

/**
 * Detailed application usage record for today on the child's phone.
 */
@Serializable
data class AppUsageItem(
    val packageName: String = "",
    val appName: String = "",
    val category: String = "Entertainment",
    val usedMinutes: Int = 0,
    val dailyLimitMinutes: Int? = null,
    val isBlocked: Boolean = false,
    val isCurrentlyActive: Boolean = false
)

/**
 * Complete status and telemetry of a child's connected phone.
 */
@Serializable
data class ChildDevice(
    val deviceId: String = "",
    val childId: String = "",
    val childName: String = "",
    val deviceName: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "Android 14",
    val isOnline: Boolean = true,
    val lastSeenMinutesAgo: Int = 0,
    val batteryPercent: Int = 85,
    val isCharging: Boolean = false,
    val networkName: String = "Home Wi-Fi (5 GHz)",
    val isScreenOn: Boolean = true,
    val currentForegroundApp: String? = "YouTube Kids",
    val lockState: DeviceLockState = DeviceLockState.ACTIVE_UNLOCKED,
    val lockedAtMillis: Long? = null,
    val lockReason: String? = null,
    val usedScreenTimeMinutes: Int = 45,
    val dailyLimitMinutes: Int = 120,
    val pickupsCount: Int = 28,
    val bedtimeStart: String = "21:00",
    val bedtimeEnd: String = "07:00",
    val bedtimeEnabled: Boolean = true,
    val schoolStart: String = "08:30",
    val schoolEnd: String = "15:30",
    val schoolEnabled: Boolean = true,
    val appUsages: List<AppUsageItem> = emptyList(),
    val hourlyUsageMinutes: List<Int> = emptyList() // 12 values for 08:00 to 20:00
) {
    val remainingMinutes: Int
        get() = (dailyLimitMinutes - usedScreenTimeMinutes).coerceAtLeast(0)

    val usageProgress: Float
        get() = (usedScreenTimeMinutes.toFloat() / dailyLimitMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)

    val isLocked: Boolean
        get() = lockState != DeviceLockState.ACTIVE_UNLOCKED
}

/**
 * Audit log entry for a remote command sent by the parent.
 */
@Serializable
data class RemoteCommand(
    val id: String = "",
    val childId: String = "",
    val childName: String = "",
    val deviceId: String = "",
    val action: RemoteAction = RemoteAction.SWITCH_OFF_LOCK,
    val description: String = "",
    val reason: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Delivered & Applied"
)

