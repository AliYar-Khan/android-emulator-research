# Limitations

Known limitations of the framework (not of any single environment).

## Measurement surface

* **App-sandbox visibility only.** Everything is observed from an ordinary
  app's perspective with no root and no dangerous permissions. Kernel-level,
  hypervisor-level, or privileged properties that an app cannot read are out
  of scope by design — a passing fingerprint can never prove an environment
  is "clean".
* **Permission-limited sections.** Anything requiring a runtime permission
  (e.g. phone state details) is recorded as `permission_denied` rather than
  requested, so those sections are systematically coarse.
* **Best-effort parsers.** `/proc`, `/sys`, and `getprop` formats are not a
  stable API. Parsers skip malformed lines rather than fail, so a vendor
  format change silently reduces coverage instead of erroring.
* **Snapshot, not time series.** A capture is a single moment; load
  averages, uptimes, and memory counters vary per run.

## Signals and interpretation

* **Marker absence is not proof.** No detected environment marker never
  proves a physical device — it only means none of the 12 known substrings
  appeared in the observed text. New containers/emulators need their markers
  added to `EnvironmentMarkerParser`.
* **`unavailable` is ambiguous.** It can mean a genuinely absent capability
  or a surface this app is not allowed to see; the framework does not
  distinguish those cases beyond the status enum.
* **Hashes pin documents, not environments.** `fingerprint_sha256` covers
  the whole document including dynamic values, so two captures of the same
  environment normally hash differently. Use `analysis.diff` for
  cross-capture comparison; hash equality only indicates byte-identical
  content modulo the declared volatile fields.
* **Volatile-field set is fixed.** Only `timestamp` (any depth) and
  `fingerprint_sha256` are excluded from the hash. Other dynamic keys
  (e.g. uptimes, loadavg, RSS) are hashed and will differ between runs.

## Tooling

* **Canonical edge cases.** The canonical contract is exact for the data the
  collector produces (ASCII keys, strings, integers, booleans, null). A
  hand-authored fixture containing a raw float with more than 9 significant
  digits, or a supplementary-plane character in a key, is an untested edge
  (device emits floats as `%.9g` strings, so this does not arise in captures).
* **Schema is permissive.** `data.raw` / `data.normalized` contents are not
  schema-constrained (`additionalProperties` is true); validation proves
  structure and hash integrity, not semantic completeness of section data.
* **Report is informational.** `analysis.report` renders what is in the
  document; the validity gate is `analysis.validate` (exit code), which
  `collect.sh` runs automatically.
* **`collect.sh` debug builds only.** The `run-as` pull path requires a
  debuggable APK; the external-files fallback depends on the shell's access
  to `/sdcard/Android/data`, which some OEM builds restrict.

## Scope

The framework does not, and is not intended to, interact with PUBG or any
other application, defeat Play Integrity/attestation, spoof properties, or
bypass anti-cheat or server-side enforcement. Findings are environmental
observations only.
