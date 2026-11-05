package com.tele.k_guard.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class AppApprovalStatus {
    APPROVED,
    PENDING_REVIEW,
    REJECTED
}

@Serializable
data class AppAccessInfo(
    val id: String,
    val appName: String,
    val packageName: String,
    val category: String,
    val minAgeRating: Int,
    val safetyScore: Int,
    val approvalStatus: AppApprovalStatus = AppApprovalStatus.PENDING_REVIEW,
    val dailyLimitMinutes: Int = 30,
    val safetyReason: String
)
