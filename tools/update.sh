#!/bin/bash
# You have to run this once in order for ant builds to work
set -e

if [ -z "${ANDROID_SDK_ROOT+xxx}" ]; then
	echo "Please define ANDROID_SDK_ROOT to point to the Android SDK"
	exit 1
fi

if [ ! -d "$ANDROID_SDK_ROOT" ]; then
    echo "The directory $ANDROID_SDK_ROOT = ${ANDROID_NDK_ROOT} does not exist."
    exit 1
fi

ANDROID="$ANDROID_SDK_ROOT/tools/android"

command -v "$ANDROID" >/dev/null 2>&1 || { echo >&2 "The $ANDROID tool is not found.  Aborting."; exit 1; }

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ATE_ROOT="$( cd $DIR/.. && pwd )"

echo "Updating android project files"

PROJECT_FILES="$( find "$ATE_ROOT" -name project.properties )"

for PROJECT_FILE in $PROJECT_FILES
do
    PROJECT_DIR="$( dirname "$PROJECT_FILE" )"
    $ANDROID update project -p "$PROJECT_DIR"
done
