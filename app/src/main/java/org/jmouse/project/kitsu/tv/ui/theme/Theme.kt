package org.jmouse.project.kitsu.tv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import org.jmouse.project.kitsu.tv.R
import kotlin.math.cbrt
import kotlin.math.pow

/*
  Kitsu at ten feet — the design system the browser wears, in Kotlin.

  Dark only: a living room is dark, and a bright ground on a television is a lamp. The browser has a
  light theme; this client has the half of each token that a dark room needs.

  ⚠️ THIS REPLACES "HARBOUR", THE GRAPHITE DESIGN THAT STOOD HERE UNTIL 2026-09-28, and the four
  decisions it was built on are reversed on purpose rather than mislaid. They are written out because
  a reversal nobody recorded is the one a later session undoes by accident:

    | Harbour said | Kitsu says |
    |---|---|
    | every corner square, radius zero everywhere | four radii — control 6, surface 8, media 10, dialog 12 |
    | the frame is achromatic; colour only means status | the accent is the mark's own hue and marks state warmly |
    | the focus ring is achromatic, never the accent | the ring is `accentText`, a LIGHTENED accent, drawn outside |
    | the neutrals are true greys | the neutrals are warm near-blacks |

  ⚠️ THE FOCUS RING FOLLOWING THE ACCENT IS THE ONE WORTH EXPLAINING, because this client tried it
  once and undid it. An aqua ring landing on an aqua-filled button vanished — the one control most
  likely to hold focus was the one that could not show it — so the ring was made near-white. Kitsu
  makes it `accentText`, which is the accent MIXED TOWARDS WHITE until it is bright enough to read
  as a line, and draws it OUTSIDE the control's own edge rather than on it. That is a different
  geometry, not the same mistake: on a filled button the ring sits on the page beside the fill.

  ⚠️ THE VALUES ARE `Kitsu/FE/src/styles.css`'s `.dark` BLOCK, NOT A SEPARATE SET. The names were
  always the browser's; the values are now too. That file carries the reasoning for each of them and
  is the source of truth — read it there before changing a number here.
 */

/**
 * The nine hues a household may mark state with.
 *
 * ⚠️ THE SAME NINE THE BROWSER OFFERS, by the same names and the same values. They are two separate
 * declarations in two languages with nothing enforcing the match, so the names are what a person
 * uses to check it: "Ember" here and "ember" there have to be the same colour or the two clients are
 * two products again.
 *
 * ⚠️ IT WAS FIVE — lime · electric · violet · raspberry · mint — AND THOSE ARE GONE, NOT RENAMED.
 * They were chosen under Harbour's rule that no accent may share a hue with a status, which is why
 * amber and orange were refused. Kitsu's ember IS that orange: it is the mark's own colour and the
 * default. The rule is superseded deliberately, because Kitsu's statuses are soft-filled chips
 * carrying a word rather than bare dots read across a room.
 *
 * ⚠️ [ink] IS PER PALETTE, NOT PER THEME. Ember and Wakaba are light enough to want a very dark ink
 * on them; the other seven want white. A rule keyed on the theme would get both of those wrong.
 *
 * @param hue  the fill: a primary button, the filled part of a bar, a checked box
 * @param over the same hue one step darker, for the pressed and hovered state
 * @param ink  the ink chosen to read ON the fill
 */
enum class Accent(val label: String, val hue: Color, val over: Color, val ink: Color) {
    Ember("Ember", Color(0xFFFF641B), Color(0xFFEF5710), Color(0xFF2B1206)),
    Kurenai("Kurenai", Color(0xFFD92B36), Color(0xFFC4212C), Color(0xFFFFFFFF)),
    Sakura("Sakura", Color(0xFFE62D72), Color(0xFFCF2162), Color(0xFFFFFFFF)),
    Fuji("Fuji", Color(0xFF8B5CF6), Color(0xFF7A4AE8), Color(0xFFFFFFFF)),
    Ao("Ao", Color(0xFF2F7BF5), Color(0xFF2268DD), Color(0xFFFFFFFF)),
    Mizu("Mizu", Color(0xFF0E8B96), Color(0xFF0B757F), Color(0xFFFFFFFF)),
    Matcha("Matcha", Color(0xFF2D9459), Color(0xFF26804B), Color(0xFFFFFFFF)),
    Wakaba("Wakaba", Color(0xFF7DA818), Color(0xFF6B9214), Color(0xFF17200A)),

