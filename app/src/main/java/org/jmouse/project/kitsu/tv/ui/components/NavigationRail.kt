package org.jmouse.project.kitsu.tv.ui.components

import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.snap
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import org.jmouse.project.kitsu.tv.ui.theme.ChipShape
import org.jmouse.project.kitsu.tv.ui.theme.ControlShape
import org.jmouse.project.kitsu.tv.ui.theme.DataFamily
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType

/*
  ==============================================================================
  The library's navigation — a rail down the left edge of every page.

  ⚠️ IT REPLACED THE DECK ACROSS THE TOP, and the reason is the room, not the
  fashion. A bar across a 16:9 screen spends its scarcest dimension — height —
  on six words that are read once, and every wall of posters lost a row to it.
  The left edge is where Android TV keeps its navigation, where a thumb pressing
  LEFT already expects it, and where a column of icons costs 64dp of width the
  page has plenty of.

  ⚠️ COLLAPSED TO ICONS, OPEN WITH WORDS WHILE IT HOLDS THE FOCUS. Closed, it is
  a quiet strip the page ignores; the moment the cursor enters, it widens OVER
  the page — never pushing it aside, so nothing under it reflows — and shows
  the labels, whose household this is and which face is watching. RIGHT hands
  the cursor back to where it was on the page.

  ⚠️ ENTERING LANDS ON THE CURRENT SECTION, not on whichever icon happens to be
  nearest. From the foot of a long wall, LEFT would otherwise open the rail on
  Search, and the one question a person pressing LEFT is answering is "where am
  I, and where else can I go".
  ==============================================================================
 */

/** The rail's width while it is only a column of icons. */
val RailCollapsedWidth = 64.dp

/** The rail's width while it holds the focus and shows its words. */
private val RailExpandedWidth = 232.dp

private val RailItemHeight = 44.dp

private fun Bay.icon(): ImageVector = when (this) {
    Bay.Home -> Icons.Outlined.Home
    Bay.Films -> Icons.Outlined.Movie
    Bay.Series -> Icons.Outlined.Tv
    Bay.Titles -> Icons.Outlined.VideoLibrary
    Bay.Collections -> Icons.Outlined.Collections
    Bay.Search -> Icons.Outlined.Search
}

