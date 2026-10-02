package org.jmouse.project.kitsu.tv.ui.components

import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.remember
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.jmouse.project.kitsu.tv.ui.theme.ControlShape
import androidx.compose.ui.draw.clip
import org.jmouse.project.kitsu.tv.ui.theme.ChipShape
import org.jmouse.project.kitsu.tv.ui.theme.CountShape
import org.jmouse.project.kitsu.tv.ui.theme.DataFamily
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType


/*
  The cursor is the focus. Everything a remote can land on is a Surface wearing the same ring — 2dp
  of the lightened accent, drawn 2dp OUTSIDE the edge — and lifting a little. One treatment,
  everywhere; nothing else on the screen looks like it.
 */

/**
 * The cursor.
 *
 * <h2>⚠️ THE LIGHTENED ACCENT, AND THE PLAIN ACCENT WAS TRIED AND UNDONE BEFORE IT</h2>
 *
 * <p>A ring the same colour as the fill it lands on is not a ring. `TvButton`'s primary weight fills
 * with {@code Kitsu.accent}, so an accent-coloured ring around it vanished completely: the one
 * control on the screen most likely to hold focus was the one that could not show it. The answer
 * then was to make the ring achromatic — near-white on near-black — and for Harbour, whose accent
 * WAS a near-white, that was the only answer available.
 *
 * <p>⚠️ KITSU'S ANSWER IS A DIFFERENT GEOMETRY RATHER THAN A DIFFERENT COLOUR, and it is the one the
 * browser uses. The ring takes {@link Kitsu#accentText} — the accent mixed towards white until it
 * reads as a line — and {@link Kitsu#focusRingInset} draws it OUTSIDE the control's own edge. On a
 * filled button the ring is therefore on the page beside the fill, not on top of it, so the two
 * colours never have to differ for the ring to be seen.
 *
 * <p>⚠️ Focus still answers "where is the cursor" and the accent still answers "what is live". They
 * remain different questions; what changed is that the ring no longer has to be a different HUE to
 * answer its own.
 *
 * @param inset negative, because a {@code Border}'s inset counts INWARD and this ring sits outside
 * @param shape the corner the ring follows — a poster's ring must not be squarer than the poster
 */
@Composable
fun focusRing(
    inset: Dp = -Kitsu.focusRingInset,
    shape: Shape = ControlShape,
): Border = Border(
    border = BorderStroke(Kitsu.focusRingWidth, Kitsu.accentText),
    inset = inset,
    shape = shape,
)

enum class ButtonWeight { Primary, Secondary, Quiet }

/**
 * A button. Three weights: primary (aqua fill — one per screen), secondary (ink
 * outline), quiet (text). A hint in mono after the label carries the number the
 * button acts on: `Resume  1:12:04`.
 */
@Composable
fun TvButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    weight: ButtonWeight = ButtonWeight.Secondary,
    icon: ImageVector? = null,
    hint: String? = null,
    enabled: Boolean = true,
) {
    val container = if (weight == ButtonWeight.Primary) Kitsu.accent else Color.Transparent
    val focusedContainer = if (weight == ButtonWeight.Primary) Kitsu.accent else Kitsu.backgroundElevated
    val content = when (weight) {
        ButtonWeight.Primary -> Kitsu.accentForeground
        ButtonWeight.Secondary -> Kitsu.foreground
        ButtonWeight.Quiet -> Kitsu.foregroundMuted
    }
    val focusedContent = if (weight == ButtonWeight.Primary) Kitsu.accentForeground else Kitsu.foreground
    val outline = if (weight == ButtonWeight.Secondary) Kitsu.borderStrong else Color.Transparent

    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 36.dp),
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(ControlShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            focusedContainerColor = focusedContainer,
            contentColor = content,
            focusedContentColor = focusedContent,
            /* ⚠️ NO FILL WHEN DISABLED, whatever the weight. The fill used to be the enabled
               colour at 40% — a primary button went a muddy, half-lit aqua over the artwork, which
               read as a rendering fault rather than as "not now". A control that cannot be pressed
               is drawn as an outline of the quietest ink: still there, plainly off. */
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Kitsu.foregroundSubtle,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, outline), shape = ControlShape),
            focusedBorder = focusRing(),
            disabledBorder = Border(BorderStroke(1.dp, Kitsu.border), shape = ControlShape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (weight == ButtonWeight.Quiet) 8.dp else 16.dp,
                vertical = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(text = text, style = KitsuType.button)
            if (hint != null) {
                /* ⚠️ A RULE BETWEEN THE ACTION AND ITS HINT. "Продовжити S01E03" and "15 хв" in one
                   run read as a single sentence at three metres; the hairline makes them two fields. */
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .height(14.dp)
                        .width(1.dp)
                        .background(LocalContentColor.current.copy(alpha = 0.5f)),
                )
                Text(
                    text = hint,
                    style = KitsuType.meta.copy(color = Color.Unspecified),
                    modifier = Modifier.alpha(0.8f),
                )
            }
        }
    }
}

