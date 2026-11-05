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
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private val AGE_PROFILE_KEY = stringPreferencesKey("selected_age_profile")
    private val IS_CHILD_DEVICE_KEY = booleanPreferencesKey("is_child_device")
    private val ACTIVE_CHILD_ID_KEY = stringPreferencesKey("active_child_id")
    private val PARENT_PIN_HASH_KEY = stringPreferencesKey("parent_pin_hash")

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

    suspend fun saveParentPinHash(hash: String) {
        context.dataStore.edit { preferences ->
            preferences[PARENT_PIN_HASH_KEY] = hash
        }
    }
}
