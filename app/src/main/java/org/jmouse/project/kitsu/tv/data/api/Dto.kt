package org.jmouse.project.kitsu.tv.data.api

import kotlinx.serialization.Serializable

/*
  What travels between the television and the server — the records in
  `Kitsu/BE/.../dto`, spelled once more in Kotlin. Field names are the server's;
  a field the server may omit (`@JsonInclude(NON_NULL)`) is nullable here with a
  default, so a missing key never fails the whole answer.
 */

/* ── Installation and pairing ─────────────────────────────────────────────── */

@Serializable
data class InstallationResponse(
    val id: String,
    val displayName: String? = null,
)

@Serializable
data class StartPairingRequest(
    val displayName: String,
    val kind: String = "ANDROID_TV",
    val directPlayProfile: String? = null,
)

@Serializable
data class StartPairingResponse(
    val pairingId: String,
    val code: String,
    val expiresAt: String? = null,
)

@Serializable
data class PairingStateResponse(
    val state: String,
    val token: String? = null,
    val deviceId: String? = null,
    val installationId: String? = null,
)

/* ── Session ──────────────────────────────────────────────────────────────── */

@Serializable
data class AccountSummary(
    val id: String,
    val login: String? = null,
    val displayName: String? = null,
    val enabled: Boolean = true,
)

@Serializable
data class ProfileSummary(
    val id: String,
    val displayName: String,
    val initials: String? = null,
    val sortOrder: Int = 0,
    val pinRequired: Boolean = false,
    /** `INITIALS`, `PRESET` or `UPLOAD`. */
    val avatarKind: String? = null,
    /**
     * The picture to show — the upload, or the copy of a generated face the browser rendered when it
     * was chosen. ⚠️ This client cannot draw a descriptor; the browser's engine is the only one that
     * can, which is why the server keeps a picture of it. Null means letters.
     */
    val avatarFileId: String? = null,
)

@Serializable
data class Session(
    val paired: Boolean,
    val account: AccountSummary? = null,
    val profile: ProfileSummary? = null,
    val profiles: List<ProfileSummary> = emptyList(),
    val deviceToken: String? = null,
)

@Serializable
data class ChooseProfileRequest(
    val profileId: String,
    val pin: String? = null,
)

/* ── Catalogue ────────────────────────────────────────────────────────────── */

