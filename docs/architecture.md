# Architecture

## Overview

The framework has three layers:

1. **Android collector app** (`collector/android`) — runs on the target
   environment, produces `fingerprint.json`.
2. **Schema** (`schema/fingerprint.schema.json`) — the contract every
   fingerprint must satisfy (JSON Schema draft 2020-12).
3. **Host-side analysis** (`analysis/`, `scripts/`, `tools/`) — validation,
   normalization, diffing, reporting, and fixture generation.

## Fingerprint document

Top-level keys, in document order:

| Key | Purpose |
|---|---|
| `schema_version` | Schema contract version (`"1.0"`). |
| `collector_version` | Collector build version (`"0.1.0"`). |
| `timestamp` | Collection start, UTC ISO-8601. Volatile. |
| `environment.label` | Operator-supplied label (`physical`, `waydroid`, `kvm`, …). |
| `<16 sections>` | One object per collector (see below), in fixed order. |
| `collector_status_summary` | Section name → status, duplicated for quick scanning. |
| `fingerprint_sha256` | SHA-256 over the canonicalized document (volatile fields removed). |

Each of the 16 sections (`build`, `abi`, `cpu`, `gpu`, `vulkan`, `kernel`,
`proc`, `sys`, `hardware_features`, `sensors`, `display`, `network`,
`telephony`, `storage`, `security`, `environment_signals`) has the shape:

```json
{
  "collector": "cpu",
  "timestamp": "2026-01-15T10:00:00Z",
  "status": "available",
  "errors": [],
  "warnings": [],
  "data": { "raw": { }, "normalized": { } }
}
```

`status` is one of:

* `available` — the collector ran and produced observations.
* `unavailable` — the capability is genuinely absent (e.g. no Vulkan). A
  legitimate observation, **not** an error and not the same as `false`.
* `permission_denied` — Android withheld information the collector asked for.
* `collection_error` — the collector crashed or timed out.
* `unknown` — the collector could not determine anything.

`data.raw` preserves observations exactly as exposed; `data.normalized` is a
deterministic projection of raw. Raw is never destroyed by normalization.

## Collector pipeline

* Every collector implements `FingerprintCollector` (`name` + `collect()`)
  and returns a `CollectorResult`.
* `CollectorRegistry.all()` returns the 16 collectors in fixed order;
  `CollectorEngine.SECTION_ORDER` and the schema's section keys mirror that
  order exactly (pinned by `tests/test_validate.py`).
* `CollectorEngine.run()` executes each collector under a 30-second
  `withTimeout` on the IO dispatcher. A crash or timeout is captured as
  `collection_error` with the exception message — one bad collector never
  aborts the run.
* Pure parsing lives in `parser/` as side-effect-free objects with JUnit
  tests; collectors do I/O and assembly only.
* `Json.value` renders every float as a `%.9g` string (9 significant digits,
  locale-independent), so device and host canonicalization agree.

### Read-only, no-root, no dangerous permissions

Collectors read only world-readable surfaces: `Build.*`, system properties
via `getprop`, `/proc`, `/sys`, `PackageManager`, `SensorManager`,
`TelephonyManager` (no privileged calls), `StatFs`, and `ConnectivityManger`
with `ACCESS_NETWORK_STATE`. The app holds only `INTERNET` and
`ACCESS_NETWORK_STATE`. Anything that would need root or a runtime permission
is recorded as `unavailable` / `permission_denied` — never requested.

### Privacy

Device identifiers never enter a fingerprint:

* cmdline/system-property keys containing `serial`, `password`, or `uuid`
  (case-insensitive) are dropped before hashing (`ProcInfoParser`).
* MAC addresses present in `/proc/net/dev` raw text are parsed for interface
  *names only* (`NetDevParser`); addresses are never surfaced.
* No IP addresses, IMEI/serials, `ANDROID_ID`, accounts, or location data
  are read.
* The environment label is operator-supplied and is the only free text that
  intentionally identifies a capture.

## Canonicalization contract

Shared by `Canonical.kt` (device) and `analysis/canonical.py` (host).
**Change both sides together or not at all.**

1. Object keys are sorted lexicographically (identical results on both sides
   for every key used in this project).
2. Separators are compact: `{"a":1,"b":[1,2]}` — no whitespace.
3. Strings use JSON escaping with `\b \f \n \r \t` shortcuts and `\uXXXX`
   for other control characters; forward slashes are not escaped;
   non-ASCII characters are emitted verbatim (UTF-8).
4. Numbers are emitted as-is; a raw float, if one ever appears, is rendered
   `%.9g` on both sides.
5. Arrays preserve order.
6. The keys `timestamp` (at any depth) and `fingerprint_sha256` are stripped
   before hashing.

The contract is pinned by a shared golden fixture: `CanonicalTest.kt` and
`tests/test_canonical.py` both hash the same document to
`b88a907dc88c7178ee7bee93b02974fafc2e5969cc5c45af2a0ced0db50269c1`.

## Export and automation

`ExportManager` writes `fingerprint.json` to both app-private internal
storage and `getExternalFilesDir()`, and exposes it through `FileProvider`
for manual sharing. For scripted collection:

```
adb shell am start -S -n com.pubgcompat.collector/.MainActivity \
  --es extra_env_label <label> --ez extra_auto_export true
```

`MainActivity` auto-collects, exports, and logs
`FINGERPRINT_EXPORTED:<path>`; `scripts/collect.sh` waits for that marker,
pulls the file (via `run-as`, falling back to the external path), and runs
`analysis.validate`.

## Host-side analysis

| Module | Purpose | Exit codes |
|---|---|---|
| `python -m analysis.validate` | Schema + hash + status-summary consistency | 0 valid, 1 errors, 2 IO |
| `python -m analysis.normalize` | Refresh hash, sorted-key output | 0, 2 |
| `python -m analysis.diff` | Structural diff, volatile fields ignored | 0 same, 1 differs, 2 IO |
| `python -m analysis.report` | Markdown report + cross-env matrix | 0, 2 |
| `scripts/collect.sh` | Build → install → trigger → pull → validate | 0, 1, 2 |
| `scripts/compare.sh` | validate all → diff (2 files) → report | 0, 1, 2 |
| `tools/make_examples.py` | Regenerate `datasets/examples/*.json` | 0 |

`tests/` (pytest) covers canonical parity, all four tools, and the committed
example fixtures; `collector/android/app/src/test` (JUnit) covers the parsers
and the device-side canonicalizer. Both suites must pass before a capture
campaign.
