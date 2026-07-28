#!/usr/bin/env bash
#
# Copyright 2015-2021 the original authors.
# Licensed under the Apache License, Version 2.0
#

set -e

APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"
APP_BASE_NAME="$(basename "$0")"
APP_NAME="Gradle"

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

warn() { echo "$*" >&2; }
die() { echo; echo "$*" >&2; echo; exit 1; }

# Detect OS
cygwin=false
darwin=false
msys=false
nonstop=false
case "$(uname)" in
  CYGWIN*)   cygwin=true ;;
  Darwin*)   darwin=true ;;
  MSYS*|MINGW*) msys=true ;;
  NONSTOP*)  nonstop=true ;;
esac

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Find java
if [ -n "$JAVA_HOME" ]; then
    if [ -x "$JAVA_HOME/jre/sh/java" ]; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ]; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    command -v java >/dev/null 2>&1 || die "ERROR: JAVA_HOME not set and 'java' not found in PATH."
fi

# Cygwin/MSYS path conversion
if "$cygwin" || "$msys"; then
    APP_HOME="$(cygpath --path --mixed "$APP_HOME")"
    CLASSPATH="$(cygpath --path --mixed "$CLASSPATH")"
    JAVACMD="$(cygpath --unix "$JAVACMD")"
fi

# Increase file descriptors if possible
if ! "$cygwin" && ! "$darwin" && ! "$nonstop"; then
    MAX_FD=$(ulimit -H -n 2>/dev/null || echo "")
    if [ -n "$MAX_FD" ] && [ "$MAX_FD" != "unlimited" ]; then
        ulimit -n "$MAX_FD" 2>/dev/null || true
    fi
fi

exec "$JAVACMD" \
  $DEFAULT_JVM_OPTS \
  $JAVA_OPTS \
  $GRADLE_OPTS \
  "-Dorg.gradle.appname=$APP_BASE_NAME" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"