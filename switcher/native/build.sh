#!/bin/sh
# Builds the aarch64 helpers in this directory (static, except the Bluetooth
# audio bridge) into ../assets/ and bin/.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
NDK="${ANDROID_NDK_HOME:-$(ls -d "$ANDROID_HOME"/ndk/* | sort -V | tail -1)}"
CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"

"$CC" --target=aarch64-linux-android29 -static -O2 -Wall -Wextra \
    -o "$DIR/../assets/zygote_exec" "$DIR/zygote_launcher.c"
"$STRIP" "$DIR/../assets/zygote_exec"
ls -la "$DIR/../assets/zygote_exec"

"$CC" --target=aarch64-linux-android29 -static -O2 -Wall -Wextra \
    -o "$DIR/bin/socket_exec" "$DIR/socket_exec.c"
"$STRIP" "$DIR/bin/socket_exec"
ls -la "$DIR/bin/socket_exec"

# Bluetooth audio bridge (oneos_btaudio.cpp): dynamic, it needs the phone's
# libbinder_ndk.so; API 31 for AParcel_appendFrom.
"${CC}++" --target=aarch64-linux-android31 -O2 -Wall -Wextra -std=c++17 -static-libstdc++ \
    -o "$DIR/../assets/oneos_btaudio" "$DIR/oneos_btaudio.cpp" -lbinder_ndk -llog -ldl
"$STRIP" "$DIR/../assets/oneos_btaudio"
ls -la "$DIR/../assets/oneos_btaudio"
