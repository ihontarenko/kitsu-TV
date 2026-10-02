package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import org.jmouse.project.kitsu.tv.ui.components.trapsFocus
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.ChooseProfileRequest
import org.jmouse.project.kitsu.tv.data.api.ProfileSummary
import org.jmouse.project.kitsu.tv.data.api.Session
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.ui.components.ButtonWeight
import org.jmouse.project.kitsu.tv.ui.components.BrandLockup
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.theme.ControlShape
import org.jmouse.project.kitsu.tv.ui.components.TvButton
import org.jmouse.project.kitsu.tv.ui.components.TvKey
import org.jmouse.project.kitsu.tv.ui.components.focusRing
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType
import org.jmouse.project.kitsu.tv.ui.theme.MediaShape
import org.jmouse.project.kitsu.tv.ui.theme.mixOklab
import org.jmouse.project.kitsu.tv.ui.theme.DialogShape

/**
 * The faces: one TONE per profile, chosen from its id so it never changes.
 *
 * <h2>⚠️ A FACE HAS ITS OWN HUE, AND IT IS NEITHER THE ACCENT NOR A GREY</h2>
 *
 * <p>Kitsu gives a face two tokens of its own — {@code Kitsu.face} and {@code Kitsu.faceForeground},
 * a warm brown carrying a pale ink — precisely so a profile is never mistaken for something that is
 * LIVE. A tile in the accent would say "this is the current one" four times over on a screen whose
 * whole question is which one to make current.
 *
 * <h2>⚠️ THIS REVERSES "GREYS, NOT HUES", AND THE OLD REASON IS STILL WORTH KNOWING</h2>
 *
 * <p>The seven steps here were true greys, chosen when the tile had been a saturated teal, violet,
 * orange or pink square four times the size of anything else on the screen. That was the right fix
 * for that problem. Kitsu's answer is different and narrower: ONE hue, shared by every face, with
 * the steps carrying the difference between two profiles. A household tells its faces apart by the
 * INITIALS — which was the true half of the old note — so the ramp only has to keep two tiles from
 * looking identical, not carry an identity of its own.
 *
 * <p>⚠️ Every step keeps {@code Kitsu.faceForeground} readable on it, which is why the ramp is built
 * by mixing towards the ground and the highlight rather than by picking seven browns: a hand-picked
 * ramp cannot promise that without a per-step foreground.
 */
private fun faceColour(profile: ProfileSummary): Color {
    val palette = listOf(
        Kitsu.face,
        mixOklab(Kitsu.face, Kitsu.background, 0.82f),
        mixOklab(Kitsu.face, Kitsu.faceForeground, 0.88f),
        mixOklab(Kitsu.face, Kitsu.background, 0.70f),
        mixOklab(Kitsu.face, Kitsu.faceForeground, 0.78f),
        mixOklab(Kitsu.face, Kitsu.background, 0.90f),
        mixOklab(Kitsu.face, Kitsu.faceForeground, 0.94f),
    )
    return palette[((profile.id.hashCode() % palette.size) + palette.size) % palette.size]
}

/**
 * Who is watching? Profiles as big faces; a closed one asks for a four-digit
 * PIN on a pad — no text field, nothing for the platform keyboard to cover.
 */
