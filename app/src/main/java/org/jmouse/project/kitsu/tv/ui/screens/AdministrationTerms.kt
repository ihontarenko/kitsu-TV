package org.jmouse.project.kitsu.tv.ui.screens

import org.jmouse.project.kitsu.tv.text.t

/*
  ==============================================================================
  The server's enums on the administration pages, in the television's language.

  ⚠️ THESE PAGES PRINTED THE ENUMS THEMSELVES — `PATTERN`, `METADATA_SEARCH`,
  `ANDROID_TV`, `KEEP_BEFORE` — as badges and facts, which is English on every
  language and not quite English either. Each function names the values this
  build knows and falls back to the enum made readable, so a value added on the
  server later still shows rather than disappearing.
  ==============================================================================
 */

/** How a reading step is written — a regular expression or a jMS rule. */
fun readingStepKindLabel(kind: String): String = when (kind) {
    "PATTERN" -> t("tv.administrationTerms.stepKind.pattern", "Regular expression")
    "SCRIPT" -> t("tv.administrationTerms.stepKind.script", "jMS rule")
    else -> readable(kind)
}

/** What a reading step works on. */
fun readingStepTargetLabel(target: String?): String? = when (target) {
    null -> null
    "TITLE" -> t("tv.administrationTerms.stepTarget.title", "The title")
    "EPISODE_NAME" -> t("tv.administrationTerms.stepTarget.episodeName", "The episode name")
    "BOTH" -> t("tv.administrationTerms.stepTarget.both", "Title and episode name")
    "FOLDERS" -> t("tv.administrationTerms.stepTarget.folders", "The folders, innermost first")
    else -> readable(target)
}

/** What a reading step does with what its expression finds. */
fun readingStepActionLabel(action: String?): String? = when (action) {
    null -> null
    "REMOVE" -> t("tv.administrationTerms.stepAction.remove", "Remove what it finds")
    "REPLACE" -> t("tv.administrationTerms.stepAction.replace", "Replace what it finds")
    "KEEP_BEFORE" -> t("tv.administrationTerms.stepAction.keepBefore", "Keep what comes before it")
    "SPLIT_EPISODE" -> t("tv.administrationTerms.stepAction.splitEpisode", "Split the episode name off after it")
    "CAPTURE" -> t("tv.administrationTerms.stepAction.capture", "Capture named groups")
    else -> readable(action)
}

/** Which field a step waits for — it runs only while that one is still unknown. */
fun readingStepFieldLabel(field: String?): String? = when (field) {
    null -> null
    "EPISODE" -> t("tv.administrationTerms.stepField.episode", "the episode")
    "SEASON" -> t("tv.administrationTerms.stepField.season", "the season")
    "YEAR" -> t("tv.administrationTerms.stepField.year", "the year")
    else -> readable(field)
}

/** What kind of matcher one link of the identification chain is. */
fun matcherKindLabel(kind: String): String = when (kind) {
    "PROGRAM" -> t("tv.administrationTerms.matcherKind.program", "Built in")
    "METADATA" -> t("tv.administrationTerms.matcherKind.metadata", "Film database")
    "AI" -> t("tv.administrationTerms.matcherKind.ai", "Language model")
    else -> readable(kind)
}

/** What a language model is asked for. */
fun modelPurposeLabel(purpose: String): String = when (purpose) {
    "MATCH_ASSIST" -> t("tv.administrationTerms.purpose.matchAssist", "Help identify files")
    "ASSISTANT" -> t("tv.administrationTerms.purpose.assistant", "Assistant")
    else -> readable(purpose)
}

/** What a configured provider is asked for. */
fun capabilityLabel(capability: String): String = when (capability) {
    "METADATA_SEARCH" -> t("tv.administrationTerms.capability.search", "Search by name")
    "METADATA_FETCH" -> t("tv.administrationTerms.capability.fetch", "Details, cast and artwork")
    else -> readable(capability)
}

/** What kind of device was admitted. */
fun deviceKindLabel(kind: String): String = when (kind) {
    "ANDROID_TV" -> t("tv.administrationTerms.deviceKind.television", "Television")
    "BROWSER" -> t("tv.administrationTerms.deviceKind.browser", "Browser")
    "PHONE" -> t("tv.administrationTerms.deviceKind.phone", "Phone")
    "TABLET" -> t("tv.administrationTerms.deviceKind.tablet", "Tablet")
    else -> readable(kind)
}

/** How serious a line in the rule log is. */
fun logLevelLabel(level: String): String = when (level) {
    "INFO" -> t("tv.administrationTerms.logLevel.info", "Info")
    "WARN" -> t("tv.administrationTerms.logLevel.warning", "Warning")
    "ERROR" -> t("tv.administrationTerms.logLevel.error", "Error")
    else -> readable(level)
}

/** The kind of place a permission is granted at. */
fun scopeLabel(scope: String): String = when (scope) {
    "GLOBAL" -> t("tv.administrationTerms.scope.global", "everywhere")
    "TAXONOMY" -> t("tv.administrationTerms.scope.taxonomy", "a genre, section or album")
    "TITLE" -> t("tv.administrationTerms.scope.title", "a film or series")
    "LIBRARY_ROOT" -> t("tv.administrationTerms.scope.libraryRoot", "a media folder")
    "ASSET" -> t("tv.administrationTerms.scope.asset", "a photograph")
    "PROFILE" -> t("tv.administrationTerms.scope.profile", "a profile")
    "DIRECTORY" -> t("tv.administrationTerms.scope.directory", "a shared folder")
    "DIRECTORY_PATH" -> t("tv.administrationTerms.scope.directoryPath", "a folder by its path")
    "SELF" -> t("tv.administrationTerms.scope.self", "their own")
    else -> readable(scope)
}

private fun readable(value: String): String = value.lowercase().replace('_', ' ')
