"""Committed example fixtures must always pass schema validation."""

from __future__ import annotations

import json
from pathlib import Path

from analysis.validate import validate_document

EXAMPLES_DIR = Path(__file__).resolve().parent.parent / "datasets" / "examples"

example_files = sorted(EXAMPLES_DIR.glob("*.json"))


def test_examples_exist():
    assert example_files, f"no example fixtures found in {EXAMPLES_DIR}"


def test_every_example_validates():
    for path in example_files:
        doc = json.loads(path.read_text(encoding="utf-8"))
        errors, _ = validate_document(doc)
        assert errors == [], f"{path.name}: {errors}"


def test_examples_cover_multiple_environments():
    labels = set()
    for path in example_files:
        doc = json.loads(path.read_text(encoding="utf-8"))
        labels.add(doc["environment"]["label"])
    assert len(labels) >= 3, f"expected >=3 distinct labels, got {labels}"
