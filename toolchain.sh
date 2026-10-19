# Shared SDK/JDK configuration; sourced by the zsh build scripts.
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
if [[ -n "${JAVA_HOME:-}" ]]; then
  JDK="$JAVA_HOME"
elif [[ -x /usr/libexec/java_home ]]; then
  JDK="$(/usr/libexec/java_home -v 17)"
else
  print -u2 'Set JAVA_HOME to a JDK 17 installation.'
  exit 1
fi
export PATH="$JDK/bin:$PATH"
PLATFORM="$SDK/platforms/android-37/android.jar"
BT="${ANDROID_BUILD_TOOLS:-$SDK/build-tools/37.0.0}"
for required in "$JDK/bin/javac" "$PLATFORM" "$BT/aapt2" "$BT/apksigner" "$BT/zipalign" "$BT/lib/d8.jar"; do
  if [[ ! -f "$required" ]]; then
    print -u2 "Missing tool or SDK file: $required"
    print -u2 'Set JAVA_HOME, ANDROID_HOME and (if needed) ANDROID_BUILD_TOOLS; see README.md.'
    exit 1
  fi
done
