#!/bin/bash

ROOT=$(pwd)

prepare() {
    # git submodule update --init --recursive
    cd tesserakt || exit 255
}

build_jvm() {
    if [[ -f benchmark/jvm.jar ]]; then
        echo "JVM benchmark detected, skipping build..."
        return
    fi
    prepare
    ./gradlew benchmarking:jvmJar
    cd "$ROOT" || exit 255
    mkdir -p benchmark
    mv tesserakt/benchmarking/build/libs/benchmarking-jvm-*.jar benchmark/jvm.jar
    echo "Java benchmark ready!"
}

build_js() {
    if [[ -L benchmark/nodejs ]]; then
        echo "NodeJS benchmark detected, skipping build..."
        return
    fi
    prepare
    ./gradlew benchmarking:build
    mv benchmarking/build/compileSync/js/main/productionExecutable/kotlin build/js
    cd "$ROOT" || exit 255
    mkdir -p benchmark
    ln -s ../tesserakt/build/js benchmark/nodejs
    echo "NodeJS benchmark ready!"
}

run_jvm() {
    java -jar benchmark/jvm.jar "${@}"
}

run_js() {
    node benchmark/nodejs/kotlin/tesserakt-benchmarking.js "${@}"
}

case "$1" in
    "jvm")
        build_jvm
        run_jvm "${@:2}"
    ;;
    "js")
        build_js
        run_js "${@:2}"
    ;;
esac

