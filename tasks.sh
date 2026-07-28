#!/bin/bash

DATE=$(date +%s)

LOGFILE=$(realpath -m "output/log-$DATE.txt")

if [ -f "$LOGFILE" ]; then
    echo "Log file \`$LOGFILE\` already exists."
    exit 1
fi

# src: https://stackoverflow.com/a/6364244
if compgen -G "output/*" > /dev/null; then
    echo "Non-empty output folder already exists! Exiting..."
    exit 1
fi

mkdir -p "$(dirname "$LOGFILE")"

touch "$LOGFILE"

run() {
    printf "\n\n >> \`%s\`\n" "$*" | tee -a "$LOGFILE"
    # Executing the command, setting the stderr fd to stdout
    "$@" 2>&1 | tee -a "$LOGFILE"
}

# doing preparation related work
## making sure the replay dataset is extracted
if ! compgen -G "*.ttl" > /dev/null; then
    mkdir -p input/replay
    cd input/replay
    tar xf ../../replay/replay-dataset.tar.xz | exit 1
    cd ../..
fi

## making sure the bench runner is available
## no daemon to prevent it from affecting the benchmark itself
cd bench
./gradlew --no-daemon clean shadowJar | exit 1
cd ..

## tesserakt runner jar
cd tesserakt
./gradlew --no-daemon clean shadowJar | exit 1
cd ..

## jena runner jar
cd jena
./gradlew --no-daemon clean jar | exit 1
cd ..

## blazegraph runner jar
cd blazegraph
./gradlew --no-daemon clean jar | exit 1
cd ..

## kolibrie runner so
cd kolibrie
cargo build --release | exit 1
cd ..

## oxigraph runner so
cd oxigraph
cargo build --release | exit 1
cd ..

bench_jvm() {
    run java -jar bench/build/libs/tesserakt-bench-*-all.jar "$@"
}

bench_js() {
    export NODE_ENV=performance
    RAM_KB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
    RAM_MB=$(expr $RAM_KB / 1024)
    MAX_MEM=$(expr $RAM_MB \* 3 / 4)
    run node --max-old-space-size=$MAX_MEM bench/build/js/packages/tesserakt-bench/kotlin/tesserakt-bench.js "$@"
}

# Actual task list
bench_jvm replay -e tesserakt/build/libs/*.jar input/replay/*
bench_jvm replay -e jena/build/libs/*.jar input/replay/*
bench_jvm replay -e blazegraph/build/libs/*.jar input/replay/*
bench_jvm replay -e oxigraph/target/release/*.so input/replay/*
for file in input/replay/*; do
	bench_js replay -e incremunica/incremunica.mjs "$file"
done
for file in input/replay/*; do
	bench_js replay -e comunica/comunica.mjs "$file"
done

# The entire output can now be compressed into a single tar
tar -czf "output-$DATE.tar.gz" output