/** A key on an on-screen pad: one character, a square, no padding to squeeze it out. */
@Composable
fun TvKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    KeySurface(onClick = onClick, modifier = modifier, size = size) {
        Text(text = label, style = KitsuType.body.copy(fontWeight = FontWeight.SemiBold))
    }
}

/**
 * A key on an on-screen pad that is an action rather than a character — clear, delete.
 *
 * ⚠️ THE SAME SQUARE AS A CHARACTER KEY, not a [TvIconButton]. An icon button is a 36dp control of its
 * own; set into a pad of larger keys it came out shorter than its neighbours and rode above their
 * baseline, and the pad stopped reading as one grid.
 */
@Composable
fun TvKey(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    KeySurface(onClick = onClick, modifier = modifier, size = size) {
        Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun KeySurface(onClick: () -> Unit, modifier: Modifier, size: Dp, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = ClickableSurfaceDefaults.shape(ControlShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Kitsu.backgroundElevated,
            focusedContainerColor = Kitsu.backgroundOverlay,
            contentColor = Kitsu.foreground,
            focusedContentColor = Kitsu.foreground,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Kitsu.border), shape = ControlShape),
            focusedBorder = focusRing(),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            content()
        }
    }
}

/** An icon-only button, square, the same 36dp as the others. */
@Composable
fun TvIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    weight: ButtonWeight = ButtonWeight.Secondary,
    /** The glyph's colour when the thing it stands for is ON — a kept favourite, a filed title. */
    tint: Color? = null,
) {
    val primary = weight == ButtonWeight.Primary
    Surface(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        shape = ClickableSurfaceDefaults.shape(ControlShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Kitsu.accent else Color.Transparent,
            focusedContainerColor = if (primary) Kitsu.accent else Kitsu.backgroundElevated,
            contentColor = if (primary) Kitsu.accentForeground else Kitsu.foreground,
            focusedContentColor = if (primary) Kitsu.accentForeground else Kitsu.foreground,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (primary) Color.Transparent else Kitsu.borderStrong), shape = ControlShape),
            focusedBorder = focusRing(),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint ?: LocalContentColor.current,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** A chip: a season, a sort, a keyboard mode. Pressed is ink on paper. */
@Composable
fun TvChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
    count: String? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 26.dp),
        shape = ClickableSurfaceDefaults.shape(ControlShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (pressed) Kitsu.ink else Kitsu.backgroundElevated,
            focusedContainerColor = if (pressed) Kitsu.ink else Kitsu.backgroundElevated,
            contentColor = if (pressed) Kitsu.inkForeground else Kitsu.foregroundMuted,
            focusedContentColor = if (pressed) Kitsu.inkForeground else Kitsu.foreground,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (pressed) Kitsu.ink else Kitsu.border), shape = ControlShape),
            focusedBorder = focusRing(),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = if (count == null) 8.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, style = KitsuType.caption)

            /*
              ⚠️ THE COUNT IS A SEPARATE FIELD, NOT A WORD AT THE END OF THE LABEL.

              It was the same colour at one point smaller, four pixels from the name — so "Сезон 1"
              and "26" read as one string with a stray number in it, which is exactly how it was
              reported. A size difference that small is not a difference at three metres.

              What tells them apart is a RULE and a SURFACE: the count gets its own compartment,
              divided by a hairline, in the data face with tabular figures. Shape, not shade — the
              one distinction that survives both the chip's states and the distance it is read from.
             */
            /*
              ⚠️ A FILLED COMPARTMENT, NOT AN OUTLINED BOX — and the box was the mistake.

              The count needs its own compartment: at one point it was the same colour a size
              smaller, four pixels from the name, and "Сезон 2 12" read as one string with a stray
              number in it. A hairline rule at 30% then vanished at distance. So it was given a
              drawn bracket — a 1dp border, squared off — and that fixed the legibility and broke
              something else: the chip around it is a ROUNDED control, and a hard-cornered outlined
              box inside a rounded outlined control reads as a field that has been pasted in.
              Two borders, two radii, one control.

              A fill has no such argument with its host. It separates by SURFACE rather than by a
              second edge, which is the one distinction that survives both the chip's states and the
              distance it is read from — and it takes the chip's own corner, so there is one shape
              on screen instead of two.

              ⚠️ The ground follows the state, not a fixed grey. Pressed, the chip is ink and the
              compartment has to lift off ink; unpressed it has to lift off an elevated panel. A
              constant would be invisible in one of the two.
             */
            if (count != null) {
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .background(
                            LocalContentColor.current.copy(alpha = if (pressed) 0.18f else 0.14f),
                            CountShape,
                        )
                        .defaultMinSize(minWidth = 24.dp, minHeight = 20.dp)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = count,
                        style = KitsuType.meta.copy(
                            color = Color.Unspecified,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * A big row in a choice list: a server found, a track, an episode. Ground rises
 * when in hand; [marked] draws the index tab — a 2dp aqua rule down the left —
 * on the current one.
 */
@Composable
fun TvListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    marked: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(ControlShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Kitsu.backgroundElevated,
            focusedContainerColor = Kitsu.backgroundOverlay,
            contentColor = Kitsu.foreground,
            focusedContentColor = Kitsu.foreground,
            disabledContainerColor = Kitsu.backgroundElevated.copy(alpha = 0.5f),
            disabledContentColor = Kitsu.foregroundSubtle,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Kitsu.border), shape = ControlShape),
            focusedBorder = focusRing(),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (marked) Modifier.indexTab() else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** The index tab: the 2dp aqua rule that marks the current place. */
fun Modifier.indexTab(colour: Color = Kitsu.accent): Modifier = drawBehind {
    drawRect(color = colour, topLeft = Offset.Zero, size = Size(2.dp.toPx(), size.height))
}

/**
 * A status badge: DIRECT PLAY, TRANSCODE, NO FILE.
 *
 * <h2>⚠️ THE BROWSER'S CHIP, AND THE BROWSER IS THE REFERENCE FOR IT — NOT THE TEMPLATE</h2>
 *
 * <p>This was a full hairline box with square corners, which is the dialect the browser left: the
 * chip there now carries a 2dp rule down its LEADING edge, clipped inside a 4dp corner, and no box
 * around a tone. `Html/KitsuUI` still shows the old shape and is behind on this — when the three
 * disagree it is the running interface that is right here, because that is where the change was
 * made and reviewed.
 *
 * <h2>⚠️ THE RULE IS {@link indexTab}'s, NOT A SECOND DRAWING OF THE SAME IDEA</h2>
 *
 * <p>The rail marks the current row with a 2dp bar down its leading edge; so does a notice; so does
 * this. One idea at three sizes, and one modifier — a hand-drawn `drawRect` here would be a fourth
 * place to get the width wrong.
 *
 * <h2>⚠️ A STATUS TAKES A TONE; A NEUTRAL LABEL KEEPS ITS BOX</h2>
 *
 * <p>This is not taste. A soft fill borrows a SURFACE colour, so a quiet chip vanishes on the
 * surface it is named after and the rule down its edge then reads as a divider between two words —
 * which is exactly what it did in the browser before it was caught. {@code Muted} is what the
 * kinds, the counts, the roles and the capabilities use, so it is the one that keeps a hairline.
 *
 * <h2>⚠️ THE TYPE IS THIS CLIENT'S OWN AND STAYS THAT WAY</h2>
 *
 * <p>Uppercase, mono, tracked — read across a room rather than at arm's length. The geometry is
 * what the browser and this client share; the typography is tuned for ten feet and the browser's
 * 11px semibold would not survive the trip.
 */
enum class BadgeTone { Ok, Warn, Danger, Muted }

@Composable
fun TvBadge(text: String, tone: BadgeTone, modifier: Modifier = Modifier) {
    val ink = when (tone) {
        BadgeTone.Ok -> Kitsu.ok
        BadgeTone.Warn -> Kitsu.warn
        BadgeTone.Danger -> Kitsu.danger
        BadgeTone.Muted -> Kitsu.foregroundMuted
    }
    val ground = when (tone) {
        BadgeTone.Ok -> Kitsu.okBackground
        BadgeTone.Warn -> Kitsu.warnBackground
        BadgeTone.Danger -> Kitsu.dangerBackground
        /* No fill of its own: a hairline is what holds this one's shape. */
        BadgeTone.Muted -> Color.Transparent
    }
    val rule = if (tone == BadgeTone.Muted) Kitsu.borderStrong else ink

    Box(
        modifier = modifier
            /* ⚠️ Clipped BEFORE the rule is drawn, which is what makes the bar follow the corner
               instead of squaring off the two it runs between. */
            .clip(ChipShape)
            .background(ground)
            .then(
                if (tone == BadgeTone.Muted) {
                    Modifier.border(1.dp, Kitsu.borderStrong, ChipShape)
                } else {
                    Modifier
                },
            )
            .indexTab(rule)
            /* The extra 2dp at the start is the rule's own width, so the word sits where it would
               have without one. */
            .padding(start = 8.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = TextStyle(fontFamily = DataFamily, fontSize = 11.sp, letterSpacing = 0.08.em, color = ink),
        )
    }
}

/** The kind mark: a square of the categorical colour and the kind's name, only where kinds mix. */
@Composable
fun KindMark(kind: String, modifier: Modifier = Modifier) {
    val colour = kindColour(kind)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(6.dp).background(colour))
        Text(
            text = kindLabel(kind).uppercase(),
            style = TextStyle(fontFamily = DataFamily, fontSize = 11.sp, letterSpacing = 0.1.em, color = colour),
        )
    }
}

