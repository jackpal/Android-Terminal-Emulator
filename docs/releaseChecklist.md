## Android Terminal Emulator Release Checklist

# Test on 1.5 

# Test on 1.6

# Test on 2.1

# Test on 2.2

# Test on 2.3

# Test on 4.0

# Test with Swype

(Has to be on a real device, Swype beta won't run on an emulator.)

# Update AndroidManifest.xml version number

# Tag git branch with version number

git tag v1.0.xx

# Push git to repository

git push
git push --tags

# Publish to market

https://market.android.com/publish

The Android Publish UI is error prone:

1) Visit https://market.android.com/publish/Home#AppEditorPlace:p=jackpal.androidterm

2) Click on the APK files tab

3) Upload your new APK.

4) Activate it by clicking on the Activate link

5) Click on the "Save" button.

6) Click on the "Product Details button".

7) Fill in the "Listing Details" for the new version.

8) Click on the "Save" button

9) Visit https://market.android.com/publish/Home and verify that the new version is listed as the current version.

10) Verify that Market is serving the new version (check the "What's New" portion.)

https://market.android.com/details?id=jackpal.androidterm

# Update the Android Terminal Emulator Wiki

https://github.com/jackpal/Android-Terminal-Emulator/wiki/Recent-Updates

# Upload a new pre-compiled version for people who can't access Market.

https://github.com/jackpal/Android-Terminal-Emulator/downloads



