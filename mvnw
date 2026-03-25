#!/bin/sh
# Maven Wrapper bootstrap script
# Downloads maven-wrapper.jar and invokes it to download and run Maven

set -e

MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(cd "$(dirname "$0")" && pwd)}"
MAVEN_WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"

if [ ! -f "$MAVEN_WRAPPER_JAR" ]; then
  WRAPPER_URL=$(grep 'wrapperUrl' "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
  echo "Downloading Maven Wrapper from $WRAPPER_URL"
  if command -v wget > /dev/null 2>&1; then
    wget -q -O "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL"
  elif command -v curl > /dev/null 2>&1; then
    curl -sS -o "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL"
  else
    echo "Error: wget or curl is required" >&2
    exit 1
  fi
fi

DIST_URL=$(grep 'distributionUrl' "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)
MAVEN_HOME="$HOME/.m2/wrapper/dists/$(basename "$DIST_URL" .zip)"

if [ ! -d "$MAVEN_HOME" ]; then
  echo "Downloading Maven from $DIST_URL"
  mkdir -p "$MAVEN_HOME"
  if command -v wget > /dev/null 2>&1; then
    wget -q -O "$MAVEN_HOME/maven.zip" "$DIST_URL"
  else
    curl -sS -o "$MAVEN_HOME/maven.zip" "$DIST_URL"
  fi
  unzip -q -d "$MAVEN_HOME" "$MAVEN_HOME/maven.zip"
  rm "$MAVEN_HOME/maven.zip"
fi

MAVEN_EXEC=$(find "$MAVEN_HOME" -name 'mvn' -type f | head -1)
exec "$MAVEN_EXEC" "$@"
