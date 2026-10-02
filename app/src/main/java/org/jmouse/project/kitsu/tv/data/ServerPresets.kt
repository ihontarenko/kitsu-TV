package org.jmouse.project.kitsu.tv.data

/** A server this build knows about before it has looked for one. */
data class ServerPreset(val name: String, val address: String)

/**
 * The servers offered on the connect screen whether or not anything answered over mDNS.
 *
 * ⚠️ COMPILED IN, ON PURPOSE. A television handed to another household — the parents' — is in a
 * house where discovery can never find home, and typing a domain on a D-pad is the one step most likely
 * to stop them. A preset is one press. Add a line here and build a release to hand out another.
 *
 * ⚠️ These are the SERVER's addresses, never the web interface's: `kitsu-be.jmouse.org`, not
 * `kitsu-fe.jmouse.org`. The television talks to `/api/v1` only.
 *
 * ⚠️ THE NAME CHANGED ON 2026-09-28, AND A DNS RECORD IS HALF OF IT. It was `mhub-server.jmouse.org`
 * — the product's old name, which this file used to keep deliberately because a hostname is not the
 * repository's to rename on its own. It is renamed now, and the change is only complete when all three
 * are done in one sitting: the DNS record, the vhost in `docker/caddy/Caddyfile`, and this line. A
 * preset pointing at a name that does not resolve is worse than one wearing a retired name, because
 * it is the one press that gets a television in another house connected at all.
 *
 * ⚠️ AND A TELEVISION ALREADY OUT THERE DOES NOT LEARN THIS. The preset is compiled in, so a set
 * handed to another household keeps offering the old name until it takes a new release.
 */
object ServerPresets {

    val all: List<ServerPreset> = listOf(
        ServerPreset(name = "jMouse", address = "https://kitsu-be.jmouse.org/"),
    )
}
