package org.jmouse.project.kitsu.tv.ui.components

import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import org.jmouse.project.kitsu.tv.data.api.ApiClient
import org.jmouse.project.kitsu.tv.data.api.Problem
import androidx.compose.ui.draw.clip
import org.jmouse.project.kitsu.tv.ui.theme.DataFamily
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.SurfaceShape
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType
import retrofit2.HttpException

/*
  ==============================================================================
  A notice that rises from the foot of the screen — why a press went nowhere.

  ⚠️ ONE HOST, IN THE SCREEN SCAFFOLD. Every screen shows notices the same way
  and in the same place, and a screen raises one by saying what happened, never
  by drawing a box of its own.

  ⚠️ A REFUSAL SAYS WHO REFUSED AND WHAT. The server's refusal carries the
  policy author's sentence, the rule as the policy spells it (`@GLOBAL
  title:write`) and the condition that failed; the notice leads with the
  sentence, and puts the rule under it in mono — the line an administrator
  searches the policy for.

  It holds no focus: it is read, not answered, and a notice that took the
  cursor would strand it when it left.
  ==============================================================================
 */

/** What a notice says. */
data class Notice(
    /** One line: what happened, in the product's words. */
    val title: String,
    /** The sentence to read — the policy author's where there is one. */
    val lead: String?,
    /** The rule as the policy spells it — `@GLOBAL title:write` — or null outside a refusal. */
    val rule: String? = null,
    /** The mechanism, quoted — the condition that failed. */
    val mechanism: String? = null,
    val tone: NoticeTone = NoticeTone.Refusal,
)

enum class NoticeTone { Refusal, Information }

/** A notice on screen, told apart from an identical one raised later — each keeps its own timer. */
data class ShownNotice(val id: Long, val notice: Notice)

/**
 * The notices on screen, newest first.
 *
 * ⚠️ A STACK, NOT A SLOT. This held ONE notice, and each `show` replaced whatever was up: two things
 * going wrong in one moment — a refusal and the failed load behind it, three titles that would not
 * add — showed only the last, and the others were never read at all. Now each gets its own card and
 * its own time on screen.
 *
 * ⚠️ AN IDENTICAL NOTICE IS NOT STACKED AGAIN, it is moved to the top with a fresh timer. Pressing the
 * same refused button five times is one thing to say, not five cards.
 *
 * ⚠️ AT MOST [MAXIMUM_SHOWN]. Past that the oldest leaves early: a column of cards down the right of
 * a television is no longer a nudge, it is a wall over the page.
 */
object Notices {
    val shown = MutableStateFlow<List<ShownNotice>>(emptyList())

    private val nextId = AtomicLong()

    fun show(notice: Notice) {
        shown.update { current ->
            val others = current.filterNot { existing -> existing.notice == notice }
            (listOf(ShownNotice(nextId.incrementAndGet(), notice)) + others).take(MAXIMUM_SHOWN)
        }
    }

    fun dismiss(id: Long) {
        shown.update { current -> current.filterNot { existing -> existing.id == id } }
    }

    private const val MAXIMUM_SHOWN = 3
}

/** How long an ordinary notice stays — long enough to read two lines, short enough not to nag. */
private const val NOTICE_MILLISECONDS = 5_000L

/** A refusal carries a rule and a condition to read, so it stays a little longer. */
private const val REFUSAL_MILLISECONDS = 7_000L

/**
 * Where notices appear — a small card that slides in at the top right and leaves by itself.
 *
 * ⚠️ THE CONSOLE'S WAY, ON PURPOSE. A notice is a nudge: a card across the foot of the screen sat on
 * top of the very row somebody was browsing, and it read as an interruption. Top right is where a
 * television keeps what it says in passing — out of the way of the page, gone before it is a bother,
 * and never holding the cursor.
 */
@Composable
fun BoxScope.NoticeHost() {
    val notices by Notices.shown.collectAsState()

    Column(
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        notices.forEach { shown ->
            key(shown.id) {
                NoticeEntry(shown)
            }
        }
    }
}

