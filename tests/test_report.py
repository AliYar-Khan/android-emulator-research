"""Tests for analysis.report."""

from __future__ import annotations

import json

from analysis.report import main, render_comparison, render_report
from analysis.validate import COLLECTOR_SECTIONS
from tests.factories import make_document, make_section


def _with_markers() -> dict:
    signals = make_section("environment_signals")
    signals["data"] = {
        "normalized": {
            "markers": {
                "qemu": True,
                "ranchu": True,
                "goldfish": False,
                "waydroid": False,
            },
            "probes": {"/proc/cpuinfo": True},
        }
    }
    abi = make_section("abi")
    abi["data"] = {
        "normalized": {"supported_abis": ["arm64-v8a", "armeabi-v7a"]}
    }
    return make_document(label="kvm", section_overrides={"environment_signals": signals, "abi": abi})


def test_report_contains_header_and_all_sections():
    doc = make_document(label="physical")
    text = render_report(doc)
    assert "# Fingerprint: physical" in text
    assert "fingerprint_sha256" in text
    assert "(verified)" in text
    for name in COLLECTOR_SECTIONS:
        assert f"| {name} | available |" in text


def test_report_flags_hash_mismatch():
    doc = make_document()
    doc["cpu"]["status"] = "unavailable"  # stale hash
    assert "**MISMATCH**" in render_report(doc)


def test_report_highlights_markers_and_abis():
    text = render_report(_with_markers())
    assert "Environment markers detected**: qemu, ranchu" in text
    assert "**Supported ABIs**: arm64-v8a, armeabi-v7a" in text


def test_report_lists_section_errors():
    doc = make_document(
        section_overrides={"gpu": make_section("gpu", status="collection_error", errors=["boom"])}
    )
    text = render_report(doc)
    assert "gpu: error: boom" in text
    assert "| gpu | collection_error | 1 error(s) |" in text


def test_comparison_matrix_across_documents():
    text = render_comparison([make_document(label="a"), make_document(label="b")])
    assert text.startswith("# Comparison")
    assert "| Section | a | b |" in text
    for name in COLLECTOR_SECTIONS:
        assert f"| {name} | available | available |" in text


def test_cli_renders_multiple_documents_with_matrix(tmp_path, capsys):
    first = tmp_path / "one.json"
    second = tmp_path / "two.json"
    first.write_text(json.dumps(make_document(label="physical")), encoding="utf-8")
    second.write_text(json.dumps(make_document(label="waydroid")), encoding="utf-8")

    assert main([str(first), str(second)]) == 0
    out = capsys.readouterr().out
    assert "# Fingerprint: physical" in out
    assert "# Fingerprint: waydroid" in out
    assert "# Comparison" in out


def test_cli_writes_output_file(tmp_path):
    target = tmp_path / "report.md"
    source = tmp_path / "doc.json"
    source.write_text(json.dumps(make_document()), encoding="utf-8")
    assert main([str(source), "-o", str(target)]) == 0
    assert "# Fingerprint: test" in target.read_text(encoding="utf-8")


def test_cli_missing_file_exits_2(tmp_path):
    assert main([str(tmp_path / "nope.json")]) == 2
