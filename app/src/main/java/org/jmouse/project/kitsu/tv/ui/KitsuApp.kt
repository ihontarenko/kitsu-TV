package org.jmouse.project.kitsu.tv.ui

import org.jmouse.project.kitsu.tv.text.t
import androidx.compose.runtime.LaunchedEffect
import org.jmouse.project.kitsu.tv.ui.components.Notices
import org.jmouse.project.kitsu.tv.ui.components.NoticeTone
import org.jmouse.project.kitsu.tv.ui.components.Notice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.Text
import org.jmouse.project.kitsu.tv.data.ServerConnection
import org.jmouse.project.kitsu.tv.kitsuContainer
import org.jmouse.project.kitsu.tv.ui.components.Bay
import org.jmouse.project.kitsu.tv.ui.components.BrandSpinner
import org.jmouse.project.kitsu.tv.ui.components.NoticeSound
import org.jmouse.project.kitsu.tv.ui.screens.AboutTitleScreen
import org.jmouse.project.kitsu.tv.ui.screens.AdministrationRuleScreen
import org.jmouse.project.kitsu.tv.ui.screens.AdministrationScreen
import org.jmouse.project.kitsu.tv.ui.screens.AddTitleScreen
import org.jmouse.project.kitsu.tv.ui.screens.CatalogScreen
import org.jmouse.project.kitsu.tv.ui.screens.CollectionsScreen
import org.jmouse.project.kitsu.tv.ui.screens.FolderScreen
import org.jmouse.project.kitsu.tv.ui.screens.Counted
import org.jmouse.project.kitsu.tv.ui.screens.KIND_COLLECTION
import org.jmouse.project.kitsu.tv.ui.screens.KIND_SUB_GENRE as KIND_SUB_GENRE_ROUTE
import org.jmouse.project.kitsu.tv.ui.screens.ConnectScreen
import org.jmouse.project.kitsu.tv.ui.screens.FavouritesScreen
import org.jmouse.project.kitsu.tv.ui.screens.HomeScreen
import org.jmouse.project.kitsu.tv.ui.screens.NodeShelfScreen
import org.jmouse.project.kitsu.tv.ui.screens.NodeTitlesScreen
import org.jmouse.project.kitsu.tv.ui.screens.PairingScreen
import org.jmouse.project.kitsu.tv.ui.screens.PersonScreen
import org.jmouse.project.kitsu.tv.ui.screens.ProviderPersonScreen
import org.jmouse.project.kitsu.tv.ui.screens.PlayerScreen
import org.jmouse.project.kitsu.tv.ui.screens.ProfilesScreen
import org.jmouse.project.kitsu.tv.ui.screens.SearchScreen
import org.jmouse.project.kitsu.tv.ui.screens.SplashScreen
import org.jmouse.project.kitsu.tv.ui.screens.LibrariesScreen
import org.jmouse.project.kitsu.tv.ui.screens.ServerScreen
import org.jmouse.project.kitsu.tv.ui.screens.SeriesScreen
import org.jmouse.project.kitsu.tv.ui.screens.TitleScreen
import org.jmouse.project.kitsu.tv.ui.screens.YourProfileScreen
import org.jmouse.project.kitsu.tv.ui.screens.SystemSettingsScreen
import org.jmouse.project.kitsu.tv.ui.theme.Kitsu
import org.jmouse.project.kitsu.tv.ui.theme.KitsuType
import java.net.URLEncoder

/** The addresses inside the library, once the gate is open. */
object Routes {
    const val HOME = "home"
    const val FILMS = "films"
    const val SERIES = "series"

    /** Every title, both kinds — and the one wall that offers "Add a title". */
    const val TITLES = "titles"

    /** Adding a title from the provider, reached from the Titles bay. */
    const val ADD_TITLE = "titles/add"
    const val COLLECTIONS = "collections"
    const val GENRES = "genres"
    /** What this profile keeps — a collection, reached from the Collections bay rather than a bay of its own. */
    const val FAVOURITES = "collections/favourites"

    /** One of this profile's folders of collections. */
    const val FOLDER = "folder/{slug}"
    const val SEARCH = "search"
    const val TITLE = "title/{slug}"
    const val SERIES_DETAIL = "series/{slug}"
    /** ⚠️ `sameAs` is the copy that was playing — the next episode comes from the same folder. */
    const val PLAYER = "player/{playKey}?sameAs={sameAs}"
    const val PERSON = "person/{personId}"

