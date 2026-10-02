package org.jmouse.project.kitsu.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.PreferenceAnswer
import org.jmouse.project.kitsu.tv.data.api.PreferenceValue
import org.jmouse.project.kitsu.tv.data.api.SettingAnswer
import org.jmouse.project.kitsu.tv.text.t
import org.jmouse.project.kitsu.tv.ui.DeckState
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.components.Notices
import org.jmouse.project.kitsu.tv.ui.components.TvChip
import org.jmouse.project.kitsu.tv.ui.components.asNotice
import org.jmouse.project.kitsu.tv.ui.components.revealsPageEdges
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType

/* ==========================================================================
   Settings on the television — the profile's own, and the installation's.

   ⚠️ THE SERVER DECLARES THEM; THIS DRAWS WHAT IT IS GIVEN. Every preference and
   setting arrives with its type, its bounds and its choices, so a key added on the
   server appears here without a release — the same bargain the browser's settings
   screen makes. What a remote does badly (free text, secrets) is shown and left
   to the browser.

   ⚠️ THE WORDS FOLLOW THE KEY. A setting's own description is English and fixed
   at the server; `tv.setting.<key>` is looked up first, with that description as
   the fallback, so a translation is a text rather than a code change.
   ========================================================================== */

/** The playback preferences the television acts on, first — MH-288 made autoplay one of them. */
const val AUTOPLAY_NEXT_PREFERENCE = "playback.autoplayNext"

/** How far through an episode the next-episode card appears, as a percent. 100 waits for the end. */
const val UP_NEXT_AT_PERCENT_PREFERENCE = "playback.upNextAtPercent"
const val UP_NEXT_AT_PERCENT_DEFAULT = 95

/**
 * The playback preferences of the chosen profile — flags as switches, and the up-next percent as a
 * stepper.
 *
 * ⚠️ FLAGS AND NUMBERS. It was flags only, which quietly hid {@code playback.upNextAtPercent} — a
 * number the profile owns and could set nowhere, on the television or the browser. The languages still
 * have their own chooser on the page; those are not read here.
 */
