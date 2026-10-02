#!/usr/bin/env bash
#
# Builds an APK you can copy to a memory stick and install on the television.
#
#   ./build-apk.sh              → release, signed with the debug key, into ./dist/
#   ./build-apk.sh debug        → the debug build instead
#   ./build-apk.sh --install    → build, then push it to whatever adb is attached
#   ./build-apk.sh --code-name "x-mas build"
#                               → this build's name for people, instead of codeName in
#                                 version.properties ("Stable"); not written back
#   ./build-apk.sh --keep-release
#                               → rebuild the SAME release rather than stamping the next one.
#                                 ⚠️ A television that already has this release is NOT offered it
#                                 again — versionCode comes from the release and would be equal.
#                                 For a release that has not left this machine yet.
#
# A release build stamps the next release number into version.properties — see that file.
#
# ⚠️ THE RELEASE BUILD IS SIGNED WITH THE DEBUG KEY, ON PURPOSE. This is a household application
# installed from a memory stick, not from a shop, and a real signing key is a thing that has to be
# kept, backed up and never lost — because an APK signed with a different key cannot upgrade one
# already installed, it can only be uninstalled and replaced. See `app/build.gradle.kts`.
#
# ⚠️ THE FILE IS NAMED WITH THE VERSION AND THE RELEASE, because a memory stick ends up with four of
# these on it and `app-release.apk` tells you nothing about which is which.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT="$HERE/dist"

VARIANT="release"
INSTALL="no"
CODE_NAME=""
KEEP_RELEASE="no"
while [ $# -gt 0 ]; do
    case "$1" in
        debug)      VARIANT="debug" ;;
        release)    VARIANT="release" ;;
        --install)  INSTALL="yes" ;;
        --keep-release) KEEP_RELEASE="yes" ;;
        --code-name)
            if [ $# -lt 2 ] || [ -z "$2" ]; then
                echo "--code-name needs a name: --code-name \"x-mas build\"" >&2
                exit 1
            fi
            CODE_NAME="$2"
            shift
            ;;
        --code-name=*) CODE_NAME="${1#--code-name=}" ;;
        -h|--help)
            sed -n '3,26p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "I do not know '$1' — try --help" >&2
            exit 1
            ;;
    esac
    shift
done

# ⚠️ WINDOWS FORM, NOT UNIX. In Git Bash a `/c/…` JAVA_HOME fails inside the Android plugin with
# "The filename, directory name, or volume label syntax is incorrect", at `compileDebugJavaWithJavac`
# and with no hint that the path is the cause. `sdk.dir` in local.properties has the same rule.
JAVA_HOME="${JAVA_HOME_WINDOWS:-C:\\Users\\Ivan_Hontarenko\\.jdks\\corretto-21.0.5}"
export JAVA_HOME

# ⚠️ A SMALLER HEAP THAN `gradle.properties` ASKS FOR. `org.gradle.jvmargs=-Xmx3g` kills the daemon
# with a native OutOfMemoryError on this laptop whenever the rest of the stack is up — 15.6 GB of
# physical memory, most of it spoken for. 1280m builds this project comfortably; raise it on a
# machine with room.
HEAP="${GRADLE_HEAP:--Xmx1280m}"

# ⚠️ The version is read from `version.properties`, the one place it is written — the build file
# derives versionName from it and no longer holds a literal to read.
property() {
    sed -n "s/^$1=\(.*\)$/\1/p" "$HERE/version.properties" | tr -d '\r' | head -1
}
VERSION="$(property major).$(property minor).$(property patch)"

