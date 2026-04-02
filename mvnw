#!/bin/sh
# Maven Wrapper Script
# Generated for url-shortener project
# Allows running Maven without installing it globally: ./mvnw clean package

set -e

MAVEN_WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES=".mvn/wrapper/maven-wrapper.properties"

# Read distribution URL from properties
DISTRIBUTION_URL=$(grep distributionUrl "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2)

MAVEN_HOME="${HOME}/.m2/wrapper/dists/apache-maven-3.9.6"
MAVEN_CMD="${MAVEN_HOME}/bin/mvn"

# Download Maven if not already present
if [ ! -f "$MAVEN_CMD" ]; then
  echo "[mvnw] Maven not found at $MAVEN_HOME. Downloading..."
  mkdir -p "${MAVEN_HOME}"
  TMP_ZIP="${MAVEN_HOME}/maven.zip"

  if command -v wget > /dev/null 2>&1; then
    wget -q -O "$TMP_ZIP" "$DISTRIBUTION_URL"
  elif command -v curl > /dev/null 2>&1; then
    curl -fsSL -o "$TMP_ZIP" "$DISTRIBUTION_URL"
  else
    echo "[mvnw] Error: wget or curl required"
    exit 1
  fi

  unzip -q "$TMP_ZIP" -d "${HOME}/.m2/wrapper/dists/"
  rm -f "$TMP_ZIP"
  # Rename the extracted folder
  EXTRACTED=$(find "${HOME}/.m2/wrapper/dists/" -maxdepth 1 -name "apache-maven-*" -type d | head -1)
  mv "$EXTRACTED" "$MAVEN_HOME" 2>/dev/null || true
  echo "[mvnw] Maven downloaded successfully."
fi

# Fall back to system mvn if wrapper download fails
if [ ! -f "$MAVEN_CMD" ]; then
  if command -v mvn > /dev/null 2>&1; then
    echo "[mvnw] Using system Maven..."
    exec mvn "$@"
  else
    echo "[mvnw] Error: Maven not found. Install Maven or ensure download succeeded."
    exit 1
  fi
fi

exec "$MAVEN_CMD" "$@"
