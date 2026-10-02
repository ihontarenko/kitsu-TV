package org.jmouse.project.kitsu.tv.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jmouse.project.kitsu.tv.text.t
import org.jmouse.project.kitsu.tv.ui.components.BrandMark
import org.jmouse.project.kitsu.tv.ui.components.wordmark
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType

/* ==========================================================================
   The first screen, and the shortest one.

   ⚠️ IT IS NOT A LOADING SCREEN AND MUST NOT BECOME ONE. The gate behind it resolves on its own
   clock — a remembered server answers in milliseconds on the same network and never at all when the
   PC is off — so hanging the interface on "splash until the session is known" would mean a black
   television for as long as a switched-off server takes to time out. This runs for a fixed
   [SPLASH_MOTION] and then gets out of the way, whatever the gate is doing; the gate's own
   Loading state is what says "still asking".

   ⚠️ A TELEVISION NEEDS THIS MORE THAN A BROWSER DOES. An application launched from the Leanback
   row has no tab, no address bar and no title anywhere — the panel goes from the launcher's
   wallpaper to the product's first frame with nothing in between saying which of the icons was
   pressed. The browser's equivalent of this screen is the tab's favicon and it is always on screen;
   here it has to be shown once, on purpose.

   ⚠️ THE MOTION IS THE STUDY'S, PLAYED AS A SENTENCE RATHER THAN AS A LOOP. The mark draws itself
   in, the word arrives beside it, a rule opens under both — and then the mark does what it does in
   `Kitsu/kitsu-motion.html`: the colour walks its three segments round twice, the segments rebuild
   into the Play sign, and the colour walks round once more. Then the product opens. Nothing bounces
   and nothing spins: the segments travel and each turns about its own centre, which is the mark
   moving rather than the mark being rotated.
   ========================================================================== */

/* ── The mark's own sentence ──────────────────────────────────────────────── */

/**
 * How long one change of colour takes — the study's own `320 ms` at its `balanced` 2×.
 *
 * ⚠️ THE SAME NUMBER THE BROWSER USES, AND THAT IS THE WHOLE POINT. This screen used to play the
 * rebuild alone, in 600ms, starting at 800ms — one beat out of a sentence, at a tempo nothing else
 * in the product runs at, and it was over before a person looking up had found it. The mark now
 * moves here at exactly the pace it moves at in a browser tab.
 */
private const val SPLASH_COLOUR_STEP = 320f

/** How long the segments take to rebuild into the Play sign — the study's own `600 ms`. */
private const val SPLASH_REBUILD = 600f

/**
 * ⚠️ TWO TURNS BEFORE THE REBUILD AND ONE AFTER IT, and the counting is the point rather than the
 * timing: the colour goes 1 · 2 · 3 · 1 · 2 · 3, the segments rebuild, and then it goes 1 · 2 · 3
 * once more. Three whole turns, with the move between the second and the third.
 *
 * ⚠️ THE WALK HOLDS THROUGH THE REBUILD, which is the one deliberate departure from the study. That
 * page loops, so its sixth colour change has to ride the move or the loop would have a seam in it;
 * this plays once, and a splash is read as a sentence — colours, then the MOVE, then colours. The
 * screen is not still while it holds: the three segments are travelling.
 */
private const val SPLASH_TURNS_BEFORE = 2
private const val SPLASH_TURNS_AFTER = 1

/** One turn of the walk is the three segments handing their colours round once. */
private const val SPLASH_TURN = 3 * SPLASH_COLOUR_STEP

private const val SPLASH_WALK_BEFORE = SPLASH_TURNS_BEFORE * SPLASH_TURN
private const val SPLASH_WALK_AFTER = SPLASH_TURNS_AFTER * SPLASH_TURN

/**
 * How long the mark is on screen before the gate takes over.
 *
 * ⚠️ IT IS DERIVED, NOT CHOSEN. The screen lasts exactly as long as the mark's sentence — 3 480ms,
 * which is where "three or four seconds" and the study's own tempo agree. A literal duration here
 * would be a number that has to be kept in step with three others by hand, and the last time this
 * screen was tuned it was cut off mid-gesture because it was not.
 *
 * ⚠️ LONG ENOUGH TO BE READ, SHORT ENOUGH NOT TO BE WAITED THROUGH. At 1 700ms the mark had time
 * for one beat and the tagline was a thing you noticed had been there rather than one you read.
 */
