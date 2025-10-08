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

# Actual task list
## Replay tests (cache variants included)
run python benchmark.py replay -i input/replay -o output/replay --runs 50
## Memory range tests (no cache variants included)
### BSBM
run python benchmark.py regular -i input/bsbm/datasets -o output/memory/bsbm -q input/bsbm/queries --memory-range 100,10000 -f "(^tesserakt$)|(^jena$)"
## Growing tests (cache variants included)
### BSBM
run python benchmark.py update --warmup-queries input/bsbm/warmup-queries --warmup-runs 3 -i input/bsbm/update -q input/bsbm/queries -o output/update/bsbm --runs 50

# The entire output can now be compressed into a single tar
tar -czf "output-$DATE.tar.gz" output
