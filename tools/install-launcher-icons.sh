#!/bin/bash
# Copy the launcher icons from the download directory to the res tree,
# changeing names as apropriate.

set -e

if [ $# -ne 2 ]  ; then
    echo "Expected 2 arguments (source directory, AndroidTerminalEmulator root)"
    exit 1
fi

SRC=$1
DST=$2

if [ ! -d "$1" ] ; then
    echo "Expected first argument to be a source directory. Got '$1'"
    exit 2
fi

if [ ! -d "$2" ] ; then
    echo "Expected second argument to be a destination directory. Got '$2'"
    exit 2
fi

RES=$DST/res

if [ ! -d "$RES" ] ; then
    echo "Expected to find directory '$RES'"
    exit 3
fi

cp $SRC/android-terminal-emulator-36.png $RES/drawable-ldpi/ic_launcher.png
cp $SRC/android-terminal-emulator-48.png $RES/drawable-mdpi/ic_launcher.png
cp $SRC/android-terminal-emulator-48.png $RES/drawable/ic_launcher.png
cp $SRC/android-terminal-emulator-72.png $RES/drawable-hdpi/ic_launcher.png
cp $SRC/android-terminal-emulator-96.png $RES/drawable-xhdpi/ic_launcher.png
cp $SRC/android-terminal-emulator-144.png $RES/drawable-xxhdpi/ic_launcher.png
# Not yet provided by artist.
# cp $SRC/android-terminal-emulator-192.png $RES/drawable-xxxhdpi/ic_launcher.png
cp $SRC/android-terminal-emulator-512.png $DST/artwork
cp $SRC/android-terminal-emulator.svg $DST/artwork
