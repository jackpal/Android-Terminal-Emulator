#!/bin/bash
# You have to run this once in order for ant builds to work

# assumes the Android SDK tool "android" is on the path

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ATE_ROOT="$( cd $DIR/.. && pwd )"

echo "Updating android project files"

PROJECT_FILES="$( find "$ATE_ROOT" -name project.properties )"

for PROJECT_FILE in $PROJECT_FILES
do
    PROJECT_DIR="$( dirname "$PROJECT_FILE" )"
    android update project -p "$PROJECT_DIR"
done
