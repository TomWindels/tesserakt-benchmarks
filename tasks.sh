#!/bin/bash

LOGFILE="output/execution-log-$(date +%s).txt"

if [ -f "$LOGFILE" ]; then
    echo "Log file \`$LOGFILE\` already exists."
    exit 1
fi

touch "$LOGFILE"

run() {
    printf "\n\n >> \`%s\`\n" "$*" | tee -a "$LOGFILE"
    # Executing the command, setting the stderr fd to stdout
    "$@" 2>&1 | tee -a "$LOGFILE"
}

# Actual task list
## Regular tests
### WatDiv
run python benchmark.py -i input/watdiv/datasets -o output/regular/watdiv -q input/watdiv/queries --runs 100
### BSBM (external tool)

## Replay tests

## Memory range tests
### WatDiv
run python benchmark.py -i input/watdiv/datasets -o output/memory/watdiv -q input/watdiv/queries --memory-range 100,10000 -f "(tesserakt)|(jena)"
### BSBM
run python benchmark.py -i input/bsbm/datasets -o output/memory/bsbm -q input/bsbm/queries --memory-range 100,10000 -f "(tesserakt)|(jena)"
