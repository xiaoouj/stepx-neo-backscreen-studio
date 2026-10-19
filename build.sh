#!/bin/zsh
set -e
ROOT="${0:A:h}"
source "$ROOT/toolchain.sh"
BUILD="$ROOT/build"
APP="$ROOT/app/src/main"

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/obj" "$BUILD/apex"

echo "[1/5] aapt2 compile+link"
"$BT/aapt2" compile --dir "$APP/res" -o "$BUILD/res.zip"
"$BT/aapt2" link -o "$BUILD/app.unaligned.apk" \
  -I "$PLATFORM" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$BUILD/gen" \
  --auto-add-overlay \
  "$BUILD/res.zip"

echo "[2/5] javac"
find "$APP/java" -name "*.java" > "$BUILD/sources.txt"
find "$BUILD/gen" -name "*.java" >> "$BUILD/sources.txt"
"$JDK/bin/javac" -encoding UTF-8 --release 8 \
  -classpath "$PLATFORM" \
  -d "$BUILD/obj" \
  @"$BUILD/sources.txt"

echo "[3/5] d8 dex"
"$JDK/bin/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --release --min-api 29 \
  --lib "$PLATFORM" \
  --output "$BUILD/apex" \
  $(find "$BUILD/obj" -name "*.class")

echo "[4/5] package"
cd "$BUILD/apex"
zip -q "$BUILD/app.unaligned.apk" classes.dex
cd "$ROOT"
"$BT/zipalign" -f 4 "$BUILD/app.unaligned.apk" "$BUILD/app.aligned.apk"

echo "[5/5] sign"
KS="$ROOT/debug.keystore"
if [ ! -f "$KS" ]; then
  "$JDK/bin/keytool" -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$ROOT/BackScreenStudio.apk" "$BUILD/app.aligned.apk"
echo "DONE: $ROOT/BackScreenStudio.apk"
ls -la "$ROOT/BackScreenStudio.apk"
