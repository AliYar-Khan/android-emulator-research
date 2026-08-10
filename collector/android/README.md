# Android collector

**PUBG Compatibility Research Collector** — `com.pubgcompat.collector`

A no-root Android application that measures observable environment
characteristics and exports a normalized JSON fingerprint.

See the repository root `README.md` for the project overview and
`docs/architecture.md` for collector architecture.

## Requirements

* Android SDK (platform 35, build-tools 35.x)
* JDK 17+
* Gradle is provided via the wrapper (8.7)

## Build

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Run

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.pubgcompat.collector/.MainActivity
```

## Collect and export

Open the app, tap **Collect**, then **Export JSON** (writes
`fingerprint.json` into the app's external files directory), **Share JSON**
(Android share sheet), or **Copy JSON**.

From the host:

```bash
adb pull /sdcard/Android/data/com.pubgcompat.collector/files/fingerprint.json .
```

## Collectors

See `docs/architecture.md` for the full list of collectors.
