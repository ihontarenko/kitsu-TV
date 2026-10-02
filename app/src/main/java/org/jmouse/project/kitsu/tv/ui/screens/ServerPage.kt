package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.formatDateTime
import org.jmouse.project.kitsu.tv.text.t
import org.jmouse.project.kitsu.tv.text.plural
import org.jmouse.project.kitsu.tv.text.formatInteger
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.jmouse.project.kitsu.tv.BuildConfig
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.ServerDescription
import org.jmouse.project.kitsu.tv.data.api.Session
import org.jmouse.project.kitsu.tv.data.build.BuildMark
import org.jmouse.project.kitsu.tv.ui.DeckState
import org.jmouse.project.kitsu.tv.ui.components.BadgeTone
import org.jmouse.project.kitsu.tv.ui.components.BrandLockup
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.components.TvBadge
import org.jmouse.project.kitsu.tv.ui.components.rememberDeliberatePress
import org.jmouse.project.kitsu.tv.ui.components.tracksDeliberatePress
import org.jmouse.project.kitsu.tv.ui.components.revealsPageEdges
import org.jmouse.project.kitsu.tv.ui.theme.DataFamily
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType
import org.jmouse.project.kitsu.tv.update.AppUpdater

/**
 * Where this television is connected, and what is running at both ends.
 *
 * <h2>⚠️ THE PROFILE PAGE'S SHAPE, ON PURPOSE</h2>
 *
 * It was a tall scroll — an update panel, eight blocks of facts, a paragraph and a button at the very
 * bottom — and "what version is this" took four presses to reach. It is the profile page's two
 * columns now: WHAT TO DO on the left, as a menu the cursor opens on, and what there is to know on
 * the right, in blocks small enough to read at once. The same page, asked from two ends: somebody
 * reporting a fault needs both builds in one glance.
 *
 * <h2>⚠️ A BUILD IS NAMED BY ITS RELEASE AND ITS CODE NAME</h2>
 *
 * The version may go down; the release number never does, and it is what an update is decided by. The
 * code name is what a person calls the build — "Stable", "x-mas build". An emoji hashed from the build
 * id stood here until 2026-09-25 and was dropped for it.
 *
 * <h2>⚠️ EVERY FIGURE IS READ, NEVER WRITTEN DOWN</h2>
 *
 * The version from `BuildConfig`, the address from the live connection, the hardware from
 * `android.os.Build`, the server's side from the server. What is not known is not claimed.
 */
