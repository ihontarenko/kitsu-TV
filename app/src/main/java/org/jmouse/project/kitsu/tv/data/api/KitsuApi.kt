package org.jmouse.project.kitsu.tv.data.api

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * The routes a television calls. Every path is relative to `<server>/api/v1/`.
 *
 * The read side of the catalogue, the personal routes, and a READ-ONLY view of how the
 * server is set up. The television watches and may look; it never administers — scanning,
 * matching, providers and access are changed in the browser only.
 */
interface KitsuApi {

    /* ── Open routes: a device with no credential may call these ────────── */

    @GET("installation")
    suspend fun installation(): InstallationResponse

    @POST("pairing/start")
    suspend fun startPairing(@Body request: StartPairingRequest): StartPairingResponse

    @GET("pairing/{pairingId}")
    suspend fun pairingState(@Path("pairingId") pairingId: String): PairingStateResponse

    @GET("auth/session")
    suspend fun session(): Session

    @POST("auth/profile")
    suspend fun chooseProfile(@Body request: ChooseProfileRequest): Session

    @POST("auth/sign-out")
    suspend fun signOut(): Session

    /**
     * The language this caller's screens speak and its texts — the chosen profile's language, else the
     * machine's, else English. Open, so the pairing screen is drawn in the household's language too.
     */
    @GET("translations/current")
    suspend fun currentTranslation(@Query("area") area: String = "tv"): CurrentTranslation

    @GET("translations/languages")
    suspend fun interfaceLanguages(): List<InterfaceLanguage>

    /* ── The chosen profile's preferences ──────────────────────────────── */

    @GET("settings/preferences")
    suspend fun preferences(): List<PreferenceAnswer>

    @PUT("settings/preferences/{key}")
    suspend fun setPreference(@Path("key") key: String, @Body value: PreferenceValue): PreferenceAnswer

    /* ── The installation's settings — behind settings:read / settings:write ── */

    @GET("settings/system")
    suspend fun systemSettings(): List<SettingAnswer>

    @GET("settings/application")
    suspend fun applicationSettings(): List<SettingAnswer>

    @PUT("settings/installation/{key}")
    suspend fun setInstallationWide(@Path("key") key: String, @Body value: PreferenceValue): SettingAnswer

    /* ── Catalogue ──────────────────────────────────────────────────────── */

    @GET("titles")
    suspend fun titles(
        @Query("kind") kind: String? = null,
        @Query("nodeId") nodeId: String? = null,
        @Query("query") query: String? = null,
        @Query("year") year: Int? = null,
        @Query("sort") sort: String? = null,
        /**
         * True for only what can be watched: a film with a present file, a series with one on any
         * episode. The Films and Series walls pass it; Titles, which is everything, does not.
         */
        @Query("playable") playable: Boolean? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 24,
    ): PageResponse<TitleCard>

    @GET("titles/{slug}")
    suspend fun title(@Path("slug") slug: String): TitleDetail

    @GET("titles/{slug}/credits")
    suspend fun credits(@Path("slug") slug: String): List<CreditEntry>

    @GET("titles/{slug}/seasons")
    suspend fun seasons(@Path("slug") slug: String): List<SeasonView>


    @GET("people/{id}")
    suspend fun person(@Path("id") id: String): PersonDetail

    @GET("people/{id}/credits")
    suspend fun filmography(@Path("id") id: String): List<PersonCredit>

    @GET("people/{id}/collaborators")
    suspend fun collaborators(@Path("id") id: String, @Query("limit") limit: Int = 18): List<Collaborator>

    @GET("nodes")
    /**
     * Every node of a kind with its count. `titleKind` and `playable` narrow the COUNT to the shelf it
     * sits beside — the Series wall asks for playable series, and a genre then says how many it opens on.
     */
    suspend fun nodes(
        @Query("kind") kind: String,
        @Query("titleKind") titleKind: String? = null,
        @Query("playable") playable: Boolean? = null,
    ): List<NodeSummary>

    @GET("nodes/{slug}/titles")
    suspend fun curated(@Path("slug") slug: String): List<TitleCard>

    /** Makes a place in the tree — a collection, here. The server decides whether this profile may. */
    @POST("nodes")
    suspend fun createNode(@Body request: NewNode): MadeNode

