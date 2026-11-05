package com.tele.k_guard.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
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

    private val _demoDevices = MutableStateFlow(
        mapOf(
            "c1" to ChildDevice(deviceId = "dev_c1", childId = "c1", childName = "Ama", deviceName = "Ama's Phone", deviceModel = "Pixel 7", usedScreenTimeMinutes = 45, dailyLimitMinutes = 120),
            "c2" to ChildDevice(deviceId = "dev_c2", childId = "c2", childName = "Kofi", deviceName = "Kofi's Tablet", deviceModel = "Galaxy Tab", usedScreenTimeMinutes = 90, dailyLimitMinutes = 150)
        )
    )

    private val _demoCommands = MutableStateFlow<List<RemoteCommand>>(emptyList())

    /**
     * Real-time stream of device telemetry for a child.
     */
    fun deviceStream(familyId: String, childId: String, childName: String): Flow<ChildDevice> = callbackFlow {
        if (familyId == "demo") {
            val dev = _demoDevices.value[childId] ?: ChildDevice(childId = childId, childName = childName)
            trySend(dev)
            val reg = _demoDevices.collect { map ->
                trySend(map[childId] ?: ChildDevice(childId = childId, childName = childName))
            }
            awaitClose { }
            return@callbackFlow
        }

        val ref = db.document("families/$familyId/devices/$childId")
        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null || snap == null || !snap.exists()) {
                val defaultDev = ChildDevice(childId = childId, childName = childName)
                trySend(defaultDev)
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
                isOnline = data?.get("isOnline") as? Boolean ?: true,
                batteryPercent = (data?.get("batteryPercent") as? Number)?.toInt() ?: 85,
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

    /**
     * Real-time stream of command audit logs.
     */
    fun commandsStream(familyId: String, childId: String): Flow<List<RemoteCommand>> = callbackFlow {
        if (familyId == "demo") {
            trySend(_demoCommands.value.filter { it.childId.isEmpty() || it.childId == childId })
            val reg = _demoCommands.collect { list ->
                trySend(list.filter { it.childId.isEmpty() || it.childId == childId })
            }
            awaitClose { }
            return@callbackFlow
        }

        val reg = db.collection("families/$familyId/commands")
            .whereEqualTo("childId", childId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, err ->
                if (err != null) {
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

    /**
     * Send remote command (Switch off, Unlock, Alarm, Limit, Bedtime, School).
     */
    suspend fun sendRemoteCommand(
        familyId: String,
        childId: String,
        childName: String,
        action: RemoteAction,
        description: String,
        reason: String? = null,
        updates: Map<String, Any?> = emptyMap()
    ) {
        Log.d("KGuard", "ParentalControlsRepository: sendRemoteCommand action=${action.id} for child=$childName")
        if (familyId == "demo") {
            val cmd = RemoteCommand(
                id = "cmd_${System.currentTimeMillis()}",
                childId = childId,
                childName = childName,
                action = action,
                description = description,
                reason = reason,
                timestamp = System.currentTimeMillis()
            )
            _demoCommands.value = listOf(cmd) + _demoCommands.value
            val currentDev = _demoDevices.value[childId] ?: ChildDevice(childId = childId, childName = childName)
            val updatedDev = when (action) {
                RemoteAction.SWITCH_OFF_LOCK -> currentDev.copy(lockState = DeviceLockState.REMOTE_LOCKED, lockReason = reason)
                RemoteAction.UNLOCK_DEVICE -> currentDev.copy(lockState = DeviceLockState.ACTIVE_UNLOCKED, lockReason = null)
                RemoteAction.ADD_BONUS_TIME -> {
                    val bonus = (updates["bonusMinutes"] as? Number)?.toInt() ?: 30
                    currentDev.copy(dailyLimitMinutes = currentDev.dailyLimitMinutes + bonus)
                }
                RemoteAction.TOGGLE_BEDTIME -> {
                    val enabled = updates["bedtimeEnabled"] as? Boolean ?: currentDev.bedtimeEnabled
                    currentDev.copy(bedtimeEnabled = enabled)
                }
                RemoteAction.TOGGLE_SCHOOL_MODE -> {
                    val enabled = updates["schoolEnabled"] as? Boolean ?: currentDev.schoolEnabled
                    currentDev.copy(schoolEnabled = enabled)
                }
                else -> currentDev
            }
            val finalDev = if (updates["dailyLimitMinutes"] != null) {
                updatedDev.copy(dailyLimitMinutes = (updates["dailyLimitMinutes"] as Number).toInt())
            } else {
                updatedDev
            }
            _demoDevices.value = _demoDevices.value + (childId to finalDev)
            return
        }

        val batch = db.batch()
        val cmdRef = db.collection("families/$familyId/commands").document()
        val cmdData = mapOf(
            "childId" to childId,
            "childName" to childName,
            "action" to action.id,
            "description" to description,
            "reason" to (reason ?: ""),
            "timestamp" to System.currentTimeMillis(),
            "status" to "Delivered & Applied"
        )
        batch.set(cmdRef, cmdData)

        val devRef = db.document("families/$familyId/devices/$childId")
        if (updates.isNotEmpty()) {
            batch.set(devRef, updates, SetOptions.merge())
        }
        batch.commit().await()
        Log.d("KGuard", "ParentalControlsRepository: sendRemoteCommand success")
    }

    /**
     * Real-time stream of apps for a given family from Firestore.
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