@Composable
fun ProfilesScreen(
    connection: ServerConnection,
    session: Session,
    onChosen: (Session) -> Unit,
    onChangeServer: () -> Unit,
) {
    var asking by remember { mutableStateOf<ProfileSummary?>(null) }
    /** Which face opened the PIN pad last — where the cursor returns when it closes. */
    var lastAsked by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun choose(profile: ProfileSummary, pin: String?) {
        scope.launch {
            problem = null
            val answer = runCatching { connection.api.chooseProfile(ChooseProfileRequest(profile.id, pin)) }
            answer.onSuccess { chosen ->
                asking = null
                onChosen(chosen)
            }
            answer.onFailure { failure ->
                problem = failure.describeForScreen()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                /* ⚠️ No radial wash of the accent — see the note in ConnectScreen. */
                .padding(horizontal = Kitsu.gutter, vertical = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BrandLockup()
                Text(text = connection.displayName ?: "Kitsu", style = KitsuType.meta)
            }

            if (session.account == null) {
                SignedOut(onChangeServer)
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Eyebrow(t("tv.profiles.account", "Account"))
                        Text(text = t("tv.profiles.heading", "Who is watching?"), style = KitsuType.h1)
                        /* ⚠️ The browser's sentence, so the two pickers say the same thing: the ACCOUNT is
                           signed in, and a profile is chosen inside it — not a second sign-in. */
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = t("tv.profiles.signedInAs", "Signed in as"), style = KitsuType.body.copy(color = Kitsu.foregroundMuted))
                            Text(
                                text = session.account.displayName ?: session.account.login ?: t("tv.profiles.thisAccount", "this account"),
                                style = KitsuType.body.copy(color = Kitsu.foreground),
                            )
                        }
                    }
                    /* ⚠️ One requester per face, so closing the PIN pad puts the cursor back on the face
                       that opened it rather than on the first one. */
                    val faces = remember(session.profiles.size) { List(session.profiles.size) { FocusRequester() } }
                    LaunchedEffect(asking) {
                        if (asking == null) {
                            faces.getOrNull(lastAsked)?.let { face -> runCatching { face.requestFocus() } }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        session.profiles.forEachIndexed { index, profile ->
                            ProfileFace(
                                profile = profile,
                                picture = connection.avatarUrl(profile.avatarFileId),
                                modifier = Modifier.focusRequester(faces[index]),
                                onClick = {
                                    if (profile.pinRequired) {
                                        problem = null
                                        lastAsked = index
                                        asking = profile
                                    } else {
                                        choose(profile, null)
                                    }
                                },
                            )
                        }
                    }
                    if (session.profiles.isEmpty()) {
                        Text(text = t("tv.profiles.none", "No profiles yet — make one on the server."), style = KitsuType.lead)
                    }
                    Text(
                        text = t("tv.profiles.hint", "Profiles are made on the server. This television remembers the last one chosen."),
                        style = KitsuType.body.copy(color = Kitsu.foregroundSubtle),
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TvButton(onClick = onChangeServer, text = t("tv.profiles.changeServer", "Change server"), weight = ButtonWeight.Quiet)
            }
        }

        val target = asking
        if (target != null) {
            PinDialog(
                profile = target,
                problem = problem,
                onCancel = { asking = null },
                onSubmit = { pin -> choose(target, pin) },
            )
        }
    }
}

@Composable
private fun SignedOut(onChangeServer: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = t("tv.profiles.signedOut", "This television is paired, but signed out."), style = KitsuType.h1)
        Text(text = t("tv.profiles.signedOutLead", "It was signed out — from its own profile page, or on the server. Remove it from the Devices screen and pair again to bring it back."), style = KitsuType.lead)
        TvButton(onClick = onChangeServer, text = t("tv.profiles.pairAgain", "Pair again"), weight = ButtonWeight.Primary)
    }
}

@Composable
private fun ProfileFace(profile: ProfileSummary, picture: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colour = faceColour(profile)
    Column(modifier = modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(112.dp),
            shape = ClickableSurfaceDefaults.shape(MediaShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = colour,
                focusedContainerColor = colour,
                contentColor = Kitsu.faceForeground,
                focusedContentColor = Kitsu.faceForeground,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(0.dp, Color.Transparent), shape = MediaShape),
                focusedBorder = focusRing(shape = MediaShape),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = Kitsu.focusLift),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (picture != null) {
                    AsyncImage(
                        model = picture,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = profile.initials?.takeIf { it.isNotBlank() } ?: profile.displayName.take(1).uppercase(),
                        style = KitsuType.hero,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                if (profile.pinRequired) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .background(Kitsu.scrim),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = Icons.Outlined.Lock, contentDescription = t("tv.profiles.asksPin", "Asks for a PIN"), tint = Kitsu.foreground, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        Text(text = profile.displayName, style = KitsuType.lead.copy(color = Kitsu.foregroundMuted), textAlign = TextAlign.Center)
        Text(text = if (profile.pinRequired) t("tv.profiles.pin", "PIN") else t("tv.profiles.open", "open"), style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle))
    }
}

/**
 * The PIN: four boxes, a number pad. BACK cancels. The digits are also taken from
 * a remote that has them, so typing on a keyboard remote skips the pad.
 */
