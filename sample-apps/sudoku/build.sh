#!/usr/bin/env bash
# Builds the APK, with the nowrun SDK downloaded from its published release.
#   ./build.sh                  debug
#   ./build.sh release          release, signed with ../sample-release.jks (or the debug key without it)
#   ./build.sh install          debug + install
#   ./build.sh release install  release + install
#
# Needs macOS or Linux, a JDK 17 or newer (JAVA_HOME, or java on the PATH), the Android SDK
# (ANDROID_HOME, or sdk.dir in local.properties), curl, and adb on the PATH to install.
set -e
cd "$(dirname "$0")"

BUILD=debug
for arg in "$@"; do
    [ "$arg" = "release" ] && BUILD=release
done

# The SDK version is pinned once, by the file name app/build.gradle compiles against.
AAR=app/$(grep -o "libs/nowrun-sdk-[^']*\.aar" app/build.gradle)
VERSION=${AAR#app/libs/nowrun-sdk-}
VERSION=${VERSION%.aar}
if [ ! -f "$AAR" ]; then
    curl -fL --create-dirs -o "$AAR" "https://downloads.nowrun.io/releases/$VERSION/nowrun-sdk-$VERSION.aar"
fi

if [ "$BUILD" = "release" ]; then
    # app/build.gradle falls back to the debug key without it; say so up front too.
    [ -f ../sample-release.jks ] || echo "warning: ../sample-release.jks not found; signing the release APK with the debug key" >&2
    ./gradlew assembleRelease
    APK=app/build/outputs/apk/release/app-release.apk
else
    ./gradlew assembleDebug
    APK=app/build/outputs/apk/debug/app-debug.apk
fi

echo "APK: $PWD/$APK"

for arg in "$@"; do
    if [ "$arg" = "install" ]; then
        adb install -r "$APK"
    fi
done
