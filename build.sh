#!/bin/bash
# build.sh — only required javac + jar
set -e

SRC=src
OUT=out
JAR=betting-service.jar
MAIN=com.betting.server.BettingServerMain

echo "clean old package..."
rm -rf "$OUT" "$JAR"
mkdir -p "$OUT"

echo "compile..."
find "$SRC" -name "*.java" | xargs javac --release 17 -d "$OUT"

echo "package JAR..."
# write to MANIFEST
MANIFEST=out/MANIFEST.MF
echo "Main-Class: $MAIN" > "$MANIFEST"

jar cfm "$JAR" "$MANIFEST" -C "$OUT" .

echo "compile done：$JAR"
echo ""
echo "run command："
echo "  java -jar $JAR"
