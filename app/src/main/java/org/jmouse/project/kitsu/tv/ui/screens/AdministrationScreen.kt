package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.formatDateTime
import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.data.api.AccessRole
import org.jmouse.project.kitsu.tv.data.api.AccessSubject
import org.jmouse.project.kitsu.tv.data.api.DeviceView
import org.jmouse.project.kitsu.tv.data.api.MatcherView
import org.jmouse.project.kitsu.tv.data.api.ProviderView
import org.jmouse.project.kitsu.tv.data.api.ReadingStepView
import org.jmouse.project.kitsu.tv.data.api.ScriptLogLine
import org.jmouse.project.kitsu.tv.data.api.ScriptSummary
import org.jmouse.project.kitsu.tv.data.api.describeForScreen
import org.jmouse.project.kitsu.tv.ui.DeckState
import org.jmouse.project.kitsu.tv.ui.components.BadgeTone
import org.jmouse.project.kitsu.tv.ui.components.ButtonWeight
import org.jmouse.project.kitsu.tv.ui.components.Eyebrow
import org.jmouse.project.kitsu.tv.ui.components.ReadingPane
import org.jmouse.project.kitsu.tv.ui.components.highlightJms
import org.jmouse.project.kitsu.tv.ui.components.TvBadge
import org.jmouse.project.kitsu.tv.ui.components.TvButton
import org.jmouse.project.kitsu.tv.ui.components.TvListRow
import org.jmouse.project.kitsu.tv.ui.components.TvRailItem
import org.jmouse.project.kitsu.tv.ui.components.revealsPageEdges
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType
import retrofit2.HttpException
import org.jmouse.project.kitsu.tv.ui.theme.SurfaceShape


/*
  ==============================================================================
  System administration — how the server is set up, READ ONLY.

  ⚠️ NOTHING HERE CHANGES ANYTHING. Reading steps, rules, matchers, providers,
  access and devices are edited in the browser, where there is a keyboard and a
  second look before a change lands; a remote is for reading them from the sofa.

  ⚠️ THE SERVER DECIDES WHO SEES IT. The profile page only offers the door to a
  profile whose rights say it may (`rememberAdministrationPermission`); every
  list here still asks the server, and a 401 or 403 is drawn IN PLACE of the
  list, with the way out — never a crash and never an empty page, because an
  empty list and a refused one mean opposite things.
  ==============================================================================
 */

/** The parts of the server's set-up this television can show. */
enum class AdministrationSection {
    ReadingSteps,
    Rules,
    RuleLog,
    Matchers,
    Providers,
    Access,
    Roles,
    Devices;

    val label: String
        get() = when (this) {
            ReadingSteps -> t("tv.administration.readingSteps", "Reading steps")
            Rules -> t("tv.administration.rules", "jMS rules")
            RuleLog -> t("tv.administration.ruleLog", "Rule log")
            Matchers -> t("tv.administration.matchers", "Matchers")
            Providers -> t("tv.administration.providers", "Providers")
            Access -> t("tv.administration.access", "Access")
            Roles -> t("tv.administration.roles", "Roles")
            Devices -> t("tv.administration.devices", "Devices")
        }

    val summary: String
        get() = when (this) {
            ReadingSteps -> t("tv.administration.readingStepsSummary", "How a file name is read, one step at a time, in the order the steps run.")
            Rules -> t("tv.administration.rulesSummary", "The household's own rules — where each runs, and whether it loads. Open one to read it.")
            RuleLog -> t("tv.administration.ruleLogSummary", "What the rules wrote with @log, newest first. Kept in the server's memory only.")
            Matchers -> t("tv.administration.matchersSummary", "The identification chain, in the order it is asked.")
            Providers -> t("tv.administration.providersSummary", "Film databases and AI vendors, one row per capability.")
            Access -> t("tv.administration.accessSummary", "Who holds which roles and grants. A refusal always wins over an allowance.")
            Roles -> t("tv.administration.rolesSummary", "What each role hands to whoever holds it.")
            Devices -> t("tv.administration.devicesSummary", "Every television and browser admitted. A revoked one stays listed, with its date.")
        }
}

/** A list read from an administration route: pending, there, refused, or not answered. */
sealed interface AdministrationLoaded<out T> {
    data object Pending : AdministrationLoaded<Nothing>
    data class Ready<T>(val value: T) : AdministrationLoaded<T>

