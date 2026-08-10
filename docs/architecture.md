# Architecture

> Skeleton — completed in the final milestone.

## Overview

The framework has three layers:

1. **Android collector app** (`collector/android`) — runs on the target
   environment, produces `fingerprint.json`.
2. **Schema** (`schema/fingerprint.schema.json`) — the contract every
   fingerprint must satisfy.
3. **Host-side analysis** (`analysis/`, `scripts/`) — validation, diffing,
   reporting.

## Canonicalization

Documented here in full (contract shared between
`Canonical.kt` and `analysis/canonical.py`):

* keys sorted by UTF-8 byte order
* compact separators
* floats rendered as `%g` strings
* volatile fields excluded from the hash
