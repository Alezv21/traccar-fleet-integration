#!/bin/bash
# Build script: compiles the project using gradle + Java 21 from sdkman

set -e

# Detect Java 21 location
sdk env

if [ ! -d "$JAVA_HOME" ]; then
    echo "ERROR: Java 21 not found at $JAVA_HOME"
    echo "Set JAVA_HOME to your Java 21 installation path"
    exit 1
fi

echo "Building with Java at: $JAVA_HOME"

# Run gradle build
gradle "$@"