    /** The server said this profile may not — its own sentence, which names the rule that refused. */
    data class Refused(val detail: String) : AdministrationLoaded<Nothing>
    data class Failed(val problem: String) : AdministrationLoaded<Nothing>
}

/** 401 and 403: not "the server is broken" but "not for this profile". */
private val REFUSAL_STATUSES = setOf(401, 403)

private fun <T> Result<T>.toAdministrationLoaded(): AdministrationLoaded<T> = fold(
    onSuccess = { value -> AdministrationLoaded.Ready(value) },
    onFailure = { failure ->
        if (failure is HttpException && failure.code() in REFUSAL_STATUSES) {
            AdministrationLoaded.Refused(failure.describeForScreen())
        } else {
            AdministrationLoaded.Failed(failure.describeForScreen())
        }
    },
)

/** Loads once per [key] and again on every [attempt]. */
@Composable
fun <T> rememberAdministrationLoad(key: Any, attempt: Int, load: suspend () -> T): AdministrationLoaded<T> {
    var loaded by remember(key) { mutableStateOf<AdministrationLoaded<T>>(AdministrationLoaded.Pending) }
    LaunchedEffect(key, attempt) {
        loaded = AdministrationLoaded.Pending
        loaded = runCatching { load() }.toAdministrationLoaded()
    }
    return loaded
}

/**
 * The administration's front page: the sections down the side, the chosen one beside them.
 *
 * <p>⚠️ A SECTION IS CHOSEN BY PRESSING IT, NOT BY PASSING OVER IT. Walking down the rail to reach
 * "Devices" would otherwise fire six requests on the way and repaint the pane under the cursor at
 * every step.
 */