    /** One person's whole career at a film database — reached from Add, or from a library person. */
    const val PROVIDER_PERSON = "provider-person/{source}/{externalId}?name={name}"

    /**
     * What is filed under one genre or one collection.
     *
     * ⚠️ ONE ROUTE FOR BOTH, with the node's kind in the path. They differ only in which request
     * fetches the titles; a second route would be a second screen to keep in step with the first.
     */
    const val NODE = "node/{kind}/{slug}"

    /*
      ⚠️ THE THREE READ-RATHER-THAN-BROWSE SCREENS, and none of them is a bay. The deck holds seven
      already, which is what a 1080p bar takes. Each is reached from the thing it is about: a
      title's own About, and the profile tile that was always in the corner.
     */
    const val ABOUT_TITLE = "about/{slug}"
    const val PROFILE = "profile"
    const val SERVER = "server"
    const val LIBRARIES = "server/libraries"

    /** How the server is set up, read only — reached from the profile page, for a profile with the rights. */
    const val ADMINISTRATION = "profile/administration"

    /** The installation's settings — for a profile holding settings:write (MH-288). */
    const val SYSTEM_SETTINGS = "profile/settings"

    /** One jMS rule's source, from the administration's rules. */
    const val ADMINISTRATION_RULE = "profile/administration/rule/{ruleId}"

    fun title(slug: String) = "title/${encode(slug)}"
    fun seriesDetail(slug: String) = "series/${encode(slug)}"
    fun player(playKey: String, sameAs: String? = null) =
        "player/${encode(playKey)}" + (sameAs?.let { "?sameAs=${encode(it)}" } ?: "")
    fun person(personId: String) = "person/${encode(personId)}"
    fun providerPerson(source: String, externalId: String, name: String) =
        "provider-person/${encode(source)}/${encode(externalId)}?name=${encode(name)}"
    fun node(kind: String, slug: String) = "node/${encode(kind)}/${encode(slug)}"
    fun aboutTitle(slug: String) = "about/${encode(slug)}"
    fun folder(slug: String) = "folder/${encode(slug)}"
    fun administrationRule(ruleId: String) = "profile/administration/rule/${encode(ruleId)}"

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}

/**
 * The whole application: the session gate decides which of the three doors is
 * open — connect, pairing, profiles — and behind them the library has its own
 * navigation.
 */
