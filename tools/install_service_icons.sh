#!/bin/bash
# Copy the service icons from the download location into the resource tree

if [ $# -ne 2 ]  ; then
    echo "Expected 2 arguments (source directory, destination directory)"
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

echo "Copying assets from $1 to $2"

ASSET="ic_stat_service_notification_icon.png"

for i in $(ls $SRC); do
    DSTDIR="$DST/$i"
    if [ ! -d "$DSTDIR" ] ; then
        mkdir "$DSTDIR"
    fi
    cp "$SRC/$i/$ASSET" "$DSTDIR/$ASSET"
done

# Create the Android 1.5 version of the icon

cp "$SRC/drawable-mdpi/$ASSET" "$DST/drawable/ASSET"
