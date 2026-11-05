package com.tele.k_guard.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.tele.k_guard.data.model.ChildProfile
import com.tele.k_guard.data.model.FamilyPrivacy
import com.tele.k_guard.data.model.ParentAction
import com.tele.k_guard.data.model.QuietHours
import com.tele.k_guard.data.model.SafetyEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Auth + family data. All writes go through Firestore rules; privileged ones go through Cloud Functions. */
object FamilyRepository {

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private val functions get() = FirebaseFunctions.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signUp(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    fun signOut() = auth.signOut()

    /** Creates the family on first sign-in (server side) and returns its id. */
    suspend fun ensureFamily(): String {
        val res = functions.getHttpsCallable("createFamily").call().await()
        @Suppress("UNCHECKED_CAST")
        val data = res.data as Map<String, Any?>
        return data["familyId"] as String
    }

    // ---------- children ----------
    fun children(familyId: String): Flow<List<ChildProfile>> = callbackFlow {
        val reg = db.collection("families/$familyId/children")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull { d ->
                    d.toObject(ChildProfile::class.java)?.copy(id = d.id)
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    suspend fun saveChild(familyId: String, child: ChildProfile): String {
        val col = db.collection("families/$familyId/children")
        val data = hashMapOf<String, Any?>(
            "name" to child.name.trim(),
            "ageProfile" to child.ageProfile,
            "categoryRules" to child.categoryRules,
            "allowReveal" to child.allowReveal
        )
        return if (child.id.isEmpty()) {
            data["createdAt"] = FieldValue.serverTimestamp()
            col.add(data).await().id
        } else {
            col.document(child.id).update(data).await()
            child.id
        }
    }

    suspend fun deleteChild(familyId: String, childId: String) {
        db.document("families/$familyId/children/$childId").delete().await()
    }

    suspend fun createPairingCode(childId: String): String {
        val res = functions.getHttpsCallable("createPairingCode").call(mapOf("childId" to childId)).await()
        @Suppress("UNCHECKED_CAST")
        return (res.data as Map<String, Any?>)["code"] as String
    }

    // ---------- events ----------
    fun events(familyId: String, limit: Long = 100): Flow<List<SafetyEvent>> = callbackFlow {
        val reg = db.collection("families/$familyId/events")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull { d ->
                    d.toObject(SafetyEvent::class.java)?.copy(id = d.id)
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    suspend fun reviewEvent(familyId: String, eventId: String, action: ParentAction, note: String) {
        db.document("families/$familyId/events/$eventId").update(
            mapOf(
                "status" to "reviewed",
                "parentAction" to action.id,
                "parentNote" to note.trim().take(300),
                "reviewedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    // ---------- privacy ----------
    fun privacy(familyId: String): Flow<FamilyPrivacy> = callbackFlow {
        val reg = db.document("families/$familyId").addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            @Suppress("UNCHECKED_CAST")
            val p = snap?.get("privacy") as? Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val q = p?.get("quietHours") as? Map<String, Any?>
            trySend(
                FamilyPrivacy(
                    notifyOn = p?.get("notifyOn") as? String ?: FamilyPrivacy.NOTIFY_WARN_AND_ABOVE,
                    includeSnippet = p?.get("includeSnippet") as? Boolean ?: false,
                    retainDays = (p?.get("retainDays") as? Number)?.toInt() ?: 30,
                    quietHours = QuietHours(
                        enabled = q?.get("enabled") as? Boolean ?: false,
                        start = q?.get("start") as? String ?: "21:00",
                        end = q?.get("end") as? String ?: "07:00",
                        utcOffsetMinutes = (q?.get("utcOffsetMinutes") as? Number)?.toInt() ?: 0
                    )
                )
            )
        }
        awaitClose { reg.remove() }
    }

    suspend fun savePrivacy(familyId: String, p: FamilyPrivacy) {
        db.document("families/$familyId").update(
            "privacy", mapOf(
                "notifyOn" to p.notifyOn,
                "includeSnippet" to p.includeSnippet,
                "retainDays" to p.retainDays,
                "quietHours" to mapOf(
                    "enabled" to p.quietHours.enabled,
                    "start" to p.quietHours.start,
                    "end" to p.quietHours.end,
                    "utcOffsetMinutes" to p.quietHours.utcOffsetMinutes
                )
            )
        ).await()
    }

    // ---------- push ----------
    suspend fun registerPushToken(token: String? = null) {
        val uid = currentUser?.uid ?: return
        val t = token ?: FirebaseMessaging.getInstance().token.await()
        db.document("users/$uid").update("fcmTokens", FieldValue.arrayUnion(t)).await()
    }
}