@Composable
fun KitsuApp() {
    val container = LocalContext.current.kitsuContainer
    val sessionModel: SessionViewModel = viewModel(factory = SessionViewModel.factory(container))
    val gate by sessionModel.gate.collectAsStateWithLifecycle()

    /*
      ⚠️ THE SPLASH IS A GATE OF ITS OWN, IN FRONT OF THE SESSION'S, AND IT IS NOT A LOADING SCREEN.
      It runs on a fixed clock and hands over, whatever the session is doing — so a server that is
      switched off and takes its whole timeout to say so does not hold a black television for that
      long. What the splash covers is the OTHER half of the same launch: on a television there is no
      tab and no title bar, so without it the panel goes from the Leanback wallpaper straight into
      either "Connecting…" or a wall of posters, with nothing anywhere naming what was opened.

      ⚠️ REMEMBERED ACROSS RECOMPOSITION, NOT ACROSS THE PROCESS. `rememberSaveable` would keep it
      shown-once over a configuration change, which sounds tidier and is wrong: a television that
      changes its UI mode has not been launched again, and one that was cold-started has, and only
      the second of those should see the mark.
     */
    var opening by remember { mutableStateOf(true) }

    if (opening) {
        SplashScreen(onDone = { opening = false })
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Kitsu.background)) {
        when (val current = gate) {
            /* ⚠️ THE MARK, NOT JUST THE WORD. This is the one screen that can stand for as long as a
               switched-off server takes to time out, and a line of grey mono in the middle of a black
               panel reads as a television that has crashed. The mark walking says the application is
               alive, and it is the product's own waiting indicator rather than a borrowed arc.

               ⚠️ NOT THE SPLASH. The splash is a fixed gesture that hands over; this is the honest
               "still asking", and merging them would mean a server that never answers holds the
               brand screen for ever. */
            Gate.Loading -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                BrandSpinner(size = 40.dp)
                Text(text = t("tv.gate.connecting", "Connecting…"), style = KitsuType.meta)
            }

            is Gate.NeedServer -> ConnectScreen(
                lastAddress = current.lastAddress,
                problem = current.problem,
                discovery = container.discovery,
                onChoose = { address, name -> sessionModel.chooseServer(address, name) },
            )

            is Gate.NeedPairing -> PairingScreen(
                connection = current.connection,
                onAdmitted = { token -> sessionModel.admitted(token) },
                onChangeServer = { sessionModel.forgetServer() },
            )

            is Gate.NeedProfile -> ProfilesScreen(
                connection = current.connection,
                session = current.session,
                onChosen = { session -> sessionModel.profileChosen(session) },
                onChangeServer = { sessionModel.forgetServer() },
            )

            /*
              ⚠️ THE LIBRARY IS KEYED ON THE PROFILE, AND THAT IS A CORRECTNESS FIX RATHER THAN
              TIDINESS. What the catalogue answers DEPENDS ON WHO IS ASKING: every listing on the
              server drops the titles in a branch this profile may not read, and a title inside one
              is NOT FOUND at its own address rather than merely absent from a wall. So a profile is
              not a label on the corner of the screen — it is the scope of everything behind it.

              Without the key, `Library`'s `rememberNavController` and every wall's remembered rows
              survive the switch: the television would come back from the picker still standing
              inside a genre the new profile cannot open, showing the previous profile's posters,
              and would only find out one press later. `key` disposes all of it and builds the
              library again, which is what "the titles are this profile's" has to mean in practice.

              ⚠️ THE KEY IS THE PROFILE'S IDENTIFIER, NOT THE SESSION. A session object is replaced
              by the re-check that follows any lost request, and keying on it would throw the whole
              library away — scroll position, open screen and all — every time a 401 was recovered
              from.
             */
            is Gate.Ready -> key(current.session.profile?.id) {
                Library(
                    sessionModel = sessionModel,
                    gate = current,
                )
            }
        }
    }
}

