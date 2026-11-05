package com.tele.k_guard.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.tele.k_guard.data.model.AppAccessInfo
import com.tele.k_guard.data.model.AppApprovalStatus
import com.tele.k_guard.data.model.ScreenTimeConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object ParentalControlsRepository {

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private val functions get() = FirebaseFunctions.getInstance()

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
            safetyReason = "Curated content for young children with strict parental controls.",
            requestedAt = Timestamp.now()
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
            safetyReason = "High volume of uncurated user-generated videos; potential exposure to inappropriate trends.",
            requestedAt = Timestamp.now()
        ),
        AppAccessInfo(
            id = "app_3",
            appName = "Roblox",
            packageName = "com.roblox.client",
            category = "Gaming & Community",
            minAgeRating = 10,
            safetyScore = 65,
            approvalStatus = AppApprovalStatus.PENDING_REVIEW,
            dailyLimitMinutes = 45,
            safetyReason = "Multiplayer gaming. Open chat features require monitoring for younger players.",
            requestedAt = Timestamp.now()
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
            safetyReason = "100% child-safe educational reading app with zero ads or external communication.",
            requestedAt = Timestamp.now()
        )
    )

    private val _demoApps = MutableStateFlow(initialApps)
    val managedApps: StateFlow<List<AppAccessInfo>> = _demoApps.asStateFlow()

    /**
     * Real-time stream of apps for a given family from Firestore.
     * Optionally filtered by childId.
     */
    fun appsStream(familyId: String, childId: String? = null): Flow<List<AppAccessInfo>> = callbackFlow {
        if (familyId == "demo") {
            val list = if (childId == null) _demoApps.value else _demoApps.value.filter { it.childId.isEmpty() || it.childId == childId }
            trySend(list)
            val reg = _demoApps.collect { current ->
                val filtered = if (childId == null) current else current.filter { it.childId.isEmpty() || it.childId == childId }
                trySend(filtered)
            }
            awaitClose { }
            return@callbackFlow
        }

        var query: Query = db.collection("families/$familyId/apps")
        if (childId != null && childId.isNotBlank()) {
            query = query.whereEqualTo("childId", childId)
        }
        query = query.orderBy("requestedAt", Query.Direction.DESCENDING)

        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) {
                // In case composite index is deploying or fallback needed
                val fallbackReg = db.collection("families/$familyId/apps").addSnapshotListener { fallbackSnap, fallbackErr ->
                    if (fallbackErr != null) {
                        trySend(_demoApps.value)
                        return@addSnapshotListener
                    }
                    val apps = fallbackSnap?.documents?.mapNotNull { doc ->
                        parseDoc(doc.id, doc.data)
                    } ?: _demoApps.value
                    val filtered = if (childId != null) apps.filter { it.childId.isEmpty() || it.childId == childId } else apps
                    trySend(filtered.ifEmpty { _demoApps.value })
                }
                return@addSnapshotListener
            }
            val apps = snap?.documents?.mapNotNull { doc ->
                parseDoc(doc.id, doc.data)
            } ?: emptyList()
            trySend(if (apps.isEmpty()) _demoApps.value else apps)
        }

        awaitClose { reg.remove() }
    }

    private fun parseDoc(docId: String, data: Map<String, Any?>?): AppAccessInfo? {
        if (data == null) return null
        val rawStatus = data["approvalStatus"] as? String
        val status = AppApprovalStatus.fromString(rawStatus)
        return AppAccessInfo(
            id = docId,
            appName = data["appName"] as? String ?: "Unknown App",
            packageName = data["packageName"] as? String ?: "",
            category = data["category"] as? String ?: "General",
            minAgeRating = (data["minAgeRating"] as? Number)?.toInt() ?: 3,
            safetyScore = (data["safetyScore"] as? Number)?.toInt() ?: 80,
            approvalStatus = status,
            dailyLimitMinutes = (data["dailyLimitMinutes"] as? Number)?.toInt() ?: 30,
            safetyReason = data["safetyReason"] as? String ?: "",
            childId = data["childId"] as? String ?: "",
            childName = data["childName"] as? String ?: "",
            childLinkedUid = data["childLinkedUid"] as? String,
            requestedAt = data["requestedAt"] as? Timestamp,
            reviewedAt = data["reviewedAt"] as? Timestamp,
            reviewedBy = data["reviewedBy"] as? String
        )
    }

    /**
     * Submit an app approval request from a child or parent.
     */
    suspend fun requestAppApproval(
        familyId: String,
        childId: String,
        appName: String,
        packageName: String,
        category: String = "General",
        minAgeRating: Int = 3,
        dailyLimitMinutes: Int = 30
    ): Map<String, Any?> {
        if (familyId == "demo") {
            val newApp = AppAccessInfo(
                id = "demo_${System.currentTimeMillis()}",
                appName = appName,
                packageName = packageName,
                category = category,
                minAgeRating = minAgeRating,
                safetyScore = if (minAgeRating > 12) 50 else 88,
                approvalStatus = AppApprovalStatus.PENDING_REVIEW,
                dailyLimitMinutes = dailyLimitMinutes,
                safetyReason = "Requested for testing. Requires parental sign-off.",
                childId = childId,
                childName = "Demo Child",
                requestedAt = Timestamp.now()
            )
            _demoApps.value = listOf(newApp) + _demoApps.value
            return mapOf("appId" to newApp.id, "approvalStatus" to "PENDING_REVIEW")
        }

        val res = functions.getHttpsCallable("requestAppApproval").call(
            mapOf(
                "childId" to childId,
                "appName" to appName,
                "packageName" to packageName,
                "category" to category,
                "minAgeRating" to minAgeRating,
                "dailyLimitMinutes" to dailyLimitMinutes
            )
        ).await()

        @Suppress("UNCHECKED_CAST")
        return res.data as Map<String, Any?>
    }

    /**
     * Parent responds to an app install request (APPROVE or REJECT).
     */
    suspend fun respondToAppApproval(
        familyId: String,
        appId: String,
        decision: AppApprovalStatus,
        dailyLimitMinutes: Int? = null
    ) {
        if (familyId == "demo") {
            _demoApps.value = _demoApps.value.map { app ->
                if (app.id == appId) app.copy(approvalStatus = decision) else app
            }
            return
        }

        try {
            functions.getHttpsCallable("respondToAppApproval").call(
                mapOf(
                    "familyId" to familyId,
                    "appId" to appId,
                    "decision" to decision.name,
                    "dailyLimitMinutes" to dailyLimitMinutes
                )
            ).await()
        } catch (_: Exception) {
            val update = mutableMapOf<String, Any?>(
                "approvalStatus" to decision.name,
                "reviewedAt" to FieldValue.serverTimestamp(),
                "reviewedBy" to auth.currentUser?.uid
            )
            if (dailyLimitMinutes != null) {
                update["dailyLimitMinutes"] = dailyLimitMinutes
            }
            db.document("families/$familyId/apps/$appId").update(update).await()
        }
    }

    /**
     * Update an app's daily usage limit in minutes.
     */
    suspend fun updateAppLimit(familyId: String, appId: String, limitMinutes: Int) {
        if (familyId == "demo") {
            _demoApps.value = _demoApps.value.map { app ->
                if (app.id == appId) app.copy(dailyLimitMinutes = limitMinutes) else app
            }
            return
        }

        db.document("families/$familyId/apps/$appId").update("dailyLimitMinutes", limitMinutes).await()
    }

    fun updateAppStatus(appId: String, status: AppApprovalStatus) {
        _demoApps.value = _demoApps.value.map { app ->
            if (app.id == appId) app.copy(approvalStatus = status) else app
        }
    }

    fun updateAppLimit(appId: String, limitMinutes: Int) {
        _demoApps.value = _demoApps.value.map { app ->
            if (app.id == appId) app.copy(dailyLimitMinutes = limitMinutes) else app
        }
    }

    fun updateScreenTimeLimits(newLimitMinutes: Int, schoolTimeEnabled: Boolean) {
        _screenTimeConfig.value = _screenTimeConfig.value.copy(
            dailyLimitMinutes = newLimitMinutes,
            schoolTimeEnabled = schoolTimeEnabled
        )
    }
}
