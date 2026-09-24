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
| `commercial` | Commercial emulator | Record product + version. |

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