@Composable
fun AdministrationScreen(
    connection: ServerConnection,
    deck: DeckState,
    onSwitchProfile: () -> Unit,
    onServer: () -> Unit,
    onRule: (ruleId: String) -> Unit,
) {
    var sectionName by rememberSaveable { mutableStateOf(AdministrationSection.ReadingSteps.name) }
    val section = AdministrationSection.valueOf(sectionName)
    val first = rememberInitialFocus()

    Screen(deck = deck, current = null) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Kitsu.gutter, vertical = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Eyebrow(t("tv.administration.eyebrow", "Your profile · read only"))
                    Text(text = t("tv.profile.administration", "System administration"), style = KitsuType.h1)
                }
                TvButton(onClick = onServer, text = t("tv.profile.server", "Server and build"), icon = Icons.Outlined.Dns)
            }

            Row(
                modifier = Modifier.fillMaxSize().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                val railState = rememberLazyListState()
                LazyColumn(
                    state = railState,
                    modifier = Modifier.width(200.dp).fillMaxHeight().revealsPageEdges(railState),
                    verticalArrangement = Arrangement.spacedBy(Kitsu.focusGap),
                ) {
                    items(AdministrationSection.entries, key = { entry -> entry.name }) { entry ->
                        TvRailItem(
                            label = entry.label,
                            count = null,
                            current = entry == section,
                            onClick = { sectionName = entry.name },
                            modifier = if (entry == AdministrationSection.entries.first()) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = section.summary,
                        style = KitsuType.body.copy(color = Kitsu.foregroundMuted),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        SectionPane(connection, section, onSwitchProfile, onRule)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionPane(
    connection: ServerConnection,
    section: AdministrationSection,
    onSwitchProfile: () -> Unit,
    onRule: (String) -> Unit,
) {
    var attempt by remember(section) { mutableIntStateOf(0) }
    val retry = { attempt += 1 }
    val api = connection.api
    when (section) {
        AdministrationSection.ReadingSteps -> AdministrationList(
            loaded = rememberAdministrationLoad(section, attempt) { api.readingSteps().sortedBy { step -> step.sortOrder } },
            counting = Counted::steps,
            empty = t("tv.administration.readingStepsEmpty", "No reading steps — every file name is taken as it is."),
            key = { step -> step.id },
            onRetry = retry,
            onSwitchProfile = onSwitchProfile,
        ) { step -> ReadingStepRow(step) }

        AdministrationSection.Rules -> AdministrationList(
            loaded = rememberAdministrationLoad(section, attempt) { api.scripts().sortedBy { rule -> rule.sortOrder } },
            counting = Counted::rules,
            empty = t("tv.administration.rulesEmpty", "No jMS rules yet — they are written in the browser, under Identification."),
            key = { rule -> rule.id },
            onRetry = retry,
            onSwitchProfile = onSwitchProfile,
        ) { rule -> RuleRow(rule, onOpen = { onRule(rule.id) }) }

        AdministrationSection.RuleLog -> AdministrationList(
            loaded = rememberAdministrationLoad(section, attempt) { api.scriptLog() },
            counting = Counted::lines,
            empty = t("tv.administration.ruleLogEmpty", "Nothing yet — a rule writes here with @log.info, @log.warn or @log.error."),
            // ⚠️ No key: a rule in a loop writes the same line in the same millisecond, and a key twice is a crash.
            key = null,
            onRetry = retry,
            onSwitchProfile = onSwitchProfile,
            onRefresh = retry,
        ) { line -> RuleLogRow(line) }

        AdministrationSection.Matchers -> AdministrationList(
            loaded = rememberAdministrationLoad(section, attempt) { api.matchers().sortedBy { matcher -> matcher.priority } },
            counting = Counted::matchers,
            empty = t("tv.administration.matchersEmpty", "No matchers — nothing identifies a file until one is added."),
            key = { matcher -> matcher.id },
            onRetry = retry,
            onSwitchProfile = onSwitchProfile,
        ) { matcher -> MatcherRow(matcher) }

        AdministrationSection.Providers -> AdministrationList(
            loaded = rememberAdministrationLoad(section, attempt) {
                api.providers().sortedWith(compareBy({ provider -> provider.capability }, { provider -> provider.priority }))
            },
            counting = Counted::providers,
            empty = t("tv.administration.providersEmpty", "No providers configured."),
            key = { provider -> provider.id },
            onRetry = retry,
            onSwitchProfile = onSwitchProfile,
        ) { provider -> ProviderRow(provider) }

        AdministrationSection.Access -> AdministrationList(
            loaded = rememberAdministrationLoad(section, attempt) { api.accessSubjects() },
            counting = Counted::subjects,
            empty = t("tv.administration.accessEmpty", "Nobody holds anything."),
            key = { subject -> subject.id },
            onRetry = retry,
            onSwitchProfile = onSwitchProfile,
        ) { subject -> SubjectRow(subject) }

        AdministrationSection.Roles -> AdministrationList(
            loaded = rememberAdministrationLoad(section, attempt) { api.accessRoles() },
            counting = Counted::roles,
            empty = t("tv.administration.rolesEmpty", "No roles defined."),
            key = { role -> role.id },
            onRetry = retry,
            onSwitchProfile = onSwitchProfile,
        ) { role -> RoleRow(role) }

        AdministrationSection.Devices -> AdministrationList(
            loaded = rememberAdministrationLoad(section, attempt) { api.devices() },
            counting = Counted::devices,
            empty = t("tv.administration.devicesEmpty", "No device has been admitted."),
            key = { device -> device.id },
            onRetry = retry,
            onSwitchProfile = onSwitchProfile,
        ) { device -> DeviceRow(device) }
    }
}

/**
 * One section's rows, or why there are none.
 *
 * @param onRefresh offered above the rows for a list that changes while it is read — the rule log
 */
@Composable
private fun <T> AdministrationList(
    loaded: AdministrationLoaded<List<T>>,
    counting: (Int) -> String,
    empty: String,
    key: ((T) -> Any)?,
    onRetry: () -> Unit,
    onSwitchProfile: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    row: @Composable (T) -> Unit,
) {
    when (loaded) {
        AdministrationLoaded.Pending -> Text(text = t("tv.administration.reading", "Reading…"), style = KitsuType.meta)
        is AdministrationLoaded.Refused -> AdministrationRefused(loaded.detail, onSwitchProfile)
        is AdministrationLoaded.Failed -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = t("tv.common.noAnswer", "The server did not answer."), style = KitsuType.h2)
            Text(text = loaded.problem, style = KitsuType.body.copy(color = Kitsu.danger))
            TvButton(onClick = onRetry, text = t("tv.common.tryAgain", "Try again"), icon = Icons.Outlined.Refresh)
        }
        is AdministrationLoaded.Ready -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = counting(loaded.value.size), style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle))
                if (onRefresh != null) {
                    TvButton(onClick = onRefresh, text = t("tv.administration.refresh", "Refresh"), icon = Icons.Outlined.Refresh, weight = ButtonWeight.Quiet)
                }
            }
            if (loaded.value.isEmpty()) {
                Text(text = empty, style = KitsuType.body.copy(color = Kitsu.foregroundMuted))
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().revealsPageEdges(listState),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(loaded.value, key = key) { value -> row(value) }
                }
            }
        }
    }
}

