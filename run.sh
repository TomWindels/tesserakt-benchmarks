#!/bin/sh
./configure.sh

WATDIV="$(pwd)/watdiv"
RAILWAY="$(pwd)/railway"
cd ./tesserakt || exit 255
# ./gradlew benchmarking:jvmRun --args="$WATDIV/dataset.nt $WATDIV/queries/S1.txt" -DmainClass=Main_jvmKt --quiet --stacktrace
./gradlew benchmarking:jvmRun --args="$RAILWAY/railway-batch-1-inferred.ttl $RAILWAY/query.txt" -DmainClass=Main_jvmKt --quiet --stacktrace