/**
 * One card in the stack: in, its time on screen, out — and only then off the list.
 *
 * ⚠️ REMOVED AFTER IT HAS SLID AWAY, not when its time is up. Dropping it from the list at once would
 * cut the card out mid-frame and jerk the ones below it upward; the list is told only once the exit
 * has finished.
 */
@Composable
private fun NoticeEntry(shown: ShownNotice) {
    val visibility = remember { MutableTransitionState(false) }
    var expired by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visibility.targetState = true
        delay(if (shown.notice.tone == NoticeTone.Refusal) REFUSAL_MILLISECONDS else NOTICE_MILLISECONDS)
        expired = true
        visibility.targetState = false
    }

    if (expired && visibility.isIdle && !visibility.currentState) {
        LaunchedEffect(Unit) {
            Notices.dismiss(shown.id)
        }
    }

    AnimatedVisibility(
        visibleState = visibility,
        enter = fadeIn(tween(160)) + slideInHorizontally(tween(220)) { width -> width / 2 },
        exit = fadeOut(tween(200)) + slideOutHorizontally(tween(260)) { width -> width / 2 },
    ) {
        NoticeCard(shown.notice)
    }
}

@Composable
private fun NoticeCard(notice: Notice) {
    val signal = if (notice.tone == NoticeTone.Refusal) Kitsu.danger else Kitsu.accent
    Row(
        /* ⚠️ IntrinsicSize.Min, so the signal stripe (fillMaxHeight) is as tall as the words. */
        modifier = Modifier
            .widthIn(min = 260.dp, max = 400.dp)
            .height(IntrinsicSize.Min)
            /* ⚠️ Clipped, and that is what keeps the signal stripe inside the corner: it is a child
               drawn to the very edge, so an unclipped panel shows it squaring off the rounding. */
            .clip(SurfaceShape)
            .background(Kitsu.backgroundOverlay)
            .border(1.dp, Kitsu.borderStrong, SurfaceShape),
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(signal))
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (notice.tone == NoticeTone.Refusal) Icons.Outlined.Block else Icons.Outlined.Info,
                contentDescription = null,
                tint = signal,
                modifier = Modifier.size(18.dp).padding(top = 1.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (notice.title.isNotBlank()) {
                    Text(
                        text = notice.title,
                        style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                notice.lead?.let { lead ->
                    Text(
                        text = lead,
                        style = KitsuType.body.copy(color = Kitsu.foreground),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                notice.rule?.let { rule ->
                    Text(
                        text = rule,
                        style = KitsuType.meta.copy(color = Kitsu.accentText, fontFamily = DataFamily),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                notice.mechanism?.let { mechanism ->
                    Text(
                        text = mechanism,
                        style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle, fontFamily = DataFamily),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * What a failed call should say, read off the server's problem.
 *
 * ⚠️ A refusal is told apart by its code, and its sentence and rule come from the fields the server
 * sends apart from the prose — never parsed out of `detail`.
 */
fun Throwable.asNotice(what: String): Notice {
    val problem = (this as? HttpException)?.response()?.errorBody()?.string()
        ?.takeIf { body -> body.isNotBlank() }
        ?.let { body -> runCatching { ApiClient.json.decodeFromString(Problem.serializer(), body) }.getOrNull() }

    if (problem == null) {
        return Notice(title = what, lead = message ?: t("tv.notice.noAnswer", "The server did not answer."), tone = NoticeTone.Information)
    }

    val refused = problem.status == 401 || problem.status == 403
    val explanation = problem.explanation
    /* The mechanism is the prose without the author's sentence at its front. */
    val mechanism = problem.detail
        ?.let { detail -> if (explanation != null) detail.removePrefix(explanation).trim() else detail }
        ?.takeIf { rest -> rest.isNotBlank() && rest != explanation }

    return if (refused) {
        /* ⚠️ THE SERVER'S WORDS AND NOTHING ELSE — its heading, the author's sentence, the rule and
           the condition. A hint written here would outlive the policy it described. */
        Notice(
            title = problem.title ?: "",
            lead = explanation ?: problem.detail,
            rule = problem.rule,
            mechanism = if (explanation != null) mechanism else null,
        )
    } else {
        Notice(title = problem.title ?: what, lead = problem.detail, tone = NoticeTone.Information)
    }
}
