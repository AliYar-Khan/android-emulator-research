"""Validate fingerprint documents against schema and internal consistency.

Checks, in order:

1. JSON parses (done by the caller / CLI).
2. JSON Schema draft 2020-12, including ``date-time`` formats.
3. ``fingerprint_sha256`` recomputes over the canonicalized document.
4. ``collector_status_summary`` agrees with each section's status.
5. All 16 collector sections are present (warning only - partial captures
   are legitimate during development).

Exit codes: 0 valid, 1 validation errors, 2 usage/IO problems.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator

from .canonical import fingerprint_sha256

__all__ = ["COLLECTOR_SECTIONS", "load_schema", "validate_document", "main"]

SCHEMA_PATH = Path(__file__).resolve().parent.parent / "schema" / "fingerprint.schema.json"

#: The 16 collector sections, in document order (mirror of CollectorEngine.SECTION_ORDER).
COLLECTOR_SECTIONS: tuple[str, ...] = (
    "build",
    "abi",
    "cpu",
    "gpu",
    "vulkan",
    "kernel",
    "proc",
    "sys",
    "hardware_features",
    "sensors",
    "display",
    "network",
    "telephony",
    "storage",
    "security",
    "environment_signals",
)

_HEX64 = re.compile(r"^[0-9a-f]{64}$")

_schema_cache: dict[str, Any] | None = None


def load_schema() -> dict[str, Any]:
    """Load schema/fingerprint.schema.json (cached)."""
    global _schema_cache
    if _schema_cache is None:
        with SCHEMA_PATH.open("r", encoding="utf-8") as fh:
            _schema_cache = json.load(fh)
    return _schema_cache


def _json_path(path: Any) -> str:
    return "".join(f"/{part}" for part in path) or "/"


def validate_document(doc: dict[str, Any]) -> tuple[list[str], list[str]]:
    """Return (errors, warnings) for one fingerprint document. Empty errors = valid."""
    errors: list[str] = []
    warnings: list[str] = []

    validator = Draft202012Validator(
        load_schema(),
        format_checker=Draft202012Validator.FORMAT_CHECKER,
    )
    for err in sorted(validator.iter_errors(doc), key=lambda e: list(e.absolute_path)):
        errors.append(f"schema {_json_path(err.absolute_path)}: {err.message}")

    declared = doc.get("fingerprint_sha256")
    if isinstance(declared, str) and _HEX64.fullmatch(declared):
        actual = fingerprint_sha256(doc)
        if actual != declared:
            errors.append(
                f"hash: fingerprint_sha256 {declared} does not match recomputed {actual}"
            )
    elif "fingerprint_sha256" in doc:
        errors.append("hash: fingerprint_sha256 is not 64 lowercase hex characters")

    summary = doc.get("collector_status_summary")
    if isinstance(summary, dict):
        for name, summary_status in summary.items():
            section = doc.get(name)
            if isinstance(section, dict):
                section_status = section.get("status")
                if section_status is not None and summary_status != section_status:
                    errors.append(
                        f"summary: {name} summary={summary_status!r} "
                        f"but section status={section_status!r}"
                    )

    for name in COLLECTOR_SECTIONS:
        if name not in doc:
            warnings.append(f"section missing: {name}")

    return errors, warnings


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="python -m analysis.validate",
        description="Validate fingerprint documents against the schema and internal consistency.",
    )
    parser.add_argument("files", nargs="+", help="fingerprint JSON file(s) to validate")
    args = parser.parse_args(argv)

    any_errors = False
    for file in args.files:
        try:
            with open(file, "r", encoding="utf-8") as fh:
                doc = json.load(fh)
        except (OSError, json.JSONDecodeError) as exc:
            print(f"{file}: IO/parse error: {exc}", file=sys.stderr)
            return 2
        if not isinstance(doc, dict):
            print(f"{file}: error: top-level JSON value must be an object")
            any_errors = True
            continue
        errors, warnings = validate_document(doc)
        if errors:
            any_errors = True
            print(f"{file}: FAIL ({len(errors)} error(s), {len(warnings)} warning(s))")
        else:
            print(f"{file}: OK ({len(warnings)} warning(s))")
        for message in errors:
            print(f"  error: {message}")
        for message in warnings:
            print(f"  warning: {message}")
    return 1 if any_errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
