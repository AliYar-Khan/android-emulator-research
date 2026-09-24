"""Tests for analysis.diff."""

from __future__ import annotations

import copy
import json

from analysis.diff import _MISSING, diff_documents, main
from tests.factories import make_document, make_section


def test_identical_documents_have_no_differences():
    a = make_document()
    b = make_document()
    assert diff_documents(a, b) == []


def test_value_change_is_reported_with_path():
    a = make_document()
    b = copy.deepcopy(a)
    b["cpu"]["status"] = "collection_error"
    diffs = diff_documents(a, b)
    assert len(diffs) == 1
    assert diffs[0].path == "cpu.status"
    assert diffs[0].format() == 'cpu.status: "available" -> "collection_error"'


def test_volatile_timestamps_are_ignored_by_default():
    a = make_document()
    b = copy.deepcopy(a)
    b["timestamp"] = "2027-06-06T06:06:06Z"
    b["cpu"]["timestamp"] = "2027-06-06T06:06:06Z"
    assert diff_documents(a, b) == []


def test_keep_volatile_includes_timestamps():
    a = make_document()
    b = copy.deepcopy(a)
    b["timestamp"] = "2027-06-06T06:06:06Z"
    diffs = diff_documents(a, b, keep_volatile=True)
    assert [d.path for d in diffs] == ["timestamp"]


def test_missing_and_added_keys():
    a = make_document()
    b = copy.deepcopy(a)
    del b["gpu"]["data"]["raw"]
    b["gpu"]["data"]["new_key"] = 1
    diffs = {d.path: d for d in diff_documents(a, b)}
    assert diffs["gpu.data.raw"].a == {}
    assert diffs["gpu.data.raw"].b is _MISSING
    assert diffs["gpu.data.new_key"].a is _MISSING
    assert diffs["gpu.data.new_key"].b == 1


def test_bool_true_differs_from_integer_one():
    a = {"flag": True}
    b = {"flag": 1}
    diffs = diff_documents(a, b)
    assert len(diffs) == 1


def test_list_element_and_length_differences():
    a = {"items": [1, 2, 3]}
    b = {"items": [1, 9]}
    diffs = diff_documents(a, b)
    assert {d.path for d in diffs} == {"items[1]", "items[2]"}


def test_status_summary_change_groups_under_summary():
    a = make_document()
    b = copy.deepcopy(a)
    b["collector_status_summary"]["telephony"] = "unavailable"
    diffs = diff_documents(a, b)
    assert diffs and diffs[0].path == "collector_status_summary.telephony"


def test_cli_exit_codes_and_grouping(tmp_path, capsys):
    a = make_document()
    b = copy.deepcopy(a)
    b["build"]["status"] = "collection_error"
    b["vulkan"]["status"] = "unavailable"

    file_a = tmp_path / "a.json"
    file_b = tmp_path / "b.json"
    file_a.write_text(json.dumps(a), encoding="utf-8")
    file_b.write_text(json.dumps(b), encoding="utf-8")

    assert main([str(file_a), str(file_b)]) == 1
    out = capsys.readouterr().out
    assert "build: 1 difference(s)" in out
    assert "vulkan: 1 difference(s)" in out
    assert "Total: 2 difference(s) in 2 section(s)" in out

    assert main([str(file_a), str(file_a)]) == 0
    assert "No differences." in capsys.readouterr().out

    assert main([str(tmp_path / "missing.json"), str(file_b)]) == 2