private const val SPLASH_MOTION = SPLASH_WALK_BEFORE + SPLASH_REBUILD + SPLASH_WALK_AFTER

/**
 * ⚠️ THE DISSOLVE IS PART OF THE DURATION, NOT ADDED TO IT. The splash used to be cut off mid-frame
 * when the gate took over, which reads as a dropped frame rather than as a hand-off; the screen
 * goes out under itself instead, opening beneath the walk's last change so the mark is still moving
 * as it leaves. Nothing is ever held still waiting for the fade.
 */
private const val SPLASH_FADE = 300

/**
 * Where the colour walk has got to at [milliseconds], as `step + fraction`.
 *
 * ⚠️ THE REBUILD IS SUBTRACTED OUT rather than skipped over, so the walk resumes on the step it
 * held on instead of jumping. See the note on [SPLASH_TURNS_BEFORE] for why it holds at all.
 */
private fun splashWalk(milliseconds: Float): Float {
    val walked = milliseconds.coerceAtMost(SPLASH_WALK_BEFORE) +
        (milliseconds - SPLASH_WALK_BEFORE - SPLASH_REBUILD).coerceAtLeast(0f)
    val step = (walked / SPLASH_COLOUR_STEP).toInt()
    val within = walked % SPLASH_COLOUR_STEP

    /* A fifth of each step is held, so the walk reads as changes rather than as one long fade —
       the study's own rule, and what keeps three colours from looking like a slow wash. */
    return step + ((within - SPLASH_COLOUR_STEP * 0.2f) / (SPLASH_COLOUR_STEP * 0.8f))
        .coerceIn(0f, 1f)
}

/**
 * How far through the rebuild the mark is at [milliseconds] — 0 corners up, 1 the Play sign.
 *
 * ⚠️ LINEAR HERE ON PURPOSE. The study's easing lives inside the pose, so easing this as well would
 * ease the move twice and the segments would crawl out and snap in.
 */
private fun splashShape(milliseconds: Float): Float =
    ((milliseconds - SPLASH_WALK_BEFORE) / SPLASH_REBUILD).coerceIn(0f, 1f)

