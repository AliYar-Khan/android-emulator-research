# Methodology

## Core rule

**Change one variable at a time whenever possible.**

Every environment comparison should differ in exactly one dimension:
the execution environment. Device, build, and runtime differences that are
inherent to a physical device must not be conflated with environment-induced
differences.

## Environment matrix

Target environments (one capture each, same collector build):

| Label | Environment | Notes |
|---|---|---|
| `physical` | Physical Android device | Reference baseline. |
| `waydroid` | Waydroid (container) | Host kernel shared with Android userspace. |
| `kvm` | Android-x86 / KVM | Full VM; goldfish/ranchu markers may appear. |
| `aosp` | AOSP Android Emulator | `qemu`/`ranchu`/`goldfish` markers expected. |
| `gameloop` | GameLoop (Tencent) | Windows host; AOW/qemu engine, x86 + houdini. |
| `ldplayer` | LDPlayer | Windows host; x86_64, adb over LAN. |
| `commercial` | Any other commercial emulator | Record product + version. |

Where available, prefer two environments that differ in a single dimension
(e.g. AOSP emulator images with/without GPU translation) over comparing
unrelated builds.

## Capture procedure

1. Pin the collector build: `scripts/collect.sh` builds the debug APK from
   the current tree (stale-checked); note the commit in the observations log.
2. Connect one device/emulator; `ANDROID_SERIAL` selects it if several are
   attached.
3. Run one capture per environment:

   ```sh
   scripts/collect.sh <label>            # e.g. scripts/collect.sh waydroid
   ```

   This installs the APK, triggers `extra_auto_export`, waits for the
   `FINGERPRINT_EXPORTED` marker (120 s timeout), pulls
   `datasets/fingerprints/<label>-<UTC>.json`, and validates it against the
   schema. Exit 0 means the capture is schema-valid with a verified hash.
4. Record the metadata listed in `docs/observations.md`.
5. Compare:

   ```sh
   scripts/compare.sh datasets/fingerprints/<a>.json datasets/fingerprints/<b>.json
   ```

   → per-section validation, structural diff (volatile fields excluded),
   then a Markdown report across all given files (add `-o report.md`).

## Fingerprinting Windows Android emulators (GameLoop, LDPlayer)

Both are ordinary Android devices reachable over adb — the collector runs
*inside* the emulator. The APK requires `minSdk = 25` (Android 7.1.2),
which matches GameLoop's common engine and all current LDPlayer versions.

**Option A — network adb from this machine (preferred)**

1. On the Windows host, expose the emulator's adb over the LAN:
   * **LDPlayer**: Settings → Other → enable *ADB over LAN*. Instances
     listen on ports 5555, 5557, 5559, … (confirm with
     `netstat -an | findstr LISTENING | findstr 555`).
   * **GameLoop**: the engine bundles its own adb (install dir,
     e.g. `AOW_64\adb.exe`); it typically listens on 127.0.0.1:5555 —
     rebind/firewall must allow the LAN interface for this option.
2. From this machine: `adb connect <windows-ip>:5555`
3. Capture:

   ```sh
   ANDROID_SERIAL=<windows-ip>:5555 scripts/collect.sh gameloop
   ```

**Option B — run the script on the Windows machine**

Install platform-tools, JDK 17, and Python, then from Git Bash in the repo
root: `./scripts/collect.sh gameloop`. Set `ANDROID_SERIAL` when several
emulator instances are attached.

**Caveats**

* One capture per product **and** version; keep the label as the product
  (`gameloop`, `ldplayer`) and record the emulator version + Android
  version in the `environment:` line of the observations block.
* x86 images with ARM translation: expect `abi` to report `x86`/`x86_64`
  and `environment_signals.markers` to include `houdini`; that is normal,
  not a mis-collection.
* If a capture fails with `INSTALL_FAILED_OLDER_SDK`, the engine is older
  than Android 7.1 — note it in observations; do not silently downgrade the
  collector.

## Interpreting results

* **`unavailable` is data, not failure.** A section reporting
  `unavailable` (e.g. no Vulkan, no sensors) is an observation about the
  environment. Only `collection_error` means the tool broke.
* **Markers are evidence, not verdicts.** `environment_signals.markers`
  records which known substrings (`qemu`, `ranchu`, `goldfish`, `waydroid`,
  …) appeared in observed text. Absence of every marker never proves a
  physical device; presence never proves malice. The raw evidence is stored
  next to the flags.
* **Compare with `diff`, not raw hash equality.** `fingerprint_sha256` pins a
  *document*, and documents contain dynamic values (load averages, uptimes,
  RSS counters in `raw`). Two captures of the same environment will usually
  have different hashes; `analysis.diff` ignores only the declared volatile
  fields and groups everything else by section, so dynamic noise shows up as
  recognizable leaf changes rather than a opaque hash mismatch.
* **Same env, same collector build → same structure.** Structural equality
  (diff shows only dynamic leaves) is the stability signal to aim for when
  re-running a capture.
* **One run is not a conclusion.** Re-run anything surprising at least once;
  timeouts (`collection_error`) and permission states can be transient.

## What this method deliberately does not do

No spoofing, no root, no `/proc`/`/sys` modification, no interaction with
PUBG or any other app, no integrity/anti-cheat probing. The framework only
reads what any ordinary app can read. See README, "What this project does
not do".