    /** Files one title into a collection. ⚠️ Idempotent: filing it twice leaves one row. */
    @PUT("nodes/{slug}/titles/{titleSlug}")
    suspend fun fileInto(@Path("slug") collectionSlug: String, @Path("titleSlug") titleSlug: String)

    /** Takes one title off a collection. The title and the collection both stay. */
    @DELETE("nodes/{slug}/titles/{titleSlug}")
    suspend fun takeOutOf(@Path("slug") collectionSlug: String, @Path("titleSlug") titleSlug: String)

    /* ── Adding to the library ─────────────────────────────────────────── */

    /**
     * What the caller may do, installation-wide — asked once to decide whether to offer adding.
     *
     * ⚠️ A hint for drawing, never the guard: the import route refuses on its own, and this only
     * keeps a television from offering a row somebody could not use.
     */
    @GET("access/mine")
    suspend fun permissions(): List<String>

    /**
     * May the caller do these things — and, where not, the server's own words for why. ⚠️ Those
     * words are shown as they arrive; a screen never writes its own, so editing a policy's
     * `reason "…"` changes what the television says.
     */
    /**
     * What the household's rules said to this television, its account or its face since it last asked
     * — collected once per device.
     */
    @GET("me/notices")
    suspend fun notices(): List<ServerNotice>

    /**
     * The server's settings a client may read, by key — every value a string, e.g.
     * `{"notices.pollSeconds":"15"}`. A client falls back to its own default when a key is absent.
     */
    @GET("settings/client")
    suspend fun clientSettings(): Map<String, String>

    /**
     * The sound a message makes, as the server ships it.
     *
     * ⚠️ FETCHED RATHER THAN BUNDLED. The name comes from an installation setting, so the file it
     * names has to come from the installation too — a set bundled here and another in the browser
     * would make one setting mean two different noises.
     */
    @Streaming
    @GET("notices/sound/{name}")
    suspend fun noticeSound(@Path("name") name: String): ResponseBody

    @POST("access/may")
    suspend fun may(@Body questions: List<AccessQuestion>): List<AccessAnswer>

    /**
     * The film databases this server can search, in the household's order — the choice of WHERE a
     * search goes is the person's, and this is the list they choose from.
     */
    @GET("metadata/sources")
    suspend fun metadataSources(): List<MetadataSource>

    /**
     * Candidates at one film database — not in the library yet. ⚠️ Someone else's rate limit.
     *
     * @param source `TMDB` or `IMDB`; null lets the server take the first it has
     */
    @GET("metadata/search")
    suspend fun searchProvider(
        @Query("query") query: String,
        @Query("language") language: String,
        @Query("source") source: String? = null,
    ): List<ProviderResult>

    /** Takes one candidate into the library; the slug that comes back is where it now lives. */
    @POST("metadata/import")
    suspend fun importTitle(
        @Query("kind") kind: String,
        @Query("externalId") externalId: String,
        @Query("language") language: String,
        @Query("source") source: String? = null,
    ): ImportedTitle

    /** People a film database knows by this name — the way to a whole career at once. */
    @GET("metadata/people")
    suspend fun searchPeople(
        @Query("query") query: String,
        @Query("language") language: String,
        @Query("source") source: String? = null,
    ): List<ProviderPerson>

    /** Everything one person worked on at a film database, each marked where the library has it. */
    @GET("metadata/people/{externalId}/works")
    suspend fun personWorks(
        @Path("externalId") externalId: String,
        @Query("language") language: String,
        @Query("source") source: String? = null,
    ): ProviderWorks

    /**
     * Starts taking many titles in; the answer comes at once and the batch is read back as it goes.
     *
     * ⚠️ A JSON OBJECT, not a data class: this client leaves nulls out (`explicitNulls = false`),
     * and the server refuses a body with a property missing — "no collection" has to arrive as an
     * explicit null. See [importBatchRequest].
     */
    @POST("metadata/import-batches")
    suspend fun startImportBatch(@Body request: JsonObject): ImportBatch

    @GET("metadata/import-batches/{id}")
    suspend fun importBatch(@Path("id") id: String): ImportBatch

    /* ── Files ──────────────────────────────────────────────────────────── */