    /**
     * ⚠️ THE ONE PALETTE WITH NO HUE, and it is a warm graphite rather than a grey — against warm
     * neutrals a true grey reads as a disabled control, which is the one thing an accent must never
     * look like.
     */
    Sumi("Sumi", Color(0xFF7D746F), Color(0xFF6B625D), Color(0xFFFFFFFF)),
}

/**
 * Which one is on.
 *
 * ⚠️ TOP-LEVEL STATE RATHER THAN A CompositionLocal, deliberately. A local would be the tidier
 * Compose answer and would require every one of the `Kitsu.accent` call sites to become a
 * `@Composable` read with a provider above it — including the ones inside `drawBehind` and inside
 * plain functions that return a `Border`. The choice is installation-wide and there is exactly one
 * screen showing at a time, so a single observable value is the honest shape.
 *
 * ⚠️ It is NOT persisted from here. `AccentStore` does that, because a theme file that reaches for
 * SharedPreferences needs a Context and stops being a theme file.
 */
val chosenAccent = androidx.compose.runtime.mutableStateOf(Accent.Ember)

object Kitsu {

    /* ── The ground, and the three steps above it ─────────────────────────
       Four steps of warm near-black, close together on purpose: they sit within a few points of
       luminance of each other, so depth is drawn by a hairline rather than by a change of colour —
       which is what keeps a poster the brightest thing on the screen. */
    val background = Color(0xFF121114)
    val backgroundElevated = Color(0xFF1A181B)
    val backgroundSunken = Color(0xFF211E22)
    val backgroundOverlay = Color(0xFF1A181B)

    /**
     * The rail's own ground.
     *
     * ⚠️ NOT THE WELL. Using `backgroundSunken` for it — which is what Harbour did — puts the rail
     * on the same surface as a table header and loses the separation the design draws with one
     * hairline.
     */
    val side = Color(0xFF171518)
    val sideLine = Color(0xFF2E2A30)

    val foreground = Color(0xFFF3F0EE)
    val foregroundMuted = Color(0xFFA8A2A8)
    val foregroundSubtle = Color(0xFF847E86)

    val border = Color(0xFF2A262C)
    val borderStrong = Color(0xFF3A353C)
    val borderHover = Color(0xFF565059)

    val row = Color(0xFF232027)
    val media = Color(0xFF252229)
    val track = Color(0xFF2A262C)

    /**
     * The chosen hue — the one primary action, the filled part of a bar.
     *
     * ⚠️ A PROPERTY, NOT A CONSTANT, and that is what makes it switchable. Hundreds of places read
     * `Kitsu.accent`. Backing it with a `get()` over a mutable state means every one of them keeps
     * its call unchanged AND recomposes when the choice changes; a `val` would need each of those
     * call sites rewritten to take a parameter, and a `var` without the state would change the
     * value while the screen kept the old one painted.
     *
     * ⚠️ IT IS THE FILL AND IS NOT READABLE AS TEXT. Anything writing a word or drawing a glyph in
     * the accent takes [accentText]. Getting these two the wrong way round is invisible on Ao and
     * unreadable on Ember.
     */
    val accent: Color get() = chosenAccent.value.hue

    /** The same hue one step darker — a pressed primary. */
    val accentHover: Color get() = chosenAccent.value.over

    /** ⚠️ The ink that reads ON the fill, and it is the palette's own, not the theme's. */
    val accentForeground: Color get() = chosenAccent.value.ink

    /**
     * The same hue used AS INK — the focus ring, an eyebrow, a link, a glyph.
     *
     * ⚠️ MIXED TOWARDS WHITE, which is the whole reason it is not [accent]. In the browser this
     * token mixes towards BLACK on a light ground and towards WHITE on a dark one; a television has
     * only the dark ground, so only the lightening half exists here.
     */
    val accentText: Color get() = mixOklab(chosenAccent.value.hue, Color.White, 0.70f)

