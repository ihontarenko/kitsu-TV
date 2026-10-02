package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.jmouse.project.kitsu.tv.data.api.InterfaceLanguage
import org.jmouse.project.kitsu.tv.data.api.PreferenceValue
import org.jmouse.project.kitsu.tv.ui.components.Notices
import org.jmouse.project.kitsu.tv.ui.components.TvChip
import org.jmouse.project.kitsu.tv.ui.components.asNotice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import org.jmouse.project.kitsu.tv.BuildConfig
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.Session
import org.jmouse.project.kitsu.tv.ui.DeckState
import org.jmouse.project.kitsu.tv.ui.components.BadgeTone
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.theme.ControlShape
import org.jmouse.project.kitsu.tv.ui.components.ProfileFace
import org.jmouse.project.kitsu.tv.ui.components.TvBadge
import org.jmouse.project.kitsu.tv.ui.components.TvListRow
import org.jmouse.project.kitsu.tv.ui.components.focusRing
import org.jmouse.project.kitsu.tv.ui.components.rememberDeliberatePress
import org.jmouse.project.kitsu.tv.ui.components.tracksDeliberatePress
import org.jmouse.project.kitsu.tv.ui.components.revealsPageEdges
import org.jmouse.project.kitsu.tv.ui.theme.Accent
import org.jmouse.project.kitsu.tv.ui.theme.AccentStore
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType
import org.jmouse.project.kitsu.tv.ui.theme.chosenAccent


/**
 * Who is watching, on this television, right now — and everything that is done from there.
 *
 * <h2>⚠️ NOT `ProfilesScreen`, AND THE NAMES CARRY THE DIFFERENCE</h2>
 *
 * `ProfilesScreen` is a GATE — it is shown before the library opens and its question is "who is
 * this". This one is a PAGE inside the library and its question is "who am I, and what else can I
 * do from here". The plural is the gate.
 *
 * <h2>⚠️ ONE SCREEN, NOT A SCROLL</h2>
 *
 * It was a tall column — the account, a large face, a column of facts, the accent, and two buttons at
 * the bottom that a remote reached last — and it no longer fitted on a 1080p screen. It is two
 * columns now: WHAT TO DO on the left, as a menu the cursor opens on, and what there is to know on
 * the right. Account › profile stays the hierarchy of the header, the browser's own.
 *
 * <h2>⚠️ ADMINISTRATION IS OFFERED BY THE PROFILE'S RIGHTS, AND REFUSED IN WORDS</h2>
 *
 * A profile without `god:all` or `settings:read` sees the row, greyed, saying why — not a missing
 * row, which would look like a television that has no such thing. The server still decides on every
 * list it is asked for; see [AdministrationScreen].
 */
