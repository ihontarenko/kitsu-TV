package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jmouse.project.kitsu.tv.data.Scheme
import org.jmouse.project.kitsu.tv.data.ServerPreset
import org.jmouse.project.kitsu.tv.data.ServerPresets
import org.jmouse.project.kitsu.tv.data.ServerAddress
import org.jmouse.project.kitsu.tv.data.api.ApiClient
import org.jmouse.project.kitsu.tv.data.discovery.PortScanner
import org.jmouse.project.kitsu.tv.data.discovery.ScanEvent
import org.jmouse.project.kitsu.tv.data.discovery.DiscoveredServer
import org.jmouse.project.kitsu.tv.data.discovery.ServerDiscovery
import org.jmouse.project.kitsu.tv.ui.components.BadgeTone
import org.jmouse.project.kitsu.tv.ui.components.ButtonWeight
import org.jmouse.project.kitsu.tv.ui.components.BrandLockup
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.components.TvBadge
import org.jmouse.project.kitsu.tv.ui.components.TvButton
import org.jmouse.project.kitsu.tv.ui.components.TvChip
import org.jmouse.project.kitsu.tv.ui.components.TvListRow
import org.jmouse.project.kitsu.tv.ui.components.Keyboard
import org.jmouse.project.kitsu.tv.ui.components.LetterPad
import org.jmouse.project.kitsu.tv.ui.theme.DataFamily
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType
import org.jmouse.project.kitsu.tv.ui.theme.SurfaceShape
import org.jmouse.project.kitsu.tv.ui.theme.ChipShape

/**
 * Find the server: a lamp on the left, the choice on the right. Servers seen over
 * mDNS are listed as they resolve; the manual address is always one press away,
 * because a guest network that isolates its clients hides the server entirely.
 */