    /** A filled chip or a selected row in the accent, quiet enough to carry ink. */
    val accentSoft: Color get() = mixOklab(chosenAccent.value.hue, backgroundElevated, 0.16f)

    /** The faintest fill the accent makes — a hovered row, a wash behind a heading. */
    val accentWash: Color get() = mixOklab(chosenAccent.value.hue, backgroundElevated, 0.07f)

    /** A hairline in the accent: the edge of a soft-filled chip. */
    val accentLine: Color get() = mixOklab(chosenAccent.value.hue, backgroundElevated, 0.46f)

    /** The accent over artwork — a scrim tint rather than a fill, so alpha rather than a mix. */
    val accentTint: Color get() = chosenAccent.value.hue.copy(alpha = 0.22f)

    /**
     * The inverted surface: a pill over a poster, a tooltip, anything that wants to be the brightest
     * thing on a dark page.
     *
     * ⚠️ It is NOT the accent and must not become it. Accent answers "which of these is live"; ink
     * answers "this one is selected". Merging them leaves no way to show a chosen row that is not
     * also the current one.
     */
    val ink = Color(0xFFF3F0EE)
    val inkForeground = Color(0xFF121114)
    val inkMuted = Color(0xFF847E86)

    /* ── Status. Lifted rather than brightened: a saturated green on near-black glows and pulls the
       eye off the content. Always paired with a word, never the only signal. ── */
    val ok = Color(0xFF6ED3A1)
    val okBackground = Color(0xFF16281F)
    val warn = Color(0xFFE8B877)
    val warnBackground = Color(0xFF2A2016)
    val danger = Color(0xFFF39A94)
    val dangerBackground = Color(0xFF2C1917)

    val rating = Color(0xFFCF9A3A)

    /** ⚠️ The chosen hue too — how far a film has been watched is a live thing, not a neutral. */
    val progress: Color get() = chosenAccent.value.hue
    val scrim = Color(0xCC050406)

    /* ── Categorical: equal weight, never a severity. The three statuses above own severity, and
       the two families never meet on one mark. ── */
    val kindFilm = Color(0xFFD99B45)
    val kindSeries = Color(0xFF9585E0)
    val kindEpisode = Color(0xFFB0A4EA)
    val kindPerson = Color(0xFFDC6F9E)
    val kindCollection = Color(0xFFDD8163)
    val kindGenre = Color(0xFF9598A1)

    /** A face keeps its own hue, so a profile is not mistaken for something that is live. */
    val face = Color(0xFF4A3524)
    val faceForeground = Color(0xFFF0CFAE)

    /**
     * The deck: the command bar's surface.
     *
     * ⚠️ It is the RAIL'S ground plus a hairline, not a second palette. It used to carry its own
     * navy so it would be recognised — and a surface that has to be recognised by its colour is a
     * surface that is not recognised by its shape.
     */
    val deck = Color(0xFF171518)
    val deckElevated = Color(0xFF211E22)
    val deckForeground = Color(0xFFF3F0EE)
    val deckMuted = Color(0xFFA8A2A8)
    val deckBorder = Color(0xFF2E2A30)

    /* ── The mark's three planes ──────────────────────────────────────────
       ⚠️ TOKENS RATHER THAN THE GRADIENTS THE SVG FILE CARRIES. `Html/KitsuUI/logo.svg` states its
       three planes as two linear gradients and a cream, which is right for a file handed to
       somebody; inside the product the mark follows whichever of nine palettes is on, and a
       gradient baked into the drawing follows none of them.

       The fold, its darker fold-back, and the pale face the cut opens onto. Lifted for a dark room,
       exactly as the browser's `.dark` block lifts them. */
    val markFold: Color get() = chosenAccent.value.hue
    val markBack: Color get() = mixOklab(chosenAccent.value.hue, Color(0xFF3D0E00), 0.68f)
    val markFace: Color get() = mixOklab(chosenAccent.value.hue, Color(0xFFFFEEDA), 0.20f)