@Composable
private fun Library(sessionModel: SessionViewModel, gate: Gate.Ready) {
    val navigation: NavHostController = rememberNavController()
    val connection = gate.connection

    /* ⚠️ WHAT THE HOUSEHOLD'S RULES SAY, AS IT ARRIVES. Asked every few seconds while the library is
       open; each notice shows once, in the rule's own words, signed with the rule — nothing is
       written here. Silent on failure: a nudge must not become an error. The pace is the server's
       setting, read once as the library opens; an older server answers 404 and the default holds. */
    val soundContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(connection) {
        val pollMilliseconds = resolveNoticePollMilliseconds(connection)
        while (true) {
            val arrived = runCatching { connection.api.notices() }.getOrNull().orEmpty()
            arrived.forEach { notice ->
                Notices.show(
                    Notice(
                        title = notice.from ?: "",
                        lead = notice.text,
                        tone = if (notice.level == "warn") NoticeTone.Refusal else NoticeTone.Information,
                    ),
                )
            }
            /* ⚠️ ONCE FOR A BATCH, NOT ONCE PER NOTICE. A scan finishing can post four messages in
               one poll, and four chimes on top of one another is a fault rather than a nudge. */
            if (arrived.isNotEmpty()) {
                NoticeSound.play(soundContext, connection.api)
            }
            kotlinx.coroutines.delay(pollMilliseconds)
        }
    }
    val profile = gate.session.profile

    fun goToBay(bay: Bay) {
        val route = when (bay) {
            Bay.Home -> Routes.HOME
            Bay.Films -> Routes.FILMS
            Bay.Series -> Routes.SERIES
            Bay.Titles -> Routes.TITLES
            Bay.Collections -> Routes.COLLECTIONS
            Bay.Search -> Routes.SEARCH
        }
        navigation.navigate(route) {
            popUpTo(Routes.HOME) { inclusive = bay == Bay.Home }
            launchSingleTop = true
        }
    }

    val deckState = DeckState(
        serverName = connection.displayName,
        profileInitials = profile?.initials ?: profile?.displayName?.take(1)?.uppercase(),
        profilePicture = connection.avatarUrl(profile?.avatarFileId),
        accountName = gate.session.account?.displayName,
        profileName = profile?.displayName,
        onBay = ::goToBay,
        /* ⚠️ Opens the profile PAGE. It used to switch profile outright — a destructive control
           wearing the clothes of a label, with nothing to say it would do that. */
        onProfile = { navigation.navigate(Routes.PROFILE) { launchSingleTop = true } },
    )

    NavHost(navController = navigation, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                connection = connection,
                deck = deckState,
                onTitle = { card -> navigation.navigate(if (card.kind == "SERIES") Routes.seriesDetail(card.slug) else Routes.title(card.slug)) },
                onPlay = { playKey -> navigation.navigate(Routes.player(playKey)) },
                onGenre = { navigation.navigate(Routes.FILMS) },
            )
        }
        composable(Routes.FILMS) {
            CatalogScreen(
                connection = connection,
                deck = deckState,
                kind = "MOVIE",
                onTitle = { card -> navigation.navigate(Routes.title(card.slug)) },
                onlyWithFiles = true,
            )
        }
        composable(Routes.SERIES) {
            CatalogScreen(
                connection = connection,
                deck = deckState,
                kind = "SERIES",
                onTitle = { card -> navigation.navigate(Routes.seriesDetail(card.slug)) },
                onlyWithFiles = true,
            )
        }
        composable(Routes.TITLES) {
            /* ⚠️ THE BUTTON IS ALWAYS THERE. It used to vanish for a profile that may not add, and a
               person on that profile concluded that the television cannot add films at all — the add
               screen says instead WHY this profile may not, and which profile can. */
            CatalogScreen(
                connection = connection,
                deck = deckState,
                kind = null,
                onTitle = { card -> navigation.navigate(if (card.kind == "SERIES") Routes.seriesDetail(card.slug) else Routes.title(card.slug)) },
                onAdd = { navigation.navigate(Routes.ADD_TITLE) { launchSingleTop = true } },
            )
        }
        composable(Routes.ADD_TITLE) {
            AddTitleScreen(
                connection = connection,
                deck = deckState,
                /* ⚠️ The add screen is REPLACED by the title it produced, so BACK from the new title
                   returns to the Titles wall — where the title now is — rather than to a search the
                   person has finished with. */
                onTitle = { card ->
                    navigation.navigate(if (card.kind == "SERIES") Routes.seriesDetail(card.slug) else Routes.title(card.slug)) {
                        popUpTo(Routes.ADD_TITLE) { inclusive = true }
                    }
                },
                onPerson = { person -> navigation.navigate(Routes.providerPerson(person.source, person.externalId, person.name)) },
            )
        }
        composable(
            Routes.PROVIDER_PERSON,
            arguments = listOf(
                navArgument("source") { type = NavType.StringType },
                navArgument("externalId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            ProviderPersonScreen(
                connection = connection,
                deck = deckState,
                source = entry.arguments?.getString("source").orEmpty(),
                externalId = entry.arguments?.getString("externalId").orEmpty(),
                name = entry.arguments?.getString("name").orEmpty(),
                onTitle = { card -> navigation.navigate(if (card.kind == "SERIES") Routes.seriesDetail(card.slug) else Routes.title(card.slug)) },
                onCollection = { slug -> navigation.navigate(Routes.node(KIND_COLLECTION, slug)) },
            )
        }
        composable(Routes.COLLECTIONS) {
            CollectionsScreen(
                connection = connection,
                deck = deckState,
                onFavourites = { navigation.navigate(Routes.FAVOURITES) },
                onGenres = { navigation.navigate(Routes.GENRES) },
                onFolder = { folder -> navigation.navigate(Routes.folder(folder.slug)) },
                onCollection = { collection -> navigation.navigate(Routes.node(KIND_COLLECTION, collection.slug)) },
            )
        }
        composable(Routes.FOLDER, arguments = listOf(navArgument("slug") { type = NavType.StringType })) { entry ->
            FolderScreen(
                connection = connection,
                deck = deckState,
                slug = entry.arguments?.getString("slug").orEmpty(),
                onCollection = { collection -> navigation.navigate(Routes.node(KIND_COLLECTION, collection.slug)) },
            )
        }
        composable(Routes.GENRES) {
            NodeShelfScreen(
                connection = connection,
                deck = deckState,
                /* ⚠️ Genres are a shelf inside Collections, not a bay of their own. */
                bay = Bay.Collections,
                kind = "genre",
                eyebrow = t("tv.genres.eyebrow", "Browse"),
                heading = t("tv.genres.heading", "Genres"),
                counting = Counted::genres,
                empty = t("tv.genres.empty", "No genres yet — they arrive with the metadata."),
                onNode = { node -> navigation.navigate(Routes.node("genre", node.slug)) },
            )
        }
        composable(Routes.FAVOURITES) {
            FavouritesScreen(
                connection = connection,
                deck = deckState,
                onTitle = { card -> navigation.navigate(if (card.kind == "SERIES") Routes.seriesDetail(card.slug) else Routes.title(card.slug)) },
            )
        }
        composable(
            Routes.NODE,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("slug") { type = NavType.StringType },
            ),
        ) { entry ->
            val kind = entry.arguments?.getString("kind").orEmpty()
            val slug = entry.arguments?.getString("slug").orEmpty()
            NodeTitlesScreen(
                connection = connection,
                deck = deckState,
                /* ⚠️ The bay stays lit while inside a node, so the deck keeps saying where this is. */
                bay = Bay.Collections,
                kind = kind,
                slug = slug,
                eyebrow = when (kind) {
                    KIND_COLLECTION -> t("tv.node.collection", "Collection")
                    /* ⚠️ A `folder` reached from here is a SUB-GENRE, not one of this profile's own
                       folders of collections — those have their own route and their own screen. The
                       word has to say which, because arriving inside "Stephen King" labelled
                       "Collection" would say it belongs to the person rather than the household. */
                    KIND_SUB_GENRE_ROUTE -> t("tv.node.subGenre", "Sub-genre")
                    else -> t("tv.node.genre", "Genre")
                },
                onTitle = { card -> navigation.navigate(if (card.kind == "SERIES") Routes.seriesDetail(card.slug) else Routes.title(card.slug)) },
                /* A sub-genre opens as the same screen, one level down. */
                onNode = { subGenre -> navigation.navigate(Routes.node(KIND_SUB_GENRE_ROUTE, subGenre.slug)) },
            )
        }
        composable(Routes.ABOUT_TITLE, arguments = listOf(navArgument("slug") { type = NavType.StringType })) { entry ->
            AboutTitleScreen(
                connection = connection,
                deck = deckState,
                slug = entry.arguments?.getString("slug").orEmpty(),
                onPerson = { personId -> navigation.navigate(Routes.person(personId)) },
            )
        }
        composable(Routes.PROFILE) {
            YourProfileScreen(
                connection = connection,
                deck = deckState,
                session = gate.session,
                onSwitchProfile = { sessionModel.switchProfile() },
                onLanguageChanged = { sessionModel.reloadTexts() },
                onSignOut = { onRefused -> sessionModel.signOut(onRefused) },
                onServer = { navigation.navigate(Routes.SERVER) { launchSingleTop = true } },
                onAdministration = { navigation.navigate(Routes.ADMINISTRATION) { launchSingleTop = true } },
                onSystemSettings = { navigation.navigate(Routes.SYSTEM_SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(Routes.SYSTEM_SETTINGS) {
            SystemSettingsScreen(connection = connection, deck = deckState)
        }
        composable(Routes.ADMINISTRATION) {
            AdministrationScreen(
                connection = connection,
                deck = deckState,
                onSwitchProfile = { sessionModel.switchProfile() },
                onServer = { navigation.navigate(Routes.SERVER) { launchSingleTop = true } },
                onRule = { ruleId -> navigation.navigate(Routes.administrationRule(ruleId)) },
            )
        }
        composable(Routes.ADMINISTRATION_RULE) { entry ->
            AdministrationRuleScreen(
                connection = connection,
                deck = deckState,
                ruleId = entry.arguments?.getString("ruleId").orEmpty(),
                onSwitchProfile = { sessionModel.switchProfile() },
            )
        }
        composable(Routes.SERVER) {
            ServerScreen(
                connection = connection,
                deck = deckState,
                session = gate.session,
                onChangeServer = { sessionModel.forgetServer() },
                onLibraries = { navigation.navigate(Routes.LIBRARIES) { launchSingleTop = true } },
            )
        }
        composable(Routes.LIBRARIES) {
            LibrariesScreen(connection = connection, deck = deckState)
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                connection = connection,
                deck = deckState,
                onTitle = { card -> navigation.navigate(if (card.kind == "SERIES") Routes.seriesDetail(card.slug) else Routes.title(card.slug)) },
            )
        }
        composable(Routes.TITLE, arguments = listOf(navArgument("slug") { type = NavType.StringType })) { entry ->
            val slug = entry.arguments?.getString("slug").orEmpty()
            TitleScreen(
                connection = connection,
                deck = deckState,
                slug = slug,
                onPlay = { playKey -> navigation.navigate(Routes.player(playKey)) },
                onTitle = { card -> navigation.navigate(if (card.kind == "SERIES") Routes.seriesDetail(card.slug) else Routes.title(card.slug)) },
                onPerson = { personId -> navigation.navigate(Routes.person(personId)) },
                onAbout = { slug -> navigation.navigate(Routes.aboutTitle(slug)) },
            )
        }
        composable(Routes.PERSON, arguments = listOf(navArgument("personId") { type = NavType.StringType })) { entry ->
            val personId = entry.arguments?.getString("personId").orEmpty()
            PersonScreen(
                connection = connection,
                deck = deckState,
                personId = personId,
                onTitle = { card -> navigation.navigate(if (card.kind == "SERIES") Routes.seriesDetail(card.slug) else Routes.title(card.slug)) },
                onPerson = { next -> navigation.navigate(Routes.person(next)) },
                onCareer = { source, externalId, name -> navigation.navigate(Routes.providerPerson(source, externalId, name)) },
            )
        }
        composable(Routes.SERIES_DETAIL, arguments = listOf(navArgument("slug") { type = NavType.StringType })) { entry ->
            val slug = entry.arguments?.getString("slug").orEmpty()
            SeriesScreen(
                connection = connection,
                deck = deckState,
                slug = slug,
                onPlay = { playKey -> navigation.navigate(Routes.player(playKey)) },
                onTitle = { card -> navigation.navigate(if (card.kind == "SERIES") Routes.seriesDetail(card.slug) else Routes.title(card.slug)) },
                onPerson = { personId -> navigation.navigate(Routes.person(personId)) },
                onAbout = { about -> navigation.navigate(Routes.aboutTitle(about)) },
            )
        }
        composable(
            Routes.PLAYER,
            arguments = listOf(
                navArgument("playKey") { type = NavType.StringType },
                navArgument("sameAs") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val playKey = entry.arguments?.getString("playKey").orEmpty()
            PlayerScreen(
                connection = connection,
                playKey = playKey,
                sameAs = entry.arguments?.getString("sameAs"),
                onLeave = { navigation.popBackStack() },
                onNext = { next, sameAs ->
                    navigation.navigate(Routes.player(next, sameAs)) { popUpTo(Routes.PLAYER) { inclusive = true } }
                },
            )
        }
    }
}

/** What every screen hands to its deck. */
data class DeckState(
    val serverName: String?,
    val profileInitials: String?,
    /** The profile's face, when it has one; the deck draws the initials otherwise. */
    val profilePicture: String?,
    /** Whose household this is — the level above the profile, as the browser shows it. */
    val accountName: String? = null,
    val profileName: String? = null,
    val onBay: (Bay) -> Unit,
    val onProfile: () -> Unit,
)

/** How often the library asks for new notices when the server does not say. */
private const val DEFAULT_NOTICE_POLL_SECONDS = 15L

/** The server's setting naming that pace, in whole seconds. */
private const val NOTICE_POLL_SETTING = "notices.pollSeconds"

/**
 * The server's notice pace, or the default when the call fails or the value is missing or not a
 * positive number.
 *
 * ⚠️ IT READS THE SOUND SETTINGS OUT OF THE SAME ANSWER. Both are installation settings marked for
 * clients, they arrive in one map, and asking twice for two halves of it would be two calls for
 * nothing. See [NoticeSound].
 */
private suspend fun resolveNoticePollMilliseconds(connection: ServerConnection): Long {
    val values = runCatching { connection.api.clientSettings() }.getOrNull().orEmpty()
    NoticeSound.read(values)

    val seconds = values[NOTICE_POLL_SETTING]
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { value -> value > 0 }
        ?: DEFAULT_NOTICE_POLL_SECONDS
    return seconds * 1_000L
}