fun kindColour(kind: String): Color = when (kind.uppercase()) {
    "MOVIE", "FILM" -> Kitsu.kindFilm
    "SERIES" -> Kitsu.kindSeries
    "EPISODE", "SEASON" -> Kitsu.kindEpisode
    "PERSON" -> Kitsu.kindPerson
    "COLLECTION" -> Kitsu.kindCollection
    else -> Kitsu.kindGenre
}

fun kindLabel(kind: String): String = when (kind.uppercase()) {
    "MOVIE" -> t("tv.kind.film", "Film")
    "SERIES" -> t("tv.kind.series", "Series")
    "EPISODE" -> t("tv.kind.episode", "Episode")
    "SEASON" -> t("tv.kind.season", "Season")
    "PERSON" -> t("tv.kind.person", "Person")
    "COLLECTION" -> t("tv.kind.collection", "Collection")
    "GENRE" -> t("tv.kind.genre", "Genre")
    else -> kind.lowercase().replaceFirstChar { it.uppercase() }
}

/** How far in: a thin aqua bar on 22% ink. */
@Composable
fun ProgressRule(fraction: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(modifier = modifier.height(height).background(Kitsu.foreground.copy(alpha = 0.22f))) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .background(Kitsu.progress),
        )
    }
}

/** The eyebrow: small, tracked, uppercase — see `KitsuType.eyebrow` for why it carries no colour. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, muted: Boolean = false) {
    Text(
        text = text.uppercase(),
        style = KitsuType.eyebrow.copy(color = if (muted) Kitsu.foregroundSubtle else Kitsu.foregroundMuted),
        modifier = modifier,
    )
}

/** A line of facts in mono: `169 min · ★ 8.5 · 2160p`. */
@Composable
fun MetaLine(vararg parts: String?, modifier: Modifier = Modifier) {
    val text = parts.filterNotNull().filter { it.isNotBlank() }.joinToString("  ·  ")
    Text(text = text, style = KitsuType.meta, modifier = modifier)
}

