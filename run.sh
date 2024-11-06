#!/bin/bash

ROOT=$(pwd)
REPO_LOCATION="$ROOT/.source"
CLEAN=false
ARGS=""
DEFAULT_MODE=true
JVM_MODE=false
JS_MODE=false
VERSION=""

current_version_short() {
    cd "$REPO_LOCATION" || exit 255
    git rev-parse --short HEAD
    cd "$ROOT" || exit 255
}

current_version_long() {
    git rev-parse HEAD
}

build_jvm() {
    if [[ -f benchmark/jvm.jar ]]; then
        echo "JVM benchmark detected (rev $(current_version_short)), skipping build..."
        return
    fi
    cd "$REPO_LOCATION" || exit 255
    ./gradlew benchmarking:jvmJar || exit 255
    cd "$ROOT" || exit 255
    mkdir -p benchmark
    mv "$REPO_LOCATION"/benchmarking/build/libs/benchmarking-jvm-*.jar benchmark/jvm.jar || exit 255
    echo "Java benchmark (rev $(current_version_short)) ready!"
}

build_js() {
    if [[ -L benchmark/nodejs ]]; then
        echo "NodeJS benchmark detected (rev $(current_version_short)), skipping build..."
        return
    fi
    cd "$REPO_LOCATION" || exit 255
    ./gradlew benchmarking:jsProductionExecutableCompileSync kotlinNpmInstall || exit 255
    mv benchmarking/build/compileSync/js/main/productionExecutable/kotlin build/js || exit 255
    cd "$ROOT" || exit 255
    mkdir -p benchmark
    ln -s "$REPO_LOCATION/build/js" benchmark/nodejs
    echo "NodeJS benchmark (rev $(current_version_short)) ready!"
}

run_jvm() {
    java -jar benchmark/jvm.jar "${@}"
}

run_js() {
    node benchmark/nodejs/kotlin/tesserakt-benchmarking.js "${@}"
}

clean() {
    # Making sure
    cd "$ROOT" || exit 255
    rm -f benchmark/* # -f as we don't care if it didn't exist
    cd "$REPO_LOCATION" || exit 255
    ./gradlew clean
    cd "$ROOT" || exit 255
}

configure_version() {
    cd "$REPO_LOCATION" || exit 255
    # Checking to see if the version already matches
    CURRENT_SHORT="$(current_version_short)"
    CURRENT_LONG="$(current_version_long)"
    if [[ "$1" == "$CURRENT_SHORT" || "$1" == "$CURRENT_LONG" ]]; then
        cd "$ROOT" || exit 255
        return
    fi
    echo "Changing tesserakt revision to $1"
    git fetch
    git checkout "$1" || exit 255
    cd "$ROOT" || exit 255
    CLEAN=true
}

exit_with_help() {
    echo "Usage: "
    echo "./run.sh [-v=.../--version=...] [--clean] [--jvm] [--js]"
    echo ""
    echo "                                                   ^^^^ - Run the JS (Node) version of the tool"
    echo "                                           ^^^^^ - Run the JVM version of the tool (default)"
    echo "                                 ^^^^^^^ - Rebuild the benchmark tool using the source code for the specified platform"
    echo "          ^^^^^^^^^^^^^^^^^^^^ - Enforce the version using a specific commit hash of the source code"
    echo "Additional arguments to the tool itself can be appended using \`-- arg1 arg2 ...\`"
    echo ""
    echo "Example: \`./run.sh --jvm -- path/to/dataset.ttl path/to/query.txt\`"
    exit
}

if [ $# -eq 0 ]; then
    exit_with_help
fi

# Parsing the args so the configuration above is updated
for arg in "$@"; do
    case $arg in
        "-h"|"--help")
            exit_with_help
        ;;
        -v=*|--version=*)
            VERSION="${arg#*=}"
            shift
        ;;
        "--clean")
            CLEAN=true
            shift
        ;;
        "--jvm")
            DEFAULT_MODE=false
            JVM_MODE=true
            shift
        ;;
        "--js")
            DEFAULT_MODE=false
            JS_MODE=true
            shift
        ;;
        "--")
            shift
            ARGS="${@}"
            break
        ;;
        *)
            echo "Unknown argument: $arg"
            echo "Use \`-h\`/\`--help\` for more information"
            exit 255
        ;;
    esac
done

# First making sure we have a repo to work with
if [ ! -d "$REPO_LOCATION" ]; then
    git clone https://github.com/TomWindels/tesserakt.git "$REPO_LOCATION"
fi

# If we have a specific version set, we can enforce that one
if [ -n "$VERSION" ]; then
    configure_version "$VERSION"
fi

# If clean is requested/required, we can do that now
if $CLEAN; then
    clean
fi

# We can now deduce what platforms to build & run
JVM=$JVM_MODE || $DEFAULT_MODE
JS=$JS_MODE

echo "Preparing requested benchmarks..."

if $JVM; then
    build_jvm
fi

if $JS; then
    build_js
fi

echo "Executing requested benchmarks..."

if $JVM; then
    run_jvm $ARGS
fi

if $JS; then
    run_js $ARGS
fi
