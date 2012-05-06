#!/bin/sh

# generate-docs.sh -- generate JavaDoc documentation for the library
# Requirements: a library project configured for the local build environment
# and Android SDK API documentation installed on the local machine.

DOCDIR=`dirname $0`

get_property() {
	egrep "^$1=" "$2" | cut -d= -f2
}

TARGET_API=`get_property target "$DOCDIR/../project.properties"`
SDK_DIR=`get_property 'sdk\.dir' "$DOCDIR/../local.properties"`

exec javadoc -d "$DOCDIR" -classpath "${SDK_DIR}/platforms/$TARGET_API/android.jar" -linkoffline http://developer.android.com/reference file:"${SDK_DIR}/docs/reference" -sourcepath "$DOCDIR/../src" jackpal.androidterm.emulatorview