    /* ── The frame. Values are 1080p pixels as dp — Android TV renders a 1080p panel at 320 dpi, so
       1dp is 2px there; these are halved from the CSS accordingly. ── */
    val gutter = 32.dp
    val deckHeight = 48.dp
    val tileGap = 12.dp
    val posterWidth = 108.dp
    val stillWidth = 200.dp
    val focusRingWidth = 2.dp

    /**
     * How far OUTSIDE the control's own edge the ring is drawn.
     *
     * ⚠️ IT WAS 3dp AND THE RING HAD NOWHERE TO GO. Drawn outside, the ring's footprint is
     * `inset + width` beyond the edge on every side — 5dp at the old figures. Rows of controls were
     * spaced 2–6dp apart, so a focused chip's ring crossed its neighbour's border and the two drew
     * over each other. It reads as a rendering fault and it is arithmetic: two neighbours need
     * TWICE the footprint between them.
     */
    val focusRingInset = 2.dp

    /**
     * The gap between two things a remote can land on, side by side.
     *
     * ⚠️ TWICE THE RING'S FOOTPRINT, and that is the whole derivation: each neighbour's ring claims
     * `focusRingInset + focusRingWidth` outward, so anything less than the sum of both overlaps.
     */
    val focusGap = 10.dp

    const val focusLift = 1.06f
}

/* ==========================================================================
   FOUR RADII, AND THEY ARE NOT ZERO.

   ⚠️ HARBOUR SET EVERY CORNER TO ZERO, which is why `RoundedCornerShape` is absent from almost all
   of this client's markup: there was nothing to ask for. Bringing Kitsu's corners back is therefore
   NOT achieved here — it is achieved by the components asking for these. This file is the
   vocabulary; `ui/components` is where it is spoken.

   ⚠️ THE NUMBERS ARE THE BROWSER'S OWN, IN dp RATHER THAN px, AND THAT IS NOT A COINCIDENCE. The
   television's CSS is drawn at twice the browser's scale and 1dp is 2px on a 1080p panel, so the
   two halvings cancel: Kitsu's 6px control corner is a 6dp control corner here, and the same corner
   arrives at the same apparent size on both screens.
   ========================================================================== */

/** A chip, a thumbnail, a swatch. */
val ChipShape = RoundedCornerShape(4.dp)

/**
 * A count riding inside a control — the episodes in a season, the files in a folder.
 *
 * ⚠️ TIGHTER THAN THE CONTROL IT SITS IN, which is the whole reason it is its own token. A
 * compartment at the host's own radius reads as a second control of the same kind that has been
 * pasted inside the first; one step in reads as part of it. It is never used on its own.
 */
val CountShape = RoundedCornerShape(3.dp)

/** Every control: a button, a field, a row a remote can land on. */
val ControlShape = RoundedCornerShape(6.dp)

/** A surface: a panel, a card, a node tile, an empty state. */
val SurfaceShape = RoundedCornerShape(8.dp)

/** Media: a poster, a still, a portrait, the player's stage. */
val MediaShape = RoundedCornerShape(10.dp)

/** A dialog. */
val DialogShape = RoundedCornerShape(12.dp)

/*
  Inter ships as ONE variable file. Android does not read a weight off a variable font by itself —
  every Font() here would otherwise render at the file's default instance — so each weight names its
  axis value explicitly.
 */
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight) = Font(
    resId = R.font.inter,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/**
 * The text face.
 *
 * ⚠️ NAMED FOR ITS ROLE, and this file is why that matters: the typeface behind this name has now
 * changed twice. It was Manrope, then Golos Text, and it is Inter. A name like `GolosFamily` would
 * have been false at every one of its call sites the moment the file changed.
 *
 * ⚠️ INTER BECAUSE IT IS KITSU'S OWN FACE, not because of coverage. Golos Text covers Cyrillic and
 * nothing was broken; the design system is drawn in Inter and ships `inter-cyrillic.woff2` beside
 * its latin, and a design system worn everywhere but in its typeface is worn in one place less than
 * everywhere. Inter's Cyrillic is complete, so the reason Golos Text was picked over Manrope — a
 * humanist Cyrillic for an interface read mostly in Ukrainian — is not lost by the change.
 */
val TextFamily = FontFamily(
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold),
    inter(FontWeight.ExtraBold),
    inter(FontWeight.Black),
)

