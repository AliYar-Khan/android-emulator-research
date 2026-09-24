"""Tests for analysis.validate."""

from __future__ import annotations

import json

from analysis.canonical import fingerprint_sha256
from analysis.validate import COLLECTOR_SECTIONS, main, validate_document
from tests.factories import make_document, make_section


def test_valid_document_has_no_errors_or_warnings():
    errors, warnings = validate_document(make_document())
    assert errors == []
    assert warnings == []


def test_missing_required_field_is_a_schema_error():
    doc = make_document()
    del doc["schema_version"]
    errors, _ = validate_document(doc)
    assert any("schema" in e and "schema_version" in e for e in errors)


def test_invalid_status_enum_is_a_schema_error():
    doc = make_document(
        section_overrides={"cpu": make_section("cpu", status="banana")},
    )
    errors, _ = validate_document(doc)
    assert any("schema" in e for e in errors)


def test_hash_mismatch_is_an_error():
    doc = make_document()
    doc["cpu"]["status"] = "unavailable"
    errors, _ = validate_document(doc)
    assert any(e.startswith("hash:") for e in errors)


def test_summary_mismatch_is_an_error():
    doc = make_document()
    doc["collector_status_summary"]["build"] = "unknown"
    errors, _ = validate_document(doc)
    assert any(e.startswith("summary: build") for e in errors)


def test_bad_timestamp_format_is_a_schema_error():
    doc = make_document()
    doc["timestamp"] = "not-a-date"
    errors, _ = validate_document(doc)
    assert any("date-time" in e for e in errors)


def test_missing_section_is_a_warning_not_an_error():
    doc = make_document()
    del doc["gpu"]
    doc["fingerprint_sha256"] = fingerprint_sha256(doc)  # re-hash after mutation
    errors, warnings = validate_document(doc)
    assert errors == []
    assert "section missing: gpu" in warnings


def test_all_sections_are_in_document_order_contract():
    # Guard: the host-side tuple must stay aligned with the schema and the device.
    schema = json.load(
        open("schema/fingerprint.schema.json", encoding="utf-8"),
    )
    section_keys = [
        k
        for k in schema["properties"]
        if isinstance(schema["properties"][k], dict) and "$ref" in schema["properties"][k]
    ]
    assert tuple(section_keys) == COLLECTOR_SECTIONS


def test_cli_exit_codes(tmp_path, capsys):
    good = tmp_path / "good.json"
    good.write_text(json.dumps(make_document()), encoding="utf-8")

    bad = make_document()
    bad["fingerprint_sha256"] = "0" * 64
    bad_file = tmp_path / "bad.json"
    bad_file.write_text(json.dumps(bad), encoding="utf-8")

    assert main([str(good)]) == 0
    out = capsys.readouterr().out
    assert "OK" in out

    assert main([str(bad_file)]) == 1
    out = capsys.readouterr().out
    assert "FAIL" in out and "hash:" in out

    assert main([str(tmp_path / "missing.json")]) == 2
