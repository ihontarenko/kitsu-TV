package org.jmouse.project.kitsu.tv.text

import android.icu.text.PluralRules
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/*
  ==============================================================================
  The words on the screen, in the language the server says this television
  speaks.

  ⚠️ THE APPLICATION HOLDS NO TRANSLATIONS. Every text is asked of the server
  (`GET /translations/current?area=tv`) and kept in memory only; the one wording
  compiled in is the English written at each call site —
  `t("tv.home.continue", "Continue watching")` — which is what a screen shows
  before the server has answered, and for every key the chosen language lacks.
  So a word changed on the server's Translations page changes the television
  the next time it asks, without a release.

  ⚠️ EVERY KEY AND ITS ENGLISH ARE LITERALS. `scripts/extract-texts.mjs` reads
  them out of the sources to write the server's English catalogue
  (`translations/en/tv.json`), and it refuses a call it cannot read. A text
  that needs a value takes it as a `{name}` placeholder, never a `$template`.

  ⚠️ SNAPSHOT STATE, NOT A FLOW. A composable that calls `t()` reads the state
  and is recomposed when the server's answer lands; code outside composition
  gets the current words, which is all it needs.
  ==============================================================================
 */

/** One language's words as the server sent them — the language code and `key → text`. */
data class Translation(val language: String, val texts: Map<String, String>) {
    companion object {
        /** What the television speaks before any server answers: its own English, nothing more. */
        val ENGLISH = Translation(language = "en", texts = emptyMap())
    }
}

object Texts {

    private var current by mutableStateOf(Translation.ENGLISH)

    /** The language being spoken, as the server named it — `en`, `uk`. */
    val language: String
        get() = current.language

    /** The locale numbers, dates and plural forms follow. */
    val locale: Locale
        get() = Locale.forLanguageTag(current.language)

    fun apply(translation: Translation) {
        current = translation
    }

    /** Back to the compiled-in English — a television that no longer has a server to ask. */
    fun forget() {
        current = Translation.ENGLISH
    }

    internal fun lookup(key: String): String? = current.texts[key]?.takeIf { text -> text.isNotEmpty() }
}

/**
 * The text for [key] in the television's language, else [english].
 *
 * @param values `"name" to value` for each `{name}` in the text
 */
fun t(key: String, english: String, vararg values: Pair<String, Any?>): String {
    return fill(Texts.lookup(key) ?: english, values)
}

/**
 * The text for [count] of something — `{count}` in it is the number, written the language's way.
 *
 * The form is the language's own (`android.icu` plural rules): the server's `key.one`, `key.few`,
 * `key.many`, `key.other`; English has only [one] and [other], which are also the fallbacks.
 */
fun plural(key: String, count: Int, one: String, other: String, vararg values: Pair<String, Any?>): String {
    val category = PluralRules.forLocale(Texts.locale).select(count.toDouble())
    val text = Texts.lookup("$key.$category")
        ?: Texts.lookup("$key.other")
        ?: if (count == 1) one else other
    return fill(text, arrayOf<Pair<String, Any?>>("count" to formatInteger(count)) + values)
}

/** A whole number as the language writes it — `1 234` in Ukrainian, `1,234` in English. */
fun formatInteger(value: Number): String = NumberFormat.getIntegerInstance(Texts.locale).format(value)

/** A number with [digits] decimal places, in the language's own separator — `7,8` in Ukrainian. */
fun formatDecimal(value: Double, digits: Int = 1): String {
    val format = NumberFormat.getNumberInstance(Texts.locale)
    format.minimumFractionDigits = digits
    format.maximumFractionDigits = digits
    return format.format(value)
}

/** A calendar date — `2022-04-01` — as the language writes it; what arrived, when it is not a date. */
fun formatDate(isoDate: String?): String? {
    if (isoDate.isNullOrBlank()) {
        return null
    }
    return runCatching { LocalDate.parse(isoDate.take(10)).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Texts.locale)) }
        .getOrDefault(isoDate)
}

/**
 * A moment the server sent — an instant with its offset, or a local date-time — as the language writes
 * it, in this television's zone. What arrived, trimmed to the minute, when it cannot be read.
 */
fun formatDateTime(isoDateTime: String?): String? {
    if (isoDateTime.isNullOrBlank()) {
        return null
    }
    val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Texts.locale)
    val local = runCatching { OffsetDateTime.parse(isoDateTime).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() }
        .recoverCatching { LocalDateTime.parse(isoDateTime) }
        .getOrNull()
        ?: return isoDateTime.replace('T', ' ').take(16)
    return local.format(format)
}

private fun fill(text: String, values: Array<out Pair<String, Any?>>): String {
    if (values.isEmpty()) {
        return text
    }
    var filled = text
    values.forEach { (name, value) ->
        filled = filled.replace("{$name}", value?.toString().orEmpty())
    }
    return filled
}
