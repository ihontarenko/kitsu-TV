package org.jmouse.project.kitsu.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jmouse.project.kitsu.tv.AppContainer
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.Session
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.text.Texts
import org.jmouse.project.kitsu.tv.text.Translation

/**
 * Which screen the television is allowed to be on. Derived from what it
 * remembers and what the server answers, in this order: no server → connect;
 * a server but no admission → pairing; admitted but no profile → the picker;
 * otherwise the library.
 */
sealed interface Gate {
    data object Loading : Gate
    data class NeedServer(val lastAddress: String?, val problem: String?) : Gate
    data class NeedPairing(val connection: ServerConnection) : Gate
    data class NeedProfile(val connection: ServerConnection, val session: Session) : Gate
    data class Ready(val connection: ServerConnection, val session: Session) : Gate
}

class SessionViewModel(private val container: AppContainer) : ViewModel() {

    private val state = MutableStateFlow<Gate>(Gate.Loading)
    val gate: StateFlow<Gate> = state

    /** Whether a re-check prompted by a lost session is already asking the server. */
    private var rechecking = false

    init {
        resume()
        watchForLostSession()
    }

    /**
     * Sends the television back to the right door when the server stops recognising it.
     *
     * ⚠️ WITHOUT THIS THE GATE ONLY EVER OPENED. It was decided once, at launch; a device revoked, an
     * account signed out or a profile removed afterwards left the library on screen with every
     * request failing inside it. Now any 401 past the gate asks the server again, and the answer
     * picks the layout: not paired → pairing, no account → the signed-out screen, no profile → the
     * picker.
     *
     * ⚠️ ONE RE-CHECK AT A TIME, and only from inside the library. A screen firing six requests that
     * all fail is one lost session, and the gate screens themselves never need rescuing — they are
     * already where a lost session is sent.
     */
    private fun watchForLostSession() {
        viewModelScope.launch {
            container.sessionLost.collect {
                val current = state.value
                if (current !is Gate.Ready || rechecking) {
                    return@collect
                }
                rechecking = true
                try {
                    describe(current.connection)
                } finally {
                    rechecking = false
                }
            }
        }
    }

    /** On launch: pick up where the television left off. */
    fun resume() {
        viewModelScope.launch {
            val remembered = container.store.read()
            val address = remembered.serverAddress
            if (address == null) {
                state.value = Gate.NeedServer(lastAddress = null, problem = null)
                return@launch
            }
            val connection = container.connect(address, remembered.serverName, remembered.deviceToken, remembered.profileId)
            describe(connection)
        }
    }

    /**
     * A server was chosen on the connect screen. The token goes only if it is a different
     * installation — choosing the one this television is already paired with resumes it.
     */
    fun chooseServer(address: String, displayName: String?) {
        viewModelScope.launch {
            state.value = Gate.Loading
            val connection = container.connect(address, displayName, token = null, profileId = null)
            val installation = runCatching { connection.api.installation() }
            installation.onFailure { failure ->
                container.disconnect()
                state.value = Gate.NeedServer(lastAddress = address, problem = failure.describeForScreen())
                return@launch
            }
            val answer = installation.getOrThrow()
            val name = displayName ?: answer.displayName
            val kept = container.store.rememberServer(connection.address, name, answer.id)
            val chosen = container.connect(connection.address, name, kept.deviceToken, kept.profileId)
            if (kept.deviceToken == null) {
                loadTexts(chosen)
                state.value = Gate.NeedPairing(chosen)
            } else {
                describe(chosen)
            }
        }
    }

    /** The pairing screen collected a token: keep it, and ask the server who this device is now. */
    fun admitted(token: String) {
        viewModelScope.launch {
            val connection = container.requireConnection()
            connection.credentials.deviceToken = token
            /* ⚠️ A NEW ADMISSION FORGETS THE REMEMBERED PROFILE. It belonged to whatever this
               television was paired into before — another account, or a database since reset — and
               sent as X-Kitsu-Profile it made the server answer the fresh token as nobody: the
               screen stayed on "This television is in" for good. */
            connection.credentials.profileId = null
            container.store.rememberProfile(null)
            container.store.rememberToken(token)
            describe(connection)
        }
    }

