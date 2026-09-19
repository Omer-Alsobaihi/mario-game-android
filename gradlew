#!/bin/sh

# Gradle start up script for POSIX
# Simplified version for Android project

# Find java
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Check if java exists
if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "ERROR: Java not found. Please set JAVA_HOME."
    exit 1
fi

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CLASSPATH="$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar"

# Run gradle
exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