# ⚠️ A RELEASE BUILD IS A NEW RELEASE. Today's date and 001, or the next number when the stamp in
# version.properties is already today's — so a forgotten detail goes out as the same version with a
# bigger release, and the television still offers it. A debug build keeps whatever stamp is there.
#
# ⚠️ `--keep-release` REBUILDS THE SAME RELEASE, and it costs something real: versionCode is derived
# from the release, so a television that already has this one WILL NOT BE OFFERED THE UPDATE — the
# update check compares codes and they are equal. It is for rebuilding a release that has not left
# this machine yet: a different code name, a file overwritten, a build that failed to copy. Anything
# somebody already installed needs a new release, which is what the ordinary build does.
if [ "$VARIANT" = "release" ] && [ "$KEEP_RELEASE" = "no" ]; then
    TODAY="$(date +%Y%m%d)"
    PREVIOUS="$(property release)"
    if [ "${PREVIOUS:0:8}" = "$TODAY" ]; then
        STAMP="$TODAY$(printf '%03d' $((10#${PREVIOUS:8:3} + 1)))"
    else
        STAMP="${TODAY}001"
    fi
    sed -i "s/^release=.*/release=$STAMP/" "$HERE/version.properties"
fi
RELEASE="$(property release)"
SHOWN_NAME="${CODE_NAME:-$(property codeName)}"
SHOWN_NAME="${SHOWN_NAME:-Stable}"

TASK=":app:assemble$(printf '%s' "${VARIANT:0:1}" | tr '[:lower:]' '[:upper:]')${VARIANT:1}"

echo "Building $VARIANT $VERSION · $RELEASE · $SHOWN_NAME …"
(
    cd "$HERE"
    # ⚠️ THE CODE NAME TRAVELS IN THE ENVIRONMENT, not as `-PcodeName=…`. The wrapper script re-splits
    # its arguments, so "x-mas build" arrived as two words and the second was read as a task name.
    # Gradle reads ORG_GRADLE_PROJECT_<name> as the project property <name>, spaces and all.
    export ORG_GRADLE_PROJECT_codeName="$SHOWN_NAME"
    ./gradlew "$TASK" \
        --console=plain \
        --no-daemon \
        -Dorg.gradle.jvmargs="$HEAP -Dfile.encoding=UTF-8" \
        -Pkotlin.compiler.execution.strategy=in-process
)


# ⚠️ Gradle names the APK for what it is (`applicationVariants` in app/build.gradle.kts), so the file
# is kitsu-tv-<version>-<release>-<variant>.apk, not the plugin's default app-<variant>.apk.
#
# ⚠️ IT SAID `mhub-tv-` UNTIL 2026-09-28, which the rename to Kitsu missed. The build itself was
# fine — Gradle wrote the file under its new name — and this line then failed to find it and reported
# "the build reported success but … is not there", two and a half minutes after the work was done.
BUILT="$HERE/app/build/outputs/apk/$VARIANT/kitsu-tv-$VERSION-$RELEASE-$VARIANT.apk"
if [ ! -f "$BUILT" ]; then
    echo "The build reported success but $BUILT is not there." >&2
    exit 1
fi

mkdir -p "$OUTPUT"
NAMED="$OUTPUT/kitsu-tv-$VERSION-$RELEASE-$VARIANT.apk"
cp "$BUILT" "$NAMED"

SIZE="$(du -h "$NAMED" | cut -f1)"

cat <<TEXT

  $NAMED
  $SIZE · version $VERSION · release $RELEASE · $SHOWN_NAME · $VARIANT

  On the television, once:
    Settings → System → About → tap "Build" seven times   (developer options)
    Settings → System → Developer options → Install unknown apps → allow your file manager

  Then: copy the file to a memory stick, open it with the television's file manager, install.

  ⚠️ Already have it installed from a DIFFERENT build of this application? Uninstall first.
     The package is org.jmouse.project.kitsu.tv and it changed from org.jmouse.project.kitsu.web.tv —
     an APK with a different package installs BESIDE the old one rather than over it.
TEXT

if [ "$INSTALL" = "yes" ]; then
    SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/sdk}}"
    ADB="$SDK/platform-tools/adb.exe"
    [ -x "$ADB" ] || ADB="$SDK/platform-tools/adb"

    if ! "$ADB" devices | grep -q "device$"; then
        echo "  --install asked for, but adb sees no device." >&2
        exit 1
    fi
    echo "  installing …"
    "$ADB" install -r "$NAMED"
fi