    /**
     * Files on the server's disk, narrowed by a jMQ expression over the `media` source.
     *
     * ⚠️ THIS REPLACED `titles/{slug}/media`, which the server removed on purpose: a work is a work
     * whether or not anything was scanned, so "which files are this title" is asked of the files. The
     * television kept calling the old route, got a 404 wrapped in `runCatching`, and drew every film
     * as having no file — a greyed-out Play button on a film that was sitting on the disk.
     */
    @GET("media")
    suspend fun files(
        @Query("filter") filter: String? = null,
        @Query("size") size: Int = 500,
    ): PageResponse<MediaAssetResponse>

    /* ── The server ─────────────────────────────────────────────────────── */

    @GET("server")
    suspend fun server(): ServerDescription

    /** Releases of this app — the APK itself is fetched by `AppUpdater`, streamed, not through here. */
    @GET("releases/android-tv")
    suspend fun releases(): ReleasesResponse

    /* ── Administration, read only ──────────────────────────────────────── */

    /*
      ⚠️ EVERY ONE OF THESE IS REFUSED TO A PROFILE WITHOUT THE RIGHTS, with a 403 whose detail says
      why. The pages draw that refusal where the list would have been; nothing here decides access.
     */

    @GET("reading-steps")
    suspend fun readingSteps(): List<ReadingStepView>

    @GET("scripts")
    suspend fun scripts(): List<ScriptSummary>

    @GET("scripts/{id}")
    suspend fun script(@Path("id") id: String): ScriptDetail

    @GET("scripts/log")
    suspend fun scriptLog(): List<ScriptLogLine>

    @GET("matchers")
    suspend fun matchers(): List<MatcherView>

    @GET("providers")
    suspend fun providers(): List<ProviderView>

    @GET("access/subjects")
    suspend fun accessSubjects(): List<AccessSubject>

    @GET("access/roles")
    suspend fun accessRoles(): List<AccessRole>

    @GET("devices")
    suspend fun devices(): List<DeviceView>

    /* ── Personal ───────────────────────────────────────────────────────── */

    @GET("me/progress")
    suspend fun progress(@Query("all") all: Boolean = false): List<Watched>

    @PUT("me/progress/{playKey}")
    suspend fun recordProgress(@Path("playKey") playKey: String, @Body position: Position): Progress

    @GET("me/favourites")
    suspend fun favourites(): List<TitleCard>

    @PUT("me/favourites/{slug}")
    suspend fun keep(@Path("slug") slug: String): Kept

    /**
     * Forgets this profile's progress — a film or a whole series (`title` alone), one season, or one
     * episode. Nobody else's (MH-283).
     */
    @DELETE("me/progress")
    suspend fun forgetProgress(
        @Query("title") title: String,
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
    ): Forgotten

    @DELETE("me/favourites/{slug}")
    suspend fun release(@Path("slug") slug: String): Kept

    /* ── Playback ───────────────────────────────────────────────────────── */

    /**
     * What to play.
     *
     * @param source ⚠️ this exact copy, where a work has several — honoured when this profile may
     *               play it, otherwise the server's default (the biggest)
     * @param sameAs the copy that was playing: the next episode is taken from its folder
     */
    @GET("play/{playKey}")
    suspend fun playable(
        @Path("playKey") playKey: String,
        @Query("source") source: String? = null,
        @Query("sameAs") sameAs: String? = null,
    ): Playable
}

/** The files matched to one work — a film's own, or every episode's of a series. */
suspend fun KitsuApi.filesOfWork(slug: String): List<MediaAssetResponse> =
    files(filter = "m.work = '${slug.replace("'", "''")}'").content

/**
 * The body of a batch import, every property present — see [KitsuApi.startImportBatch].
 *
 * @param collection the slug of the collection each title is filed into, or null for none
 */
fun importBatchRequest(source: String, language: String, collection: String?, works: List<ProviderResult>): JsonObject =
    buildJsonObject {
        put("source", source)
        put("language", language)
        put("collection", if (collection == null) JsonNull else JsonPrimitive(collection))
        put("titles", buildJsonArray {
            works.forEach { work ->
                add(buildJsonObject {
                    put("kind", work.kind)
                    put("externalId", work.externalId)
                    put("title", work.title)
                })
            }
        })
    }