@Composable
fun SplashScreen(onDone: () -> Unit) {
    /* The things that move, each its own value so the gesture can overlap rather than queue. */
    val markScale = remember { Animatable(0.82f) }
    val markAlpha = remember { Animatable(0f) }
    /* ⚠️ ONE CLOCK FOR THE MARK, READ AT TWO OFFSETS — the pose and the colour are `splashShape`
       and `splashWalk` of the same milliseconds, so they can never drift out of step. Two
       Animatables, one per property, is exactly how a hand-built version of this fails after a
       recomposition: the colours arrive a beat late and the rebuild looks like a different
       animation happening at the same time.

       ⚠️ AND IT IS THIS SCREEN'S OWN SENTENCE, not the waiting mark's loop. That loops the study's
       whole cycle out and back for ever; this plays two turns of colour, the rebuild, and a third
       turn — once, forwards, and straight into the dissolve. */
    val markPhase = remember { Animatable(0f) }
    val wordAlpha = remember { Animatable(0f) }
    val wordShift = remember { Animatable(-14f) }
    val ruleWidth = remember { Animatable(0f) }
    /* The tagline is its own beat now — it used to ride the word's alpha and arrived before the rule
       that is supposed to introduce it. */
    val lineAlpha = remember { Animatable(0f) }
    /* ⚠️ THE WASH BREATHES, and it is the only thing on screen that is still moving at the end. A
       glow that reaches its size in the first third leaves two thirds of a static picture; opening
       slowly under everything else is what makes the hold feel held rather than stalled. */
    val washBloom = remember { Animatable(0.55f) }
    val screenAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        /* ⚠️ LAUNCHED SIDE BY SIDE, NOT AWAITED IN TURN. `animateTo` suspends until it lands, so a
           straight sequence here would add the durations together and make the splash several times
           as long as the number above says. */
        /* ⚠️ THE WASH OPENS ACROSS THE WHOLE SENTENCE NOW. It reached its size in 1 400ms when the
           screen lasted 1 700; on a screen that lasts twice as long that left two thirds of a
           static glow, which is what makes a splash feel like a wait rather than a beat. */
        launch { washBloom.animateTo(1f, tween(2_400, easing = FastOutSlowInEasing)) }
        launch { markAlpha.animateTo(1f, tween(360, easing = LinearEasing)) }
        launch { markScale.animateTo(1f, tween(620, easing = FastOutSlowInEasing)) }

        /* ⚠️ LINEAR, AND THE ONLY ANIMATION HERE THAT MUST BE. This is a clock, not a movement —
           every curve the mark has is inside `splashWalk` and `splashShape`, and easing the clock
           as well would bend all of them. */
        launch {
            markPhase.animateTo(
                SPLASH_MOTION,
                tween(SPLASH_MOTION.toInt(), easing = LinearEasing),
            )
        }

        launch {
            delay(220)
            wordAlpha.animateTo(1f, tween(420, easing = LinearEasing))
        }
        launch {
            delay(220)
            wordShift.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
        }
        launch {
            delay(420)
            ruleWidth.animateTo(1f, tween(640, easing = FastOutSlowInEasing))
        }
        launch {
            delay(820)
            lineAlpha.animateTo(0.9f, tween(460, easing = LinearEasing))
        }

        /* ⚠️ The dissolve, on the frame the gesture lands — and `onDone` only once the screen is
           actually gone, so the gate's first frame is never drawn under a splash that is still
           fading over it. */
        delay((SPLASH_MOTION - SPLASH_FADE).toLong())
        screenAlpha.animateTo(0f, tween(SPLASH_FADE, easing = LinearEasing))
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            /* ⚠️ The ground goes out with everything on it, so the dissolve is to black rather than
               to a bare panel with the logo peeled off it. */
            .alpha(screenAlpha.value)
            /* The rail's ground rather than the page's — the same near-black the window is painted
               in `themes.xml`, so the launcher's hand-off has no step in it. */
            .background(Kitsu.side),
        contentAlignment = Alignment.Center,
    ) {
        /* ⚠️ A WASH, NOT A GRADIENT BACKDROP. One accent-tinted glow behind the mark is what keeps
           the screen from being a logo floating on a flat rectangle; it is drawn at the accent's
           own hue so the splash tells you which palette the household chose before anything else
           does. */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Kitsu.accent.copy(alpha = 0.04f + 0.09f * washBloom.value),
                            Color.Transparent,
                        ),
                        radius = 420f + 360f * washBloom.value,
                    ),
                ),
        )

        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                BrandMark(
                    modifier = Modifier
                        .alpha(markAlpha.value)
                        .scale(markScale.value),
                    size = 76.dp,
                    progress = splashShape(markPhase.value),
                    walk = splashWalk(markPhase.value),
                )
                Text(
                    text = wordmark(),
                    style = KitsuType.wordmark,
                    modifier = Modifier
                        .alpha(wordAlpha.value)
                        .padding(start = wordShift.value.dp.coerceAtLeast(0.dp)),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            /* The rule opens from the middle: one line, the width of the wordmark, in the accent. */
            Box(modifier = Modifier.width(220.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ruleWidth.value)
                        .height(2.dp)
                        .background(Kitsu.accentText.copy(alpha = 0.55f)),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                /* ⚠️ THE ONE TRANSLATED LINE HERE, and it says what the product is rather than what
                   it is doing. "Loading…" would be a lie: nothing is being waited for. */
                text = t("tv.splash.tagline", "Your library, on this television"),
                style = KitsuType.lead.copy(color = Kitsu.foregroundSubtle),
                modifier = Modifier.alpha(lineAlpha.value),
            )
        }
    }
}