@Composable
fun ConnectScreen(
    lastAddress: String?,
    problem: String?,
    discovery: ServerDiscovery,
    onChoose: (address: String, name: String?) -> Unit,
) {
    val found = remember { mutableStateListOf<DiscoveredServer>() }
    var manual by remember { mutableStateOf(problem != null) }

    LaunchedEffect(Unit) {
        discovery.discover().collect { server ->
            val already = found.indexOfFirst { it.host == server.host && it.port == server.port }
            if (already >= 0) {
                found[already] = server
            } else {
                found.add(server)
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Lamp(modifier = Modifier.weight(1f).fillMaxHeight())

        Column(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .padding(horizontal = Kitsu.gutter, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (manual) {
                ManualAddress(
                    initial = lastAddress,
                    problem = problem,
                    onConnect = { address -> onChoose(address, null) },
                    onBack = { manual = false },
                )
            } else {
                ServersFound(
                    found = found,
                    onChoose = { server -> onChoose(server.address, server.name) },
                    onPreset = { preset -> onChoose(preset.address, preset.name) },
                    onManual = { manual = true },
                )
            }
        }
    }
}

@Composable
private fun Lamp(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            /* ⚠️ NO GLOW. A radial wash of the accent sat here — decoration wearing the clothes of
               depth. With an achromatic accent it becomes a grey haze over a grey ground, which is
               worse than the colour it replaced. The panel is its own ground. */
            .background(Kitsu.backgroundSunken)
            .padding(horizontal = Kitsu.gutter, vertical = 40.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        BrandLockup()
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Eyebrow(t("tv.connect.eyebrow", "Films · series · people · files"))
            Text(text = t("tv.connect.heading", "Your films, on the television — straight from the server."), style = KitsuType.h1)
            Text(
                text = t("tv.connect.lead", "Nothing is copied here. The library lives on the server; this screen is a window on it."),
                style = KitsuType.lead,
            )
        }
        Eyebrow(t("tv.connect.staysHere", "Everything stays on this network"), muted = true)
    }
}

@Composable
private fun ServersFound(
    found: List<DiscoveredServer>,
    onChoose: (DiscoveredServer) -> Unit,
    onPreset: (ServerPreset) -> Unit,
    onManual: () -> Unit,
) {
    val presets = ServerPresets.all
    val first = remember { FocusRequester() }
    LaunchedEffect(found.isEmpty()) {
        runCatching { first.requestFocus() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = if (found.isEmpty()) t("tv.connect.looking", "Looking for a server") else t("tv.connect.choose", "Choose a server"), style = KitsuType.h2)
            Text(text = t("tv.connect.onThisNetwork", "On this network · {service}", "service" to "_kitsu._tcp"), style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle))
        }

        if (found.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Kitsu.border, SurfaceShape)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = t("tv.connect.nothingYet", "Nothing has answered yet."), style = KitsuType.lead.copy(color = Kitsu.foreground))
                Text(
                    text = t("tv.connect.nothingYetHint", "Make sure Kitsu is running on the server and both are on the same network. A network that isolates its devices hides the server — enter the address instead."),
                    style = KitsuType.body.copy(color = Kitsu.foregroundMuted),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                found.forEachIndexed { index, server ->
                    TvListRow(
                        onClick = { onChoose(server) },
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    ) {
                        Box(
                            modifier = Modifier.size(28.dp).background(Kitsu.backgroundOverlay, ChipShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(imageVector = Icons.Outlined.Dns, contentDescription = null, tint = Kitsu.accentText, modifier = Modifier.size(16.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = server.name, style = KitsuType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                            Text(text = "${server.host}:${server.port}" + (server.version?.let { " · v$it" } ?: ""), style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle))
                        }
                        TvBadge(text = t("tv.connect.found", "Found"), tone = BadgeTone.Ok)
                    }
                }
            }
        }

        /* ⚠️ Beside what discovery found, never instead of it: a preset is a server in ANOTHER house,
           and a household's own server — found on this network — is the likelier choice at home. */
        if (presets.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow(t("tv.connect.presets", "Known servers"), muted = true)
                presets.forEachIndexed { index, preset ->
                    TvListRow(
                        onClick = { onPreset(preset) },
                        modifier = if (found.isEmpty() && index == 0) Modifier.focusRequester(first) else Modifier,
                    ) {
                        Box(
                            modifier = Modifier.size(28.dp).background(Kitsu.backgroundOverlay, ChipShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(imageVector = Icons.Outlined.Public, contentDescription = null, tint = Kitsu.accentText, modifier = Modifier.size(16.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = preset.name, style = KitsuType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                            Text(text = preset.address.removeSuffix("/"), style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle))
                        }
                        TvBadge(text = t("tv.connect.preset", "Preset"), tone = BadgeTone.Muted)
                    }
                }
            }
        }

        TvButton(
            onClick = onManual,
            text = t("tv.connect.enterAddress", "Enter an address"),
            weight = if (found.isEmpty() && presets.isEmpty()) ButtonWeight.Primary else ButtonWeight.Secondary,
            icon = Icons.Outlined.Keyboard,
            modifier = if (found.isEmpty() && presets.isEmpty()) Modifier.focusRequester(first) else Modifier,
        )
    }
}

/**
 * The address, typed on the shared pad with its address keyboard, and how to reach it.
 *
 * ⚠️ NOT DIGITS ONLY ANY MORE. The pad used to offer `0-9 . :` because an address on this network is
 * an IP and a port — but a server reached from another house is a DOMAIN (`kitsu-be.jmouse.org`),
 * behind a TLS proxy. A television given to somebody else's household has no other way to reach home.
 *
 * ⚠️ THE SCHEME IS A SELECTOR, NOT SOMETHING TO TYPE. Eight presses of `h t t p s : / /` on a D-pad
 * is a chore nobody finishes. It follows what is typed — an IP suggests `http`, a name `https` — until
 * it is pressed, and from then on it is the person's choice. See [ServerAddress].
 *
 * ⚠️ AND A PORT CAN BE FOUND RATHER THAN KNOWN. When Kitsu was moved off 8130 the person at the
 * television rarely knows where to; "Find the port" asks the host itself — see [PortScanner].
 */
@Composable
private fun ManualAddress(
    initial: String?,
    problem: String?,
    onConnect: (String) -> Unit,
    onBack: () -> Unit,
) {
    val (initialScheme, initialTyped) = remember(initial) { ServerAddress.split(initial) }
    var address by remember { mutableStateOf(initialTyped) }
    var chosenScheme by remember { mutableStateOf(initialScheme) }
    val scheme = chosenScheme ?: ServerAddress.suggestedScheme(address)
    val composed = ServerAddress.compose(scheme, address)
    val usable = remember(composed) { address.isNotBlank() && ApiClient.isUsableAddress(composed) }

    val scanner = remember { PortScanner() }
    val scope = rememberCoroutineScope()
    var scan by remember { mutableStateOf<Job?>(null) }
    var scanState by remember { mutableStateOf<ScanEvent?>(null) }

    fun stopScanning() {
        scan?.cancel()
        scan = null
    }

    fun changeAddress(typed: String) {
        stopScanning()
        scanState = null
        /* A scheme typed by hand — a keyboard makes that easy — moves into the selector. */
        val (typedScheme, rest) = ServerAddress.split(typed)
        if (typedScheme != null) {
            chosenScheme = typedScheme
        }
        address = rest
    }

    fun findPort() {
        stopScanning()
        val host = ServerAddress.hostOf(address)
        scan = scope.launch {
            scanner.scan(scheme, host).collect { event ->
                scanState = event
                if (event is ScanEvent.Found) {
                    address = "$host:${event.server.port}"
                }
            }
            scan = null
        }
    }

    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { first.requestFocus() }
    }

    /* A remote with keys, or a keyboard, types straight in; the pad is for the rest. */
    Column(
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) {
                return@onPreviewKeyEvent false
            }
            val typed = event.utf16CodePoint.toChar()
            when {
                typed.isAddressCharacter() -> {
                    changeAddress(address + typed.lowercaseChar())
                    true
                }
                event.key == Key.Backspace || event.key == Key.Delete -> {
                    changeAddress(address.dropLast(1))
                    true
                }
                else -> false
            }
        },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = t("tv.connect.addressHeading", "Server address"), style = KitsuType.h2)
            Text(
                text = t(
                    "tv.connect.addressHint",
                    "On this network, the IP printed on the Server screen of Kitsu. From another house, the domain it is published at. The port is {port} unless it was changed.",
                    "port" to 8130,
                ),
                style = KitsuType.body.copy(color = Kitsu.foregroundMuted),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Kitsu.focusGap), verticalAlignment = Alignment.CenterVertically) {
            Scheme.entries.forEach { option ->
                TvChip(
                    text = option.prefix.removeSuffix("://"),
                    pressed = option == scheme,
                    onClick = { chosenScheme = option },
                )
            }
            Text(
                text = if (scheme == Scheme.HTTPS) {
                    t("tv.connect.schemeHttps", "Over TLS — a domain from outside this network")
                } else {
                    t("tv.connect.schemeHttp", "Plain — an address on this network")
                },
                style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Kitsu.borderStrong, SurfaceShape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = scheme.prefix,
                style = KitsuType.h2.copy(fontFamily = DataFamily, color = Kitsu.foregroundSubtle),
            )
            Text(
                text = if (address.isEmpty()) "192.168.0.104 · kitsu-be.jmouse.org" else address,
                style = KitsuType.h2.copy(fontFamily = DataFamily, color = if (address.isEmpty()) Kitsu.foregroundSubtle else Kitsu.foreground),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.width(2.dp).height(22.dp).background(Kitsu.accentText))
        }

        ScanLine(scanState, ServerAddress.hostOf(address))

        /* ⚠️ Said while typing is still going on, but only once there is something to judge — an empty
           field is not a mistake. A server that cannot be reached is a different message, below. */
        if (address.isNotBlank() && !usable) {
            Text(
                text = t("tv.connect.notAnAddress", "Not an address. For example: 192.168.0.104, 192.168.0.104:8131, kitsu-be.jmouse.org"),
                style = KitsuType.caption.copy(color = Kitsu.foregroundMuted),
            )
        } else if (problem != null && scanState == null) {
            Text(text = t("tv.connect.unreachable", "Could not reach it: {problem}", "problem" to problem), style = KitsuType.caption.copy(color = Kitsu.danger))
        }

        LetterPad(
            query = address,
            onQueryChange = { changeAddress(it) },
            firstKey = first,
            keyboards = listOf(Keyboard.Address),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvButton(
                onClick = {
                    if (usable) {
                        stopScanning()
                        onConnect(composed)
                    }
                },
                text = t("tv.connect.connect", "Connect"),
                weight = ButtonWeight.Primary,
                enabled = usable,
            )
            if (scan != null) {
                TvButton(onClick = { stopScanning() }, text = t("tv.connect.stopScan", "Stop searching"), icon = Icons.Outlined.Close)
            } else {
                TvButton(
                    onClick = { findPort() },
                    text = t("tv.connect.findPort", "Find the port"),
                    icon = Icons.Outlined.Search,
                    enabled = usable,
                )
            }
            TvButton(onClick = onBack, text = t("tv.connect.backToList", "Back to the list"), weight = ButtonWeight.Quiet)
        }
    }
}