@Composable
fun ServerScreen(
    connection: ServerConnection,
    deck: DeckState,
    session: Session,
    onChangeServer: () -> Unit,
    onLibraries: () -> Unit,
) {
    var installationName by remember { mutableStateOf<String?>(null) }
    var reachable by remember { mutableStateOf<Boolean?>(null) }
    var server by remember { mutableStateOf<ServerDescription?>(null) }

    LaunchedEffect(connection.address) {
        val answer = runCatching { connection.api.installation() }
        answer.onSuccess { installation ->
            installationName = installation.displayName
            reachable = true
        }
        answer.onFailure { reachable = false }
        server = runCatching { connection.api.server() }.getOrNull()
    }

    val context = LocalContext.current
    val decoders = remember { decodableFormats() }
    val applicationBuild = remember { BuildMark.identifier(context) }
    /* ⚠️ The cursor opens on the update row, never on "Change server" — the one row here that undoes
       the pairing, which a single OK on arriving must not reach. */
    val first = rememberInitialFocus()
    val pageScroll = rememberScrollState()

    Screen(deck = deck, current = null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .revealsPageEdges(pageScroll)
                .verticalScroll(pageScroll)
                .padding(horizontal = Kitsu.gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    /* ⚠️ NOT AN EYEBROW. An eyebrow upper-cases, and an address in capitals is an
                       address nobody can copy by eye — the name in the case it was given, the
                       address in mono, as it would be typed. */
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = t("tv.server.connectedTo", "Connected to"), style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle))
                        Text(
                            text = installationName ?: connection.displayName ?: t("tv.server.theServer", "the server"),
                            style = KitsuType.caption.copy(color = Kitsu.foreground, fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = connection.address.removeSuffix("/"),
                            style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle, fontFamily = DataFamily),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = t("tv.profile.server", "Server and build"), style = KitsuType.h1)
                        /* ⚠️ Three states, not two: null is "still asking", and drawing it as a
                           failure for the second it takes makes a working server flicker. */
                        when (reachable) {
                            true -> TvBadge(text = t("tv.server.answering", "Answering"), tone = BadgeTone.Ok)
                            false -> TvBadge(text = t("tv.server.notAnswering", "Not answering"), tone = BadgeTone.Danger)
                            null -> TvBadge(text = t("tv.server.asking", "Asking…"), tone = BadgeTone.Muted)
                        }
                    }
                }
            }

            /*
              ⚠️ THE UPDATE IS THE PAGE'S SUBJECT, so it sits at the top and spans it.

              It used to be the first of three rows in a 340dp column beside a wide table, which made
              the one thing somebody opens this page to do the narrowest element on it — and put it
              level with "Change server", which undoes the pairing. Position is the strongest thing
              this interface has; giving the update the full width above everything says what the
              page is for before a word is read.
             */
            UpdateAction(
                connection = connection,
                modifier = Modifier.fillMaxWidth().focusRequester(first),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(modifier = Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow(t("tv.profile.fromHere", "From here"), muted = true)
                    val libraries = server?.libraries.orEmpty()
                    if (libraries.isNotEmpty()) {
                        PageAction(
                            icon = Icons.Outlined.Info,
                            title = t("tv.libraries.heading", "Libraries"),
                            detail = plural(
                                "tv.server.librariesDetail",
                                libraries.size,
                                "{count} library the server runs on, by family",
                                "{count} libraries the server runs on, by family",
                            ),
                            onClick = onLibraries,
                        )
                    }
                    ChangeServerAction(onChangeServer)

                    /*
                      ⚠️ THE DEVICE'S OWN FACTS GO UNDER ITS OWN BUTTONS, and that is not shuffling.

                      Two or three rows in a fixed 340dp column left a column-deep hole beside a
                      table that was meanwhile wrapping its values — the page read as one dense half
                      and one empty one. And the facts belong here by SUBJECT: everything in this
                      column is about the television in front of you, everything in the other is
                      about the machine it talks to. Filing them by what they are about is what
                      makes the split mean something rather than being where the space was.
                     */
                    Spacer(modifier = Modifier.height(14.dp))
                    CompactFactBlock(t("tv.server.thisDevice", "This device")) {
                        CompactFact(t("tv.server.model", "Model"), android.os.Build.MODEL)
                        CompactFact("Android", "${android.os.Build.VERSION.RELEASE} · API ${android.os.Build.VERSION.SDK_INT}")
                        val metrics = context.resources.displayMetrics
                        CompactFact(t("tv.server.screen", "Screen"), "${metrics.widthPixels}×${metrics.heightPixels}")
                        CompactFact(t("tv.server.player", "Player"), "Media3 ${androidx.media3.common.MediaLibraryInfo.VERSION}")
                        CompactFact(t("tv.server.decodes", "Decodes"), decoders)
                    }
                }

                /* ⚠️ ONE COLUMN OF WIDE ROWS, NOT FOUR NARROW ONES. A television is 960dp across; four
                   blocks beside a menu left each value a hundred, and every version wrapped in two. */
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    BuildsTable(
                        application = BuildColumn(
                            version = BuildConfig.VERSION_NUMBER,
                            release = BuildConfig.RELEASE.toString(),
                            codeName = BuildConfig.CODE_NAME,
                            commit = BuildConfig.GIT_COMMIT,
                            builtAt = BuildMark.builtAt(applicationBuild),
                        ),
                        server = server?.application?.let { application ->
                            BuildColumn(
                                version = listOfNotNull(application.name, application.version).joinToString(" "),
                                release = null,
                                codeName = null,
                                commit = application.commit?.let { hash -> hash + if (application.dirty) " *" else "" },
                                builtAt = formatDateTime(application.builtAt),
                            )
                        },
                    )
                    /*
                      ⚠️ ONE GRID DOWN THE WHOLE COLUMN, and that is the point of the spacer.

                      The builds table is `[label 72dp][value][value]` with 12dp between; these two
                      blocks were `[block][block]` with 28dp between and no label gutter, so their
                      headings and every value in them sat at x-positions the table above never used.
                      Two tables of facts, one under the other, each with its own vertical rules,
                      reads as a page assembled out of parts — which is what it was.

                      The spacer stands in for the label column, the gap matches, and both halves are
                      `weight(1f)` exactly as the table's are: "This device" now begins where "This
                      app" does, and "Server machine" where "The server" does.
                     */
                    /* ⚠️ "This device" moved to the OTHER column, beside the buttons that act on it —
                       see the note there. What is left is about the machine at the other end, which
                       is what the whole of this column is about, so it keeps the label gutter and
                       begins where "The server" does. */
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spacer(modifier = Modifier.width(LABEL_WIDTH))
                        CompactFactBlock(t("tv.server.machine", "Server machine"), modifier = Modifier.weight(1f)) {
                            ServerMachineFacts(server)
                        }
                    }

                    /*
                      What the product is, and who made it.

                      ⚠️ THE LOCKUP, NOT A SENTENCE BEGINNING "Kitsu —". This was one grey caption
                      reading "Kitsu — a home media library. Made by …", set in the same ink as the
                      readings above it, which made the one thing on this page that is true of the
                      PRODUCT look like the last and least of the facts about this installation. The
                      mark says which product before a word is read, and it is the same lockup the
                      deck and the splash draw — the browser puts its own at the top of every screen.

                      ⚠️ AND IT SAYS BOTH HALVES. This page compares two builds all the way down and
                      never states the thing that makes the comparison sensible: the television client
                      and the server are one product, not an app and somebody else's backend. That is
                      exactly the sentence a person reporting a fault needs, and it belonged nowhere
                      else on this page.

                      ⚠️ ON THE SAME GRID AS EVERYTHING ABOVE IT — indented past the label gutter so
                      it starts where the values do, rather than floating at the column's left edge
                      where nothing else on this page begins.

                      ⚠️ NO LABEL. Giving it one would file it among readings that change.
                     */
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spacer(modifier = Modifier.width(LABEL_WIDTH))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BrandLockup(markSize = 32.dp)
                            Text(
                                text = t(
                                    "tv.server.productIs",
                                    "One product in two halves: this television, and the server it reads from.",
                                ),
                                style = KitsuType.caption.copy(color = Kitsu.foregroundMuted),
                            )
                            Text(
                                text = t("tv.server.madeBy", "Made by {author}.", "author" to AUTHOR),
                                style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One side of the builds table. */
private data class BuildColumn(
    val version: String?,
    /** The television's release number; the server has none, and says so with a dash. */
    val release: String?,
    val codeName: String?,
    val commit: String?,
    val builtAt: String?,
)

/**
 * The two builds, row against row — version, build, commit, time.
 *
 * ⚠️ A TABLE, because the question this page answers is a comparison: is the television as new as the
 * server, and are these the builds somebody just made. Two blocks side by side made the eye travel
 * between labels; one row per fact puts the two answers next to each other.
 */
@Composable
private fun BuildsTable(application: BuildColumn, server: BuildColumn?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BuildsRow(label = "", first = t("tv.libraries.thisApp", "This app"), second = t("tv.libraries.theServer", "The server"), heading = true)
        BuildsRow(t("tv.server.version", "Version"), application.version, server?.version)
        BuildsRow(t("tv.server.release", "Release"), application.release, server?.release)
        BuildsRow(t("tv.server.codeName", "Code name"), application.codeName, server?.codeName)
        /* ⚠️ No «Build» row: it showed a random per-build identifier that tells a person nothing the
           release number does not — removed 2026-10-01 at Ivan's request. */
        BuildsRow(t("tv.server.commit", "Commit"), application.commit, server?.commit)
        BuildsRow(t("tv.server.built", "Built"), application.builtAt, server?.builtAt)
    }
}

