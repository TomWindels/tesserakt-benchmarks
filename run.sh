#!/bin/sh
./configure.sh

WATDIV="$(pwd)/watdiv"
cd ./tesserakt || exit 255
./gradlew benchmarking:jvmRun --args="$WATDIV/dataset.nt $WATDIV/queries/S1.txt" -DmainClass=Main_jvmKt --quiet --stacktrace
