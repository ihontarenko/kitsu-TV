package org.jmouse.project.kitsu.tv.data.api

import kotlinx.serialization.Serializable

/*
  What the administration pages read — the browser's `naming.ts`, `scripts.ts`, `providers.ts`,
  `access.ts` and `pairing.ts` shapes, spelled once more in Kotlin.

  ⚠️ READ-ONLY BY DESIGN. The television shows how the server is set up; changing it stays in the
  browser, where a keyboard and a second look are. So only the fields a page draws are here, every one
  with a default — a field the server adds later, or leaves out, never fails the whole answer.
 */

/** One step of reading a file name — a pattern or a jMS rule, in the order they run. */
@Serializable
data class ReadingStepView(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    /** `PATTERN` or `SCRIPT`. */
    val kind: String = "PATTERN",
    val target: String? = null,
    val action: String? = null,
    val pattern: String? = null,
    val replacement: String? = null,
    val onlyIfUnknown: String? = null,
    val scriptName: String? = null,
    val description: String? = null,
    val example: String? = null,
    val matchesExample: Boolean? = null,
)

/** One jMS rule, without its text. */
@Serializable
data class ScriptSummary(
    val id: String,
    val name: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    /** `BOUND` — it loads and runs — or `REFUSED`, with [bindProblem] saying why. */
    val bindState: String = "BOUND",
    val bindProblem: String? = null,
    val stages: List<String> = emptyList(),
    /** Moments this build no longer declares — the rule is attached to them and runs nowhere. */
    val unknownStages: List<String> = emptyList(),
)

/** One jMS rule with its text, exactly as written. */
@Serializable
data class ScriptDetail(
    val document: ScriptSummary,
    val source: String = "",
)

/** One line a rule wrote with `@log` — kept in the server's memory, the last two thousand. */
@Serializable
data class ScriptLogLine(
    val at: String,
    /** `INFO`, `WARN` or `ERROR`. */
    val level: String,
    val rule: String,
    val stage: String? = null,
    val message: String = "",
)

/** One matcher in the identification chain. */
@Serializable
data class MatcherView(
    val id: String,
    val displayName: String,
    /** `PROGRAM`, `METADATA` or `AI`. */
    val kind: String,
    val providerName: String? = null,
    val aiPurpose: String? = null,
    val enabled: Boolean = true,
    val priority: Int = 0,
    /** Whether it can run as configured; [blockedBy] says why not. */
    val ready: Boolean = true,
    val blockedBy: String? = null,
)

/** One configured provider — a film database, an AI vendor — for one capability. */
@Serializable
data class ProviderView(
    val id: String,
    val providerName: String,
    val capability: String,
    val displayName: String? = null,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val endpoint: String? = null,
    /** Whether a key is stored — never the key itself. */
    val credentialSet: Boolean = false,
    val timeoutSeconds: Int = 0,
)

/** Somebody — or everybody — access is written for: their roles and their direct grants. */
@Serializable
data class AccessSubject(
    val id: String,
    val displayName: String,
    /** False for a subject no account stands behind — `*`, everyone. */
    val known: Boolean = true,
    val roles: List<RoleHeld> = emptyList(),
    val grants: List<GrantHeld> = emptyList(),
)

@Serializable
data class RoleHeld(
    val role: String,
    val at: String,
)

@Serializable
data class GrantHeld(
    val permission: String,
    val at: String,
    /** A refusal rather than an allowance — and a refusal wins. */
    val deny: Boolean = false,
    val condition: String? = null,
    val reason: String? = null,
)

/** A named bundle of permissions. */
@Serializable
data class AccessRole(
    val id: String,
    val name: String,
    val assignableAt: String,
    val bundle: List<BundleEntry> = emptyList(),
)

@Serializable
data class BundleEntry(
    val permission: String,
    val scopeType: String,
    val conditionSource: String? = null,
)

/** A device this installation admitted. ⚠️ A revoked one is still listed, with its date. */
@Serializable
data class DeviceView(
    val id: String,
    val displayName: String,
    val kind: String,
    val address: String? = null,
    val applicationVersion: String? = null,
    val pairedAt: String? = null,
    val lastSeenAt: String? = null,
    val revokedAt: String? = null,
    val revoked: Boolean = false,
)
