package org.jmouse.project.kitsu.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.mixOklab
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/* ==========================================================================
   The mark.

   ⚠️ ONE DRAWING, IN ONE FILE, AND EVERY OTHER COPY OF IT IS A COPY OF THIS ONE. The same three
   segments are in `Kitsu/kitsu-motion.html`, in `Kitsu/FE/src/components/domain/brand-mark.tsx`,
   in `Kitsu/FE/src/lib/favicon.ts` and in `Kitsu/FE/public/favicon.svg`. A mark that is
   approximately the same in five places is the one thing a mark must never be, so the numbers below
   are that study's own — copied across rather than redrawn, and changed in all of them or in none.

   ⚠️ THREE SEGMENTS, NOT THREE PLANES, AND THAT IS THE CHANGE. What stood here was one folded
   object drawn as a fold, a fold-back and a face, turned +90° so the cut read as a Play triangle.
   The mark is now three flat segments, and it is a different idea: they are separable, each turns
   about its own centre, and the shape they make is a POSE rather than a fixed outline.

   ⚠️ AT REST THE CORNERS POINT UP. That is the study's own first state — the tension before the
   move. The Play sign is where the cycle ENDS, not where the mark sits.

   ⚠️ THE SEGMENTS TAKE TOKENS, NOT THE STUDY'S THREE ORANGES. `#FF641A / #FFD0A7 / #B84D14` are
   right for a page handed to somebody and wrong inside a product where the accent is one of nine.
   `Kitsu.markFold` / `markFace` / `markBack` follow whichever palette is on.
   ========================================================================== */

/** A segment, by its Play vertices. Every pose in the cycle is a rigid move of these. */
private val SEGMENTS = mapOf(
    /* The quad — the largest segment, which lands on itself. */
    "orange" to listOf(112f to 103f, 88f to 65f, 38f to 65f, 62f to 103f),
    /* The pale triangle. It and the dark one change places across the move. */
    "cream" to listOf(112f to 103f, 62f to 103f, 88f to 141f),
    /* The dark triangle. */
    "burnt" to listOf(62f to 103f, 38f to 141f, 88f to 141f),
)

private val NAMES = listOf("orange", "cream", "burnt")

/** Which segment sets off first — the cascade that makes three moves read as one gesture. */
private val ORDER = mapOf("orange" to 0, "cream" to 1, "burnt" to 2)

/** How far each centre bows off the straight line between its two places. */
private val ARC = mapOf(
    "orange" to (24f to -18f),
    "cream" to (-28f to 28f),
    "burnt" to (34f to -16f),
)

/**
 * The window the mark is drawn through — the resting shape's own box.
 *
 * ⚠️ IT IS NOT THE STUDY'S `viewBox`. That page states `20 45 110 120`, sized for the TRAVEL: the
 * segments bow well outside themselves on their way across. Scaled to it, the mark sits in the
 * middle of a wide margin and is visibly smaller than the one it replaced in every lockup. So the
 * box is the mark, and the waiting version simply paints outside it — which nothing here clips.
 */
private const val BOX_LEFT = 37f
private const val BOX_TOP = 66f
private const val BOX_WIDTH = 76f
private const val BOX_HEIGHT = 74f

/* ── The tempo, from the study's `balanced` profile at its own 2× ─────────── */

private const val MOVE = 600f
private const val STEP = 320f
private const val STAGGER = 30f
private const val COLOURS = 5 * STEP
private const val HALF = COLOURS + MOVE
private const val TOTAL = 2 * HALF

/** `cubic-bezier(.4, 0, .2, 1)`, solved for x before y — the way CSS evaluates it. */
private fun bezier(t: Float): Float {
    fun at(u: Float, a: Float, b: Float) =
        3f * (1f - u) * (1f - u) * u * a + 3f * (1f - u) * u * u * b + u * u * u

    var low = 0f
    var high = 1f
    repeat(24) {
        val middle = (low + high) / 2f
        if (at(middle, 0.4f, 0.2f) < t) {
            low = middle
        } else {
            high = middle
        }
    }
    return at((low + high) / 2f, 0f, 1f)
}

private fun ease(t: Float): Float = when {
    t <= 0f -> 0f
    t >= 1f -> 1f
    else -> bezier(t)
}

/** Where a segment's centre and angle are at motion parameter [t] — 0 corners up, 1 the Play sign. */
private data class Pose(val x: Float, val y: Float, val degrees: Float, val cx: Float, val cy: Float)

