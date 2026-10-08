"""ConversionError boundary and actionable error diagnostics."""

import builtins
import io
from pathlib import Path
import re

import pytest

from ctosim_xlsform import ConversionError, convert_xlsform
from xform_assertions import assert_row_mentioned


def assert_error(path, *, row=None, clues=(), cause=False):
    with pytest.raises(ConversionError) as caught:
        convert_xlsform(path)
    message = str(caught.value)
    assert path.name in message
    assert message.strip() != path.name
    if row is not None:
        assert_row_mentioned(message, row)
    if clues:
        assert any(clue.casefold() in message.casefold() for clue in clues), message
    if cause:
        assert isinstance(caught.value.__cause__, BaseException)
    return caught.value


def test_nonexistent_input_raises_conversion_error(tmp_path):
    assert_error(tmp_path / "missing.xlsx")


@pytest.mark.parametrize("payload", [b"", b"not a workbook", b"<html>not Excel</html>", b"PK\x03\x04broken"])
def test_invalid_workbook_raises_conversion_error(tmp_path, payload):
    path = tmp_path / "broken.xlsx"
    path.write_bytes(payload)
    assert_error(path)


def test_directory_instead_of_workbook_raises_conversion_error(tmp_path):
    path = tmp_path / "directory.xlsx"
    path.mkdir()
    assert_error(path)


def test_read_permission_failure_is_wrapped_and_kept_as_cause(workbook_factory, monkeypatch):
    path = workbook_factory()
    failure = PermissionError("Test denies workbook read access")

    def guarded(original):
        def open_file(file, *args, **kwargs):
            if isinstance(file, (str, bytes, Path)):
                candidate = Path(file.decode() if isinstance(file, bytes) else file)
                if candidate.resolve() == path.resolve():
                    raise failure
            return original(file, *args, **kwargs)
        return open_file

    # Cover Python's standard filesystem entry points, without inspecting or
    # patching the converter or pyxform implementation.
    monkeypatch.setattr(builtins, "open", guarded(builtins.open))
    monkeypatch.setattr(io, "open", guarded(io.open))
    error = assert_error(path, cause=True)
    causes = []
    cause = error.__cause__
    while cause is not None and all(cause is not item for item in causes):
        causes.append(cause)
        cause = cause.__cause__
    assert any(item is failure for item in causes)


def test_missing_survey_sheet_is_reported(workbook_factory):
    assert_error(workbook_factory(omit_survey=True), clues=("survey",))


@pytest.mark.parametrize("headers, missing", [
    (["name", "label"], "type"), (["type", "label"], "name"),
    (["Type", "name", "label"], "type"), (["type", "Name", "label"], "name"),
])
def test_required_survey_headers_are_case_sensitive(workbook_factory, headers, missing):
    assert_error(workbook_factory(survey=[headers]), clues=(missing,))


def test_completely_empty_survey_sheet_is_reported(workbook_factory):
    assert_error(workbook_factory(survey=[]), clues=("survey", "type", "name"))


@pytest.mark.parametrize("kind", ["group", "repeat"])
def test_end_without_matching_begin_reports_its_row(workbook_factory, kind):
    path = workbook_factory(survey=[["type", "name", "label"], ["text", "q", "Q"],
                                    [f"end {kind}"]])
    assert_error(path, row=3, clues=(kind, "begin", "match", "balance"))


@pytest.mark.parametrize("kind", ["group", "repeat"])
def test_unclosed_begin_reports_its_row(workbook_factory, kind):
    path = workbook_factory(survey=[["type", "name", "label"], [f"begin {kind}", "block", "Block"],
                                    ["text", "q", "Q"]])
    assert_error(path, row=2, clues=(kind, "end", "close", "balance"))


@pytest.mark.parametrize("begin, end", [("group", "repeat"), ("repeat", "group")])
def test_wrong_kind_of_end_reports_mismatched_row(workbook_factory, begin, end):
    path = workbook_factory(survey=[["type", "name", "label"], [f"begin {begin}", "block", "Block"],
                                    ["text", "q", "Q"], [f"end {end}"]])
    error = assert_error(path, clues=("group", "repeat", "match", "balance"))
    assert re.search(r"(?<!\d)(2|4)(?!\d)", str(error))


def test_crossed_group_repeat_nesting_is_rejected(workbook_factory):
    path = workbook_factory(survey=[["type", "name", "label"], ["begin group", "outer", "Outer"],
        ["begin repeat", "inner", "Inner"], ["text", "q", "Q"], ["end group"], ["end repeat"]])
    error = assert_error(path, clues=("group", "repeat", "match", "balance"))
    assert re.search(r"(?<!\d)(2|3|5|6)(?!\d)", str(error))


@pytest.mark.parametrize("field_type", ["select_one", "select_multiple"])
@pytest.mark.parametrize("choices", [None, [["list_name", "name", "label"]],
    [["list_name", "name", "label"], ["other", "a", "A"]]])
def test_missing_choice_list_is_reported_at_select_row(workbook_factory, field_type, choices):
    path = workbook_factory(survey=[["type", "name", "label"], [],
        [f"{field_type} missing_list", "q", "Q"]], choices=choices)
    assert_error(path, row=3, clues=("missing_list", "choice", "list"))


@pytest.mark.parametrize("field_type", ["not_a_real_type", "sensor_statistic_extra", "sensor_stream_extra"])
def test_unsupported_type_is_reported_and_sensor_prefixes_require_a_whole_word(workbook_factory, field_type):
    path = workbook_factory(survey=[["type", "name", "label"], ["text", "valid", "Valid"],
                                    [field_type, "bad", "Bad"]])
    assert_error(path, row=3, clues=(field_type, "type", "unknown", "support"), cause=True)


def test_duplicate_field_names_are_reported(workbook_factory):
    path = workbook_factory(survey=[["type", "name", "label"], ["text", "duplicate", "First"],
                                    ["integer", "duplicate", "Second"]])
    # Either conflicting row identifies the problem; the spec does not select one.
    error = assert_error(path, clues=("duplicate", "name"))
    assert re.search(r"(?<!\d)[23](?!\d)", str(error))


def test_invalid_xml_field_name_is_reported_with_row(workbook_factory):
    path = workbook_factory(survey=[["type", "name", "label"], ["text", "bad name", "Bad"]])
    assert_error(path, row=2, clues=("name", "valid"))


def test_failed_conversion_does_not_modify_the_input(workbook_factory):
    path = workbook_factory(survey=[["type", "name", "label"], ["not_a_real_type", "q", "Q"]])
    before = path.read_bytes()
    assert_error(path)
    assert path.read_bytes() == before
