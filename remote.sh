#!/usr/bin/env bash
#
# A remote control for Kitsu TV, over adb.
#
# ⚠️ WHY THIS EXISTS. A television is driven by a D-pad, and the emulator window is an awkward place
# to find one: an Android TV AVD ships with `hw.keyboard=no` and `hw.screen=no-touch`, so out of the
# box neither the host's arrow keys nor a mouse click reaches the guest at all, and the window looks
# broken rather than unfocused. Both are fixed in the AVD now — but a remote you can drive from the
# terminal still beats one you have to keep clicking into a window for, and it works over the network
# on a real television too.
#
#   ./remote.sh up down left right ok back home
#   ./remote.sh right x4 ok          # repeat the previous key four times
#   ./remote.sh shot                 # save a screenshot beside this script
#   ./remote.sh open                 # (re)launch the app
#   ./remote.sh keys                 # what the words mean
#
# Any number of words in one go, in order, with a short pause between them — which is what makes a
# sequence like `up right right ok` land the way a person's thumb would.

set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/sdk}}"
ADB="$SDK/platform-tools/adb.exe"
[ -x "$ADB" ] || ADB="$SDK/platform-tools/adb"

PACKAGE="org.jmouse.project.kitsu.tv"
ACTIVITY="$PACKAGE/.MainActivity"

# ⚠️ Named for what a thumb does, not for what Android calls it. `KEYCODE_DPAD_CENTER` is `ok`.
key_for() {
    case "$1" in
        up)              echo 19 ;;
        down)            echo 20 ;;
        left)            echo 21 ;;
        right)           echo 22 ;;
        ok|enter|select) echo 23 ;;
        back)            echo 4 ;;
        home)            echo 3 ;;
        menu)            echo 82 ;;
        play|pause)      echo 85 ;;
        next)            echo 87 ;;
        previous|prev)   echo 88 ;;
        forward)         echo 90 ;;
        rewind)          echo 89 ;;
        *)               echo "" ;;
    esac
}

usage() {
    cat <<'TEXT'
Kitsu TV remote

  ./remote.sh <word> [<word> …]

Movement   up · down · left · right · ok · back · home · menu
Playback   play · pause · next · previous · forward · rewind
Repeat     x2 … x20        repeats the word before it
Other      shot            screenshot → .shots/tv-<time>.png
           open            launch the app
           stop            force-stop the app
           keys            this list

Examples
  ./remote.sh up right right ok
  ./remote.sh down x5 ok
  ./remote.sh back back shot
TEXT
}

require_device() {
    if ! "$ADB" devices | grep -q "device$"; then
        echo "No device. Start the emulator first:" >&2
        echo "  \"$SDK/emulator/emulator.exe\" -avd kitsu_tv -no-audio -no-boot-anim -gpu swiftshader_indirect" >&2
        exit 1
    fi
}

press() {
    "$ADB" shell input keyevent "$1" >/dev/null 2>&1
    # ⚠️ Long enough for Compose to settle focus before the next key. Without it a burst of keys
    # arrives faster than the focus moves and the sequence lands somewhere nobody asked for.
    sleep 0.45
}

shot() {
    local directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/.shots"
    mkdir -p "$directory"
    local path="$directory/tv-$(date +%H%M%S).png"
    "$ADB" exec-out screencap -p > "$path"
    echo "$path"
}

if [ $# -eq 0 ] || [ "${1:-}" = "keys" ] || [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
    usage
    exit 0
fi

require_device

previous=""
for word in "$@"; do
    case "$word" in
        shot)
            shot
            continue
            ;;
        open)
            "$ADB" shell am start -n "$ACTIVITY" >/dev/null 2>&1
            echo "opened $PACKAGE"
            sleep 2
            continue
            ;;
        stop)
            "$ADB" shell am force-stop "$PACKAGE" >/dev/null 2>&1
            echo "stopped $PACKAGE"
            continue
            ;;
        x[0-9]*)
            count="${word#x}"
            if [ -z "$previous" ]; then
                echo "'$word' has nothing before it to repeat" >&2
                exit 1
            fi
            code="$(key_for "$previous")"
            for _ in $(seq 2 "$count"); do
                press "$code"
            done
            echo "$previous ×$count"
            continue
            ;;
    esac

    code="$(key_for "$word")"
    if [ -z "$code" ]; then
        echo "I do not know '$word' — try ./remote.sh keys" >&2
        exit 1
    fi
    press "$code"
    previous="$word"
    echo "$word"
done