@Composable
private fun BuildsRow(label: String, first: String?, second: String?, heading: Boolean = false) {
    val valueStyle = if (heading) {
        KitsuType.caption.copy(color = Kitsu.foregroundSubtle)
    } else {
        KitsuType.meta.copy(color = Kitsu.foreground, fontFamily = DataFamily)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
            modifier = Modifier.width(LABEL_WIDTH),
        )
        Text(text = first ?: "—", style = valueStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(text = second ?: "—", style = valueStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** The machine the server runs on — absent rather than guessed when it did not answer. */
@Composable
private fun ServerMachineFacts(server: ServerDescription?) {
    if (server == null) {
        return
    }
    val runtime = server.runtime
    CompactFact("Java", runtime?.javaVersion)
    CompactFact(t("tv.server.system", "System"), runtime?.operatingSystem)
    CompactFact(t("tv.server.database", "Database"), server.components.firstOrNull { component -> component.role == "Database" }
        ?.let { component -> listOfNotNull(component.name, component.version?.substringBefore('-')).joinToString(" ") })
    CompactFact(
        t("tv.server.memory", "Memory"),
        runtime?.takeIf { it.heapMaxBytes > 0 }?.let { "${formatBytes(it.heapUsedBytes)} / ${formatBytes(it.heapMaxBytes)}" },
    )
    CompactFact(t("tv.server.upFor", "Up for"), runtime?.uptimeSeconds?.let { formatUptime(it) })
    CompactFact(
        t("tv.server.files", "Files"),
        server.media?.let { media ->
            t("tv.server.filesWaiting", "{files} · {waiting} waiting", "files" to formatInteger(media.files), "waiting" to formatInteger(media.awaitingInspection))
        },
    )
}

private val LABEL_WIDTH = 72.dp

/**
 * Who wrote this.
 *
 * ⚠️ A CONSTANT RATHER THAN A TRANSLATION VALUE, because a person's name is not translated — the
 * sentence around it is, and that is what `tv.server.madeBy` holds.
 */
private const val AUTHOR = "Ivan Hontarenko"

/**
 * Checking for, downloading and installing a newer version — one row that says what it will do next.
 *
 * ⚠️ ONE ROW, NOT A PANEL WITH A BUTTON THAT IS SWAPPED. A replaced button took the focus with it and
 * dropped the cursor into the deck; a row that changes its words keeps it.
 */
@Composable
private fun UpdateAction(connection: ServerConnection, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by AppUpdater.state.collectAsState()

    val title = when (val current = state) {
        is AppUpdater.State.Available -> t("tv.update.downloadInstall", "Download and install {version}", "version" to current.release.shownName.orEmpty()).trim()
        is AppUpdater.State.NeedsPermission -> t("tv.update.install", "Install {version}", "version" to current.release.shownName.orEmpty()).trim()
        AppUpdater.State.Checking -> t("tv.update.checking", "Checking for updates…")
        is AppUpdater.State.Downloading -> t("tv.update.downloadingPercent", "Downloading… {percent}%", "percent" to (current.fraction * 100).toInt())
        AppUpdater.State.Installing -> t("tv.update.installing", "Installing…")
        else -> t("tv.update.check", "Check for updates")
    }
    val busy = state is AppUpdater.State.Checking || state is AppUpdater.State.Downloading || state is AppUpdater.State.Installing

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PageAction(
            icon = if (state is AppUpdater.State.Available || state is AppUpdater.State.NeedsPermission) Icons.Outlined.Download else Icons.Outlined.Refresh,
            title = title,
            detail = describeUpdate(state),
            danger = state is AppUpdater.State.Failed,
            onClick = {
                when (val current = state) {
                    is AppUpdater.State.Available -> scope.launch { AppUpdater.install(context, connection, current.release) }
                    is AppUpdater.State.NeedsPermission -> scope.launch { AppUpdater.install(context, connection, current.release) }
                    else -> if (!busy) {
                        scope.launch { AppUpdater.check(connection) }
                    }
                }
            },
            modifier = modifier,
        )
        (state as? AppUpdater.State.Downloading)?.let { downloading ->
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(Kitsu.foreground.copy(alpha = 0.2f))) {
                Box(modifier = Modifier.fillMaxWidth(downloading.fraction).height(3.dp).background(Kitsu.progress))
            }
        }
    }
}

private fun describeUpdate(state: AppUpdater.State): String = when (state) {
    AppUpdater.State.Idle -> t("tv.update.idle", "Asks the server for a newer Kitsu. Releases are APKs in its Releases / Android TV folder.")
    AppUpdater.State.Checking -> t("tv.libraries.asking", "Asking the server…")
    is AppUpdater.State.UpToDate ->
        if (state.serverVersion == null) {
            t("tv.update.upToDate", "Up to date — the server has nothing newer.")
        } else {
            t("tv.update.upToDateNewest", "Up to date — the server's newest is {version}.", "version" to state.serverVersion)
        }
    is AppUpdater.State.Available -> t(
        "tv.update.available",
        "Release {release} · {size} — on the server now.",
        "release" to (state.release.release ?: state.release.versionCode),
        "size" to formatBytes(state.release.sizeBytes),
    )
    is AppUpdater.State.Downloading -> t("tv.update.downloading", "Downloading {version}.", "version" to state.release.shownName)
    is AppUpdater.State.NeedsPermission -> t("tv.update.needsPermission", "Allow installs from Kitsu in the settings that opened, then press again.")
    AppUpdater.State.Installing -> t("tv.update.confirm", "Confirm the install on screen. The app restarts as the new version.")
    is AppUpdater.State.Failed -> state.reason
}

/**
 * Forgetting this server — pressed twice.
 *
 * ⚠️ TWICE, because it undoes the pairing: the television goes back to finding a server and a code.
 * The first press says so and arms the row; moving off it disarms it.
 */
@Composable
private fun ChangeServerAction(onChangeServer: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    val press = rememberDeliberatePress()
    PageAction(
        icon = Icons.Outlined.Dns,
        title = if (armed) t("tv.server.changeConfirm", "Press again to change server") else t("tv.profiles.changeServer", "Change server"),
        detail = if (armed) {
            t("tv.server.changeWarning", "This television forgets the server and must be paired again.")
        } else {
            t("tv.server.changeDetail", "Connect this television to a different Kitsu.")
        },
        danger = armed,
        onClick = {
            if (!press.isDeliberate()) {
                return@PageAction
            }
            if (armed) {
                armed = false
                onChangeServer()
            } else {
                armed = true
            }
        },
        modifier = Modifier
            .tracksDeliberatePress(press)
            .onFocusChanged { focus ->
                if (!focus.hasFocus) {
                    armed = false
                }
            },
    )
}

/** A small titled block of facts — an eyebrow, not a heading, so four fit beside a menu. */
@Composable
private fun CompactFactBlock(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Eyebrow(title, muted = true)
        content()
    }
}

/** One label and one mono value; absent rather than empty when nothing is known. */
@Composable
private fun CompactFact(label: String, value: String?) {
    if (value.isNullOrBlank()) {
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = label,
            style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
            modifier = Modifier.width(LABEL_WIDTH),
        )
        Text(
            text = value,
            style = KitsuType.meta.copy(color = Kitsu.foreground, fontFamily = DataFamily),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
