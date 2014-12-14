Building
========

To keep from typing "Terminal Emulator for Android" over and over again, this
document will use the abbreviation "TEA" to stand for "Terminal
Emulator for Android".


Download the Software Needed to Build Terminal Emulator for Android
-------------------------------------------------------------------

TEA is built using:

 + Android Studio 1.0 or newer
 + Android NDK r10d or newer

Download Android Studio from:

  http://developer.android.com/sdk

Download the NDK from:

  http://developer.android.com/tools/sdk/ndk/


Telling Gradle where to find the Android NDK and SDK
----------------------------------------------------

Android Studio and the gradle build tool need to know where to find the NDK and
SDK on your computer.

Create a file local.properties in the root directiory of the TEA project that
contains this text:

    ndk.dir=path/to/ndk
    sdk.dir=path/to/sdk

On my personal dev machine the file looks like this, but of course it will
be different on your machine, depending upon your OS, user name, directory
tree, and version of the NDK that you have installed.

    ndk.dir=/Users/jack/code/android-ndk-r10d
    sdk.dir=/Users/jack/Library/Android/sdk


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


Building TEA from the command line
----------------------------------

  1. Open a command line shell window and navigate to the main TEA directory.
  2. Define the environment variable ANDROID_SDK_ROOT to point to the root
     of your Android SDK installation.
  3. Build

      $ ./tools/build-debug

  4. Copy the built executable to a device:

      $ ./tools/push-and-run-debug
