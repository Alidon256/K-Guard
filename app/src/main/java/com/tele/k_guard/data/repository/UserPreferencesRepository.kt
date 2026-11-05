package com.tele.k_guard.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tele.k_guard.data.model.AgeProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private val AGE_PROFILE_KEY = stringPreferencesKey("selected_age_profile")

    val ageProfileFlow: Flow<AgeProfile> = context.dataStore.data.map { preferences ->
        val profileId = preferences[AGE_PROFILE_KEY]
        AgeProfile.fromId(profileId)
    }

    suspend fun saveAgeProfile(ageProfile: AgeProfile) {
        context.dataStore.edit { preferences ->
            preferences[AGE_PROFILE_KEY] = ageProfile.id
        }
    }
}
