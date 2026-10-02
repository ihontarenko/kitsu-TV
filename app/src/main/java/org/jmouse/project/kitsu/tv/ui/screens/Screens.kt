package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import org.jmouse.project.kitsu.tv.text.plural
import org.jmouse.project.kitsu.tv.text.formatDecimal
import org.jmouse.project.kitsu.tv.ui.components.NoticeHost
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import org.jmouse.project.kitsu.tv.data.api.TitleCard
import org.jmouse.project.kitsu.tv.ui.DeckState
import org.jmouse.project.kitsu.tv.ui.components.Bay
import org.jmouse.project.kitsu.tv.ui.components.BusySpinner
import org.jmouse.project.kitsu.tv.ui.components.ButtonWeight
import org.jmouse.project.kitsu.tv.ui.components.LibraryRail
import org.jmouse.project.kitsu.tv.ui.components.RailCollapsedWidth
import androidx.compose.ui.ExperimentalComposeUiApi
import org.jmouse.project.kitsu.tv.ui.components.LaneScrolling
import org.jmouse.project.kitsu.tv.ui.components.TvButton
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType

/*
  What every library screen shares: the navigation rail down the left, the page beside it,
  and the three states a page can be in before it has anything to show.
 */

/**
 * A page's loading state: the mark, and one line under it.
 *
 * ⚠️ IT WAS A SENTENCE ALONE, AND THAT IS WHAT THIS FIXES. Eleven places wait through this — every
 * page between one screen and the next, and the player while it asks the server for a file — and on
 * a television that is several seconds of a black panel with one grey line of mono on it. Nothing
 * said the application was still there, which on a ten-foot screen reads as a crash rather than as
 * a wait.
 *
 * ⚠️ THE SAME MARK THE REST OF THE CLIENT WAITS WITH, at the size a route uses. A second waiting
 * shape for pages would be the one thing this client already refused when it stopped borrowing a
 * turning arc.
 */
@Composable
fun Loading(what: String = t("tv.common.loading", "Loading…")) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            BusySpinner(size = 44.dp)
            Text(text = what, style = KitsuType.meta)
        }
    }
}

/** A page's failure: what went wrong, and one button to ask again. */
@Composable
fun Failed(problem: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Kitsu.gutter),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        Text(text = t("tv.common.noAnswer", "The server did not answer."), style = KitsuType.h1)
        Text(text = problem, style = KitsuType.lead.copy(color = Kitsu.danger))
        TvButton(onClick = onRetry, text = t("tv.common.tryAgain", "Try again"), weight = ButtonWeight.Primary)
    }
}

/**
 * The deck plus the page.
 *
 * <h2>⚠️ `backdrop` IS THE GROUND, NOT AN ILLUSTRATION</h2>
 *
 * A picture here sits behind the entire page — deck included — at low strength under a heavy scrim,
 * so it colours the room rather than competing with anything on it. It was first tried INSIDE the
 * genre buttons, which is where it was reported as wrong: a picture in a control makes the control
 * the picture's frame, so nine genres became nine little posters and the one thing they are for —
 * being chosen between — got harder.
 *
 * <p>⚠️ IT CROSS-FADES. Moving along a row of genres changes the backdrop on every press, and an
 * instant cut at that rate reads as the screen flickering. 320ms is slow enough to feel like the
 * room changing and fast enough not to lag the cursor.
 *
 * <p>⚠️ THE SCRIM IS NOT OPTIONAL and does not depend on the picture. Somebody uploads whatever
 * they like; the interface has to stay legible over a white photograph as well as a dark one.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Screen(
    deck: DeckState,
    current: Bay?,
    backdrop: String? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Kitsu.background)) {
        Crossfade(targetState = backdrop, animationSpec = tween(320), label = "backdrop") { picture ->
            if (picture != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = picture,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        /* ⚠️ Raised from 0.5: the picture is the one thing on this screen that says WHICH film it is,
                           and at half strength it read as a dark smear. The washes below carry legibility. */
                        modifier = Modifier.fillMaxSize().alpha(0.78f),
                    )
                    /* ⚠️ Two washes, not one. The vertical keeps the deck and the foot readable; the
                       horizontal keeps the left column — where every heading and label lives —
                       readable over whatever happens to be in that part of the picture. */
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Kitsu.background.copy(alpha = 0.55f),
                                0.35f to Kitsu.background.copy(alpha = 0.25f),
                                0.7f to Kitsu.background.copy(alpha = 0.55f),
                                1f to Kitsu.background.copy(alpha = 0.9f),
                            ),
                        ),
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                0f to Kitsu.background.copy(alpha = 0.8f),
                                0.45f to Kitsu.background.copy(alpha = 0.35f),
                                0.75f to Color.Transparent,
                            ),
                        ),
                    )
                }
            }
        }

        /* ⚠️ The page starts where the COLLAPSED rail ends, and the open rail is drawn over it —
           so opening the rail never reflows a wall of posters under the cursor.
           ⚠️ No focusRestorer here: it was tried, and when the child it saved had been recomposed
           away (a lazy row does that) it kept the focus on the page itself — nothing lit, and the
           remote dead. RIGHT out of the rail lands on the nearest thing level with the cursor. */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = RailCollapsedWidth),
        ) {
            LaneScrolling(content = content)
        }
        LibraryRail(
            current = current,
            onBay = deck.onBay,
            serverName = deck.serverName,
            profileInitials = deck.profileInitials,
            profilePicture = deck.profilePicture,
            onProfile = deck.onProfile,
            accountName = deck.accountName,
            profileName = deck.profileName,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        NoticeHost()
    }
}