    /** A profile was chosen (and unlocked); every call from here carries it. */
    fun profileChosen(session: Session) {
        viewModelScope.launch {
            val connection = container.requireConnection()
            val profileId = session.profile?.id
            connection.credentials.profileId = profileId
            container.store.rememberProfile(profileId)
            loadTexts(connection)
            state.value = Gate.Ready(connection, session)
        }
    }

    /** Back to the picker — the device stays paired, only the profile is dropped. */
    fun switchProfile() {
        viewModelScope.launch {
            val connection = container.requireConnection()
            connection.credentials.profileId = null
            container.store.rememberProfile(null)
            describe(connection)
        }
    }

    /**
     * Signs the account out of this television.
     *
     * ⚠️ IT STAYS PAIRED, and nothing on it can sign back in — the gate lands on the signed-out
     * screen, whose way back is pairing again. The profile page asks twice before calling this.
     *
     * @param onRefused told the server's reason when it did not sign out; nothing changes then
     */
    fun signOut(onRefused: (String) -> Unit) {
        viewModelScope.launch {
            val connection = container.requireConnection()
            val answer = runCatching { connection.api.signOut() }
            answer.onFailure { failure ->
                onRefused(failure.describeForScreen())
                return@launch
            }
            connection.credentials.profileId = null
            container.store.rememberProfile(null)
            describe(connection)
        }
    }

    /** Forget the server entirely: the token with it. Pairing starts over next time. */
    fun forgetServer() {
        viewModelScope.launch {
            container.store.forgetServer()
            container.disconnect()
            Texts.forget()
            state.value = Gate.NeedServer(lastAddress = null, problem = null)
        }
    }

    /** The chosen profile's language changed: speak the new one without leaving the page. */
    fun reloadTexts() {
        viewModelScope.launch {
            val connection = container.connection.value ?: return@launch
            loadTexts(connection)
        }
    }

    /**
     * Asks the server which language this television speaks now, and its words.
     *
     * ⚠️ ASKED BEFORE A GATE OPENS, not after, so a door is drawn in its language from its first frame
     * rather than flashing English. ⚠️ A failure keeps the words already held: a server that did not
     * answer this one question has not changed its language.
     */
    private suspend fun loadTexts(connection: ServerConnection) {
        runCatching { connection.api.currentTranslation() }
            .onSuccess { answer -> Texts.apply(Translation(answer.language, answer.texts)) }
    }

    private suspend fun describe(connection: ServerConnection) {
        val session = runCatching { connection.api.session() }
        session.onFailure { failure ->
            state.value = Gate.NeedServer(lastAddress = connection.address, problem = failure.describeForScreen())
            return
        }
        val answer = session.getOrThrow()
        /* ⚠️ THE SERVER'S ANSWER IS THE FACE, in both directions. A remembered profile the server did
           not honour — its PIN unlock expired overnight — kept being sent, so the picker's own
           requests went out claiming a face that was locked and were refused with it. */
        val answeredProfileId = answer.profile?.id
        if (connection.credentials.profileId != answeredProfileId) {
            connection.credentials.profileId = answeredProfileId
            container.store.rememberProfile(answeredProfileId)
        }
        loadTexts(connection)
        state.value = when {
            !answer.paired -> Gate.NeedPairing(connection)
            answer.profile == null -> Gate.NeedProfile(connection, answer)
            else -> {
                val current = state.value
                /* ⚠️ A re-check that finds nothing changed keeps the gate it already had. A fresh
                   Ready would recompose the whole library, and the 401 that prompted the question
                   was about one request, not about where the person was standing. */
                if (current is Gate.Ready && current.session.profile?.id == answer.profile.id) {
                    current
                } else {
                    Gate.Ready(connection, answer)
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return SessionViewModel(container) as T
            }
        }
    }
}
