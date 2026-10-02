package org.jmouse.project.kitsu.tv.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jmouse.project.kitsu.tv.data.ServerConnection


/*
  What the profile watching may do — asked of `access/mine`, once per screen, as a HINT FOR DRAWING.

  ⚠️ NEVER THE GUARD. Every route refuses on its own; this only keeps a television from offering a
  door somebody could not go through, and says so where the door would have been.
 */

/**
 * The server's answer to "may this profile …", once it has given one.
 *
 * ⚠️ THREE STATES, NOT TWO. "Not yet known" and "refused" drew the same — nothing — so a profile the
 * household deliberately keeps from something looked exactly like a television that could not do it
 * at all. A refusal is said; a question still in flight is not.
 */
enum class PermissionAnswer { Checking, Allowed, Refused }

/**
 * Asks once what this profile holds and answers [allowedWhen] of it.
 *
 * ⚠️ An unanswered question is not a refusal: when the server cannot be asked, the feature is offered
 * and the route itself decides — a page then says the refusal in place.
 */
@Composable
fun rememberPermission(connection: ServerConnection, allowedWhen: (Set<String>) -> Boolean): PermissionAnswer {
    var answer by remember(connection) { mutableStateOf(PermissionAnswer.Checking) }
    LaunchedEffect(connection) {
        answer = runCatching { connection.api.permissions().toSet() }
            .map { held -> if (allowedWhen(held)) PermissionAnswer.Allowed else PermissionAnswer.Refused }
            .getOrDefault(PermissionAnswer.Allowed)
    }
    return answer
}

/** Everything, whatever the scope — the owner's own rights. */
private const val GOD_ALL = "god:all"

/** Reading how the server is set up: steps, rules, matchers, providers, access, devices. */
private const val SETTINGS_READ = "settings:read"

/** Changing how the installation behaves — the system settings. */
private const val SETTINGS_WRITE = "settings:write"

/**
 * Whether this profile may change the installation's settings from the television — see
 * [rememberPermission]. ⚠️ Asked of the server, which answers it from jmp; never decided here.
 */
@Composable
fun rememberSettingsPermission(connection: ServerConnection): PermissionAnswer =
    rememberPermission(connection) { held -> GOD_ALL in held || SETTINGS_WRITE in held }

/** Whether this profile may look at the server's administration — see [rememberPermission]. */
@Composable
fun rememberAdministrationPermission(connection: ServerConnection): PermissionAnswer =
    rememberPermission(connection) { held -> GOD_ALL in held || SETTINGS_READ in held }
