package com.tele.k_guard.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
        if (com.tele.k_guard.DebugPreview.demo) Result.success(demoText(text)) else call(mapOf("type" to "text", "text" to text, "childId" to childId))

    suspend fun moderateImage(context: Context, uri: Uri, childId: String): Result<ModerationResponse> =
        withContext(Dispatchers.IO) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
                ?: return@withContext Result.failure(IllegalStateException("Please sign in first."))
            // Check the declared size BEFORE reading so an oversized image is rejected without
            // loading it into memory (a full read of a huge image can exhaust the heap).
            val declaredSize = imageReportedSize(context, uri)
            if (declaredSize != null && declaredSize > MAX_IMAGE_BYTES) {
                return@withContext Result.failure(IllegalStateException("That image is larger than 8 MB."))
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                // Read with a hard cap in case the provider reported no size or a wrong one.
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > MAX_IMAGE_BYTES) {
                        return@withContext Result.failure(IllegalStateException("That image is larger than 8 MB."))
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            if (bytes == null || bytes.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Unable to read the selected image."))
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

    /** Best-effort size of the content without opening a full stream; null when unknown. */
    private fun imageReportedSize(context: Context, uri: Uri): Long? = try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else null
            }
            ?: context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length.takeIf { it >= 0 }
            }
    } catch (e: Exception) {
        null
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

    /** Debug demo only: canned answers so the result UIs can be previewed without a backend. */
    private fun demoText(t: String): ModerationResponse {
        val s = t.lowercase()
        return when {
            "kill" in s -> ModerationResponse("threat", 0.92f, false, "BLOCK", "Likely contains threatening language, which is not suitable for ages 8-12.", "harmful")
            "ugly" in s || "stupid" in s -> ModerationResponse("cyberbullying", 0.68f, false, "WARN", "Likely contains bullying or harassment, which is not suitable for ages 8-12.", "harmful")
            "war" in s || "blood" in s -> ModerationResponse("violence", 0.75f, false, "BLUR", "Contains violent content in an educational setting, so the response was reduced for ages 8-12.", "educational", canReveal = false)
            "offline" in s -> unavailable()
            else -> ModerationResponse("general", 0.04f, true, "ALLOW", "No concerns were found for this age group.", "unclear")
        }
    }

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
