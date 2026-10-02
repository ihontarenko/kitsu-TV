package org.jmouse.project.kitsu.tv.data.build

import android.content.Context

/**
 * This application's build identity: an identifier unique to each build, stamped into
 * `build-id.txt` — the build time in base 36.
 *
 * ⚠️ There used to be an emoji hashed from it, a Kotlin twin of the server's. It was dropped on
 * 2026-09-25: a build now carries a CODE NAME a person chose (`BuildConfig.CODE_NAME`), and a random
 * animal beside it was a second name for the same thing that meant nothing.
 */
object BuildMark {

    @Volatile
    private var identifier: String? = null

    /** The identifier the build stamped into `build-id.txt`, or null when it is missing. */
    fun identifier(context: Context): String? {
        identifier?.let { known -> return known }
        val read = runCatching {
            context.assets.open("build-id.txt").bufferedReader().use { reader -> reader.readText().trim() }
        }.getOrNull()?.ifBlank { null }
        identifier = read
        return read
    }

    /**
     * When the build with this identifier was made, as UTC "yyyy-MM-dd HH:mm" — the identifier IS the
     * build time in base 36, so nothing else has to be stamped to know it. Null for an unreadable one.
     */
    fun builtAt(identifier: String?): String? {
        val milliseconds = identifier?.toLongOrNull(36) ?: return null
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return format.format(java.util.Date(milliseconds))
    }

}
