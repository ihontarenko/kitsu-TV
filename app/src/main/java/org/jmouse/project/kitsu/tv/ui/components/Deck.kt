package org.jmouse.project.kitsu.tv.ui.components

import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.jmouse.project.kitsu.tv.ui.theme.DataFamily
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType

/**
 * The bays a person moves between.
 *
 * <h2>⚠️ SEVEN, NOT FOUR — the shelf was bigger than the way into it</h2>
 *
 * The television could reach films, series and a search box; the browser could also reach
 * collections, genres and the household's own list of kept titles. Everything behind those three
 * was already in the catalogue and already served by routes this application calls — it simply had
 * no door. A section that exists on one screen and not the other is not a smaller television
 * interface, it is a wrong one.
 *
 * <h2>⚠️ AND THE BAYS LOST THEIR ICONS, which is what made room for them</h2>
 *
 * Seven labelled icons do not fit a 1080p deck beside a brand, a server name and a profile. They
 * also do not help: at three metres a WORD is read faster than a sixteen-pixel glyph, and the glyph
 * was carrying no meaning the word did not already carry. Dropping them bought the width and made
 * the bar quieter in the same stroke.
 *
 * <h2>⚠️ AND THEN SIX — Favourites went into Collections</h2>
 *
 * A profile's favourites are a collection on the server, its own, made on the first heart. A bay
 * of their own beside Collections said there were two kinds of set when there is one; they now lead
 * the Collections wall, and the width they gave back is the profile's name beside its face.
 *
 * <h2>⚠️ AND SEVEN AGAIN — Titles, which is where a title is ADDED</h2>
 *
 * Films and Series are each half the library; Titles is all of it, and the one wall that carries
 * "Add a title" — the browser's Add, on the remote. Placed after Series because it is the same kind
 * of wall, widened, rather than a new kind of place.
 */
enum class Bay {
    Home,
    Films,
    Series,
    Titles,
    Collections,
    Search;

    /** The bay's name in the deck — asked each time it is drawn, so it follows the language. */
    val label: String
        get() = when (this) {
            Home -> t("tv.deck.home", "Home")
            Films -> t("tv.deck.films", "Films")
            Series -> t("tv.deck.series", "Series")
            Titles -> t("tv.deck.titles", "Titles")
            Collections -> t("tv.deck.collections", "Collections")
            Search -> t("tv.deck.search", "Search")
        }
}

/**
 * The mark and the wordmark, spelled as the browser spells them.
 *
 * <h2>⚠️ ONE LOCKUP, USED FIVE TIMES — it was five lockups, written five times</h2>
 *
 * The deck had one and the gate screens each had their own hand-built copy. Five copies of a brand
 * is five things to forget, and they had already drifted from the browser in two ways at once. There
 * is now one definition to change, and the mark inside it is {@link BrandMark} — the only file that
 * knows the geometry.
 *
 * <h2>⚠️ ONE INK AND ONE WORD, WHICH REVERSES A DECISION RATHER THAN LOSING ONE</h2>
 *
 * The wordmark used to be "m" muted against "hub" full, because the two halves of *mediahub* were a
 * real division of the word and Ivan asked for the first one greyer. *Kitsu* has no such seam:
 * splitting it anywhere would be inventing a division the name does not have, and the design system
 * this product now wears draws its lockup as a single word. The browser made the same change.
 *
 * <h2>⚠️ LOWERCASE, AND THAT IS THE DRAWING RATHER THAN A STYLE CHOICE</h2>
 *
 * `kitsu` at the heaviest weight with the tracking pulled in is the one piece of type in this
 * product that is a picture rather than a label. Capitalising it here and not in the browser would
 * be exactly the class of drift this one definition exists to stop.
 *
 * @param tint the ink; a gate screen sits on the page ground, the deck on its own darker one
 */
@Composable
fun BrandLockup(
    modifier: Modifier = Modifier,
    tint: Color = Kitsu.foreground,
    /**
     * ⚠️ KEPT THOUGH THE WORD NO LONGER USES IT. Five call sites pass it, and the day the lockup
     * grows a line under it — the server's name, which is what the browser's gate puts there — it is
     * the ink that line wants. Removing it and putting it back is two edits to five files.
     */
    muted: Color = Kitsu.foregroundMuted,
    markSize: Dp = 18.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrandMark(size = markSize)
        /*
          ⚠️ THE WORD IS SIZED BY THE MARK, not set to a step of the type scale.

          They were two independent numbers — a 20dp mark beside a fixed 16sp word — so raising the
          mark for one surface left the word where it was, and the lockup came apart at exactly the
          place it is most looked at. One number decides both now, and a lockup cannot drift.

          ⚠️ 0.9 is what the pair already was at the default: an 18dp mark beside `h2`'s 16sp. So
          every call site that does not name a `markSize` draws what it drew before, to a fifth of
          a point.
         */
        Text(
            text = wordmark(),
            style = KitsuType.h2.copy(
                color = tint,
                fontSize = (markSize.value * 0.9f).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.055).em,
            ),
        )
    }
}

/**
 * The product's name, drawn.
 *
 * ⚠️ NOT TRANSLATED AND NOT A STRING RESOURCE. It is the one piece of text in this client that is
 * the same in every language, because it is a mark rather than a label.
 */
const val WORDMARK = "kitsu"

/**
 * The wordmark with its full stop — the word in whatever ink the caller's style names, the stop in
 * the accent.
 *
 * <h2>⚠️ THE FULL STOP IS THE LOCKUP'S ONE PIECE OF COLOUR</h2>
 *
 * <p>The word is deliberately one ink — splitting *kitsu* anywhere would invent a seam the name does
 * not have, which is the decision recorded above. A stop after it invents nothing and gives the mark
 * somewhere to be the accent. The browser draws exactly this.
 *
 * <h2>⚠️ A FUNCTION RATHER THAN A SECOND CONSTANT, and that is this file's whole doctrine</h2>
 *
 * <p>Two places draw the word — the lockup here and the splash — and a plain string beside a
 * hand-built {@code AnnotatedString} is how one of them ends up without the stop for a release
 * nobody notices. {@link #WORDMARK} stays because the stop is a drawing, not a letter: anything that
 * needs the NAME (a log line, a file name, a content description) wants the word alone.
 *
 * <p>⚠️ {@code Kitsu.accent} RATHER THAN {@code accentText}. Glyphs take `accentText` because it is
 * the accent lightened until it reads on this client's grounds — which is the wrong value for a mark
 * that is supposed to be the brand's colour. Nothing is being read here; a pale stop is simply a
 * stop somebody forgot to colour.
 */
@Composable
fun wordmark(dot: Color = Kitsu.accent): AnnotatedString = buildAnnotatedString {
    append(WORDMARK)
    withStyle(SpanStyle(color = dot)) {
        append(".")
    }
}