/**
 * What a profile without the rights sees, where the list would have been.
 *
 * ⚠️ WITH THE WAY OUT, and with the server's own sentence: it names the rule that refused, which is
 * what somebody needs to ask the owner for the right thing.
 */
@Composable
fun AdministrationRefused(detail: String, onSwitchProfile: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .widthIn(max = 640.dp)
            .border(1.dp, Kitsu.danger, SurfaceShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TvBadge(text = t("tv.administration.refused", "Refused"), tone = BadgeTone.Danger)
        Text(text = t("tv.administration.refusedHeading", "This profile cannot see system administration."), style = KitsuType.h2)
        Text(
            text = t("tv.administration.refusedHint", "Switch to a profile that can — the owner grants it on the Access screen in the browser."),
            style = KitsuType.body.copy(color = Kitsu.foregroundMuted),
        )
        Text(text = detail, style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle))
        TvButton(onClick = onSwitchProfile, text = t("tv.profile.switch", "Switch profile"), weight = ButtonWeight.Primary, icon = Icons.Outlined.SwapHoriz)
    }
}

/* ── Rows ─────────────────────────────────────────────────────────────────── */

/**
 * One row of an administration list: a title, its marks, one line of what it is — and, pressed, the
 * rest of it.
 *
 * ⚠️ PRESSING OPENS IN PLACE rather than to a page of its own: every one of these is a handful of
 * facts, and a page per row would be a Back press per row to compare two of them. A rule is the
 * exception — its source is a page's worth of text — and says "Open" instead.
 *
 * @param facts shown when the row is opened; a fact with no value is left out
 * @param onOpen when set, pressing leaves for a page of its own instead of opening in place
 */
@Composable
private fun AdministrationRow(
    title: String,
    meta: String?,
    facts: List<Pair<String, String?>> = emptyList(),
    onOpen: (() -> Unit)? = null,
    badges: @Composable () -> Unit = {},
) {
    var opened by remember { mutableStateOf(false) }
    val shownFacts = facts.filter { (_, value) -> !value.isNullOrBlank() }
    TvListRow(
        onClick = {
            if (onOpen != null) {
                onOpen()
            } else {
                opened = !opened
            }
        },
        modifier = Modifier.fillMaxWidth(),
        marked = opened,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    style = KitsuType.body.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                badges()
            }
            if (!meta.isNullOrBlank()) {
                Text(
                    text = meta,
                    style = KitsuType.meta,
                    maxLines = if (opened) 6 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (opened) {
                Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    shownFacts.forEach { (label, value) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = label,
                                style = KitsuType.caption.copy(color = Kitsu.foregroundSubtle),
                                modifier = Modifier.width(140.dp),
                            )
                            Text(text = value.orEmpty(), style = KitsuType.meta.copy(color = Kitsu.foreground))
                        }
                    }
                }
            }
        }
        val hint = when {
            onOpen != null -> t("tv.home.open", "Open")
            shownFacts.isEmpty() -> null
            opened -> t("tv.administration.less", "Less")
            else -> t("tv.administration.more", "More")
        }
        if (hint != null) {
            Text(text = hint, style = KitsuType.meta.copy(color = Kitsu.foregroundSubtle))
        }
    }
}

@Composable
private fun ReadingStepRow(step: ReadingStepView) {
    AdministrationRow(
        title = "${step.sortOrder} · ${step.name}",
        meta = step.description,
        facts = listOf(
            t("tv.administration.reads", "Reads") to readingStepTargetLabel(step.target),
            t("tv.administration.does", "Does") to readingStepActionLabel(step.action),
            t("tv.administration.pattern", "Pattern") to step.pattern,
            t("tv.administration.replacedBy", "Replaced by") to step.replacement?.ifEmpty { t("tv.administration.nothing", "nothing") },
            t("tv.administration.onlyWhenUnknown", "Only when unknown") to readingStepFieldLabel(step.onlyIfUnknown),
            t("tv.administration.rule", "Rule") to step.scriptName,
            t("tv.administration.example", "Example") to step.example,
        ),
    ) {
        TvBadge(text = readingStepKindLabel(step.kind), tone = BadgeTone.Muted)
        if (!step.enabled) {
            TvBadge(text = t("tv.administration.off", "Off"), tone = BadgeTone.Warn)
        }
        if (step.matchesExample == false) {
            TvBadge(text = t("tv.administration.exampleFails", "Example fails"), tone = BadgeTone.Danger)
        }
    }
}