/**
 * A focus requester that actually lands, for the first control on a screen.
 *
 * <h2>⚠️ `LaunchedEffect(Unit) { requestFocus() }` DOES NOT WORK, AND FAILS SILENTLY</h2>
 *
 * The body of a `LaunchedEffect` starts as soon as the composition that declared it is applied —
 * which is BEFORE `Modifier.focusRequester(…)` has been attached to the node it names. The request
 * therefore throws `IllegalStateException: FocusRequester is not initialized`, and every call site
 * in this codebase wraps it in `runCatching`, so the throw is swallowed and the screen simply opens
 * with the cursor nowhere. From a sofa that is indistinguishable from a broken application: the
 * D-pad does nothing, and nothing on screen says why.
 *
 * <h2>⚠️ WHY THE SHELVES GOT AWAY WITH IT</h2>
 *
 * `CatalogScreen` and `ShelfScreen` key their effect on the LOADED DATA, which arrives a frame or
 * more after the first composition — so by the time the effect re-runs, the modifier is attached.
 * It worked by accident of being asynchronous. A screen with nothing to fetch — a profile, a page
 * of facts — has no such accident, and that is exactly where the cursor went missing.
 *
 * <h2>⚠️ SO IT WAITS FOR A FRAME</h2>
 *
 * `withFrameNanos` suspends until the next frame is produced, by which point composition and layout
 * have run and the requester is bound to a node. One line, in one place, instead of a `runCatching`
 * at every call site hiding the same failure.
 */
@Composable
fun rememberInitialFocus(vararg keys: Any?): FocusRequester {
    val requester = remember { FocusRequester() }

    LaunchedEffect(*keys) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }

    return requester
}

/** What a loading page holds: nothing yet, the answer, or the reason there is none. */
sealed interface Loaded<out T> {
    data object Pending : Loaded<Nothing>
    data class Ready<T>(val value: T) : Loaded<T>
    data class Failed(val problem: String) : Loaded<Nothing>
}

@Composable
fun <T> rememberLoaded(): androidx.compose.runtime.MutableState<Loaded<T>> {
    return remember { mutableStateOf<Loaded<T>>(Loaded.Pending) }
}

/* ── Formatting shared by the screens ─────────────────────────────────────── */

fun TitleCard.metaLine(): String {
    return listOfNotNull(year?.toString(), rating?.let { value -> formatRating(value) }).joinToString("  ")
}

/** A rating as the badge shows it — `★ 7.8`, with the language's decimal separator. */
fun formatRating(rating: Double): String = "★ " + formatDecimal(rating)

