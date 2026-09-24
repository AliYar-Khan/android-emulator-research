"""Tests for analysis.normalize."""

from __future__ import annotations

import json

from analysis.canonical import canonical_json, fingerprint_sha256
from analysis.normalize import main, normalize_document
from tests.factories import make_document


def test_refreshes_stale_hash():
    doc = make_document()
    doc["cpu"]["status"] = "unavailable"  # stale hash now
    normalized = normalize_document(doc)
    assert normalized["fingerprint_sha256"] == fingerprint_sha256(doc)
    assert normalized["fingerprint_sha256"] == fingerprint_sha256(normalized)


def test_is_idempotent():
    once = normalize_document(make_document())
    twice = normalize_document(once)
    assert once == twice


def test_changes_no_observation_data():
    doc = make_document()
    normalized = normalize_document(doc)
    for key in doc:
        if key != "fingerprint_sha256":
            assert normalized[key] == doc[key]


def test_cli_pretty_output_is_sorted(tmp_path, capsys):
    source = tmp_path / "in.json"
    source.write_text(json.dumps(make_document(), indent=4), encoding="utf-8")

    assert main([str(source)]) == 0
    out = capsys.readouterr().out
    parsed = json.loads(out)
    assert list(parsed.keys()) == sorted(parsed.keys())
    assert parsed["fingerprint_sha256"] == fingerprint_sha256(parsed)


def test_cli_compact_output_is_canonical(tmp_path, capsys):
    doc = make_document()
    source = tmp_path / "in.json"
    source.write_text(json.dumps(doc), encoding="utf-8")

    assert main([str(source), "--compact"]) == 0
    out = capsys.readouterr().out
    assert out == canonical_json(normalize_document(doc)) + "\n"


def test_cli_writes_output_file(tmp_path):
    doc = make_document()
    source = tmp_path / "in.json"
    target = tmp_path / "out.json"
    source.write_text(json.dumps(doc), encoding="utf-8")

    assert main([str(source), "-o", str(target)]) == 0
    written = json.loads(target.read_text(encoding="utf-8"))
    assert written["fingerprint_sha256"] == fingerprint_sha256(doc)


def test_cli_missing_file_exits_2(tmp_path):
    assert main([str(tmp_path / "nope.json")]) == 2