@OptIn(ExperimentalTextApi::class)
private fun jetBrainsMono(weight: FontWeight) = Font(
    resId = R.font.jetbrains_mono,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/**
 * The data face — runtimes, sizes, codes, addresses.
 *
 * ⚠️ NAMED FOR ITS ROLE, NOT FOR THE TYPEFACE, for the same reason [TextFamily] is.
 *
 * ⚠️ DM MONO WAS REPLACED BECAUSE IT HAS NO CYRILLIC. Not a preference: `KitsuType.meta` is this
 * family and is used across the whole product, so any Ukrainian string reaching it fell out to the
 * system face and the screen was in two typefaces at once. JetBrains Mono is the same face the
 * browser uses — one data face across both clients, which is the point of having one at all.
 */
val DataFamily = FontFamily(
    jetBrainsMono(FontWeight.Normal),
    jetBrainsMono(FontWeight.Medium),
)

/**
 * The type scale, in sp at the television's 2px-per-dp density: the CSS 28px body is 14sp here, the
 * 60px hero 30sp, and so on down the same ladder.
 *
 * ⚠️ NOTHING READABLE BELOW 11sp. That is the floor the nine mockups were drawn to and it is about
 * the room rather than about the panel — 22 physical pixels is what survives being read from a sofa.
 */
object KitsuType {
    /**
     * ⚠️ MUTED, AND THAT IS THE RULE RATHER THAN A SHADE. An eyebrow differs by CASE, TRACKING and
     * WEIGHT, never by colour. It used to be the aqua, which made a six-character label the loudest
     * small thing on the screen.
     */
    val eyebrow = TextStyle(
        fontFamily = TextFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.14.em,
        color = Kitsu.foregroundMuted,
    )
    val meta = TextStyle(fontFamily = DataFamily, fontSize = 12.sp, color = Kitsu.foregroundMuted)
    val caption = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    val body = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
    val lead = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 21.sp, color = Kitsu.foregroundMuted)
    val h2 = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = (-0.01).em, color = Kitsu.foreground)
    val h1 = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.02).em, color = Kitsu.foreground)
    val hero = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.025).em, color = Kitsu.foreground)

    /**
     * The wordmark, beside the mark — on the splash and on the gate.
     *
     * ⚠️ THE HEAVIEST WEIGHT, TRACKING PULLED TO −0.055em — the browser's own lockup, exactly. It is
     * the one place in this client that type is used as a picture rather than as a label.
     */
    val wordmark = TextStyle(
        fontFamily = TextFamily,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.055).em,
        color = Kitsu.foreground,
    )
    val code = TextStyle(fontFamily = DataFamily, fontWeight = FontWeight.Medium, fontSize = 64.sp, letterSpacing = 0.1.em, color = Kitsu.foreground)

    /* No colour: a button's label takes the surface's content colour, which flips on the primary fill. */
    val button = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
}

private val colourScheme = darkColorScheme(
    primary = Kitsu.accent,
    onPrimary = Kitsu.accentForeground,
    secondary = Kitsu.accentText,
    onSecondary = Kitsu.accentForeground,
    background = Kitsu.background,
    onBackground = Kitsu.foreground,
    surface = Kitsu.background,
    onSurface = Kitsu.foreground,
    surfaceVariant = Kitsu.backgroundElevated,
    onSurfaceVariant = Kitsu.foregroundMuted,
    border = Kitsu.border,
    error = Kitsu.danger,
    onError = Kitsu.foreground,
)

private val typography = Typography(
    displayLarge = KitsuType.hero,
    displayMedium = KitsuType.h1,
    headlineMedium = KitsuType.h2,
    titleMedium = KitsuType.lead,
    bodyLarge = KitsuType.body,
    bodyMedium = KitsuType.body,
    labelLarge = KitsuType.button,
    labelMedium = KitsuType.caption,
    labelSmall = KitsuType.eyebrow,
)

