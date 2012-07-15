#!/bin/bash
# You have to run this once in order for ant builds to work

# Check that the Android SDK tool "android" is on the path

ANDROID=android
command -v "$ANDROID" >/dev/null 2>&1 || { echo >&2 "The $ANDROID tool is not on the current PATH.  Aborting."; exit 1; }

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ATE_ROOT="$( cd $DIR/.. && pwd )"

echo "Updating android project files"

PROJECT_FILES="$( find "$ATE_ROOT" -name project.properties )"

for PROJECT_FILE in $PROJECT_FILES
do
    PROJECT_DIR="$( dirname "$PROJECT_FILE" )"
    $ANDROID update project -p "$PROJECT_DIR"
done
