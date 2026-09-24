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

## Scripted capture (preferred)

From the repository root, with a device attached:

```bash
scripts/collect.sh physical          # label the environment
```

This builds the APK if stale, installs it, triggers an automated
collect+export, waits for the `FINGERPRINT_EXPORTED` logcat marker, pulls
`fingerprint.json`, and validates it against the schema. Output lands in
`datasets/fingerprints/<label>-<UTC>.json`.

## Manual collect and export

Open the app, tap **Collect**, then **Export JSON** (writes
`fingerprint.json` to the app's internal and external files directories),
**Share JSON** (Android share sheet), or **Copy JSON**.

From the host:

```bash
adb pull /sdcard/Android/data/com.pubgcompat.collector/files/fingerprint.json .
```

## Intent extras

`MainActivity` supports automation:

| Extra | Type | Effect |
|---|---|---|
| `extra_env_label` | String | Environment label for `environment.label` (default `android`). |
| `extra_auto_export` | Boolean | Collect on launch, export, log `FINGERPRINT_EXPORTED:<path>`. |

```bash
adb shell am start -S -n com.pubgcompat.collector/.MainActivity \
  --es extra_env_label physical --ez extra_auto_export true
```

(`-S` force-stops first so `onCreate` always runs.)

## Collectors

See `docs/architecture.md` for the full list of collectors.
