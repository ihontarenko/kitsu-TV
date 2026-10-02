package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.StartPairingRequest
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.ui.components.ButtonWeight
import org.jmouse.project.kitsu.tv.ui.components.BrandLockup
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.components.ProgressRule
import org.jmouse.project.kitsu.tv.ui.components.TvButton
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType
import org.jmouse.project.kitsu.tv.ui.theme.ChipShape
import org.jmouse.project.kitsu.tv.ui.theme.SurfaceShape

private sealed interface PairingState {
    data object Starting : PairingState
    data class Waiting(val pairingId: String, val code: String, val startedAt: Long) : PairingState
    data object Admitted : PairingState
    data object Expired : PairingState
    data class Failed(val reason: String) : PairingState
}

/** Codes last five minutes on the server; the bar counts the same five. */
private const val CODE_LIFETIME_MILLIS = 5 * 60 * 1000L

/**
 * Pairing: the code is the screen. It is typed on the server — Kitsu → Server →
 * Devices → Admit a device — and this screen polls until somebody does.
 */
@Composable
fun PairingScreen(
    connection: ServerConnection,
    onAdmitted: (token: String) -> Unit,
    onChangeServer: () -> Unit,
) {
    var state by remember { mutableStateOf<PairingState>(PairingState.Starting) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        state = PairingState.Starting
        val started = runCatching {
            connection.api.startPairing(
                StartPairingRequest(
                    displayName = deviceName(),
                    kind = "ANDROID_TV",
                    directPlayProfile = "h264,hevc,aac,ac3,eac3,mkv,mp4",
                ),
            )
        }
        val response = started.getOrElse { failure ->
            state = PairingState.Failed(failure.describeForScreen())
            return@LaunchedEffect
        }
        state = PairingState.Waiting(response.pairingId, response.code, System.currentTimeMillis())

        while (true) {
            delay(2000)
            val answer = runCatching { connection.api.pairingState(response.pairingId) }.getOrNull() ?: continue
            when (answer.state) {
                "ADMITTED" -> {
                    val token = answer.token
                    if (token != null) {
                        state = PairingState.Admitted
                        delay(900)
                        onAdmitted(token)
                    }
                    return@LaunchedEffect
                }
                "EXPIRED", "COLLECTED" -> {
                    state = PairingState.Expired
                    return@LaunchedEffect
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            /* ⚠️ No radial wash of the accent — see the note in ConnectScreen. */
            .padding(horizontal = Kitsu.gutter, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BrandLockup()
            Text(
                text = (connection.displayName ?: "Kitsu") + " · " + connection.address.removePrefix("http://").removeSuffix("/"),
                style = KitsuType.meta,
            )
        }

        when (val current = state) {
            PairingState.Starting -> Text(text = t("tv.pairing.asking", "Asking the server for a code…"), style = KitsuType.lead)

            is PairingState.Waiting -> Waiting(current)

            PairingState.Admitted -> Admitted(connection)

            PairingState.Expired -> Expired(onNewCode = { attempt++ }, onChangeServer = onChangeServer)

            is PairingState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = t("tv.pairing.refused", "The server refused to start pairing."), style = KitsuType.h1)
                Text(text = current.reason, style = KitsuType.lead.copy(color = Kitsu.danger))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvButton(onClick = { attempt++ }, text = t("tv.common.tryAgain", "Try again"), weight = ButtonWeight.Primary)
                    TvButton(onClick = onChangeServer, text = t("tv.pairing.otherServer", "Choose another server"), weight = ButtonWeight.Quiet)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (state is PairingState.Waiting) {
                TvButton(onClick = onChangeServer, text = t("tv.pairing.otherServer", "Choose another server"), weight = ButtonWeight.Quiet)
            } else {
                Box {}
            }
            Text(text = t("tv.pairing.device", "Device · {name}", "name" to deviceName()), style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle))
        }
    }
}

@Composable
private fun Waiting(state: PairingState.Waiting) {
    var elapsed by remember { mutableStateOf(0L) }
    LaunchedEffect(state.pairingId) {
        while (true) {
            elapsed = System.currentTimeMillis() - state.startedAt
            delay(1000)
        }
    }
    val remaining = (CODE_LIFETIME_MILLIS - elapsed).coerceAtLeast(0)

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Eyebrow(t("tv.pairing.code", "Pairing code"))
            Text(text = state.code.chunked(3).joinToString(" "), style = KitsuType.code)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProgressRule(fraction = remaining.toFloat() / CODE_LIFETIME_MILLIS, modifier = Modifier.width(160.dp))
                Text(text = t("tv.pairing.validFor", "Valid for {time}", "time" to formatClock(remaining)), style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle))
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = t("tv.pairing.heading", "Let this television in"), style = KitsuType.h1)
            Step(1, t("tv.pairing.step1", "On the server, open Kitsu → Administration → Devices."))
            Step(2, t("tv.pairing.step2", "Choose Admit a device and type the six digits."))
            Step(3, t("tv.pairing.step3", "This screen continues on its own."))
            Text(text = t("tv.pairing.waiting", "Waiting for the server to confirm…"), style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle))
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(20.dp).border(1.dp, Kitsu.borderStrong, ChipShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number.toString(), style = KitsuType.meta.copy(color = Kitsu.accentText))
        }
        Text(text = text, style = KitsuType.body.copy(color = Kitsu.foregroundMuted))
    }
}

@Composable
private fun Admitted(connection: ServerConnection) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(48.dp).background(Kitsu.okBackground, SurfaceShape).border(1.dp, Kitsu.ok, SurfaceShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Outlined.Check, contentDescription = null, tint = Kitsu.ok, modifier = Modifier.size(28.dp))
        }
        Eyebrow(t("tv.pairing.admitted", "Admitted"))
        Text(text = t("tv.pairing.admittedHeading", "This television is in."), style = KitsuType.h1)
        Text(
            text = connection.displayName
                ?.let { server -> t("tv.pairing.pairedWith", "Paired with {server}. It stays paired until it is removed on the Server screen.", "server" to server) }
                ?: t("tv.pairing.pairedWithServer", "Paired with the server. It stays paired until it is removed on the Server screen."),
            style = KitsuType.lead,
        )
    }
}

@Composable
private fun Expired(onNewCode: () -> Unit, onChangeServer: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow(t("tv.pairing.expired", "Code expired"))
        Text(text = t("tv.pairing.expiredHeading", "Nobody confirmed in time."), style = KitsuType.h1)
        Text(text = t("tv.pairing.expiredLead", "A new code takes a second. The PC has not been told anything about this television yet."), style = KitsuType.lead)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvButton(onClick = onNewCode, text = t("tv.pairing.newCode", "New code"), weight = ButtonWeight.Primary)
            TvButton(onClick = onChangeServer, text = t("tv.pairing.otherServer", "Choose another server"), weight = ButtonWeight.Quiet)
        }
    }
}

private fun deviceName(): String {
    val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android TV"
    val maker = Build.MANUFACTURER?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
    return if (maker != null && !model.startsWith(maker, ignoreCase = true)) "$maker $model" else model
}

fun formatClock(millis: Long): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