@Composable
private fun PinDialog(profile: ProfileSummary, problem: String?, onCancel: () -> Unit, onSubmit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { first.requestFocus() }
    }
    LaunchedEffect(pin) {
        if (pin.length == PIN_LENGTH) {
            onSubmit(pin)
            pin = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Kitsu.scrim)
            .trapsFocus()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        onCancel()
                        true
                    }
                    else -> {
                        val digit = event.key.toDigit()
                        if (digit != null && pin.length < PIN_LENGTH) {
                            pin += digit
                            true
                        } else {
                            false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        /* ⚠️ ONE GRID. The four boxes and the twelve keys share a width — four boxes and their gaps
           measure exactly three keys and theirs — so both sit on one centre line, and every key,
           clear and delete included, is the same square in the same rows. */
        Column(
            modifier = Modifier
                .background(Kitsu.backgroundOverlay, DialogShape)
                .border(1.dp, Kitsu.borderStrong, DialogShape)
                .padding(PIN_DIALOG_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PIN_RHYTHM),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(28.dp).background(faceColour(profile)), contentAlignment = Alignment.Center) {
                    Text(text = profile.displayName.take(1).uppercase(), style = KitsuType.caption.copy(color = Kitsu.background))
                }
                Text(text = profile.displayName, style = KitsuType.lead.copy(color = Kitsu.foreground))
            }
            /* ⚠️ The refusal sits under the question it answers, and only when there is one — a line
               kept empty for it left a gap twice the dialog's rhythm between the boxes and the keys. */
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = t("tv.profiles.enterPin", "Enter the PIN"), style = KitsuType.h2)
                problem?.let { refusal ->
                    Text(
                        text = refusal,
                        style = KitsuType.caption.copy(color = Kitsu.danger),
                        modifier = Modifier.widthIn(max = PIN_KEY * 3 + PIN_GAP * 2),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(PIN_GAP)) {
                repeat(PIN_LENGTH) { index ->
                    Box(
                        modifier = Modifier
                            .width(PIN_BOX_WIDTH)
                            .height(PIN_BOX_HEIGHT)
                            .border(1.dp, if (index == pin.length) Kitsu.accentText else Kitsu.borderStrong),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index < pin.length) {
                            Text(text = "•", style = KitsuType.h1)
                        }
                    }
                }
            }

            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
            Column(verticalArrangement = Arrangement.spacedBy(PIN_GAP)) {
                rows.forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(PIN_GAP)) {
                        row.forEachIndexed { keyIndex, key ->
                            TvKey(
                                label = key,
                                onClick = { if (pin.length < PIN_LENGTH) pin += key },
                                modifier = if (rowIndex == 0 && keyIndex == 0) Modifier.focusRequester(first) else Modifier,
                                size = PIN_KEY,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(PIN_GAP)) {
                    TvKey(icon = Icons.Outlined.Close, contentDescription = t("tv.common.cancel", "Cancel"), onClick = onCancel, size = PIN_KEY)
                    TvKey(label = "0", onClick = { if (pin.length < PIN_LENGTH) pin += "0" }, size = PIN_KEY)
                    TvKey(
                        icon = Icons.AutoMirrored.Outlined.Backspace,
                        contentDescription = t("tv.keyboard.deleteDigit", "Delete last digit"),
                        onClick = { pin = pin.dropLast(1) },
                        size = PIN_KEY,
                    )
                }
            }
        }
    }
}

private const val PIN_LENGTH = 4

/** One key of the pad — every one of the twelve, digits and actions alike. */
private val PIN_KEY = 56.dp

/** Between keys, between boxes: one gap for the whole grid. */
private val PIN_GAP = 8.dp

/** Four boxes and three gaps measure exactly three keys and two gaps (4 × 40 + 3 × 8 = 3 × 56 + 2 × 8). */
private val PIN_BOX_WIDTH = (PIN_KEY * 3 + PIN_GAP * 2 - PIN_GAP * (PIN_LENGTH - 1)) / PIN_LENGTH
private val PIN_BOX_HEIGHT = 48.dp

/** The space between the dialog's groups, and around them — one measure, so the rhythm is even. */
private val PIN_RHYTHM = 16.dp
private val PIN_DIALOG_PADDING = 24.dp

private fun Key.toDigit(): String? = when (this) {
    Key.Zero, Key.NumPad0 -> "0"
    Key.One, Key.NumPad1 -> "1"
    Key.Two, Key.NumPad2 -> "2"
    Key.Three, Key.NumPad3 -> "3"
    Key.Four, Key.NumPad4 -> "4"
    Key.Five, Key.NumPad5 -> "5"
    Key.Six, Key.NumPad6 -> "6"
    Key.Seven, Key.NumPad7 -> "7"
    Key.Eight, Key.NumPad8 -> "8"
    Key.Nine, Key.NumPad9 -> "9"
    else -> null
}
