"""Golden-hash parity with the on-device Kotlin canonicalizer and canonical rules.

The fixture and expected SHA-256 are identical to those pinned in
``CanonicalTest.kt``: if either side's canonicalization contract changes, both
suites must be updated together.
"""

from __future__ import annotations

import json

from analysis.canonical import canonical_json, fingerprint_sha256, strip_volatile

GOLDEN_FIXTURE = r'''{"schema_version":"1.0","timestamp":"2026-01-02T03:04:05Z","fingerprint_sha256":"deadbeef","environment":{"label":"test"},"z":"tab\there \"q\" back\\slash é","a_number":42,"a_bool":true,"a_null":null,"list":[1,"two",false,null,{"nested":"object"}],"build":{"collector":"BuildCollector","timestamp":"2026-01-02T03:04:06Z","status":"available","errors":[],"warnings":[],"data":{"normalized":{"model":"Test Device","sdk_int":34}}}}'''

GOLDEN_HASH = "b88a907dc88c7178ee7bee93b02974fafc2e5969cc5c45af2a0ced0db50269c1"


def test_golden_hash_matches_kotlin_canonicalizer():
    doc = json.loads(GOLDEN_FIXTURE)
    assert fingerprint_sha256(doc) == GOLDEN_HASH


def test_hash_is_deterministic_across_parses():
    assert fingerprint_sha256(json.loads(GOLDEN_FIXTURE)) == fingerprint_sha256(
        json.loads(GOLDEN_FIXTURE)
    )


def test_keys_sorted_by_byte_order_compact_separators():
    assert canonical_json({"b": 1, "a": 2}) == '{"a":2,"b":1}'
    assert canonical_json({"a": {"z": True, "y": None}}) == '{"a":{"y":null,"z":true}}'


def test_string_escaping_shortcuts_and_control_chars():
    value = {"s": 'a"b\\c\td\n'}
    assert canonical_json(value) == '{"s":"a\\"b\\\\c\\td\\n"}'
    assert canonical_json({"c": "\u0001"}) == '{"c":"\\u0001"}'


def test_non_ascii_emitted_verbatim():
    assert canonical_json({"u": "é 日本語"}) == '{"u":"é 日本語"}'


def test_floats_rendered_like_device_percent_9g():
    assert canonical_json({"f": 1 / 3}) == '{"f":0.333333333}'
    assert canonical_json({"f": 1.5}) == '{"f":1.5}'


def test_bool_is_not_a_number():
    # canonical comparison basis used by diff: true must not equal 1.
    assert canonical_json(True) != canonical_json(1)


def test_strip_volatile_removes_timestamps_recursively():
    doc = json.loads(GOLDEN_FIXTURE)
    stripped = strip_volatile(doc)
    assert "timestamp" not in stripped
    assert "fingerprint_sha256" not in stripped
    assert "timestamp" not in stripped["build"]
    assert stripped["build"]["status"] == "available"
    assert stripped["z"] == doc["z"]
    # Stripping is idempotent and does not touch values.
    assert strip_volatile(stripped) == stripped
