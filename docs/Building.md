Building
========

To keep from typing "Terminal Emulator for Android" over and over again, this
document will use the abbreviation "TEA" to stand for "Terminal
Emulator for Android".

Obtain the Software Needed to Build Terminal Emulator for Android
-----------------------------------------------------------------

TEA is built using:

 + Android Studio 1.0 or newer
 + Android NDK r10d or newer

Download Android Studio from:

  http://developer.android.com/sdk

Download the NDK from:

  http://developer.android.com/tools/sdk/ndk/


Telling Gradle where to find the NDK
------------------------------------

Android Studio and the gradle build tool need manual help to find where the
NDK is located on your computer. The easiest way to do this is to try building,
wait for the build to fail, then fix the build by editing the file
local.properties that was created by gradle as part of the failing build.

When it exists, the file local.properties will be in the root directiory of
the TEA source tree. Use a text editor to edit the file local.properties, and
add a line that specifies where the NDK is located:

  ndk.dir=path/to/ndk

On my personal dev machine this line looks like this, but of course it will
be different on your machine, depending upon your OS, user name, directory
tree, and version of the NDK that you have installed.

  ndk.dir=/Users/jack/code/android-ndk-r10d


Building TEA
------------

You can build TEA two ways:

  1. Using the Android Studio IDE
  2. Using the "gradlew" command line tool

Using Android Studio is convenient for development. Using "gradlew" is
convenient for automated testing and publishing.


Building TEA with Android Studio
--------------------------------

  1. Open Android Studio
  2. Choose "Open an existing Android Studio project" from the "Quick Start"
     wizard.
  3. Choose the top-level TEA directory. (If you installed the source code from
     github, this directory will be named Android-Terminal-Emulator).
  4. Use the Android Studio menu "Run : Run 'term'" to build and run the app.
       a. The first time you do this you will get an error message about
          needing to define ndk.dir. See above for how to fix this.

Building TEA from the command line
----------------------------------

  1. Open a command line shell window and navigate to the main TEA directory.
  2. Create a local.properties file with two lines in it:

    sdk.dir=/path/to/android/sdk
    ndk.dir=/path/to/android/ndk

  3. Build

      $ ./tools/build-debug

  3. To copy the built executable to a device:

      $ ./tools/push-and-run-debug
