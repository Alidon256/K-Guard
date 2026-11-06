package com.tele.k_guard.data.model

import kotlinx.serialization.Serializable

/**
 * ScreenLock model matching KidSafe architecture.
 * Holds duration in hours and minutes, total seconds, and lock status.
 */
@Serializable
data class ScreenLock(
    val hours: Int = 0,
    val minutes: Int = 0,
    val isLocked: Boolean = false,
    val lockReason: String? = null,
    val timeInMinutes: Int = hours * 60 + minutes,
    val timeInSeconds: Int = (hours * 60 + minutes) * 60
)
