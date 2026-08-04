#!/usr/bin/bash

fail() {
    echo "$@" 1>&2 # stderr
    exit 1
}

if [ "$#" -ne 1 ]; then
    fail "Usage: ./create_runner.sh <commit>"
fi

if [[ ! -d ./.tesserakt ]]; then
    git clone https://github.com/TomWindels/tesserakt.git ./.tesserakt
fi

if [[ -f ./tesserakt-{$1}.jar ]]; then
    fail "JAR with hash already exists!"
fi

cd .tesserakt
git fetch || fail "Syncing with the repository failed!"
git checkout --detach $1 || fail "No commit with hash `$1` was found!"
cd ..
./gradlew --no-daemon --refresh-dependencies clean shadowJar || fail "Failed to build the runner!"
mv ./build/libs/tesserakt-*-all.jar "./tesserakt-$1.jar"
