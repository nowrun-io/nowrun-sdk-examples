#!/usr/bin/env bash
# Installs the nowrun-expo module from its published release and builds the release APK.
#
# Needs macOS or Linux, Node.js and npm, a JDK 17 or newer (JAVA_HOME, or java on the PATH), the
# Android SDK (ANDROID_HOME) and curl.
set -e
cd "$(dirname "$0")"

APK=android/app/build/outputs/apk/release/app-release.apk

# The module version is pinned once, by the tarball package.json installs from vendor/.
TGZ=$(node -p "require('./package.json').dependencies['nowrun-expo'].replace(/^file:/, '')")
VERSION=${TGZ#vendor/nowrun-expo-}
VERSION=${VERSION%.tgz}
if [ ! -f "$TGZ" ]; then
  curl -fL --create-dirs -o "$TGZ" "https://downloads.nowrun.io/releases/$VERSION/nowrun-expo-$VERSION.tgz"
fi

# The old copy goes first: while the version stays the same, npm decides the package is
# already installed and keeps whatever is there, however old.
rm -rf node_modules/nowrun-expo
npm install --no-audit --no-fund --silent "./$TGZ"

# Checked rather than trusted: a stale copy still builds, it just builds the wrong thing.
tar -xzOf "$TGZ" package/build/index.js | diff -q - node_modules/nowrun-expo/build/index.js >/dev/null || {
  echo "error: node_modules/nowrun-expo does not match $TGZ" >&2
  exit 1
}

# android/ is not committed: prebuild generates it from app.json, and regenerates it exactly.
[ -d android ] || CI=1 npx expo prebuild --platform android --no-install

# Metro's bundle task excludes node_modules from its inputs, so a changed module leaves the
# task UP-TO-DATE and the APK keeps whatever JS it bundled last time — against freshly
# compiled Kotlin. Dropping its output is what forces the two halves to be built together.
rm -rf android/app/build/generated/assets/react/release \
       android/app/build/intermediates/assets/release

(cd android && ./gradlew assembleRelease --no-daemon -q)

# The bundle is the half that goes stale silently; prove this APK carries a fresh one.
BUNDLE=android/app/build/generated/assets/react/release/index.android.bundle
[ "$BUNDLE" -nt "$TGZ" ] || {
  echo "error: bundled JS is older than $TGZ" >&2
  exit 1
}

echo "APK: $PWD/$APK"
