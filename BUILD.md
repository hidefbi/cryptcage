# Building NoteCrypt Pro from the terminal

These steps use the Android command-line tools, Gradle and adb. Android Studio is not needed.

## Requirements

- JDK 17
- Android SDK command-line tools, with platform 34 and build-tools 34.0.0
- Gradle 8.7 (only needed once, to create the wrapper)
- A phone with USB debugging enabled, or an emulator, for installing

## Build and test

    export ANDROID_HOME=$HOME/Android/Sdk
    export PATH=$PATH:$ANDROID_HOME/platform-tools
    cd notecryptpro-2.0-src
    echo "sdk.dir=$ANDROID_HOME" > local.properties
    ./gradlew testDebugUnitTest
    ./gradlew assembleDebug

The debug APK is written to app/build/outputs/apk/debug/app-debug.apk.

## Signed release build

Create a keystore once and keep it safe. Losing it means existing installs can no longer be updated.

    keytool -genkeypair -v -keystore $HOME/notecrypt-release.jks \
        -alias notecrypt -keyalg RSA -keysize 4096 -validity 10000

Add these lines to ~/.gradle/gradle.properties (not to the project):

    NC_KEYSTORE=/home/you/notecrypt-release.jks
    NC_KEY_ALIAS=notecrypt
    NC_STORE_PASS=...
    NC_KEY_PASS=...

Then build:

    ./gradlew assembleRelease

The signed APK is app/build/outputs/apk/release/app-release.apk.

## Install

    adb devices
    adb install -r app/build/outputs/apk/release/app-release.apk

Use -r to keep the data of an existing install. This only works when the signature matches the installed app.

## Warnings

- Databases are stored in the app's private storage. Uninstalling the app deletes them. Export every database first.
- The debug and release builds have different signatures. Installing one over the other fails until the app is uninstalled, which deletes the data.
- Databases from version 1.x are in the shared NoteCrypt folder. Import them from the start screen after the update.

## Checks before a release

    ./gradlew testDebugUnitTest
    ./gradlew lintDebug
    ./gradlew assembleRelease

`lintDebug` reports API problems, missing translations and manifest issues. Read its HTML report at
app/build/reports/lint-results-debug.html before publishing.
