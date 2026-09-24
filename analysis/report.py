"""Render human-readable Markdown reports for fingerprint documents.

Per document: header (label, timestamp, versions), hash verification, the
16-section status table, environment-marker highlights, and errors/warnings.
With more than one document a section-status comparison matrix is appended.

Reports are informational: validation gates belong to
``python -m analysis.validate``.

Exit codes: 0 success, 2 usage/IO problems.
"""

from __future__ import annotations

import argparse
import json
import sys
from typing import Any

from .canonical import canonical_json, fingerprint_sha256
from .validate import COLLECTOR_SECTIONS

__all__ = ["render_report", "render_comparison", "main"]


def _fmt_scalar(value: Any, limit: int = 80) -> str:
    text = canonical_json(value)
    if len(text) > limit:
        text = text[: limit - 3] + "..."
    return text


def _section_status(doc: dict[str, Any], name: str) -> str:
    section = doc.get(name)
    if isinstance(section, dict):
        status = section.get("status")
        if isinstance(status, str):
            return status
    return "missing"


def _section_note(doc: dict[str, Any], name: str) -> str:
    section = doc.get(name)
    if not isinstance(section, dict):
        return ""
    notes = []
    errors = section.get("errors") or []
    warnings = section.get("warnings") or []
    if errors:
        notes.append(f"{len(errors)} error(s)")
    if warnings:
        notes.append(f"{len(warnings)} warning(s)")
    return ", ".join(notes)


def _scalar_leaves(
    obj: Any, prefix: str = "", limit: int = 5, out: list[tuple[str, Any]] | None = None
) -> list[tuple[str, Any]]:
    if out is None:
        out = []
    if isinstance(obj, dict):
        for key, value in obj.items():
            if len(out) >= limit:
                break
            sub = f"{prefix}.{key}" if prefix else str(key)
            _scalar_leaves(value, sub, limit, out)
    elif isinstance(obj, list):
        return out
    elif len(out) < limit:
        out.append((prefix, obj))
    return out


def _normalized(doc: dict[str, Any], section: str) -> dict[str, Any]:
    node = doc.get(section)
    if isinstance(node, dict):
        data = node.get("data")
        if isinstance(data, dict):
            normalized = data.get("normalized")
            if isinstance(normalized, dict):
                return normalized
    return {}


def _highlights(doc: dict[str, Any]) -> list[str]:
    lines: list[str] = []

    markers = _normalized(doc, "environment_signals").get("markers")
    if isinstance(markers, dict):
        present = [name for name, value in markers.items() if value is True]
        if present:
            lines.append(f"- **Environment markers detected**: {', '.join(present)}")
        else:
            lines.append("- Environment markers: none detected")

    abis = _normalized(doc, "abi").get("supported_abis")
    if isinstance(abis, list) and abis:
        lines.append(f"- **Supported ABIs**: {', '.join(str(v) for v in abis)}")

    for section in ("build", "cpu", "gpu"):
        if _section_status(doc, section) != "available":
            continue
        leaves = _scalar_leaves(_normalized(doc, section), limit=5)
        for path, value in leaves:
            lines.append(f"- {section}.{path} = {_fmt_scalar(value)}")
    return lines


def _problems(doc: dict[str, Any]) -> list[str]:
    lines: list[str] = []
    for name in COLLECTOR_SECTIONS:
        section = doc.get(name)
        if not isinstance(section, dict):
            continue
        for kind in ("errors", "warnings"):
            for message in (section.get(kind) or [])[:3]:
                lines.append(f"- {name}: {kind[:-1]}: {message}")
    return lines[:12]


def render_report(doc: dict[str, Any]) -> str:
    """Markdown report for one document."""
    environment = doc.get("environment") if isinstance(doc.get("environment"), dict) else {}
    label = environment.get("label", "(unlabeled)")

    lines = [f"# Fingerprint: {label}", ""]
    lines.append(f"- timestamp: `{doc.get('timestamp', 'missing')}`")
    lines.append(f"- schema_version: `{doc.get('schema_version', 'missing')}`")
    lines.append(f"- collector_version: `{doc.get('collector_version', 'missing')}`")

    declared = doc.get("fingerprint_sha256")
    if isinstance(declared, str) and declared == fingerprint_sha256(doc):
        lines.append(f"- fingerprint_sha256: `{declared}` (verified)")
    else:
        lines.append(f"- fingerprint_sha256: `{declared}` **MISMATCH**")

    lines += ["", "## Sections", "", "| Section | Status | Notes |", "|---|---|---|"]
    for name in COLLECTOR_SECTIONS:
        lines.append(f"| {name} | {_section_status(doc, name)} | {_section_note(doc, name)} |")

    highlights = _highlights(doc)
    if highlights:
        lines += ["", "## Highlights", ""] + highlights

    problems = _problems(doc)
    if problems:
        lines += ["", "## Errors and warnings", ""] + problems

    return "\n".join(lines)


def render_comparison(docs: list[dict[str, Any]]) -> str:
    """Status matrix across documents. Labels are disambiguated if repeated."""
    labels: list[str] = []
    seen: dict[str, int] = {}
    for doc in docs:
        environment = doc.get("environment") if isinstance(doc.get("environment"), dict) else {}
        base = str(environment.get("label", "unlabeled"))
        count = seen.get(base, 0) + 1
        seen[base] = count
        labels.append(base if count == 1 else f"{base} ({count})")

    lines = ["# Comparison", "", "| Section | " + " | ".join(labels) + " |"]
    lines.append("|---" * (len(labels) + 1) + "|")
    for name in COLLECTOR_SECTIONS:
        cells = [_section_status(doc, name) for doc in docs]
        lines.append(f"| {name} | " + " | ".join(cells) + " |")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="python -m analysis.report",
        description="Render Markdown reports for fingerprint document(s).",
    )
    parser.add_argument("files", nargs="+", help="fingerprint JSON file(s)")
    parser.add_argument("-o", "--output", help="output path (default: stdout)")
    args = parser.parse_args(argv)

    docs: list[dict[str, Any]] = []
    for name in args.files:
        try:
            with open(name, "r", encoding="utf-8") as fh:
                doc = json.load(fh)
        except (OSError, json.JSONDecodeError) as exc:
            print(f"error: {name}: {exc}", file=sys.stderr)
            return 2
        if not isinstance(doc, dict):
            print(f"error: {name}: top-level JSON value must be an object", file=sys.stderr)
            return 2
        docs.append(doc)

    sections = [render_report(doc) for doc in docs]
    if len(docs) > 1:
        sections.append(render_comparison(docs))
    text = "\n\n---\n\n".join(sections) + "\n"

    if args.output:
        try:
            with open(args.output, "w", encoding="utf-8") as fh:
                fh.write(text)
        except OSError as exc:
            print(f"error: {exc}", file=sys.stderr)
            return 2
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
