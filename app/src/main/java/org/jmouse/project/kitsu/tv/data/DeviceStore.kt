package org.jmouse.project.kitsu.tv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "kitsu-device")

/**
 * What this television remembers between launches: where the server is, the
 * token it was admitted with, and which profile was chosen last.
 *
 * ⚠️ The token is the one credential the device has, handed over exactly once
 * at pairing (see `PairingController`). Losing it means pairing again, so it is
 * written the moment it arrives and cleared only on an explicit "forget".
 */
class DeviceStore(private val context: Context) {

    data class Remembered(
        val serverAddress: String?,
        val serverName: String?,
        val deviceToken: String?,
        val profileId: String?,
    )

    val remembered: Flow<Remembered> = context.deviceDataStore.data.map(::rememberedFrom)

    suspend fun read(): Remembered = remembered.first()

    /**
     * Remembers the server chosen on the connect screen, and answers what is still held for it.
     *
     * ⚠️ THE TOKEN SURVIVES CHOOSING THE SAME SERVER AGAIN. It used to be dropped on every choice, and
     * the connect screen is exactly where a television lands when the server did not answer at launch
     * — so picking the server it already knew paired it again, and the Devices screen gained a row
     * every time the PC had been asleep. A different installation is still a different pairing.
     *
     * ⚠️ Installations are compared by id, since an address moves with the router; the address is the
     * fallback only for a television paired before the id was remembered.
     */
    suspend fun rememberServer(address: String, name: String?, installationId: String): Remembered {
        val stored = context.deviceDataStore.edit { preferences ->
            val knownInstallation = preferences[INSTALLATION_ID]
            val sameInstallation = if (knownInstallation != null) {
                knownInstallation == installationId
            } else {
                preferences[SERVER_ADDRESS] == address
            }
            preferences[SERVER_ADDRESS] = address
            preferences[INSTALLATION_ID] = installationId
            if (name != null) {
                preferences[SERVER_NAME] = name
            } else {
                preferences.remove(SERVER_NAME)
            }
            if (!sameInstallation) {
                preferences.remove(DEVICE_TOKEN)
                preferences.remove(PROFILE_ID)
            }
        }
        return rememberedFrom(stored)
    }

    private fun rememberedFrom(preferences: Preferences): Remembered = Remembered(
        serverAddress = preferences[SERVER_ADDRESS],
        serverName = preferences[SERVER_NAME],
        deviceToken = preferences[DEVICE_TOKEN],
        profileId = preferences[PROFILE_ID],
    )

    suspend fun rememberToken(token: String) {
        context.deviceDataStore.edit { preferences ->
            preferences[DEVICE_TOKEN] = token
        }
    }

    suspend fun rememberProfile(profileId: String?) {
        context.deviceDataStore.edit { preferences ->
            if (profileId != null) {
                preferences[PROFILE_ID] = profileId
            } else {
                preferences.remove(PROFILE_ID)
            }
        }
    }

    suspend fun forgetServer() {
        context.deviceDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private companion object {
        val SERVER_ADDRESS = stringPreferencesKey("server-address")
        val SERVER_NAME = stringPreferencesKey("server-name")
        val INSTALLATION_ID = stringPreferencesKey("installation-id")
        val DEVICE_TOKEN = stringPreferencesKey("device-token")
        val PROFILE_ID = stringPreferencesKey("profile-id")
    }
}
