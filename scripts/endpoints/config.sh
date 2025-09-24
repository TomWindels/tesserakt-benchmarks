#! /bin/bash

## JAVA CONFIGURATION
export JAVA_BIN="${JAVA_HOME:-/usr/lib/jvm/default-runtime}"/bin/java
export JAVA_FLAGS="${JAVA_FLAGS:--Xmx1g}"
#export JAVA_FLAGS="-server -Xmx16g"
