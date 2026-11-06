package com.tele.k_guard.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tele.k_guard.data.model.AgeProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private val FAMILY_ID_KEY = stringPreferencesKey("family_id")
    private val AGE_PROFILE_KEY = stringPreferencesKey("selected_age_profile")
    private val IS_CHILD_DEVICE_KEY = booleanPreferencesKey("is_child_device")
    private val ACTIVE_CHILD_ID_KEY = stringPreferencesKey("active_child_id")
    private val PARENT_PIN_HASH_KEY = stringPreferencesKey("parent_pin_hash")
    private val THEME_MODE_KEY = stringPreferencesKey("app_theme_mode")

    val familyIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[FAMILY_ID_KEY]
    }

    val ageProfileFlow: Flow<AgeProfile> = context.dataStore.data.map { preferences ->
        val profileId = preferences[AGE_PROFILE_KEY]
        AgeProfile.fromId(profileId)
    }

    val isChildDeviceFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_CHILD_DEVICE_KEY] ?: false
    }

    val activeChildIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACTIVE_CHILD_ID_KEY]
    }

    val parentPinHashFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PARENT_PIN_HASH_KEY]
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY] ?: "SYSTEM"
    }

    suspend fun saveFamilyId(familyId: String?) {
        context.dataStore.edit { preferences ->
            if (familyId != null) {
                preferences[FAMILY_ID_KEY] = familyId
            } else {
                preferences.remove(FAMILY_ID_KEY)
            }
        }
    }

    suspend fun saveAgeProfile(ageProfile: AgeProfile) {
        context.dataStore.edit { preferences ->
            preferences[AGE_PROFILE_KEY] = ageProfile.id
        }
    }

    suspend fun saveChildDeviceMode(isChild: Boolean, childId: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[IS_CHILD_DEVICE_KEY] = isChild
            if (childId != null) {
                preferences[ACTIVE_CHILD_ID_KEY] = childId
            } else if (!isChild) {
                preferences.remove(ACTIVE_CHILD_ID_KEY)
            }
        }
    }

    /**
     * Stores the parent's PIN for this phone as a salted SHA-256 hash ("salt:hash"); the PIN itself is never
     * written to disk.
     */
    suspend fun saveParentPinHash(pin: String) {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        context.dataStore.edit { preferences ->
            preferences[PARENT_PIN_HASH_KEY] = "$saltHex:${hashPin(saltHex, pin)}"
        }
    }

    /** True if [pin] matches the stored parent PIN. */
    suspend fun verifyParentPin(pin: String): Boolean {
        val stored = parentPinHashFlow.firstOrNull() ?: return false
        val (salt, hash) = stored.split(":").takeIf { it.size == 2 } ?: return false
        return java.security.MessageDigest.isEqual(hashPin(salt, pin).toByteArray(), hash.toByteArray())
    }

    private fun hashPin(saltHex: String, pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest("$saltHex:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }
}
