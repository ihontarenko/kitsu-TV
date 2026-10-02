package org.jmouse.project.kitsu.tv.ui.theme

import android.content.Context

/**
 * Where the chosen accent is kept between sittings.
 *
 * <h2>⚠️ SEPARATE FROM `Theme.kt`, and that is the whole point of the file</h2>
 *
 * The theme holds values and the state that says which one is on. The moment it also knows about
 * `Context` and `SharedPreferences` it stops being a theme and becomes a component with a lifecycle
 * — and every preview, test and screenshot of it needs an Android runtime to render a colour.
 *
 * <h2>⚠️ A NAME THAT IS NOT IN THE SET IS DISCARDED, not passed through</h2>
 *
 * `valueOf` throws on an unknown name, which would crash the application on launch after the set
 * changes — and the set has already changed once: three accents were offered for an afternoon and
 * dropped. A stored "Amber" must open in the default, not fail to open.
 */
object AccentStore {

    private const val FILE = "kitsu.appearance"
    private const val KEY = "accent"

    /** Reads the stored choice into [chosenAccent]. Call once, before the first frame. */
    fun restore(context: Context) {
        val stored = context
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null)

        chosenAccent.value = Accent.entries.firstOrNull { it.name == stored } ?: Accent.Ember
    }

    /** Applies a choice and remembers it. */
    fun choose(context: Context, accent: Accent) {
        chosenAccent.value = accent
        context
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, accent.name)
            .apply()
    }
}
