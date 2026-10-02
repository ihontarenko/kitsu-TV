package org.jmouse.project.kitsu.tv.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Rocket
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The television's half of the icon vocabulary.
 *
 * <h2>⚠️ THE SERVER OWNS THE NAMES; THIS OWNS THE DRAWINGS</h2>
 *
 * The set is `NodeIcon` in the backend, served so a screen offers the enum rather than a copy. What
 * a name LOOKS like is each client's own business: the browser draws from lucide, this draws from
 * Material, and the two will not always find the same picture. That is why the names describe the
 * subject — `CRIME`, not `handcuffs` — and why this file is the only place the television decides
 * what crime looks like.
 *
 * <h2>⚠️ NULL IS AN ANSWER, AN UNKNOWN NAME IS NOT</h2>
 *
 * A node with no icon returns null and the caller draws a plain button — that is the ordinary state
 * and it is not a failure. An unknown NAME, though, can only mean the server's vocabulary grew and
 * this file did not, so it is worth seeing rather than smoothing over: it draws the tag as a
 * last resort, and the `when` below is written over the wire names so the gap is at least visible
 * in one place instead of scattered through the screens.
 */
fun nodeIcon(name: String?): ImageVector? = when (name?.trim()?.lowercase()) {
    null, "" -> null

    "action" -> Icons.Outlined.LocalFireDepartment
    "adventure" -> Icons.Outlined.Explore
    "animation" -> Icons.Outlined.AutoAwesome
    "comedy" -> Icons.Outlined.TheaterComedy
    "crime" -> Icons.Outlined.Gavel
    "documentary" -> Icons.Outlined.MenuBook
    "drama" -> Icons.Outlined.TheaterComedy
    "family" -> Icons.Outlined.FamilyRestroom
    "fantasy" -> Icons.Outlined.AutoAwesome
    "history" -> Icons.Outlined.HistoryEdu
    "horror" -> Icons.Outlined.DarkMode
    "kids" -> Icons.Outlined.ChildCare
    "music" -> Icons.Outlined.MusicNote
    "mystery" -> Icons.Outlined.Psychology
    "romance" -> Icons.Outlined.FavoriteBorder
    "science-fiction" -> Icons.Outlined.Rocket
    "sport" -> Icons.Outlined.SportsSoccer
    "thriller" -> Icons.Outlined.Bolt
    "war" -> Icons.Outlined.Shield
    "western" -> Icons.Outlined.Terrain

    "film" -> Icons.Outlined.Movie
    "series" -> Icons.Outlined.Tv
    "collection" -> Icons.Outlined.Layers
    "folder" -> Icons.Outlined.Folder
    "person" -> Icons.Outlined.Person
    "studio" -> Icons.Outlined.Business
    "country" -> Icons.Outlined.Public
    "year" -> Icons.Outlined.CalendarMonth
    "tag" -> Icons.Outlined.LocalOffer

    /* ⚠️ A name this build has never heard of. Drawn as a tag so the button still reads as a
       button, rather than collapsing to a nameless rectangle nobody can explain. */
    else -> Icons.Outlined.LocalOffer
}
