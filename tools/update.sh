#!/bin/bash
# You have to run this once in order for ant builds to work
set -e

if [ -z "${ANDROID_SDK_ROOT+xxx}" ]; then
	echo "Please define ANDROID_SDK_ROOT to point to the Android SDK"
	exit 1
fi

if [ ! -d "$ANDROID_SDK_ROOT" ]; then
    echo "The directory $ANDROID_SDK_ROOT = ${ANDROID_SDK_ROOT} does not exist."
    exit 1
fi

ANDROID="$ANDROID_SDK_ROOT/tools/android"

# Note: android list sdk -e -a will give you a list of all the parts of the
# SDK that you want to install.

command -v "$ANDROID" >/dev/null 2>&1 || { echo >&2 "The $ANDROID tool is not found.  Aborting."; exit 1; }

ANDROID_TARGET=android-11

# Make sure the target SDK is installed and up-to-date.

$ANDROID update sdk -a -u -t tools,platform-tools,build-tools-17.0.0,$ANDROID_TARGET

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ATE_ROOT="$( cd $DIR/.. && pwd )"

echo "Updating android project files"

PROJECT_FILES="$( find "$ATE_ROOT" -name project.properties )"

for PROJECT_FILE in $PROJECT_FILES
do
    PROJECT_DIR="$( dirname "$PROJECT_FILE" )"
    echo "Updating $PROJECT_FILE"
    $ANDROID update project -p "$PROJECT_DIR" --target $ANDROID_TARGET
done