private fun poseAt(name: String, t: Float): Pose {
    val vertices = SEGMENTS.getValue(name)
    val cx = vertices.sumOf { (x, _) -> x.toDouble() }.toFloat() / vertices.size
    val cy = vertices.sumOf { (_, y) -> y.toDouble() }.toFloat() / vertices.size

    val delay = ORDER.getValue(name) * STAGGER / MOVE
    val progress = ease(((t - delay) / (1f - delay)).coerceIn(0f, 1f))

    /* At rest the whole mark is the Play sign turned a quarter back about (75, 103). */
    val fromX = 75f - (cy - 103f)
    val fromY = 103f + (cx - 75f)
    /* The quad lands on itself; the two triangles land on each other, through (75, 122). */
    val toX = if (name == "orange") cx else 150f - cx
    val toY = if (name == "orange") cy else 244f - cy

    val (arcX, arcY) = ARC.getValue(name)
    val viaX = (fromX + toX) / 2f + arcX
    val viaY = (fromY + toY) / 2f + arcY
    val rest = 1f - progress

    return Pose(
        x = rest * rest * fromX + 2f * rest * progress * viaX + progress * progress * toX,
        y = rest * rest * fromY + 2f * rest * progress * viaY + progress * progress * toY,
        degrees = 90f + progress * (if (name == "orange") -90f else 90f),
        cx = cx,
        cy = cy,
    )
}

/**
 * One segment as a closed path, posed at [t] and already in canvas units.
 *
 * ⚠️ THE SCALE IS APPLIED TO THE POINTS RATHER THAN THROUGH A `Matrix`. Three multiplications per
 * vertex is the whole of it, and a matrix whose operations compose in an order this file would have
 * to be read twice to confirm is a worse way to say the same thing.
 */
private fun pathAt(name: String, t: Float, origin: Offset, factor: Float): Path {
    val pose = poseAt(name, t)
    val radians = pose.degrees * Math.PI.toFloat() / 180f
    val cosine = cos(radians)
    val sine = sin(radians)
    val path = Path()

    SEGMENTS.getValue(name).forEachIndexed { index, (x, y) ->
        val dx = x - pose.cx
        val dy = y - pose.cy
        val pointX = origin.x + (pose.x + dx * cosine - dy * sine) * factor
        val pointY = origin.y + (pose.y + dx * sine + dy * cosine) * factor
        if (index == 0) {
            path.moveTo(pointX, pointY)
        } else {
            path.lineTo(pointX, pointY)
        }
    }
    path.close()
    return path
}

/**
 * Draws the mark into whatever box the caller gives.
 *
 * ⚠️ NOTHING CLIPS, AND THAT IS DELIBERATE. While the segments travel they leave the resting box by
 * about a fifth of its height; a `Canvas` paints outside its bounds without reserving space, so the
 * move is drawn whole. The browser does the same thing with `overflow: visible`.
 */
private fun DrawScope.drawMark(t: Float, inks: List<Color>) {
    val factor = minOf(size.width / BOX_WIDTH, size.height / BOX_HEIGHT)
    val origin = Offset(
        x = (size.width - BOX_WIDTH * factor) / 2f - BOX_LEFT * factor,
        y = (size.height - BOX_HEIGHT * factor) / 2f - BOX_TOP * factor,
    )

    NAMES.forEachIndexed { index, name ->
        drawPath(pathAt(name, t, origin, factor), inks[index])
    }
}

/**
 * How the three segments are inked on a fill they cannot take a palette against.
 *
 * ⚠️ THE ALPHAS KEEP THE VALUE ORDER, they are not decoration. All three segments touch, so one ink
 * at one strength is a silhouette with no mark in it. Dark stays heavier than pale, which is the
 * relationship the coloured version has, restated in the only dimension a single ink has.
 */
enum class MarkTone { Accent, OnColour }

private val ON_COLOUR_ALPHA = listOf(1f, 0.45f, 0.72f)

/**
 * The three inks at a point in the colour walk.
 *
 * ⚠️ ONE RULE FOR EVERY WALK IN THIS CLIENT — the waiting mark's and the splash's alike. It was
 * inside `BrandSpinner`, which meant a second caller wanting the walk had to restate the modulo,
 * the blend and the argument order of [mixOklab]; getting the last of those the wrong way round
 * runs the whole walk backwards, one step out of phase with the move.
 *
 * @param walk where the walk has got to, as `step + fraction`. Zero is the resting order.
 */
private fun walked(palette: List<Color>, walk: Float): List<Color> {
    val whole = floor(walk).toInt()
    val blend = walk - whole
    val eased = blend * blend * (3f - 2f * blend)

    return List(3) { index ->
        /* ⚠️ `share` WEIGHTS THE FIRST ARGUMENT, so the colour being moved TO goes first. */
        mixOklab(
            first = palette[(index + whole + 1).mod(3)],
            second = palette[(index + whole).mod(3)],
            share = eased,
        )
    }
}