@Composable
fun YourProfileScreen(
    connection: ServerConnection,
    deck: DeckState,
    session: Session,
    onSwitchProfile: () -> Unit,
    /** The profile's language was changed here — the texts have to be asked for again. */
    onLanguageChanged: () -> Unit,
    onSignOut: (onRefused: (String) -> Unit) -> Unit,
    onServer: () -> Unit,
    onAdministration: () -> Unit,
    onSystemSettings: () -> Unit,
) {
    val first = rememberInitialFocus()
    val mayConfigure = rememberSettingsPermission(connection)
    val profile = session.profile
    val administration = rememberAdministrationPermission(connection)
    val pageScroll = rememberScrollState()

    Screen(deck = deck, current = null) {
        /* ⚠️ STILL SCROLLABLE, though it fits: a TV Surface measured at zero height draws its focus
           border at a negative inset and takes the application down, and a larger font scale is
           all it takes to push the last row off. */
        Column(
            modifier = Modifier
                .fillMaxSize()
                .revealsPageEdges(pageScroll)
                .verticalScroll(pageScroll)
                .padding(horizontal = Kitsu.gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                /*
                  ⚠️ THE SHARED FACE, not a second one drawn here.

                  This page filled a 56dp box with `Kitsu.accent` and put the initials in it, while
                  the rail's `ProfileFace` fills with `Kitsu.face` — and the two colours are kept
                  apart on purpose, because `accent` is what the ACTIVE bay wears and a profile is
                  not a thing that is live. So the same person had two different faces depending on
                  which half of the screen you looked at, and the page's was the one wearing the
                  wrong meaning.

                  ⚠️ 88dp, because this page's subject IS the person. On a rail the face is a marker
                  beside a name; here it is the thing the page is about, and the previous 56 left the
                  header reading as a list item that had wandered up to the top.

                  ⚠️ The face and the name are changed in the browser — choosing a face is a grid of
                  thirty-two and a file picker, which a remote does badly.
                 */
                ProfileFace(initials = deck.profileInitials, picture = deck.profilePicture, size = 88.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = profile?.displayName ?: t("tv.profile.noProfile", "No profile"), style = KitsuType.hero)
                        if (profile?.pinRequired == true) {
                            TvBadge(text = t("tv.profiles.pin", "PIN"), tone = BadgeTone.Muted)
                        }
                    }
                    /*
                      ⚠️ THE NAME FIRST NOW, WITH ITS CONTEXT UNDER IT — it used to be the other way
                      round, an upper-cased eyebrow reading «ACCOUNT · BOSS · OWNER» above the name.

                      The account really is what the profile sits inside, and putting it first was an
                      attempt to draw that. But an eyebrow is a LABEL, and a label holding three
                      proper nouns reads as a heading nobody wrote — while the one question the page
                      answers, who is watching, was the line underneath it. Hierarchy follows the
                      question; the containment is still legible because «Account» names it in
                      words.
                     */
                    Text(
                        text = listOfNotNull(
                            t("tv.profile.account", "Account"),
                            session.account?.displayName ?: t("tv.profile.notSignedIn", "not signed in"),
                            session.account?.login,
                            connection.displayName,
                        ).distinctBy { part -> part.lowercase() }.joinToString(" · "),
                        style = KitsuType.lead.copy(color = Kitsu.foregroundMuted),
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(modifier = Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow(t("tv.profile.fromHere", "From here"), muted = true)
                    PageAction(
                        icon = Icons.Outlined.SwapHoriz,
                        title = t("tv.profile.switch", "Switch profile"),
                        detail = t(
                            "tv.profile.switchDetail",
                            "Back to “Who is watching?” — {profiles} on this account",
                            "profiles" to Counted.profiles(session.profiles.size),
                        ),
                        onClick = onSwitchProfile,
                        modifier = Modifier.focusRequester(first),
                    )
                    PageAction(
                        icon = Icons.Outlined.AdminPanelSettings,
                        title = t("tv.profile.administration", "System administration"),
                        detail = when (administration) {
                            PermissionAnswer.Checking -> t("tv.profile.administrationChecking", "Checking what this profile may see…")
                            PermissionAnswer.Allowed -> t("tv.profile.administrationAllowed", "Reading steps, rules, matchers, providers, access, devices — read only")
                            PermissionAnswer.Refused -> t("tv.profile.administrationRefused", "Not for this profile — it holds no administration rights. Switch to one that does.")
                        },
                        onClick = onAdministration,
                        enabled = administration == PermissionAnswer.Allowed,
                    )
                    /* ⚠️ HIDDEN, NOT DISABLED, for a profile that may not change them — unlike the
                       administration row above, which explains its refusal because reading is what most
                       profiles are asking about. */
                    if (mayConfigure == PermissionAnswer.Allowed) {
                        PageAction(
                            icon = Icons.Outlined.Tune,
                            title = t("tv.profile.systemSettings", "System settings"),
                            detail = t("tv.profile.systemSettingsDetail", "How this installation behaves for everybody"),
                            onClick = onSystemSettings,
                        )
                    }
                    PageAction(
                        icon = Icons.Outlined.Dns,
                        title = t("tv.profile.server", "Server and build"),
                        detail = t(
                            "tv.profile.serverDetail",
                            "{server} · Kitsu {version} · updates",
                            "server" to (connection.displayName ?: connection.address),
                            "version" to "${BuildConfig.VERSION_NUMBER} · ${BuildConfig.CODE_NAME}",
                        ),
                        onClick = onServer,
                    )
                    SignOutAction(onSignOut)
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Eyebrow(t("tv.profile.profilesHere", "Profiles on this account"), muted = true)
                        session.profiles.forEach { each ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = each.displayName,
                                    style = KitsuType.body.copy(
                                        color = if (each.id == profile?.id) Kitsu.foreground else Kitsu.foregroundMuted,
                                        fontWeight = if (each.id == profile?.id) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                )
                                Text(
                                    text = listOfNotNull(
                                        if (each.pinRequired) t("tv.profiles.pin", "PIN") else t("tv.profiles.open", "open"),
                                        t("tv.profile.watchingNow", "watching now").takeIf { each.id == profile?.id },
                                    ).joinToString(" · "),
                                    style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle),
                                )
                            }
                        }
                        Text(
                            text = t("tv.profile.madeInBrowser", "Profiles, names and faces are made and changed in the browser."),
                            style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
                        )
                    }
                    ProfileSwitches(connection = connection)
                    AccentChoice()
                    LanguageChoice(connection = connection, onChanged = onLanguageChanged)
                }
            }
        }
    }
}

