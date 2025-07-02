#! /bin/bash

# can be kept empty / put in comment if no initial endpoint data is required
export ENDPOINT_DATA=../watdiv/dataset.nt
# whether supported engines should have their caching strategy enabled - 0 or 1
export ENABLE_QUERY_CACHE=0

## JAVA CONFIGURATION
export JAVA_BIN=/usr/lib/jvm/default-runtime/bin/java
export JAVA_FLAGS="-Xmx1g"
#export JAVA_FLAGS="-server -Xmx16g"