@Composable
private fun RuleRow(rule: ScriptSummary, onOpen: () -> Unit) {
    AdministrationRow(
        title = rule.name,
        meta = listOfNotNull(
            rule.stages.takeIf { stages -> stages.isNotEmpty() }?.let { stages -> t("tv.administration.runsAt", "runs at {stages}", "stages" to stages.joinToString(", ")) },
            rule.bindProblem ?: rule.description,
        ).joinToString(" · "),
        onOpen = onOpen,
    ) {
        if (rule.bindState == "BOUND") {
            TvBadge(text = t("tv.administration.loads", "Loads"), tone = BadgeTone.Ok)
        } else {
            TvBadge(text = t("tv.administration.refused", "Refused"), tone = BadgeTone.Danger)
        }
        if (!rule.enabled) {
            TvBadge(text = t("tv.administration.off", "Off"), tone = BadgeTone.Muted)
        }
        if (rule.unknownStages.isNotEmpty()) {
            TvBadge(text = t("tv.administration.unknownMoment", "Unknown moment"), tone = BadgeTone.Warn)
        }
    }
}

@Composable
private fun RuleLogRow(line: ScriptLogLine) {
    AdministrationRow(
        title = "${line.at.replace('T', ' ').take(19)} · ${line.rule}",
        meta = line.message,
        facts = listOf(t("tv.administration.moment", "Moment") to line.stage, t("tv.administration.at", "At") to line.at),
    ) {
        TvBadge(
            text = logLevelLabel(line.level),
            tone = when (line.level) {
                "ERROR" -> BadgeTone.Danger
                "WARN" -> BadgeTone.Warn
                else -> BadgeTone.Muted
            },
        )
    }
}

@Composable
private fun MatcherRow(matcher: MatcherView) {
    AdministrationRow(
        title = "${matcher.priority} · ${matcher.displayName}",
        meta = matcher.blockedBy ?: listOfNotNull(
            matcher.providerName?.let { provider -> t("tv.administration.asks", "asks {provider}", "provider" to provider) },
            matcher.aiPurpose?.let { purpose -> t("tv.administration.aiFor", "AI for {purpose}", "purpose" to modelPurposeLabel(purpose)) },
        ).joinToString(" · ").ifBlank { t("tv.administration.onServer", "runs on the server itself") },
    ) {
        TvBadge(text = matcherKindLabel(matcher.kind), tone = BadgeTone.Muted)
        if (!matcher.enabled) {
            TvBadge(text = t("tv.administration.off", "Off"), tone = BadgeTone.Muted)
        } else if (!matcher.ready) {
            TvBadge(text = t("tv.administration.blocked", "Blocked"), tone = BadgeTone.Warn)
        }
    }
}

@Composable
private fun ProviderRow(provider: ProviderView) {
    AdministrationRow(
        title = provider.displayName ?: provider.providerName,
        meta = listOf(
            provider.providerName,
            t("tv.administration.priority", "priority {priority}", "priority" to provider.priority),
            t("tv.administration.timeout", "timeout {seconds} s", "seconds" to provider.timeoutSeconds),
            if (provider.credentialSet) t("tv.administration.keyStored", "key stored") else t("tv.administration.noKey", "no key"),
        ).joinToString(" · "),
        facts = listOf(t("tv.administration.endpoint", "Endpoint") to (provider.endpoint ?: t("tv.administration.providersOwn", "the provider's own"))),
    ) {
        TvBadge(text = capabilityLabel(provider.capability), tone = BadgeTone.Muted)
        if (!provider.enabled) {
            TvBadge(text = t("tv.administration.off", "Off"), tone = BadgeTone.Muted)
        }
    }
}

@Composable
private fun SubjectRow(subject: AccessSubject) {
    AdministrationRow(
        title = if (subject.known) subject.displayName else "${subject.displayName} (${subject.id})",
        meta = subject.roles.joinToString(", ") { held -> t("tv.administration.heldAt", "{what} at {where}", "what" to held.role, "where" to held.at) }
            .ifBlank { t("tv.administration.noRoles", "no roles") },
        facts = subject.grants.map { grant ->
            (if (grant.deny) t("tv.administration.refuses", "Refuses") else t("tv.administration.allows", "Allows")) to listOfNotNull(
                t("tv.administration.heldAt", "{what} at {where}", "what" to grant.permission, "where" to grant.at),
                grant.condition?.let { condition -> t("tv.administration.when", "when {condition}", "condition" to condition) },
                grant.reason,
            ).joinToString(" · ")
        },
    ) {
        if (subject.grants.isNotEmpty()) {
            TvBadge(text = Counted.grants(subject.grants.size), tone = BadgeTone.Muted)
        }
    }
}

