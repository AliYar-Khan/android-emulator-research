# PUBG Compatibility Research

A reproducible research framework for measuring what an Android application can
observe across different Android execution environments:

1. Physical Android devices
2. Waydroid (container)
3. Android-x86 / KVM (virtual machine)
4. AOSP Android Emulator
5. Commercial Android emulators (where available)
6. Future Linux gaming emulators

> This project measures observable Android/environment characteristics. It does
> not attempt to defeat application security, Play Integrity, anti-cheat
> systems, or server-side enforcement.

## What this project does

* Runs a no-root Android collector app on any Android environment.
* Produces normalized, machine-readable fingerprints (`fingerprint.json`).
* Hashes the normalized fingerprint deterministically (`fingerprint_sha256`).
* Compares fingerprints across environments with `diff.py`.
* Generates a Markdown comparison report across N environments with `report.py`.
* Validates every capture against a JSON Schema.

## What this project does not do

* Does not spoof device properties, modify `/system`, `/proc`, or `/sys`.
* Does not hide virtualization or disguise emulators.
* Does not forge, replay, or bypass Play Integrity or attestation.
* Does not hook, patch, or interact with PUBG (or any other app).
* Does not disable anti-cheat or circumvent server-side enforcement.
* Does not capture credentials, personal data, or traffic.

## Project layout

```
collector/android/   Android collector app (Kotlin)
schema/              fingerprint.schema.json
datasets/            captured fingerprints + example fixtures
analysis/            diff.py, report.py, normalize.py
tools/               adb / export helpers
docs/                methodology, architecture, limitations
scripts/             collect.sh, compare.sh
tests/               Python test suite
```

## Status

Progress per milestone:

| Milestone | Scope | Status |
|---|---|---|
| M0 | Repo hygiene (untrack `.cxx`), Python packaging (`pyproject.toml`) | **done** |
| M1 | Remaining 9 collectors → 16/16 schema sections | **done** |
| M2 | Host-side analysis: `validate.py`, `normalize.py`, `diff.py`, `report.py` + Python test suite | **done** |
| M3 | Capture tooling: `scripts/`, `tools/`, committed example fixtures | **done** |
| M4 | Final documentation (`docs/*`) | **done** |

Field captures (physical device, Waydroid, KVM, AOSP / commercial emulators)
are run by the operator using `scripts/collect.sh` (see `docs/methodology.md`
and the recording template in `docs/observations.md`). For the Windows
emulators GameLoop and LDPlayer, follow the step-by-step guide:
[`docs/windows-emulators.md`](docs/windows-emulators.md).

See `docs/architecture.md` for the design and `docs/methodology.md` for the
research method. See `collector/android/README.md` for Android-specific build
instructions.
