#!/bin/bash
# A script to merge selected translations from the CyanogenMOD version of
# Android Terminal Emulator to the upstream version.

set -e

# Losely based on
# http://stackoverflow.com/questions/1214906/how-do-i-merge-a-sub-directory-in-git

SRC_REPO=https://github.com/CyanogenMod/android_packages_apps_AndroidTerm
SRC=android_packages_apps_AndroidTerm
SRC_REMOTE_NAME=CyanogenMOD
SRC_BRANCH=gingerbread

DST_REPO=git@github.com:jackpal/Android-Terminal-Emulator.git
DST=Android-Terminal-Emulator

SANDBOX_DIR=/tmp/mergeTrans/sandbox
rm -rf $SANDBOX_DIR
mkdir -p $SANDBOX_DIR
cd $SANDBOX_DIR
git clone $DST_REPO
cd $DST

git remote add -f $SRC_REMOTE_NAME $SRC_REPO
git merge -s ours --no-commit $SRC_REMOTE_NAME/$SRC_BRANCH

# The localizations we want to copy
dirs="nb nl pl pt-rPT sk sv zh-rTW"

for i in $dirs; do
  echo "Merging $i"
  DIR=res/values-$i
  git read-tree --prefix=$DIR -u $SRC_REMOTE_NAME/$SRC_BRANCH:$DIR
done

git commit -m "merge localizations for $dirs from $SRC_REPO branch $SRC_BRANCH"