@Composable
private fun RoleRow(role: AccessRole) {
    AdministrationRow(
        title = role.name,
        meta = Counted.permissions(role.bundle.size),
        facts = role.bundle.map { entry ->
            entry.permission to listOfNotNull(
                t("tv.administration.atScope", "at {scope}", "scope" to scopeLabel(entry.scopeType)),
                entry.conditionSource?.let { condition -> t("tv.administration.when", "when {condition}", "condition" to condition) },
            )
                .joinToString(" · ")
        },
    ) {
        TvBadge(text = role.assignableAt, tone = BadgeTone.Muted)
    }
}

@Composable
private fun DeviceRow(device: DeviceView) {
    AdministrationRow(
        title = device.displayName,
        meta = listOfNotNull(
            device.address,
            device.applicationVersion?.let { version -> t("tv.administration.appVersion", "app {version}", "version" to version) },
            device.lastSeenAt?.let { seen -> t("tv.administration.seen", "seen {when}", "when" to formatDateTime(seen)) },
        ).joinToString(" · ").ifBlank { null },
        facts = listOf(
            t("tv.administration.paired", "Paired") to formatDateTime(device.pairedAt),
            t("tv.administration.revoked", "Revoked") to formatDateTime(device.revokedAt),
            t("tv.administration.device", "Device") to device.id,
        ),
    ) {
        TvBadge(text = deviceKindLabel(device.kind), tone = BadgeTone.Muted)
        if (device.revoked) {
            TvBadge(text = t("tv.administration.revoked", "Revoked"), tone = BadgeTone.Danger)
        }
    }
}

/* ── One rule's source ────────────────────────────────────────────────────── */

/**
 * One jMS rule, read: where it runs, whether it loads, and its text exactly as written.
 *
 * ⚠️ The text sits in a [ReadingPane] — a rule is routinely longer than a screen, and text alone
 * gives the remote nowhere to stand.
 */
@Composable
fun AdministrationRuleScreen(
    connection: ServerConnection,
    deck: DeckState,
    ruleId: String,
    onSwitchProfile: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    val loaded = rememberAdministrationLoad(ruleId, attempt) { connection.api.script(ruleId) }
    val first = rememberInitialFocus(loaded is AdministrationLoaded.Ready)

    Screen(deck = deck, current = null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = Kitsu.gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (loaded) {
                AdministrationLoaded.Pending -> Loading(t("tv.administration.readingRule", "Reading the rule…"))
                is AdministrationLoaded.Refused -> AdministrationRefused(loaded.detail, onSwitchProfile)
                is AdministrationLoaded.Failed -> Failed(loaded.problem) { attempt += 1 }
                is AdministrationLoaded.Ready -> {
                    val rule = loaded.value.document
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Eyebrow(t("tv.administration.ruleEyebrow", "System administration · jMS rule · read only"))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(text = rule.name, style = KitsuType.h1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (rule.bindState == "BOUND") {
                                TvBadge(text = t("tv.administration.loads", "Loads"), tone = BadgeTone.Ok)
                            } else {
                                TvBadge(text = t("tv.administration.refused", "Refused"), tone = BadgeTone.Danger)
                            }
                            if (!rule.enabled) {
                                TvBadge(text = t("tv.administration.off", "Off"), tone = BadgeTone.Muted)
                            }
                        }
                        Text(
                            text = listOfNotNull(
                                rule.stages.takeIf { stages -> stages.isNotEmpty() }
                                    ?.let { stages -> t("tv.administration.runsAtCapital", "Runs at {stages}", "stages" to stages.joinToString(", ")) },
                                rule.unknownStages.takeIf { stages -> stages.isNotEmpty() }
                                    ?.let { stages -> t("tv.administration.unknownStages", "attached to moments this build no longer has: {stages}", "stages" to stages.joinToString(", ")) },
                                rule.bindProblem,
                                rule.description,
                            ).joinToString(" · "),
                            style = KitsuType.body.copy(color = Kitsu.foregroundMuted),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    ReadingPane(modifier = Modifier.fillMaxSize().focusRequester(first)) {
                        Text(
                            text = highlightJms(loaded.value.source),
                            style = KitsuType.meta.copy(color = Kitsu.foreground, lineHeight = KitsuType.body.lineHeight),
                        )
                    }
                }
            }
        }
    }
}