@Serializable
data class PageResponse<T>(
    val content: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

@Serializable
data class TitleCard(
    val id: String,
    val kind: String,
    val slug: String,
    val displayName: String,
    val year: Int? = null,
    val rating: Double? = null,
    val voteCount: Int? = null,
    val posterPath: String? = null,
    val posterFileId: String? = null,
)

/** A candidate at the metadata provider. `posterUrl` is built by the server, relative to itself — pass it through `ServerConnection.resolve`. */
@Serializable
data class ProviderResult(
    val kind: String,
    val externalId: String,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val rating: Double? = null,
    /**
     * The address this work already has in the library, or null when the house does not hold it.
     * ⚠️ The server's answer, keyed on the provider's id — never guessed here from a name and a year.
     */
    val librarySlug: String? = null,
    /**
     * Which film database answered for this candidate — `TMDB` or `IMDB`. ⚠️ Sent back on import
     * with the id, because an id means nothing without the registry it belongs to: TMDB 603 and
     * IMDb tt0133093 are the same film, and 603 at IMDb is nothing at all.
     */
    val source: String? = null,
)

/**
 * A film database this server can search, as `GET metadata/sources` lists it, in the household's
 * priority order.
 *
 * @param available false when it is known but not set up — no key, switched off — and then
 *                  `reason` says why, in words to show beside it
 */
@Serializable
data class MetadataSource(
    val source: String,
    val name: String,
    val available: Boolean = false,
    val reason: String? = null,
)

/** One person a film database found by name — `GET metadata/people`. */
@Serializable
data class ProviderPerson(
    val source: String,
    val externalId: String,
    val name: String,
    val knownFor: String? = null,
    /** This server's address for the portrait, never the film database's own. */
    val portraitUrl: String? = null,
    /** A few titles they are known for — how two people with one name are told apart. */
    val knownForWorks: List<String> = emptyList(),
)

/**
 * One part a person had in a work — "Novel", or the character they played.
 *
 * @param category the server's grouping: SOURCE_MATERIAL, WRITING, DIRECTING, PRODUCTION, ACTING,
 *                 APPEARANCE (as themselves) or CREW
 */
@Serializable
data class WorkPart(
    val category: String,
    val department: String? = null,
    val job: String? = null,
    val character: String? = null,
)

/**
 * One work in a person's career at a film database, with every part they had in it.
 *
 * @param votes how many people rated it there — what tells a known film from a student short
 */
@Serializable
data class ProviderWork(
    val title: ProviderResult,
    val votes: Int? = null,
    val categories: List<String> = emptyList(),
    val parts: List<WorkPart> = emptyList(),
)

/** A person's whole career at one film database, newest first. */
@Serializable
data class ProviderWorks(
    val source: String,
    val personExternalId: String,
    val works: List<ProviderWork> = emptyList(),
)

/** One title in a batch, and how far it got. */
@Serializable
data class ImportBatchItem(
    val kind: String,
    val externalId: String,
    val title: String? = null,
    /** QUEUED, IMPORTING, ADDED, ALREADY_HERE or FAILED. */
    val state: String,
    val slug: String? = null,
    val problem: String? = null,
)

/** Many titles being taken in on the server, one at a time, each with its own state. */
@Serializable
data class ImportBatch(
    val id: String,
    val source: String? = null,
    val collectionSlug: String? = null,
    val collectionName: String? = null,
    val finished: Boolean = false,
    val total: Int = 0,
    val added: Int = 0,
    val alreadyHere: Int = 0,
    val failed: Int = 0,
    val items: List<ImportBatchItem> = emptyList(),
)

/** What an import answers: the title as it now exists in the library. */
@Serializable
data class ImportedTitle(
    val id: String,
    val kind: String,
    val displayName: String,
    val slug: String? = null,
)

@Serializable
data class NodeCard(
    val id: String,
    val slug: String,
    val displayName: String,
    val kind: String? = null,
    val icon: String? = null,
)

@Serializable
data class TitleDetail(
    val id: String,
    val kind: String,
    val slug: String,
    val displayName: String,
    val sortName: String? = null,
    val tagline: String? = null,
    val summary: String? = null,
    val originalLanguage: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val voteCount: Int? = null,
    val addedAt: String? = null,
    val releasedOn: String? = null,
    val runtimeMinutes: Int? = null,
    val endedYear: Int? = null,
    val status: String? = null,
    val posterPath: String? = null,
    val posterFileId: String? = null,
    val backdropPath: String? = null,
    val backdropFileId: String? = null,
    val nodes: List<NodeCard> = emptyList(),
    val available: Boolean = false,
)

/**
 * One place in the tree, as a shelf needs it.
 *
 * ⚠️ `icon` is a name from the server's own vocabulary, never a glyph — see `nodeIcon`, which is
 * where the television decides what each name looks like.
 *
 * ⚠️ The picture is carried the same way a poster is: a provider PATH or a local FILE id, and
 * `ServerConnection.imageUrl` decides between them. One rule for every picture in the product.
 */
@Serializable
data class NodeSummary(
    val id: String,
    val slug: String,
    val displayName: String,
    val kind: String? = null,
    val icon: String? = null,
    val imagePath: String? = null,
    val imageFileId: String? = null,
    val titleCount: Long = 0,
    /** The place this one sits in — how a profile's folder is told what it holds. */
    val parentId: String? = null,
    /** The caller's own profile's, rather than the household's. */
    val personal: Boolean = false,
    /** Made by the metadata sync — a franchise the provider knows. */
    val synced: Boolean = false,
)

@Serializable
data class CreditEntry(
    val personId: String,
    val personName: String,
    val kind: String? = null,
    val characterName: String? = null,
    val department: String? = null,
    val job: String? = null,
    val portraitPath: String? = null,
    val portraitFileId: String? = null,
    val sortOrder: Int = 0,
)

@Serializable
data class PersonDetail(
    val id: String,
    val displayName: String,
    val sortName: String? = null,
    val summary: String? = null,
    val bornOn: String? = null,
    val diedOn: String? = null,
    val bornIn: String? = null,
    val knownFor: String? = null,
    val portraitPath: String? = null,
    val portraitFileId: String? = null,
    val aliases: List<String> = emptyList(),
    /** Which film database knows this person — null for somebody only named by a credit. */
    val externalSource: String? = null,
    /** Their identifier there; with the source, the way to their whole career. */
    val externalId: String? = null,
)

@Serializable
data class PersonCredit(
    val titleId: String,
    val titleKind: String,
    val titleSlug: String,
    val titleName: String,
    val year: Int? = null,
    val posterPath: String? = null,
    val posterFileId: String? = null,
    val kind: String? = null,
    val characterName: String? = null,
    val job: String? = null,
)

@Serializable
data class Collaborator(
    val personId: String,
    val personName: String,
    val portraitPath: String? = null,
    val portraitFileId: String? = null,
    val together: Long = 0,
)

@Serializable
data class SeasonView(
    val id: String,
    val number: Int,
    val displayName: String? = null,
    val airedYear: Int? = null,
    val posterPath: String? = null,
    val posterFileId: String? = null,
    val episodes: List<EpisodeView> = emptyList(),
)

/**
 * One episode, as the CATALOGUE describes it.
 *
 * <h2>⚠️ THE DISK IS NOT IN HERE, AND THAT IS THE SERVER'S DECISION</h2>
 *
 * This record used to require `playKey`, and the screen used `available`, `resumeAtSeconds` and
 * `finished` beside it. The backend removed all four on purpose — `SeriesShelf`'s own note explains
 * it at length: the catalogue describes works that exist whether or not this household owns a copy,
 * and "is there a file, where did somebody get to, what is the key to play it" are three facts about
 * this machine's disk. The arrow points one way now.
 *
 * ⚠️ **The television was never told.** `playKey` stayed required, so `kotlinx.serialization` threw
 * on the first episode of every series and the screen showed *"Field 'playKey' is required … missing
 * at path: $[0].episodes[0]"* — an error about a field, for a contract that had moved.
 *
 * ⚠️ So these four are now OPTIONAL, and `available` is a `Boolean?`: **unknown, not false.** A false
 * would grey out every episode of every series and say the household owns nothing, which is a worse
 * lie than the crash. Where it is unknown the episode is offered and `/play/{playKey}` is left to
 * refuse — the server is the authority on what can be played, and it always was.
 */
@Serializable
data class EpisodeView(
    val id: String,
    /** ⚠️ Null from the catalogue — build it with [episodePlayKey]. */
    val playKey: String? = null,
    val number: Int,
    val displayName: String? = null,
    val airedOn: String? = null,
    val runtimeMinutes: Int? = null,
    /** The episode's own frame, where the film database published one. */
    val stillPath: String? = null,
    val stillFileId: String? = null,
    /** ⚠️ Null means NOBODY ASKED THE DISK, which is not the same as "no file". */
    val available: Boolean? = null,
    val resumeAtSeconds: Int = 0,
    val finished: Boolean = false,
)

/**
 * The key an episode is played and keyed on: `series-slug:s01e04`.
 *
 * ⚠️ BUILT HERE BECAUSE IT IS ALREADY PARSED HERE. The television reads this exact shape to draw an
 * `S1 · E4` badge, so the format was a contract this client already depended on; writing it beside
 * the reading of it is what keeps the two from drifting apart. The backend's `EpisodeKey` is the
 * authority, and `EpisodeKey.SHAPE` is the same pattern.
 */
fun episodePlayKey(seriesSlug: String, seasonNumber: Int, episodeNumber: Int): String =
    "%s:s%02de%02d".format(seriesSlug, seasonNumber, episodeNumber)

/**
 * One file on the server's disk, from `GET /media` — the file domain, not the catalogue.
 *
 * ⚠️ `cover` is how a file names the work it was matched to and, for an episode, WHICH episode:
 * the catalogue no longer says whether a work has a file, so a screen that wants to know asks the
 * files and reads this.
 */
@Serializable
data class MediaAssetResponse(
    val id: String,
    val fileName: String? = null,
    val relativePath: String? = null,
    val sizeBytes: Long = 0,
    val probeState: String? = null,
    val container: String? = null,
    val durationSeconds: Int? = null,
    val videoCodec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Double? = null,
    val bitrate: Long? = null,
    val hdrFormat: String? = null,
    val cover: MediaCover? = null,
)

@Serializable
data class MediaCover(
    val titleSlug: String? = null,
    val playKey: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
)

/* ── Personal ─────────────────────────────────────────────────────────────── */

@Serializable
data class Watched(
    val playKey: String,
    val positionSeconds: Int = 0,
    val durationSeconds: Int? = null,
    val finished: Boolean = false,
    val watchCount: Int = 0,
    val lastWatchedAt: String? = null,
    val title: TitleCard? = null,
)

@Serializable
data class Position(
    val positionSeconds: Int,
    val durationSeconds: Int? = null,
)

@Serializable
data class Forgotten(val forgotten: Int = 0)

@Serializable
data class Progress(
    val playKey: String,
    val positionSeconds: Int = 0,
    val durationSeconds: Int? = null,
    val finished: Boolean = false,
    val watchCount: Int = 0,
    /**
     * ⚠️ The household's console stopped this device (MH-261). Withdrawn tickets alone leave minutes
     * of buffer playing, so the answer to the progress report is where a player learns to stop.
     */
    val halted: Boolean = false,
)

@Serializable
data class Kept(
    val slug: String,
    val changed: Boolean = false,
    val kept: Boolean = false,
)

/* ── Playback ─────────────────────────────────────────────────────────────── */

@Serializable
data class Track(
    val kind: String,
    val index: Int,
    val codec: String? = null,
    val language: String? = null,
    val title: String? = null,
    val channels: Int? = null,
    val defaultTrack: Boolean = false,
    val forced: Boolean = false,
)

@Serializable
data class Playable(
    val playKey: String,
    val displayName: String,
    val kind: String? = null,
    val assetId: String? = null,
    val ticket: String,
    val fileName: String? = null,
    val durationSeconds: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val videoCodec: String? = null,
    val container: String? = null,
    val sizeBytes: Long = 0,
    val resumeAtSeconds: Int = 0,
    val finished: Boolean = false,
    val qualities: List<Int> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val nextPlayKey: String? = null,
    val nextDisplayName: String? = null,
    /** The episode before this one, on the same terms as `nextPlayKey` (MH-285). */
    val previousPlayKey: String? = null,
    val previousDisplayName: String? = null,
    /** Every copy of this work the profile may play — two or more is when there is a choice. */
    val sources: List<PlaybackSource> = emptyList(),
)

/** One copy of the work on the disk — what tells two of them apart on the remote. */
@Serializable
data class PlaybackSource(
    val assetId: String,
    val fileName: String,
    val rootName: String? = null,
    val folder: String? = null,
    val height: Int? = null,
    val videoCodec: String? = null,
    val sizeBytes: Long = 0,
    val durationSeconds: Int? = null,
)

/* ── Errors ───────────────────────────────────────────────────────────────── */

/** The server's ProblemDetail, as much of it as a screen needs to say what went wrong. */
/** `POST /nodes` — a new place in the tree, a collection when `kindSlug` is "collection". */
@Serializable
data class NewNode(
    val kindSlug: String,
    val name: String,
    val parentSlug: String? = null,
)

/** What `POST /nodes` answers. */
@Serializable
data class MadeNode(
    val id: String,
    val slug: String,
    val displayName: String,
    val kind: String? = null,
    val icon: String? = null,
)

/** A message a household's rule posted with `@notify`. */
@Serializable
data class ServerNotice(
    val id: String,
    val from: String? = null,
    val level: String = "info",
    val text: String,
)

@Serializable
data class AccessQuestion(
    val permission: String,
    val scopeType: String? = null,
    val scopeId: String? = null,
)

@Serializable
data class AccessAnswer(
    val permission: String,
    val allowed: Boolean,
    val title: String? = null,
    val explanation: String? = null,
    val words: String? = null,
    val rule: String? = null,
)

@Serializable
data class Problem(
    val title: String? = null,
    val detail: String? = null,
    val status: Int? = null,
    val code: String? = null,
    /** A refusal: the rule as the policy spells it — `@GLOBAL title:write`. */
    val rule: String? = null,
    val permission: String? = null,
    val scope: String? = null,
    /** A refusal: what the policy author wrote with `reason "…"`, apart from the prose. */
    val explanation: String? = null,
)

/* ── The server itself ────────────────────────────────────────────────────── */

/** `GET /server` — the build, the runtime, the frameworks and every library, read off the process. */
@Serializable
data class ServerDescription(
    val application: ServerApplication? = null,
    val runtime: ServerRuntime? = null,
    val components: List<ServerComponent> = emptyList(),
    val media: ServerMedia? = null,
    val libraries: List<ServerLibrary> = emptyList(),
)

@Serializable
data class ServerApplication(
    val name: String? = null,
    val version: String? = null,
    val builtAt: String? = null,
    val javaTarget: String? = null,
    val commit: String? = null,
    val branch: String? = null,
    val dirty: Boolean = false,
    val buildId: String? = null,
)

@Serializable
data class ServerRuntime(
    val javaVersion: String? = null,
    val javaVendor: String? = null,
    val virtualMachine: String? = null,
    val operatingSystem: String? = null,
    val architecture: String? = null,
    val processors: Int = 0,
    val heapUsedBytes: Long = 0,
    val heapMaxBytes: Long = 0,
    val startedAt: String? = null,
    val uptimeSeconds: Long = 0,
)

@Serializable
data class ServerComponent(
    val role: String,
    val name: String,
    val version: String? = null,
)

@Serializable
data class ServerMedia(
    val files: Long = 0,
    val inspected: Long = 0,
    val awaitingInspection: Long = 0,
    val inspectorAnswering: Boolean = false,
    val inspector: String? = null,
)

@Serializable
data class ServerLibrary(
    val name: String,
    val version: String? = null,
    val family: String,
)

/* ── Releases of this app ─────────────────────────────────────────────────── */

/** `GET /releases/android-tv` — what the server has in its Releases / Android TV folder. */
@Serializable
data class ReleasesResponse(
    val latest: AppRelease? = null,
    val releases: List<AppRelease> = emptyList(),
    val folder: String? = null,
    val applicationId: String? = null,
)

/** One APK, as the server read it out of the file's own manifest. */
@Serializable
data class AppRelease(
    val fileId: String,
    val fileName: String? = null,
    val sizeBytes: Long = 0,
    val uploadedAt: String? = null,
    val applicationId: String? = null,
    val versionCode: Long = 0,
    /** The label whole — `1.0.0 · 20260925004 · Stable`. */
    val versionName: String? = null,
    /** The label's parts, as the server took them apart; any may be null for an older build. */
    val version: String? = null,
    val release: Long? = null,
    val codeName: String? = null,
    val applicable: Boolean = false,
) {
    /** What a person is shown: version and code name — `1.0.0 · x-mas build` — or the label as it is. */
    val shownName: String?
        get() = if (version != null) listOfNotNull(version, codeName).joinToString(" · ") else versionName
}

/* ── The interface's words ────────────────────────────────────────────────── */

/**
 * `GET /translations/current` — the language this television should speak and every text that
 * language has. ⚠️ Only what it has: a key missing here is drawn in the English at its call site.
 */
@Serializable
data class CurrentTranslation(
    val language: String = "en",
    val texts: Map<String, String> = emptyMap(),
)

/** One language the server can speak, named in itself and in English. */
@Serializable
data class InterfaceLanguage(
    val code: String,
    val nativeName: String? = null,
    val englishName: String? = null,
    val shipped: Boolean = false,
)

/** One of the chosen profile's preferences, as `GET /settings/preferences` answers it. */
@Serializable
data class PreferenceAnswer(
    val key: String,
    val value: String? = null,
    val defaultTo: String? = null,
    /** False when the profile never chose, and the value is the declared default. */
    val set: Boolean = false,
    /** `FLAG`, `NUMBER`, `LANGUAGE`… — what kind of control it takes. */
    val type: String? = null,
    /** The server's own sentence for it — the fallback for `tv.setting.<key>`. */
    val description: String? = null,
)

/** One installation-wide setting, as `GET /settings/system` and `/settings/application` answer. */
@Serializable
data class SettingAnswer(
    val key: String,
    val level: String? = null,
    val type: String? = null,
    val category: String = "",
    val description: String? = null,
    val value: String? = null,
    val defaultTo: String? = null,
    val secret: Boolean = false,
    val set: Boolean = false,
    val minimum: Long? = null,
    val maximum: Long? = null,
    val unit: String? = null,
    val options: List<String> = emptyList(),
)

/** `PUT /settings/preferences/{key}` — ⚠️ blank clears the preference back to its default. */
@Serializable
data class PreferenceValue(val value: String)
