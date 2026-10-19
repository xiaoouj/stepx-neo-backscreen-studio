#!/bin/zsh
set -eu
ROOT="${0:A:h}"
source "$ROOT/toolchain.sh"
mkdir -p "$ROOT/build/tests"
"$JDK/bin/javac" --release 8 -encoding UTF-8 -classpath "$ROOT/build/obj:$PLATFORM" -d "$ROOT/build/tests" "$ROOT/tests/ChargeArtTest.java" "$ROOT/tests/FrameArchiveTest.java" "$ROOT/tests/HandoffPolicyTest.java"
"$JDK/bin/java" -classpath "$ROOT/build/tests:$ROOT/build/obj:$PLATFORM" com.workbuddy.backscreen.ChargeArtTest "$@"

"$JDK/bin/java" -classpath "$ROOT/build/tests:$ROOT/build/obj:$PLATFORM" com.workbuddy.backscreen.FrameArchiveTest

"$JDK/bin/java" -classpath "$ROOT/build/tests:$ROOT/build/obj:$PLATFORM" com.workbuddy.backscreen.HandoffPolicyTest