/**
 * One place in a side rail — a genre on the walls, a section of the administration.
 *
 * ⚠️ QUIET UNTIL CHOSEN: no fill and no border at rest, because a rail of twelve bordered boxes is
 * louder than the wall beside it. The current place carries the index tab; the one in hand, the ring.
 *
 * @param count a figure beside the label, in mono, or null for none
 * @param current whether this is the place the page is showing
 */
@Composable
fun TvRailItem(label: String, count: String?, current: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(ControlShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (current) Kitsu.backgroundElevated else Color.Transparent,
            focusedContainerColor = Kitsu.backgroundElevated,
            contentColor = if (current) Kitsu.foreground else Kitsu.foregroundMuted,
            focusedContentColor = Kitsu.foreground,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.dp, Color.Transparent), shape = ControlShape),
            focusedBorder = focusRing(),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (current) Modifier.indexTab() else Modifier)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = KitsuType.body.copy(fontWeight = FontWeight.SemiBold))
            if (count != null) {
                Text(text = count, style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle))
            }
        }
    }
}

/**
 * Whether a press on a control that cannot be undone was meant for it.
 *
 * ⚠️ A KEY RELEASED ON THE PREVIOUS SCREEN LANDS ON THIS ONE. The OK that opened a page can arrive as a
 * click on whatever the new page focused first — and when the page's own first focus is late, the
 * focus falls by position onto the row where the old one stood. For "Change server" or "Sign out"
 * that is the first half of an act that cannot be taken back. So a press that comes within moments
 * of the control receiving the focus is not counted: nobody reads a row and presses it that fast.
 */
class DeliberatePress {
    private var focusedAt = 0L
    private var focused = false

    internal fun focusChanged(hasFocus: Boolean) {
        if (hasFocus && !focused) {
            focusedAt = android.os.SystemClock.uptimeMillis()
        }
        focused = hasFocus
    }

    /** True unless the control received the focus a moment ago. */
    fun isDeliberate(): Boolean = android.os.SystemClock.uptimeMillis() - focusedAt > DELIBERATE_PRESS_MILLISECONDS
}

private const val DELIBERATE_PRESS_MILLISECONDS = 350L

@Composable
fun rememberDeliberatePress(): DeliberatePress = remember { DeliberatePress() }

/** Tells [press] when this control receives the focus. */
fun Modifier.tracksDeliberatePress(press: DeliberatePress): Modifier = onFocusChanged { state ->
    press.focusChanged(state.hasFocus)
}
