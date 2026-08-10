"""Canonicalization and deterministic hashing of fingerprint documents.

Mirror of the on-device implementation (collector/android/.../Canonical.kt).
Contract:

1. Object keys are sorted by UTF-8 byte order.
2. Compact separators: ``{"a":1,"b":[1,2]}`` - no whitespace.
3. Strings use JSON escaping with ``\\b \\f \\n \\r \\t`` shortcuts and
   ``\\uXXXX`` for other control characters; forward slashes are NOT escaped;
   non-ASCII characters are emitted verbatim (UTF-8).
4. Numbers are emitted as-is. Fingerprint data only ever contains integers,
   strings, booleans and null in canonical positions (floats are rendered as
   ``%g`` strings on the device).
5. Arrays preserve order.
"""

from __future__ import annotations

import hashlib
import json
from typing import Any

__all__ = ["canonical_json", "fingerprint_sha256", "strip_volatile"]


def _write_string(s: str, out: list[str]) -> None:
    out.append('"')
    for ch in s:
        if ch == '"':
            out.append('\\"')
        elif ch == "\\":
            out.append("\\\\")
        elif ch == "\b":
            out.append("\\b")
        elif ch == "\f":
            out.append("\\f")
        elif ch == "\n":
            out.append("\\n")
        elif ch == "\r":
            out.append("\\r")
        elif ch == "\t":
            out.append("\\t")
        elif ord(ch) < 0x20:
            out.append("\\u%04x" % ord(ch))
        else:
            out.append(ch)
    out.append('"')


def _write(value: Any, out: list[str]) -> None:
    if value is None:
        out.append("null")
    elif value is True:
        out.append("true")
    elif value is False:
        out.append("false")
    elif isinstance(value, str):
        _write_string(value, out)
    elif isinstance(value, int):  # bool is a subclass of int but caught above
        out.append(str(value))
    elif isinstance(value, float):
        # Fingerprint data should never contain floats (see contract).
        # If one arrives, render it like Java's %g so both sides agree.
        out.append("%g" % value)
    elif isinstance(value, (list, tuple)):
        out.append("[")
        for i, item in enumerate(value):
            if i:
                out.append(",")
            _write(item, out)
        out.append("]")
    elif isinstance(value, dict):
        out.append("{")
        for i, key in enumerate(sorted(value.keys())):
            if i:
                out.append(",")
            _write_string(key, out)
            out.append(":")
            _write(value[key], out)
        out.append("}")
    else:
        raise TypeError(f"cannot canonicalize {type(value).__name__}")


def canonical_json(value: Any) -> str:
    """Deterministic compact JSON string for a parsed fingerprint document."""
    out: list[str] = []
    _write(value, out)
    return "".join(out)


def _strip_volatile(value: Any) -> Any:
    if isinstance(value, dict):
        return {
            k: _strip_volatile(v)
            for k, v in value.items()
            if k not in ("timestamp", "fingerprint_sha256")
        }
    if isinstance(value, list):
        return [_strip_volatile(v) for v in value]
    return value


def strip_volatile(document: dict) -> dict:
    """Remove volatile fields ('timestamp', 'fingerprint_sha256') recursively."""
    return _strip_volatile(document)


def fingerprint_sha256(document: dict) -> str:
    """Deterministic fingerprint hash over canonicalized normalized JSON."""
    stable = strip_volatile(document)
    canonical = canonical_json(stable).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest()


def load_json(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)
