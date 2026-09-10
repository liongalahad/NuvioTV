package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class RandomEpisodeSettings(
    val profileId: Int = 1,
    val enabled: Boolean = true,
    val enabledShows: Set<String> = emptySet(),
    val unwatchedShows: Set<String> = emptySet()
) {
    fun isEnabled(contentId: String, contentType: String) =
        enabled && contentType.lowercase() in setOf("series", "tv") && contentId in enabledShows
}

/** Device-local choices, scoped to the active Nuvio profile and excluded from settings sync. */
@Singleton
class RandomEpisodeDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    private val enabledKey = booleanPreferencesKey("enabled")
    private val showsKey = stringSetPreferencesKey("enabled_shows")
    private val unwatchedKey = stringSetPreferencesKey("unwatched_shows")
    private fun store(profileId: Int) = factory.get(profileId, "random_episode")

    val settings = profileManager.activeProfileId.flatMapLatest(::settingsForProfile)

    fun settingsForProfile(profileId: Int) = store(profileId).data.map {
        RandomEpisodeSettings(profileId, it[enabledKey] ?: true, it[showsKey].orEmpty(), it[unwatchedKey].orEmpty())
    }

    suspend fun setEnabled(enabled: Boolean) {
        store(profileManager.activeProfileId.value).edit { it[enabledKey] = enabled }
    }

    suspend fun toggleShow(contentId: String) {
        if (contentId.isBlank()) return
        store(profileManager.activeProfileId.value).edit {
            val shows = it[showsKey].orEmpty()
            it[showsKey] = if (contentId in shows) shows - contentId else shows + contentId
        }
    }

    suspend fun selectPool(contentId: String, unwatchedOnly: Boolean) {
        if (contentId.isBlank()) return
        store(profileManager.activeProfileId.value).edit {
            it[showsKey] = it[showsKey].orEmpty() + contentId
            val shows = it[unwatchedKey].orEmpty()
            it[unwatchedKey] = if (unwatchedOnly) shows + contentId else shows - contentId
        }
    }
}
