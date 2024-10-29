#!/bin/bash

if [[ $(ls ./**/*.nt 2> /dev/null) ]] || [[ $* == -f ]]; then
  printf "Turtle dataset files appear to be present. You can force configuration by passing \`-f\` as an argument.\n"
  exit
fi

ROOT=$(pwd)

cd "$ROOT/watdiv" || exit 255
tar xvf dataset.tar.gz

cd "$ROOT/railway" || exit 255
tar xvf dataset.tar.gz
