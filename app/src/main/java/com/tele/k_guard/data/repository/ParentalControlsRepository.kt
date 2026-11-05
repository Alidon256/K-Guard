package com.tele.k_guard.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.tele.k_guard.data.model.AppAccessInfo
import com.tele.k_guard.data.model.AppApprovalStatus
import com.tele.k_guard.data.model.ChildDevice
import com.tele.k_guard.data.model.DeviceLockState
import com.tele.k_guard.data.model.RemoteAction
import com.tele.k_guard.data.model.RemoteCommand
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Parent-side device telemetry, remote commands and app approvals, all backed by
 * `families/{familyId}/...` in Firestore and the matching Cloud Functions. There is no demo
 * fallback: a family with no data shows honest empty states.
 */
object ParentalControlsRepository {

    private val db get() = FirebaseFirestore.getInstance()
    private val functions get() = FirebaseFunctions.getInstance()

    /** Real-time stream of device telemetry for a child. */
    fun deviceStream(familyId: String, childId: String, childName: String): Flow<ChildDevice> = callbackFlow {
        val ref = db.document("families/$familyId/devices/$childId")
        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e("KGuard", "deviceStream error for child=$childId", err)
                // Device is clearly NOT paired/telemetry unknown: never fake "online" defaults.
                trySend(ChildDevice(childId = childId, childName = childName, isOnline = false, batteryPercent = 0, networkName = "Not connected"))
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) {
                trySend(ChildDevice(childId = childId, childName = childName, isOnline = false, batteryPercent = 0, networkName = "Not connected"))
                return@addSnapshotListener
            }
            val data = snap.data
            val dev = ChildDevice(
                deviceId = data?.get("deviceId") as? String ?: "dev_$childId",
                childId = childId,
                childName = data?.get("childName") as? String ?: childName,
                deviceName = data?.get("deviceName") as? String ?: "$childName's Phone",
                deviceModel = data?.get("deviceModel") as? String ?: "Android Device",
                androidVersion = data?.get("androidVersion") as? String ?: "Android 14",
                isOnline = data?.get("isOnline") as? Boolean ?: false,
                batteryPercent = (data?.get("batteryPercent") as? Number)?.toInt() ?: 0,
                isCharging = data?.get("isCharging") as? Boolean ?: false,
                networkName = data?.get("networkName") as? String ?: "Wi-Fi",
                lockState = DeviceLockState.entries.find { it.id == data?.get("lockState") as? String } ?: DeviceLockState.ACTIVE_UNLOCKED,
                usedScreenTimeMinutes = (data?.get("usedScreenTimeMinutes") as? Number)?.toInt() ?: 45,
                dailyLimitMinutes = (data?.get("dailyLimitMinutes") as? Number)?.toInt() ?: 120,
                pickupsCount = (data?.get("pickupsCount") as? Number)?.toInt() ?: 20,
                bedtimeStart = data?.get("bedtimeStart") as? String ?: "21:00",
                bedtimeEnd = data?.get("bedtimeEnd") as? String ?: "07:00",
                bedtimeEnabled = data?.get("bedtimeEnabled") as? Boolean ?: true,
                schoolStart = data?.get("schoolStart") as? String ?: "08:30",
                schoolEnd = data?.get("schoolEnd") as? String ?: "15:30",
                schoolEnabled = data?.get("schoolEnabled") as? Boolean ?: true
            )
            trySend(dev)
        }
        awaitClose { reg.remove() }
    }

    /** Real-time stream of command audit logs. */
    fun commandsStream(familyId: String, childId: String): Flow<List<RemoteCommand>> = callbackFlow {
        val reg = db.collection("families/$familyId/commands")
            .whereEqualTo("childId", childId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("KGuard", "commandsStream error for child=$childId", err)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val cmds = snap?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    RemoteCommand(
                        id = doc.id,
                        childId = data["childId"] as? String ?: childId,
                        childName = data["childName"] as? String ?: "",
                        action = RemoteAction.entries.find { it.id == data["action"] as? String } ?: RemoteAction.SWITCH_OFF_LOCK,
                        description = data["description"] as? String ?: "",
                        reason = data["reason"] as? String,
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        status = data["status"] as? String ?: "Executed"
                    )
                } ?: emptyList()
                trySend(cmds)
            }
        awaitClose { reg.remove() }
    }

    /** Send remote command (Switch off, Unlock, Alarm, Limit, Bedtime, School). */
    suspend fun sendRemoteCommand(
        familyId: String,
        childId: String,
        childName: String,
        action: RemoteAction,
        description: String,
        reason: String? = null,
        updates: Map<String, Any?> = emptyMap()
    ) {
        val batch = db.batch()
        val cmdRef = db.collection("families/$familyId/commands").document()
        val cmdData = mapOf(
            "childId" to childId,
            "childName" to childName,
            "action" to action.id,
            "description" to description,
            "reason" to (reason ?: ""),
            "timestamp" to System.currentTimeMillis(),
            "status" to "Sent"
        )
        batch.set(cmdRef, cmdData)

        val devRef = db.document("families/$familyId/devices/$childId")
        if (updates.isNotEmpty()) {
            val effectiveUpdates = if (action == RemoteAction.ADD_BONUS_TIME) {
                // Server-side atomic increment so two rapid bonus taps never lose one (fixes stale-read races).
                val bonus = (updates["bonusMinutes"] as? Number)?.toLong() ?: 30L
                val u = updates.toMutableMap()
                u.remove("dailyLimitMinutes")
                u["dailyLimitMinutes"] = FieldValue.increment(bonus)
                u
            } else {
                updates
            }
            batch.set(devRef, effectiveUpdates, SetOptions.merge())
        }
        batch.commit().await()
    }

    /** Real-time stream of apps for a given family from Firestore. */
    fun appsStream(familyId: String, childId: String? = null): Flow<List<AppAccessInfo>> = callbackFlow {
        var query: Query = db.collection("families/$familyId/apps")
        var fallbackReg: com.google.firebase.firestore.ListenerRegistration? = null
        if (childId != null && childId.isNotBlank()) {
            query = query.whereEqualTo("childId", childId)
        }
        query = query.orderBy("requestedAt", Query.Direction.DESCENDING)

        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e("KGuard", "appsStream error; falling back to unfiltered query", err)
                if (fallbackReg == null) {
                    fallbackReg = db.collection("families/$familyId/apps").addSnapshotListener { fallbackSnap, fallbackErr ->
                        if (fallbackErr != null) {
                            Log.e("KGuard", "appsStream fallback error; emitting empty list", fallbackErr)
                            trySend(emptyList())
                            return@addSnapshotListener
                        }
                        val apps = fallbackSnap?.documents?.mapNotNull { doc ->
                            parseDoc(doc.id, doc.data)
                        } ?: emptyList()
                        val filtered = if (childId != null) apps.filter { it.childId.isEmpty() || it.childId == childId } else apps
                        trySend(filtered)
                    }
                }
                return@addSnapshotListener
            }
            // A real family with an empty app list gets an empty list.
            val apps = snap?.documents?.mapNotNull { doc ->
                parseDoc(doc.id, doc.data)
            } ?: emptyList()
            trySend(apps)
        }

        awaitClose {
            fallbackReg?.remove()
            reg.remove()
        }
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
            requestedAt = data["requestedAt"] as? com.google.firebase.Timestamp,
            reviewedAt = data["reviewedAt"] as? com.google.firebase.Timestamp,
            reviewedBy = data["reviewedBy"] as? String
        )
    }

    suspend fun requestAppApproval(
        familyId: String,
        childId: String,
        appName: String,
        packageName: String,
        category: String = "General",
        minAgeRating: Int = 3,
        dailyLimitMinutes: Int = 30
    ): Map<String, Any?> {
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

    suspend fun respondToAppApproval(
        familyId: String,
        appId: String,
        decision: AppApprovalStatus,
        dailyLimitMinutes: Int? = null
    ) {
        try {
            functions.getHttpsCallable("respondToAppApproval").call(
                mapOf(
                    "familyId" to familyId,
                    "appId" to appId,
                    "decision" to decision.name,
                    "dailyLimitMinutes" to dailyLimitMinutes
                )
            ).await()
        } catch (e: Exception) {
            // Propagate the failure — the server function owns validation. No silent raw-client fallback.
            Log.e("KGuard", "respondToAppApproval callable failed for app=$appId", e)
            throw e
        }
    }

    suspend fun updateAppLimit(familyId: String, appId: String, limitMinutes: Int) {
        db.document("families/$familyId/apps/$appId").update("dailyLimitMinutes", limitMinutes).await()
    }
}