@Composable
fun ProfileSwitches(connection: ServerConnection) {
    val scope = rememberCoroutineScope()
    var preferences by remember(connection) { mutableStateOf<List<PreferenceAnswer>>(emptyList()) }

    LaunchedEffect(connection) {
        preferences = runCatching { connection.api.preferences() }
            .getOrDefault(emptyList())
            .filter { preference -> preference.type == "FLAG" || preference.type == "NUMBER" }
            .sortedBy { preference -> if (preference.key == AUTOPLAY_NEXT_PREFERENCE) 0 else 1 }
    }

    if (preferences.isEmpty()) {
        return
    }

    fun write(preference: PreferenceAnswer, value: String) {
        scope.launch {
            runCatching { connection.api.setPreference(preference.key, PreferenceValue(value)) }
                .onSuccess { answer ->
                    preferences = preferences.map { each -> if (each.key == preference.key) answer.copy(type = each.type, description = each.description) else each }
                }
                .onFailure { failure -> Notices.show(failure.asNotice(t("tv.settings.notChanged", "That was not changed"))) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow(t("tv.profile.preferences", "Playback · for this profile"), muted = true)
        preferences.forEach { preference ->
            val label = t("tv.setting.${preference.key}", preference.description ?: preference.key)
            when (preference.type) {
                "NUMBER" -> ProfilePercentRow(
                    label = label,
                    value = (preference.value ?: preference.defaultTo)?.toLongOrNull() ?: UP_NEXT_AT_PERCENT_DEFAULT.toLong(),
                    onWrite = { chosen -> write(preference, chosen.toString()) },
                )
                else -> FlagRow(
                    label = label,
                    on = (preference.value ?: preference.defaultTo) == "true",
                    onChoose = { chosen -> write(preference, chosen.toString()) },
                )
            }
        }
    }
}

/**
 * A percent the profile steps with the remote — no keyboard worth the name on a television.
 *
 * ⚠️ Bounds are the up-next setting's own (50–100 by fives); {@link PreferenceAnswer} carries no range,
 * and this is the one number preference a profile has. 100 reads as "at the very end".
 */
@Composable
private fun ProfilePercentRow(label: String, value: Long, onWrite: (Long) -> Unit) {
    val low = 50L
    val high = 100L
    val step = 5L
    var shown by remember(label, value) { mutableStateOf(value.coerceIn(low, high)) }

    fun move(up: Boolean) {
        val next = (if (up) shown + step else shown - step).coerceIn(low, high)
        if (next != shown) {
            shown = next
            onWrite(next)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowLabel(label)
        TvChip(text = "−", onClick = { move(up = false) })
        Text(
            text = if (shown >= high) t("tv.setting.upNextAtEnd", "at the end") else "$shown %",
            style = KitsuType.body.copy(color = Kitsu.foreground),
        )
        TvChip(text = "+", onClick = { move(up = true) })
    }
}

/**
 * The installation's settings — for a profile that may change them.
 *
 * ⚠️ THE ENTRY IS HIDDEN FOR ANYONE ELSE (see the profile page), and the server refuses the write
 * regardless: this screen is drawn from `settings:write` asked through jmp, never trusted for it.
 */
@Composable
fun SystemSettingsScreen(connection: ServerConnection, deck: DeckState) {
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableIntStateOf(0) }
    var state by rememberLoaded<List<SettingAnswer>>()
    val first = rememberInitialFocus(state is Loaded.Ready)
    val pageScroll = rememberScrollState()

    LaunchedEffect(connection, attempt) {
        state = Loaded.Pending
        state = runCatching { connection.api.systemSettings() + connection.api.applicationSettings() }
            .fold({ Loaded.Ready(it) }, { Loaded.Failed(it.describeForScreen()) })
    }

    fun write(setting: SettingAnswer, value: String) {
        scope.launch {
            runCatching { connection.api.setInstallationWide(setting.key, PreferenceValue(value)) }
                .onSuccess { answer ->
                    val current = (state as? Loaded.Ready)?.value ?: return@onSuccess
                    state = Loaded.Ready(current.map { each -> if (each.key == answer.key) answer else each })
                }
                .onFailure { failure -> Notices.show(failure.asNotice(t("tv.settings.notChanged", "That was not changed"))) }
        }
    }

    Screen(deck = deck, current = null) {
        when (val current = state) {
            Loaded.Pending -> Loading()
            is Loaded.Failed -> Failed(current.problem) { attempt++ }
            is Loaded.Ready -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .revealsPageEdges(pageScroll)
                    .verticalScroll(pageScroll)
                    .padding(horizontal = Kitsu.gutter, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = t("tv.settings.title", "System settings"), style = KitsuType.hero)
                    Text(
                        text = t("tv.settings.lead", "How this installation behaves for everybody. Texts and secrets are changed in the browser."),
                        style = KitsuType.lead.copy(color = Kitsu.foregroundMuted),
                    )
                }
                var focusGiven = false
                current.value.groupBy { setting -> setting.category }.forEach { (category, settings) ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Eyebrow(t("tv.settings.category.$category", category), muted = true)
                        settings.forEach { setting ->
                            val takesFocus = !focusGiven && setting.editableOnTelevision()
                            if (takesFocus) {
                                focusGiven = true
                            }
                            SettingRow(
                                setting = setting,
                                onWrite = { value -> write(setting, value) },
                                modifier = if (takesFocus) Modifier.focusRequester(first) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One setting, drawn by its type. */
@Composable
private fun SettingRow(setting: SettingAnswer, onWrite: (String) -> Unit, modifier: Modifier = Modifier) {
    val label = t("tv.setting.${setting.key}", setting.description ?: setting.key)
    val value = setting.value ?: setting.defaultTo

    when {
        setting.type == "FLAG" -> FlagRow(label, value == "true", { on -> onWrite(on.toString()) }, modifier)

        setting.options.isNotEmpty() -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RowLabel(label)
            setting.options.forEachIndexed { index, option ->
                TvChip(
                    text = t("tv.settings.option.${setting.key}.$option", option),
                    pressed = option == value,
                    onClick = { onWrite(option) },
                    modifier = if (index == 0) modifier else Modifier,
                )
            }
        }

        /* ⚠️ A NUMBER STEPS, it is not typed: a remote has no keyboard worth the name.
           ⚠️ BY A ROUND STEP, ON A ROUND GRID, FROM WHAT IS ON SCREEN. The step used to be a twentieth
           of the declared range — +2 here, +29 there — and each press counted from the last value the
           server had answered, so quick presses were lost and the figure seemed to move at random.
           Now: a step from 1-2-5-10-15-30-60-…, a press lands on the next multiple of it, and the
           figure moves the moment the button is pressed. */
        setting.type == "NUMBER" && value?.toLongOrNull() != null -> {
            val low = setting.minimum ?: 0L
            val high = setting.maximum ?: Long.MAX_VALUE
            val step = roundStepFor(setting.minimum, setting.maximum)
            var shown by remember(setting.key, value) { mutableStateOf(value.toLong()) }

            fun move(up: Boolean) {
                val next = if (up) {
                    Math.floorDiv(shown, step) * step + step
                } else {
                    Math.floorDiv(shown + step - 1, step) * step - step
                }.coerceIn(low, high)
                if (next != shown) {
                    shown = next
                    onWrite(next.toString())
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RowLabel(label)
                TvChip(text = "−", onClick = { move(up = false) }, modifier = modifier)
                Text(
                    text = listOfNotNull(shown.toString(), setting.unit?.let { unit -> t("tv.settings.unit.$unit", unit) }).joinToString(" "),
                    style = KitsuType.body.copy(color = Kitsu.foreground),
                    modifier = Modifier.width(96.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                TvChip(text = "+", onClick = { move(up = true) })
            }
        }

        /* ⚠️ Shown, not editable: free text and secrets belong to the browser. A secret shows that it
           is set and never what it is. */
        else -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RowLabel(label)
            Text(
                text = when {
                    setting.secret -> t("tv.settings.secret", "set in the browser")
                    value.isNullOrBlank() -> "—"
                    else -> value
                },
                style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle),
            )
        }
    }
}

/** On and Off, side by side — the chosen one pressed. */
@Composable
private fun FlagRow(label: String, on: Boolean, onChoose: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RowLabel(label)
        TvChip(text = t("tv.settings.on", "On"), pressed = on, onClick = { onChoose(true) }, modifier = modifier)
        TvChip(text = t("tv.settings.off", "Off"), pressed = !on, onClick = { onChoose(false) })
    }
}

/*
  ⚠️ THE REST OF THE ROW, CAPPED AT 520dp — NOT A FIXED 520dp.

  A fixed width was right on System settings, where the page is wide and the cap is what lines the
  controls up into a column. Then the same row went onto the profile page, into a column of about
  330dp, and a 520dp label pushed On and Off past the screen's right edge. Taking what the controls
  leave, up to the cap, keeps the column on the wide page and the controls on screen in the narrow one.
 */
@Composable
private fun RowScope.RowLabel(text: String) {
    Text(
        text = text,
        style = KitsuType.body.copy(color = Kitsu.foregroundMuted),
        modifier = Modifier.widthIn(max = 520.dp).weight(1f),
    )
}

/** Whether a remote can change this one — anything but free text and secrets. */
private fun SettingAnswer.editableOnTelevision(): Boolean =
    !secret && (type == "FLAG" || options.isNotEmpty() || (type == "NUMBER" && (value ?: defaultTo)?.toLongOrNull() != null))

/**
 * The step a number moves by: the first round figure at least a twentieth of its range.
 *
 * ⚠️ ROUND FIGURES ONLY, so the values a remote can reach are the ones a person would type — 15, 30,
 * 45 seconds, not 29, 58, 87. Without a declared range a number moves by one.
 */
private fun roundStepFor(minimum: Long?, maximum: Long?): Long {
    if (minimum == null || maximum == null || maximum <= minimum) {
        return 1
    }
    val wanted = (maximum - minimum) / 20.0
    return ROUND_STEPS.firstOrNull { step -> step >= wanted } ?: ROUND_STEPS.last()
}

private val ROUND_STEPS = listOf(1L, 2L, 5L, 10L, 15L, 30L, 60L, 100L, 250L, 500L, 1000L, 2500L, 5000L)
