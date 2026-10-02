package org.jmouse.project.kitsu.tv.data

import org.jmouse.project.kitsu.tv.data.api.ApiClient

/** How the television talks to a server: plainly on the household's network, over TLS from outside it. */
enum class Scheme(val prefix: String) {
    HTTP("http://"),
    HTTPS("https://"),
}

/**
 * An address as a person types it on the connect screen — `host`, `host:port` — and what it becomes.
 *
 * ⚠️ THE SCHEME IS CHOSEN SEPARATELY, and the typed part never carries one. A server on this network is
 * an IP and Kitsu itself on 8130, over plain HTTP; a server in another house is a domain behind a
 * TLS proxy on 443. So the selector follows what is typed — an IP suggests `http`, a name `https` —
 * until the person picks one, and then it stays picked.
 */
object ServerAddress {

    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    /** The host of `host`, `host:port` or `host:port/path`. */
    fun hostOf(typed: String): String {
        return typed.trim().substringBefore("/").substringBefore(":")
    }

    /** The port, when one was typed. */
    fun portOf(typed: String): Int? {
        val authority = typed.trim().substringBefore("/")
        if (!authority.contains(":")) {
            return null
        }
        return authority.substringAfter(":").toIntOrNull()
    }

    /** An address a person would give by number, not by name. */
    fun isIpLiteral(host: String): Boolean {
        return host == "localhost" || IPV4.matches(host)
    }

    /**
     * An address on a household network — the only kind a full port sweep is allowed against.
     *
     * ⚠️ Sweeping every port of a host on the internet is what a scanner does, and a server behind the
     * limits this product now has would be right to treat it as one. Outside a private range only the
     * likely ports are asked.
     */
    fun isPrivate(host: String): Boolean {
        if (host == "localhost") {
            return true
        }
        if (!IPV4.matches(host)) {
            return false
        }
        val octets = host.split(".").map { it.toInt() }
        return octets[0] == 10 ||
            octets[0] == 127 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }

    /** What the selector shows before the person has chosen: `http` for a number, `https` for a name. */
    fun suggestedScheme(typed: String): Scheme {
        val host = hostOf(typed)
        if (host.isEmpty() || isIpLiteral(host)) {
            return Scheme.HTTP
        }
        return Scheme.HTTPS
    }

    /** `https://jmouse.org/` → (HTTPS, `jmouse.org`); an address with no scheme comes back with none. */
    fun split(address: String?): Pair<Scheme?, String> {
        val trimmed = address?.trim().orEmpty()
        val scheme = Scheme.entries.firstOrNull { trimmed.startsWith(it.prefix) }
        val rest = if (scheme == null) trimmed else trimmed.removePrefix(scheme.prefix)
        return scheme to rest.removeSuffix("/")
    }

    /**
     * The address to connect to.
     *
     * ⚠️ AN IP WITH NO PORT OVER HTTP IS KITSU ITSELF, on 8130 — that is what a server on this
     * network is. Everything else keeps its scheme's own port: `https://jmouse.org` is 443 and
     * `http://jmouse.org` is 80, because a name without a port is a proxy's name.
     */
    fun compose(scheme: Scheme, typed: String): String {
        val trimmed = typed.trim()
        if (scheme == Scheme.HTTP && portOf(trimmed) == null && isIpLiteral(hostOf(trimmed))) {
            return "${scheme.prefix}${hostOf(trimmed)}:${ApiClient.DEFAULT_PORT}"
        }
        return scheme.prefix + trimmed
    }
}