/** Where a port search is, in one line — or nothing, before the first search. */
@Composable
private fun ScanLine(state: ScanEvent?, host: String) {
    if (state == null) {
        return
    }
    val text = when (state) {
        is ScanEvent.Checking -> t("tv.connect.scanChecking", "Asking {host} — {checked} of {total} ports", "host" to host, "checked" to state.checked, "total" to state.total)
        is ScanEvent.Found -> t(
            "tv.connect.scanFound",
            "Found {name} on port {port} · v{version}",
            "name" to (state.server.name ?: "Kitsu"),
            "port" to state.server.port,
            "version" to state.server.version,
        )
        is ScanEvent.NothingFound -> t("tv.connect.scanNothing", "No Kitsu answered on {host} — {checked} ports asked.", "host" to host, "checked" to state.checked)
        ScanEvent.Unresolvable -> t("tv.connect.scanUnresolvable", "{host} does not resolve to an address.", "host" to host)
    }
    val colour = when (state) {
        is ScanEvent.Found -> Kitsu.accentText
        is ScanEvent.Checking -> Kitsu.foregroundMuted
        else -> Kitsu.danger
    }
    Text(text = text, style = KitsuType.caption.copy(color = colour, fontFamily = DataFamily))
}

/** What an address is spelled with — the same set the address keyboard offers. */
private fun Char.isAddressCharacter(): Boolean {
    return this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '.' || this == ':' || this == '-' || this == '/'
}