/** One thing to do from a page — profile, server — as a menu row: an icon, what it is, and one line of what it will do. */
@Composable
internal fun PageAction(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    TvListRow(onClick = onClick, modifier = modifier.fillMaxWidth(), enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            /*
              ⚠️ `LocalContentColor`, NEVER `Color.Unspecified` — they are opposites here.

              `Color.Unspecified` does not mean "inherit": in Compose it means DO NOT TINT, and the
              vector is then drawn in whatever the asset carries. `Icons.Outlined.*` carry black, so
              every icon on this menu was a black glyph on a near-black card — legible on a monitor a
              foot away and effectively absent on a television across a room.

              The row already publishes the right colour: `TvListRow` sets `contentColor` and a
              focused variant of it, which is exactly what `LocalContentColor.current` reads — so the
              icon now follows the row through focus and the disabled state with nothing to keep in
              step.
             */
            tint = if (danger) Kitsu.danger else LocalContentColor.current,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = KitsuType.body.copy(fontWeight = FontWeight.SemiBold))
            Text(
                text = detail,
                style = KitsuType.caption.copy(color = if (danger) Kitsu.danger else Kitsu.foregroundSubtle),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Signing the account out of this television — pressed twice.
 *
 * ⚠️ TWICE, BECAUSE IT CANNOT BE UNDONE FROM HERE. The television stays paired, but nothing on it
 * can sign back in: bringing it back means removing it on the Devices screen and pairing again. The
 * first press says exactly that and arms the row; moving off it disarms it.
 */
@Composable
private fun SignOutAction(onSignOut: (onRefused: (String) -> Unit) -> Unit) {
    var armed by remember { mutableStateOf(false) }
    var refusal by remember { mutableStateOf<String?>(null) }
    val press = rememberDeliberatePress()
    PageAction(
        icon = Icons.AutoMirrored.Outlined.Logout,
        title = if (armed) t("tv.profile.signOutConfirm", "Press again to sign out") else t("tv.profile.signOut", "Sign out"),
        detail = refusal
            ?: if (armed) {
                t("tv.profile.signOutWarning", "This television will need pairing again — it cannot sign back in by itself.")
            } else {
                t("tv.profile.signOutDetail", "Signs the account out of this television.")
            },
        danger = armed || refusal != null,
        onClick = {
            if (!press.isDeliberate()) {
                return@PageAction
            }
            if (armed) {
                armed = false
                onSignOut { problem -> refusal = t("tv.profile.signOutRefused", "Not signed out — {problem}", "problem" to problem) }
            } else {
                refusal = null
                armed = true
            }
        },
        modifier = Modifier
            .tracksDeliberatePress(press)
            .onFocusChanged { focus ->
                if (!focus.hasFocus) {
                    armed = false
                }
            },
    )
}

/**
 * Picking the hue that marks state.
 *
 * <h2>⚠️ NINE SWATCHES, EACH PAINTED IN ITS OWN COLOUR</h2>
 *
 * The row IS the answer to the only question anybody has here, so it is shown rather than hidden
 * behind a list of nine words. Each swatch carries the colour choosing it produces, which is what
 * stops a label drifting from what it does.
 *
 * <h2>⚠️ THE CHOSEN ONE IS RINGED, NOT ENLARGED OR BRIGHTENED</h2>
 *
 * On a row of nine saturated colours at three metres, "the bigger one" and "the brighter one" are
 * both unreadable — whichever swatch is loudest reads as selected whether it is or not. A ring in
 * the foreground colour is the one mark that means the same thing on all nine.
 *
 * <h2>⚠️ THE SWATCHES DIVIDE THE ROW; THEY ARE NOT A FIXED WIDTH</h2>
 *
 * They were 56dp wide each, which needed 568dp with the gaps. This column is what is left of 960dp
 * after the rail, both gutters, the 460dp column beside it and the space between — about 332dp — so
 * four of the nine were cut off the right edge and could not be reached at all. A weight makes the
 * arithmetic the layout's rather than a number's: nine fit whatever the column turns out to be, and
 * a longer set or a wider font scale cannot put that back.
 */
@Composable
private fun AccentChoice() {
    val context = LocalContext.current
    val current = chosenAccent.value

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(t("tv.profile.accent", "Accent · on this television"), muted = true)
        /* ⚠️ `focusGap`, not a gap of its own: two neighbouring rings each claim outward space, and
           the theme derives that number once. */
        Row(horizontalArrangement = Arrangement.spacedBy(Kitsu.focusGap)) {
            Accent.entries.forEach { accent ->
                AccentSwatch(
                    accent = accent,
                    chosen = accent == current,
                    onChoose = { AccentStore.choose(context, accent) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = t("tv.profile.accentHint", "The colour that marks what is active, focused and filled. The browser offers the same nine."),
            /* ⚠️ THE FALLBACK HERE WAS RIGHT AND THE SCREEN STILL SAID "five" — until 2026-09-28 the
               SERVED translation (`tv.profile.accentHint` in `translations/{en,uk}/tv.json`) carried
               the Harbour-era count, and the served value wins over the literal. A fallback is not
               proof of what a television shows; both have to be corrected, and only one of them is
               in this repository's Kotlin. */
            style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
        )
    }
}

/** The profile preference naming its interface language — blank follows the machine. */
private const val LANGUAGE_PREFERENCE = "interface.language"

/**
 * Which language this profile's screens speak — every language the server has, named in itself, and
 * "Follow the machine".
 *
 * <h2>⚠️ A PREFERENCE OF THE PROFILE, KEPT ON THE SERVER</h2>
 *
 * Not a setting of this television: the same face in the browser speaks the same language, and the
 * texts themselves come from the server either way. Choosing writes the preference and asks for the
 * texts again at once, so the page redraws in the new language under the cursor.
 *
 * <h2>⚠️ "FOLLOW THE MACHINE" IS AN EMPTY VALUE, NOT A LANGUAGE</h2>
 *
 * It clears the preference, so the machine's default — whatever it is changed to later — applies.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LanguageChoice(connection: ServerConnection, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var languages by remember { mutableStateOf<List<InterfaceLanguage>>(emptyList()) }
    /* Null while unknown; "" for following the machine. */
    var chosen by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(connection) {
        languages = runCatching { connection.api.interfaceLanguages() }.getOrDefault(emptyList())
        chosen = runCatching { connection.api.preferences() }
            .getOrNull()
            ?.firstOrNull { preference -> preference.key == LANGUAGE_PREFERENCE }
            ?.let { preference -> if (preference.set) preference.value.orEmpty() else "" }
            ?: ""
    }

    fun choose(code: String) {
        if (code == chosen) {
            return
        }
        scope.launch {
            runCatching { connection.api.setPreference(LANGUAGE_PREFERENCE, PreferenceValue(code)) }
                .onSuccess {
                    chosen = code
                    onChanged()
                }
                .onFailure { failure -> Notices.show(failure.asNotice(t("tv.profile.languageRefused", "The language was not changed"))) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(t("tv.profile.language", "Language · for this profile"), muted = true)
        /* ⚠️ A FLOW, NOT A ROW: the column is about 330dp, and "Follow the machine" with two languages
           named in themselves already fills it — a third went off the right edge with no way to reach it. */
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvChip(
                text = t("tv.profile.languageMachine", "Follow the machine"),
                pressed = chosen == "",
                onClick = { choose("") },
            )
            languages.forEach { language ->
                TvChip(
                    text = language.nativeName ?: language.englishName ?: language.code,
                    pressed = chosen == language.code,
                    onClick = { choose(language.code) },
                )
            }
        }
        Text(
            text = t("tv.profile.languageHint", "The words on this television and in the browser, for this profile. The server keeps the texts."),
            style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
        )
    }
}

@Composable
private fun AccentSwatch(accent: Accent, chosen: Boolean, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onChoose,
        /* ⚠️ The width comes from the caller's weight; only the height is decided here. 26dp is 52px
           on the panel — still a comfortable place for the cursor to land, and enough for the
           12sp letter at a raised font scale. */
        modifier = modifier.height(26.dp),
        shape = ClickableSurfaceDefaults.shape(ControlShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = accent.hue,
            focusedContainerColor = accent.hue,
            /* ⚠️ THE LABEL RIDES ON THE SWATCH, SO ITS INK IS THE PALETTE'S OWN — and that is a
               correction, not a refactor. It used to be `Kitsu.background` for every swatch, on the
               reasoning that "every accent here is light enough that near-white on it is
               unreadable". That was true of the five Harbour offered and is false of seven of the
               nine here: near-black on Kurenai, Fuji, Ao, Mizu, Matcha or Sumi is the unreadable
               half of the pair. `Accent.ink` is per palette exactly because the answer is. */
            contentColor = accent.ink,
            focusedContentColor = accent.ink,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(if (chosen) 2.dp else 0.dp, if (chosen) Kitsu.foreground else Color.Transparent),
                shape = ControlShape,
            ),
            focusedBorder = focusRing(),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = accent.label.take(1), style = KitsuType.caption)
        }
    }
}
