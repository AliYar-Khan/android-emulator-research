"""Synthetic fingerprint document factories shared by the Python test suite."""

from __future__ import annotations

from typing import Any

from analysis.canonical import fingerprint_sha256
from analysis.validate import COLLECTOR_SECTIONS

TIMESTAMP = "2026-01-02T03:04:05Z"

__all__ = ["TIMESTAMP", "make_section", "make_document"]


def make_section(
    collector: str,
    status: str = "available",
    data: dict[str, Any] | None = None,
    errors: list[str] | None = None,
    warnings: list[str] | None = None,
) -> dict[str, Any]:
    return {
        "collector": collector,
        "timestamp": TIMESTAMP,
        "status": status,
        "errors": list(errors or []),
        "warnings": list(warnings or []),
        "data": dict(data) if data is not None else {"raw": {}, "normalized": {}},
    }


def make_document(
    label: str = "test",
    section_overrides: dict[str, dict[str, Any]] | None = None,
) -> dict[str, Any]:
    """A schema-valid document with all 16 sections available and a fresh hash."""
    doc: dict[str, Any] = {
        "schema_version": "1.0",
        "collector_version": "0.1.0",
        "timestamp": TIMESTAMP,
        "environment": {"label": label},
    }
    overrides = section_overrides or {}
    for name in COLLECTOR_SECTIONS:
        doc[name] = overrides.get(name) or make_section(name)
    doc["collector_status_summary"] = {
        name: doc[name]["status"] for name in COLLECTOR_SECTIONS if name in doc
    }
    doc["fingerprint_sha256"] = fingerprint_sha256(doc)
    return doc