/**
 * The rail. Drawn over the page's left edge; the page itself starts [RailCollapsedWidth] in.
 *
 * @param current the section this page belongs to — marked, and where the cursor lands on entering
 * @param serverName which server this is, shown under the face while the rail is open
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LibraryRail(
    current: Bay?,
    onBay: (Bay) -> Unit,
    serverName: String?,
    profileInitials: String?,
    profilePicture: String?,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
    accountName: String? = null,
    profileName: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    /* Opening eases; closing SNAPS — RIGHT has to find the page beside a rail that is already narrow. */
    val width by animateDpAsState(
        targetValue = if (open) RailExpandedWidth else RailCollapsedWidth,
        animationSpec = if (open) tween(120) else snap(),
        label = "rail width",
    )
    val requesters = remember { Bay.entries.associateWith { FocusRequester() } }
    val profileRequester = remember { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .background(Kitsu.deck)
            .drawBehind {
                drawRect(Kitsu.deckBorder, topLeft = Offset(size.width - 1f, 0f), size = Size(1f, size.height))
            }
            .clipToBounds()
            .onFocusChanged { state -> open = state.hasFocus }
            .focusProperties {
                enter = { requesters[current] ?: requesters.getValue(Bay.Home) }
            }
            /* ⚠️ RIGHT CLOSES THE RAIL FIRST, THEN SEARCHES. The open rail lies over the page's left
               edge, so a search from it found nothing "to the right" and the cursor stayed in the
               rail. Closed at once, the rail sits beside the page again and the
               search finds the nearest thing level with the cursor. */
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    open = false
                    scope.launch {
                        withFrameNanos { }
                        withFrameNanos { }
                        focusManager.moveFocus(FocusDirection.Right)
                    }
                    true
                } else {
                    false
                }
            }
            /* ⚠️ A focus GROUP, or the enter rule above is never asked and LEFT lands on whichever
               icon sits level with the cursor. */
            .focusGroup()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(Kitsu.focusGap),
    ) {
        /* ⚠️ 26dp rather than 20: the rail is read from a sofa, and the lockup is the one thing on
           it that says which application this is. The word follows the mark by itself — see
           `BrandLockup` — so this is one number, not two kept in step. */
        Box(modifier = Modifier.height(44.dp).padding(start = 20.dp), contentAlignment = Alignment.CenterStart) {
            if (open) {
                BrandLockup(tint = Kitsu.deckForeground, muted = Kitsu.deckMuted, markSize = 26.dp)
            } else {
                /* ⚠️ The same mark either way. Collapsed, it is all there is, so shrinking it would
                   make the rail smallest exactly where it carries the most. */
                BrandMark(size = 26.dp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Bay.entries.forEach { bay ->
            RailItem(
                icon = bay.icon(),
                label = bay.label,
                current = bay == current,
                open = open,
                onClick = { onBay(bay) },
                modifier = Modifier.focusRequester(requesters.getValue(bay)),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        ProfileItem(
            open = open,
            initials = profileInitials,
            picture = profilePicture,
            accountName = accountName,
            profileName = profileName,
            serverName = serverName,
            onClick = onProfile,
            modifier = Modifier.focusRequester(profileRequester),
        )
    }
}

/** One section: an icon always, its word while the rail is open; the current one carries the tab. */
@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    current: Boolean,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(RailItemHeight)
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = label },
        shape = ClickableSurfaceDefaults.shape(ControlShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (current) Kitsu.deckElevated else Color.Transparent,
            focusedContainerColor = Kitsu.deckElevated,
            contentColor = if (current) Kitsu.deckForeground else Kitsu.deckMuted,
            focusedContentColor = Kitsu.deckForeground,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.dp, Color.Transparent), shape = ControlShape),
            /* The shared ring, inset: the rail clips at its edges and an outset ring would be cut. */
            focusedBorder = focusRing(inset = 0.dp),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .then(if (current) Modifier.indexTab() else Modifier)
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            /* ⚠️ The rail's own step up, with the lockup's. A menu read from a sofa at `body` was
               the same size as a paragraph read from a desk; the icon rises with the word so the
               pair keeps its proportion. */
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            if (open) {
                Text(
                    text = label,
                    style = KitsuType.body.copy(
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The face at the foot: opens the profile page. Open, it names the household, the face and the server. */
@Composable
private fun ProfileItem(
    open: Boolean,
    initials: String?,
    picture: String?,
    accountName: String?,
    profileName: String?,
    serverName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .semantics {
                contentDescription = listOfNotNull(accountName, profileName ?: initials)
                    .joinToString(" › ")
                    .ifBlank { null }
                    ?.let { owner -> t("tv.deck.openProfileOf", "{owner}. Open your profile", "owner" to owner) }
                    ?: t("tv.deck.openProfile", "Open your profile")
            },
        shape = ClickableSurfaceDefaults.shape(ControlShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Kitsu.deckElevated,
            contentColor = Kitsu.deckForeground,
            focusedContentColor = Kitsu.deckForeground,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.dp, Color.Transparent), shape = ControlShape),
            focusedBorder = focusRing(inset = 0.dp),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileFace(initials = initials, picture = picture, size = 38.dp)
            if (open) {
                /*
                  ⚠️ ONE NAME AND ONE CONTEXT LINE — it used to be three texts of near-equal weight.

                  The account sat above the profile and the server below it, each a line of its own,
                  so «Owner / Boss / KitsuLocal» read at three metres as a list of three unrelated
                  words with no way to tell which of them was the person. The question this row
                  answers is WHO IS WATCHING; the account and the server are where that person is,
                  which is one fact, not two, and it belongs under the name rather than around it.

                  ⚠️ The face carries the weight now — 38dp against the name — because on a rail
                  read from a sofa a picture is legible at a distance a caption is not.
                 */
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = profileName ?: initials ?: t("tv.deck.profile", "Profile"),
                        style = KitsuType.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    /* ⚠️ Joined rather than stacked, and each half is optional: a household with one
                       account and one server should not get two lines saying so. */
                    listOfNotNull(accountName, serverName)
                        .joinToString(" · ")
                        .takeIf { it.isNotBlank() }
                        ?.let { where ->
                            Text(
                                text = where,
                                style = KitsuType.meta.copy(color = Kitsu.deckMuted),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                }
            }
        }
    }
}

/** A profile's face: its picture, or its initials on the accent in the data face. */
@Composable
fun ProfileFace(initials: String?, picture: String?, size: Dp) {
    /*
      ⚠️ THE FACE'S OWN HUE, NOT THE ACCENT, AND THIS WAS A REAL CONFUSION RATHER THAN A SHADE. The
      tile sat at the foot of the rail filled with `Kitsu.accent` — the same fill the ACTIVE bay
      above it wears — so the brightest two things on the rail were "where you are" and "who you
      are", in one colour, saying two unrelated things. Kitsu keeps `face` separate from `accent`
      exactly so a profile is never mistaken for something that is live.

      ⚠️ A picture still fills the whole tile. The ground is only what shows when there is none.
     */
    Box(
        modifier = Modifier.size(size).background(Kitsu.face, ChipShape),
        contentAlignment = Alignment.Center,
    ) {
        if (picture != null) {
            AsyncImage(model = picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                text = initials ?: "?",
                style = KitsuType.caption.copy(fontFamily = DataFamily, fontWeight = FontWeight.SemiBold, color = Kitsu.faceForeground),
            )
        }
    }
}
