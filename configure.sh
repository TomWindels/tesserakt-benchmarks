#!/bin/bash

if [[ $(ls ./**/*.nt 2> /dev/null) ]] || [[ $* == -f ]]; then
  printf "Benchmarks appear to be configured. You can force configuration by passing \`-f\` as an argument.\nExiting...\n"
  exit
fi

cd watdiv || exit 255
tar xvf dataset.tar.gz