@Composable
fun KitsuTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colourScheme,
        typography = typography,
    ) {
        /* Text outside any Surface would otherwise take the library's default content colour, which
           is not Kitsu's ink. */
        CompositionLocalProvider(LocalContentColor provides Kitsu.foreground, content = content)
    }
}

val SingleLine = TextOverflow.Ellipsis

/* ==========================================================================
   Mixing two colours the way the browser does.

   ⚠️ IN OKLAB, NOT IN sRGB, AND THE DIFFERENCE IS VISIBLE ON EXACTLY THE TOKENS THAT MATTER. Every
   derived accent value in `styles.css` is a `color-mix(in oklab, …)`, and a straight channel-wise
   blend of Ember towards white passes through a chalky pink that oklab does not: `accentText` is
   the ring, the eyebrow and every accent-coloured glyph, so getting it approximately right is
   getting the most-seen colour in the interface approximately right.

   ⚠️ FIVE TOKENS ARE COMPUTED WITH THIS PER READ rather than precomputed into the enum, because
   nine palettes × five derivations is forty-five hand-maintained values that can disagree with the
   browser one at a time. The arithmetic is a few dozen floating-point operations and it runs when a
   colour is asked for, not per frame.
   ========================================================================== */

/** sRGB's transfer function, and its inverse — oklab is defined over LINEAR light. */
private fun toLinear(channel: Float): Float =
    if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

private fun toGamma(channel: Float): Float =
    if (channel <= 0.0031308f) channel * 12.92f else 1.055f * channel.pow(1f / 2.4f) - 0.055f

private class Oklab(val lightness: Float, val greenRed: Float, val blueYellow: Float)

private fun Color.toOklab(): Oklab {
    val redLinear = toLinear(red)
    val greenLinear = toLinear(green)
    val blueLinear = toLinear(blue)

    val long = cbrt(0.4122214708f * redLinear + 0.5363325363f * greenLinear + 0.0514459929f * blueLinear)
    val medium = cbrt(0.2119034982f * redLinear + 0.6806995451f * greenLinear + 0.1073969566f * blueLinear)
    val short = cbrt(0.0883024619f * redLinear + 0.2817188376f * greenLinear + 0.6299787005f * blueLinear)

    return Oklab(
        lightness = 0.2104542553f * long + 0.7936177850f * medium - 0.0040720468f * short,
        greenRed = 1.9779984951f * long - 2.4285922050f * medium + 0.4505937099f * short,
        blueYellow = 0.0259040371f * long + 0.7827717662f * medium - 0.8086757660f * short,
    )
}

private fun Oklab.toColor(alpha: Float): Color {
    val long = (lightness + 0.3963377774f * greenRed + 0.2158037573f * blueYellow).let { it * it * it }
    val medium = (lightness - 0.1055613458f * greenRed - 0.0638541728f * blueYellow).let { it * it * it }
    val short = (lightness - 0.0894841775f * greenRed - 1.2914855480f * blueYellow).let { it * it * it }

    return Color(
        red = toGamma(4.0767416621f * long - 3.3077115913f * medium + 0.2309699292f * short).coerceIn(0f, 1f),
        green = toGamma(-1.2684380046f * long + 2.6097574011f * medium - 0.3413193965f * short).coerceIn(0f, 1f),
        blue = toGamma(-0.0041960863f * long - 0.7034186147f * medium + 1.7076147010f * short).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

/**
 * `color-mix(in oklab, [first] [share]%, [second])`.
 *
 * @param share how much of [first] survives, 0..1 — the same number the CSS percentage states
 */
internal fun mixOklab(first: Color, second: Color, share: Float): Color {
    val one = first.toOklab()
    val other = second.toOklab()
    val rest = 1f - share

    return Oklab(
        lightness = one.lightness * share + other.lightness * rest,
        greenRed = one.greenRed * share + other.greenRed * rest,
        blueYellow = one.blueYellow * share + other.blueYellow * rest,
    ).toColor(first.alpha * share + second.alpha * rest)
}
