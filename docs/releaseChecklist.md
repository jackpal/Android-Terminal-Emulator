## Terminal Emulator for Android Release Checklist

# Test on 1.6 Donut API 4

(Lowest supported level -- will be dropped soon.)

# Test on 2.1 Eclair API 7

# Test on 2.2 Froyo API 8

# Test on 2.3 Gingerbread API 10

(Still popular with cheap phones.)

# Test on 4.3 Jelly Bean API 18

# Test on 4.4 Kit Kat API 19

# Test on 5.1 Lollipop API 22

(Or whatever latest is.)

# Test with Swype

(Has to be on a real device, Swype beta won't run on an emulator.)

# Update ./term/src/main/AndroidManifest.xml version number

    tools/increment-version-number

# Commit changes

    git commit -a -m "Increment version number to v1.0.xx"

# Tag git branch with version number

    git tag v1.0.xx

# Push git to repository

    git push
    git push --tags

# Build release apk

    tools/build-release

(Will only work if you have the signing keys for the app.)

# Publish to the Google Play Store

    open https://play.google.com/apps/publish

The Android Developer Console Publishing UI is error prone:

1) Click on the "Terminal Emulator for Android" link.

2) Click on the APK files tab

3) Upload your new APK.

4) Activate it by clicking on the Activate link

5) Click on the "Save" button.

6) Click on the "Product Details button".

7) Fill in the "Listing Details" for the new version.

8) Click on the "Save" button

9) Visit https://play.google.com/apps/publish and verify that the new version is listed as the current version.

10) Verify that Google Play Store is serving the new version
(check the "What's New" portion.)

https://play.google.com/store/apps/details?id=jackpal.androidterm

(Note, it can take several hours for the app to appear in the store.)

# Update the Terminal Emulator for Android Wiki

    open https://github.com/jackpal/Android-Terminal-Emulator/wiki/Recent-Updates

# Publish a new pre-compiled version of the APK for people who can't access Market.

Github serves pages out of branch gh-pages , directory downloads/Term.apk
Also update the version number in index.html

    cp ./term/build/outputs/apk/Term.apk /tmp
    git checkout gh-pages
    mv /tmp/Term.apk downloads/Term.apk
    git add downloads/Term.apk
    subl index.html
    # Update version save index.html
    git add index.html
    git commit -m "Update to version v1.0.xx"
    git push
    git checkout master

Public URL is http://jackpal.github.com/Android-Terminal-Emulator/downloads/Term.apk


