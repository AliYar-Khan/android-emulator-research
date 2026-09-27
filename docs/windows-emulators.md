# Fingerprinting GameLoop & LDPlayer (Windows)

Both emulators are ordinary Android devices reachable over adb — the
collector APK runs *inside* the emulator. You can capture either on the
same Windows machine (simplest) or from a Linux machine over network adb.

The APK requires **minSdk 25 (Android 7.1.2)**, which matches GameLoop's
common engine and all current LDPlayer versions.

## Prerequisites (Windows)

| Tool | Why |
|---|---|
| Git for Windows | Provides **Git Bash** — the scripts are bash; do not run them in PowerShell/cmd |
| JDK 17 (Temurin) | Gradle build (`JAVA_HOME` must point at it) |
| Python 3 | `analysis.validate` after capture (`python` on PATH is fine; the script falls back) |
| Android platform-tools | `adb` on PATH |

## 1. Prepare the emulator

**LDPlayer**

* Settings (设置) → Other (其他) → enable **ADB over LAN** if you want to
  capture from another machine (not needed on the same machine).
* Instances listen on ports **5555, 5557, 5559, …** (first, second, third).

**GameLoop**

* The engine bundles its own adb (install dir, e.g.
  `C:\Program Files\TxGameAssistant\AOW_64\adb.exe` or `ui\adb.exe`) and
  listens on **127.0.0.1:5555**.
* Wait until the emulator has fully booted to the home screen before
  capturing.

## 2. Connect (Git Bash)

```sh
adb kill-server && adb start-server
adb connect 127.0.0.1:5555      # LDPlayer instance 1 / GameLoop; else 5557, …
adb devices                      # expect: 127.0.0.1:5555    device
```

**adb version conflict** (`adb server version (X) doesn't match this
client (Y)`): two adb versions are fighting over one server. Kill every
adb (Task Manager + `adb kill-server`) and use a single binary — if the
emulator refuses your platform-tools adb, export the emulator's bundled
one for the session:

```sh
export ADB="/c/Program Files/TxGameAssistant/AOW_64/adb.exe"   # adjust
```

(`collect.sh` honors `$ADB`.)

## 3. Capture

```sh
cd /c/path/to/android-emulator-research
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.x+xx"  # adjust
./scripts/collect.sh gameloop        # or: ldplayer
```

Several instances attached? Select one:

```sh
ANDROID_SERIAL=127.0.0.1:5557 ./scripts/collect.sh ldplayer
```

The script builds/stale-checks the APK, installs it, triggers
auto-export, waits for the `FINGERPRINT_EXPORTED` marker (120 s), pulls
`datasets/fingerprints/<label>-<UTC>.json`, and validates it. Exit 0 =
schema-valid with verified hash.

Capture files are **gitignored by design**; copy them to the Linux
machine for comparisons (`scripts/compare.sh`).

## 4. Record

Fill one block per capture in `docs/observations.md`:

* `label`: `gameloop` or `ldplayer` (keep the label = product)
* `environment`: emulator product **+ version** + its Android version
* `capture command`, `output`, `date (UTC)`, validation summary

## Capture from a Linux machine instead (network adb)

1. On Windows: enable **ADB over LAN** (LDPlayer) or expose GameLoop's
   port 5555 on the LAN; add a firewall rule for the emulator/adb.
2. From Linux:

   ```sh
   adb connect <windows-ip>:5555
   ANDROID_SERIAL=<windows-ip>:5555 scripts/collect.sh gameloop
   ```

## Expected results

* `abi` reports `x86` / `x86_64`; `environment_signals.markers` includes
  `houdini` (ARM translation) and possibly `qemu`/`goldfish` (AOW
  engine). That is normal — not a mis-collection.
* GameLoop on Android 7.1.2 exposes *more* `/proc`/`/sys` than modern
  physical devices (Android 16 hides several files) — a useful contrast
  for the research, record what differs in observations.
* Environment markers are evidence, not verdicts — see
  `docs/methodology.md` § Interpreting results.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `INSTALL_FAILED_OLDER_SDK` | Engine older than Android 7.1.2 (SDK 25) — record it in observations; do **not** downgrade the collector |
| `adb server version doesn't match` | One adb only: `adb kill-server`, close other adb processes, or `export ADB=` the emulator's bundled adb |
| `error: multiple devices attached` | `ANDROID_SERIAL=<host>:<port>` |
| `timed out … waiting for FINGERPRINT_EXPORTED` | Unlock the emulator screen, check `adb logcat -d -s System.out:I`, retry with `--skip-build` |
| Pulled fingerprint empty/malformed | Confirm install: `adb shell pm list packages \| grep pubgcompat`; fallback path `/sdcard/Android/data/...` is tried automatically |
| Collectors report warnings | Expected: restricted `/proc`/`/sys` surfaces become section *warnings*, not errors — only `collection_error` means the tool broke |
