import java.util.Properties
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

/*
  ⚠️ THE VERSION IS READ, NEVER TYPED HERE. `version.properties` holds major.minor.patch and the release
  stamp (YYYYMMDDNNN); the code is derived from the RELEASE, so a rebuild of the same version still
  upgrades — see that file for why the century is dropped.
 */
/* ⚠️ READ AS UTF-8, AND `Properties.load` DOES NOT DO THAT BY ITSELF. Given an InputStream it decodes
   ISO-8859-1 — a rule from 1998 that nothing in the file says — so a code name with anything but ASCII
   in it arrived as mojibake in `versionName`, on the About screen and in the server's release list,
   while the build printed it correctly (bash read the same file as UTF-8). Handed a Reader it uses the
   reader's charset, which is the whole fix. */
val release = Properties().apply {
    rootProject.file("version.properties").reader(Charsets.UTF_8).use { load(it) }
}
val versionMajor = release.getProperty("major").toInt()
val versionMinor = release.getProperty("minor").toInt()
val versionPatch = release.getProperty("patch").toInt()
val versionNumber = "$versionMajor.$versionMinor.$versionPatch"
val releaseStamp = release.getProperty("release").trim().toLong()
check(releaseStamp in 20_000_000_000..21_474_836_479) {
    "version.properties: release must be YYYYMMDDNNN, 20xx or 2100–2121, got $releaseStamp"
}
/* ⚠️ The code name comes from `-PcodeName` when build-apk.sh was given one, from version.properties
   otherwise. One line of words: a newline or the separator would break the label every reader parses. */
val codeName = ((findProperty("codeName") as String?)?.takeIf { given -> given.isNotBlank() } ?: release.getProperty("codeName") ?: "Stable").trim()
check(codeName.length in 1..40 && codeName.none { character -> character.isISOControl() } && !codeName.contains(" · ")) {
    "code name must be 1–40 characters on one line, without ' · ', got '$codeName'"
}
val releaseName = "$versionNumber · $releaseStamp · $codeName"
val releaseCode = (releaseStamp - 20_000_000_000).toInt()

/* The commit this build came from — "unknown" outside a repository rather than a failed build. */
/* ⚠️ Through `providers.exec`, never a ProcessBuilder: the configuration cache refuses a process started
   while the build is being configured, and this one is declared so the cache can track it. */
val commit: String = runCatching {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        workingDir = rootProject.projectDir
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifBlank { null }
}.getOrNull() ?: "unknown"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.jmouse.project.kitsu.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.jmouse.project.kitsu.tv"
        // Android 8 is where adaptive icons begin; every Android TV still receiving apps is on it or later.
        minSdk = 26
        targetSdk = 35
        versionCode = releaseCode
        versionName = releaseName

        buildConfigField("String", "GIT_COMMIT", "\"$commit\"")
        /* The three parts apart, so a screen never has to parse its own versionName. */
        buildConfigField("String", "VERSION_NUMBER", "\"$versionNumber\"")
        buildConfigField("long", "RELEASE", "${releaseStamp}L")
        buildConfigField("String", "CODE_NAME", "\"${codeName.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true

        /* ⚠️ Generates `BuildConfig`, which is the ONLY honest source for what this build is — the
           version name, the variant, the application id. The Server screen reports them, and a
           screen that reports a version somebody typed into a string resource reports the version
           that was true when they last remembered to change it. */
        buildConfig = true
    }

    /* ⚠️ The APK is named for what it is, so the file dropped into the server's Releases folder says
       its version on sight: kitsu-tv-1.7.0-20260925001-release.apk. The server does not trust the name — it
       reads the manifest — but a person choosing between two files does. */
    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "kitsu-tv-$versionNumber-$releaseStamp-${variant.buildType.name}.apk"
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

/*
  ⚠️ EVERY BUILD GETS ITS OWN IDENTIFIER, and the commit cannot be it: work here is built many times
  between commits, and all of those builds report the same hash. The build time in base 36 is written
  into an asset by a task that never counts as up to date — so it runs on every build, and the
  configuration cache (which would freeze a value computed while configuring) never sees it.
 */
abstract class StampBuildIdentity : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun stamp() {
        val directory = outputDirectory.get().asFile
        directory.mkdirs()
        File(directory, "build-id.txt").writeText(System.currentTimeMillis().toString(36))
    }
}

val stampBuildIdentity = tasks.register<StampBuildIdentity>("stampBuildIdentity")

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(stampBuildIdentity, StampBuildIdentity::outputDirectory)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
}

/*
  ⚠️ WHAT THIS APP IS BUILT FROM, written into its own assets at build time — the runtime classpath of
  each variant, resolved, one `group:name:version` per line. The Server and build page lists it beside
  the server's libraries; read from the build it cannot drift from the truth the way a hand-kept list
  would.
 */
abstract class AppLibrariesTask : DefaultTask() {
    @get:Input
    abstract val coordinates: ListProperty<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun write() {
        output.get().file("app-libraries.txt").asFile.writeText(coordinates.get().sorted().joinToString("\n"))
    }
}

fun collectModules(root: ResolvedComponentResult): List<String> {
    val seen = linkedSetOf<String>()
    val pending = ArrayDeque(listOf(root))
    val visited = mutableSetOf<ResolvedComponentResult>()
    while (pending.isNotEmpty()) {
        val component = pending.removeFirst()
        if (!visited.add(component)) {
            continue
        }
        val identifier = component.id
        if (identifier is ModuleComponentIdentifier) {
            seen += "${identifier.group}:${identifier.module}:${identifier.version}"
        }
        component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { pending += it.selected }
    }
    return seen.toList()
}

androidComponents {
    onVariants { variant ->
        val task = tasks.register<AppLibrariesTask>("${variant.name}AppLibraries") {
            coordinates.set(variant.runtimeConfiguration.incoming.resolutionResult.rootComponent.map(::collectModules))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, AppLibrariesTask::output)
    }
}