/**
 * `1 title`, `12 titles` — a count that reads, in the language's own plural forms.
 *
 * ⚠️ ONE FUNCTION PER NOUN, not a noun passed in: Ukrainian has three forms where English has two, and
 * a noun glued to an `s` cannot be translated. A screen that names what it counts takes one of these
 * as a `(Int) -> String`.
 */
object Counted {
    fun titles(count: Int) = plural("tv.count.titles", count, "{count} title", "{count} titles")
    fun genres(count: Int) = plural("tv.count.genres", count, "{count} genre", "{count} genres")
    fun profiles(count: Int) = plural("tv.count.profiles", count, "{count} profile", "{count} profiles")
    fun grants(count: Int) = plural("tv.count.grants", count, "{count} grant", "{count} grants")
    fun permissions(count: Int) = plural("tv.count.permissions", count, "{count} permission", "{count} permissions")
    fun jars(count: Int) = plural("tv.count.jars", count, "{count} jar", "{count} jars")
    fun seasons(count: Int) = plural("tv.count.seasons", count, "{count} season", "{count} seasons")
    fun episodes(count: Int) = plural("tv.count.episodes", count, "{count} episode", "{count} episodes")
    fun collections(count: Int) = plural("tv.count.collections", count, "{count} collection", "{count} collections")
    fun steps(count: Int) = plural("tv.count.steps", count, "{count} step", "{count} steps")
    fun rules(count: Int) = plural("tv.count.rules", count, "{count} rule", "{count} rules")
    fun lines(count: Int) = plural("tv.count.lines", count, "{count} line", "{count} lines")
    fun matchers(count: Int) = plural("tv.count.matchers", count, "{count} matcher", "{count} matchers")
    fun providers(count: Int) = plural("tv.count.providers", count, "{count} provider", "{count} providers")
    fun subjects(count: Int) = plural("tv.count.subjects", count, "{count} subject", "{count} subjects")
    fun roles(count: Int) = plural("tv.count.roles", count, "{count} role", "{count} roles")
    fun devices(count: Int) = plural("tv.count.devices", count, "{count} device", "{count} devices")
    fun credited(count: Int) = plural("tv.count.credited", count, "{count} credited", "{count} credited")
    fun places(count: Int) = plural("tv.count.places", count, "{count} place", "{count} places")
}

fun formatRuntime(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) {
        return null
    }
    return t("tv.time.minutes", "{minutes} min", "minutes" to minutes)
}

fun formatSeconds(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}

/**
 * What is left, as a bare duration — `15 хв`, not `лишилося 15 хв`. For a button hint, where the
 * verb beside it ("Resume") already says what the time is for.
 */
fun formatRemaining(position: Int, duration: Int?): String? {
    if (duration == null || duration <= 0) {
        return null
    }
    val left = (duration - position).coerceAtLeast(0)
    val hours = left / 3600
    val minutes = (left % 3600) / 60
    return if (hours > 0) {
        t("tv.time.hoursMinutes", "{hours} h {minutes} min", "hours" to hours, "minutes" to minutes)
    } else {
        t("tv.time.minutes", "{minutes} min", "minutes" to minutes)
    }
}

fun formatLeft(position: Int, duration: Int?): String? {
    if (duration == null || duration <= 0) {
        return null
    }
    val left = (duration - position).coerceAtLeast(0)
    val hours = left / 3600
    val minutes = (left % 3600) / 60
    return if (hours > 0) {
        t("tv.time.hoursMinutesLeft", "{hours} h {minutes} min left", "hours" to hours, "minutes" to minutes)
    } else {
        t("tv.time.minutesLeft", "{minutes} min left", "minutes" to minutes)
    }
}

/**
 * The class of picture a file is, as people name it.
 *
 * ⚠️ BY WIDTH AS WELL AS HEIGHT. A film cropped to 2.00:1 is 1920×960 and a scope film 1920×800 —
 * both are 1080p releases, and judged by height alone they were called "720p".
 */
fun resolution(width: Int?, height: Int?): String? {
    if (height == null) {
        return null
    }
    val across = width ?: 0
    return when {
        height >= 2000 || across >= 3800 -> "2160p"
        height >= 1000 || across >= 1900 -> "1080p"
        height >= 700 || across >= 1260 -> "720p"
        else -> "${height}p"
    }
}
