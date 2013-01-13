## Android Terminal Emulator Release Checklist

# Test on 1.5

# Test on 1.6

# Test on 2.1

# Test on 2.2

# Test on 2.3

# Test on 4.0

# Test on 4.1

# Test with Swype

(Has to be on a real device, Swype beta won't run on an emulator.)

# Update AndroidManifest.xml version number

Update both android:versionName and android:versionCode.

# Commit changes

# Tag git branch with version number

git tag v1.0.xx

# Push git to repository

git push
git push --tags

# Build release apk

tools/build-release

(Will only work if you have the signing keys for the app.)

# Publish to the Google Play Store

https://play.google.com/apps/publish

The Android Developer Console Publishing UI is error prone:

1) Click on the "Android Terminal Emulator" link.

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

# Update the Android Terminal Emulator Wiki

https://github.com/jackpal/Android-Terminal-Emulator/wiki/Recent-Updates

# Publish a new pre-compiled version of the APK for people who can't access Market.

Github serves pages out of branch gh-pages , directory downloads/Term.apk
Public URL is http://jackpal.github.com/Android-Terminal-Emulator/downloads/Term.apk