/**
 * The mark, posed.
 *
 * @param progress where in the rebuild to hold it: 0 is rest, corners up — which is where a mark
 *   that is not moving belongs — and 1 is the Play sign. The splash drives it to play the gesture
 *   once, as one more of its beats; everything else leaves it alone.
 * @param walk where the colour walk has got to, as `step + fraction`. ⚠️ Zero is the resting order,
 *   which is why every caller but the splash can ignore it — and why the splash can drive the pose
 *   and the colour from ONE clock of its own rather than borrowing the waiting mark's loop.
 *   Ignored by [MarkTone.OnColour]: there is one ink there at three strengths, and walking those
 *   round is a flicker rather than a colour change.
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tone: MarkTone = MarkTone.Accent,
    onColour: Color = Kitsu.foreground,
    progress: Float = 0f,
    walk: Float = 0f,
) {
    val inks = when (tone) {
        MarkTone.Accent -> walked(listOf(Kitsu.markFold, Kitsu.markFace, Kitsu.markBack), walk)
        MarkTone.OnColour -> ON_COLOUR_ALPHA.map { alpha -> onColour.copy(alpha = alpha) }
    }

    Canvas(modifier = modifier.size(size)) {
        drawMark(t = progress.coerceIn(0f, 1f), inks = inks)
    }
}

/* ==========================================================================
   Waiting.

   ⚠️ IN THIS FILE BECAUSE IT IS THE MARK, not a second glyph that resembles it. The segments above
   are the only copy of the drawing in this client and they stay that way.

   ⚠️ IT IS THE STUDY'S CYCLE, WHICH IS WHY IT IS NOT A SPINNER. The mark rests with its corners up
   while the colour walks the three segments five times over; then they travel, each turning about
   its own centre, and settle as the Play sign while the sixth colour lands. Then back. A borrowed
   rotating arc says nothing about this product, and the colour walk that stood here before said
   only half of what the study does — the movement was the other half.

   ⚠️ ONE CLOCK FOR THE WHOLE CYCLE, not one per segment and not one per property. A segment's pose
   and its colour are both this single phase read at their own offsets, so they can never drift out
   of step — which is exactly how a hand-built several-animation version fails after a
   recomposition.
   ========================================================================== */

/**
 * Where the colour walk has got to at [milliseconds] — the study's `paint` position, 0 through 6
 * across each half of the cycle.
 *
 * ⚠️ IT RESTARTS EACH HALF rather than running 0 through 12. The study repaints from the same
 * starting colour on the way out and on the way back, so one turn of the walk covers a rebuild and
 * its return alike.
 */
private fun walkAt(milliseconds: Float): Float {
    val local = milliseconds % HALF
    return if (local < COLOURS) {
        val step = (local / STEP).toInt()
        val phase = local % STEP
        /* A fifth of each step is held, so the walk reads as five changes rather than one long fade. */
        step + ((phase - STEP * 0.2f) / (STEP * 0.8f)).coerceAtLeast(0f)
    } else {
        /* The sixth change runs WITH the rebuild rather than before it — the study's whole point. */
        5f + (local - COLOURS) / MOVE
    }
}

/** How far through the rebuild the mark is: 0 at rest, 1 the Play sign, and back again. */
private fun shapeAt(milliseconds: Float): Float {
    val back = milliseconds >= HALF
    val local = milliseconds % HALF
    if (local < COLOURS) {
        return if (back) 1f else 0f
    }
    val progress = (local - COLOURS) / MOVE
    return if (back) 1f - progress else progress
}

/** Something is being done and has not answered yet. */
@Composable
fun BrandSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    colour: Color = Kitsu.accent,
) {
    val walking = rememberInfiniteTransition(label = "brand-spinner")
    val phase by walking.animateFloat(
        initialValue = 0f,
        targetValue = TOTAL,
        animationSpec = infiniteRepeatable(
            tween(TOTAL.toInt(), easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "phase",
    )

    /* The three the walk hands round. `colour` replaces the accent where the mark sits on a fill. */
    val palette = listOf(
        if (colour == Kitsu.accent) Kitsu.markFold else colour,
        Kitsu.markFace,
        Kitsu.markBack,
    )

    Canvas(modifier = modifier.size(size)) {
        drawMark(t = shapeAt(phase), inks = walked(palette, walkAt(phase)))
    }
}
