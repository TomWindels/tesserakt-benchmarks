#!/bin/bash

DATE=$(date +%s)

LOGFILE="output/log-$DATE.txt"

if [ -f "$LOGFILE" ]; then
    echo "Log file \`$LOGFILE\` already exists."
    exit 1
fi

mkdir -p "$(dirname "$LOGFILE")"

touch "$LOGFILE"

run() {
    printf "\n\n >> \`%s\`\n" "$*" | tee -a "$LOGFILE"
    # Executing the command, setting the stderr fd to stdout
    "$@" 2>&1 | tee -a "$LOGFILE"
}

bench_jvm() {
    run java -jar tesserakt-bench/build/libs/tesserakt-bench-*-all.jar "$@"
}

bench_js() {
    export NODE_ENV=performance
    RAM_KB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
    RAM_MB=$(expr $RAM_KB / 1024)
    MAX_MEM=$(expr $RAM_MB \* 3 / 4)
    run node --max-old-space-size=$MAX_MEM tesserakt-bench/build/js/packages/tesserakt-bench/kotlin/tesserakt-bench.js "$@"
}

# Actual task list
bench_jvm replay -e tesserakt-jar/build/libs/*.jar input/replay/*
bench_jvm replay -e jena/build/libs/*.jar input/replay/*
bench_jvm replay -e blazegraph/build/libs/*.jar input/replay/*
bench_jvm replay -e oxigraph/target/release/*.so input/replay/*
bench_js replay -e incremunica-replay-evaluation/incremunica.mjs input/replay/*
bench_js replay -e comunica/comunica.mjs input/replay/*

# The entire output can now be compressed into a single tar
tar -czf "output-$DATE.tar.gz" output
