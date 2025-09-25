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
## Regular tests
### WatDiv (no cache variants included)
run python benchmark.py regular -i input/watdiv/datasets -o output/regular/watdiv -q input/watdiv/queries --runs 100 --filter "^(?:(?!-cache).)*$"
### BSBM (no cache variants included)
run python benchmark.py bsbm -i input/bsbm/datasets -u bin/bsbm-tools/usecases/explore/sparql.txt -o output/bsbm --filter "^(?:(?!-cache).)*$"
## Replay tests (cache variants included)
run python benchmark.py replay -i input/replay -o output/replay --runs 100
## Memory range tests (no cache variants included)
### WatDiv
run python benchmark.py regular -i input/watdiv/datasets -o output/memory/watdiv -q input/watdiv/queries --memory-range 100,10000 -f "(^tesserakt$)|(^jena$)"
### BSBM
run python benchmark.py regular -i input/bsbm/datasets -o output/memory/bsbm -q input/bsbm/queries --memory-range 100,10000 -f "(^tesserakt$)|(^jena$)"

# The entire output can now be compressed into a single tar
tar -czf "output-$DATE.tar.gz" output
