#!/bin/sh
set -eu

RACINE=$(CDPATH= cd "$(dirname "$0")/.." && pwd)
cd "$RACINE"

REPERTOIRE_CIBLE="target"
JAR_SOURCE="${1:-$RACINE/$REPERTOIRE_CIBLE/Leopold-1.0-SNAPSHOT.jar}"
REPERTOIRE_ENTREE="$REPERTOIRE_CIBLE/jpackage-input"
REPERTOIRE_SORTIE="$REPERTOIRE_CIBLE/app-image"
APPLICATION="$REPERTOIRE_SORTIE/Leopold.app"

if [ ! -f "$JAR_SOURCE" ]; then
    if command -v mvn >/dev/null 2>&1; then
        mvn -q -DskipTests package
        JAR_SOURCE="$RACINE/$REPERTOIRE_CIBLE/Leopold-1.0-SNAPSHOT.jar"
    else
        echo "JAR introuvable et Maven absent. Fournis le JAR en argument ou installe Maven." >&2
        exit 1
    fi
fi

mkdir -p "$REPERTOIRE_ENTREE" "$REPERTOIRE_SORTIE"
cp "$JAR_SOURCE" "$REPERTOIRE_ENTREE/Leopold.jar"

if [ -e "$APPLICATION" ]; then
    rm -rf "$APPLICATION"
fi

jpackage --type app-image --name Leopold --input "$REPERTOIRE_ENTREE" \
    --main-jar Leopold.jar --main-class org.example.App \
    --icon packaging/macos/Leopold.icns \
    --dest "$REPERTOIRE_SORTIE"

echo "Application créée : $APPLICATION"
