package com.tele.k_guard.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.tele.k_guard.data.model.ModerationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Sends content to the `moderateContent` Cloud Function. The server applies the child's age profile and
 * category rules, records the event and notifies parents.
 *
 * FAIL CLOSED: if the service cannot be reached the result is REVIEW, never ALLOW.
 */
class ModerationRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {

    suspend fun moderateText(text: String, childId: String): Result<ModerationResponse> =
        call(mapOf("type" to "text", "text" to text, "childId" to childId))

    suspend fun moderateImage(context: Context, uri: Uri, childId: String): Result<ModerationResponse> =
        withContext(Dispatchers.IO) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
                ?: return@withContext Result.failure(IllegalStateException("Please sign in first."))
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Unable to read the selected image."))
            }
            if (bytes.size > MAX_IMAGE_BYTES) {
                return@withContext Result.failure(IllegalStateException("That image is larger than 8 MB."))
            }
            val mime = context.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            val path = "users/$uid/uploads/${UUID.randomUUID()}"
            try {
                storage.reference.child(path)
                    .putBytes(bytes, StorageMetadata.Builder().setContentType(mime).build())
                    .await()
            } catch (e: Exception) {
                return@withContext Result.success(unavailable())
            }
            call(mapOf("type" to "image", "storagePath" to path, "childId" to childId))
        }

    private suspend fun call(payload: Map<String, Any?>): Result<ModerationResponse> = try {
        val res = functions.getHttpsCallable("moderateContent").call(payload).await()
        @Suppress("UNCHECKED_CAST")
        Result.success(parse(res.data as Map<String, Any?>))
    } catch (e: FirebaseFunctionsException) {
        when (e.code) {
            // The caller can fix these, so surface them instead of hiding behind REVIEW.
            FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            FirebaseFunctionsException.Code.UNAUTHENTICATED,
            FirebaseFunctionsException.Code.PERMISSION_DENIED,
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            FirebaseFunctionsException.Code.NOT_FOUND -> Result.failure(IllegalStateException(e.message ?: "Request rejected."))
            else -> Result.success(unavailable())
        }
    } catch (e: FirebaseNetworkException) {
        Result.success(unavailable())
    } catch (e: Exception) {
        Result.success(unavailable())
    }

    private fun parse(m: Map<String, Any?>) = ModerationResponse(
        category = (m["category"] as? String)?.takeIf { it != "none" } ?: "general",
        riskScore = (m["riskScore"] as? Number)?.toFloat() ?: 0f,
        ageAppropriate = m["ageAppropriate"] as? Boolean ?: false,
        decision = m["decision"] as? String ?: "REVIEW",
        reason = m["explanation"] as? String ?: "K-Guard could not explain this decision.",
        context = m["context"] as? String ?: "unclear",
        canReveal = m["canReveal"] as? Boolean ?: false,
        eventId = m["eventId"] as? String
    )

    private fun unavailable() = ModerationResponse(
        category = "unavailable",
        riskScore = 0f,
        ageAppropriate = false,
        decision = "REVIEW",
        reason = "K-Guard could not reach its safety service, so this content is being held. " +
            "Nothing was approved. Please try again when you are online."
    )

    private companion object {
        const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    }
}
