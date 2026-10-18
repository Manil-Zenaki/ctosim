"""Amendment 8: long row runs and validation of independently enabled rows."""

import pytest

from ctosim_xlsform import ConversionError, convert_xlsform
from xform_assertions import assert_row_mentioned
from test_converter_disabled_rows_amendment8 import (
    assert_catalog, assert_flattened_like, assert_warnings, make, row,
)


@pytest.mark.parametrize("length", [100, 136, 1000, 1001])
@pytest.mark.parametrize("kind", ["group", "repeat"])
@pytest.mark.parametrize("gap_type", ["empty", "disabled"])
def test_long_gaps_inside_disabled_container_keep_enabled_children_and_later_rows(
        workbook_factory, length, kind, gap_type):
    rows = [row("begin group", "outer"),
            row(f"begin {kind}", "removed", disabled="yes"), row("text", "first")]
    gap = ([{} for _ in range(length)] if gap_type == "empty" else [
        row("text", f"omitted_{i}", disabled="yes") for i in range(length)])
    rows += gap
    last_row = len(rows) + 2
    rows += [row("text", "last"), row(f"end {kind}"),
             row("text", "sibling"), row("end group"), row("text", "outside")]
    result = convert_xlsform(make(workbook_factory, rows))
    assert_catalog(result, [
        ("outer", "begin group", "/data/outer", 2),
        ("first", "text", "/data/outer/first", 4),
        ("last", "text", "/data/outer/last", last_row),
        ("sibling", "text", "/data/outer/sibling", last_row + 2),
        ("outside", "text", "/data/outside", last_row + 4),
    ])
    baseline = convert_xlsform(make(workbook_factory, [rows[0], rows[2],
        rows[-5], rows[-3], rows[-2], rows[-1]]))
    assert_flattened_like(result, baseline)
    warnings = [(3, "removed", None)]
    if gap_type == "disabled":
        warnings += [(5 + i, f"omitted_{i}", None) for i in range(length)]
    assert_warnings(result, warnings, optional_closers=(last_row + 1,))


@pytest.mark.parametrize("length", [100, 1000])
@pytest.mark.parametrize("kind", ["group", "repeat"])
def test_long_run_of_enabled_children_is_not_discarded_with_its_parent(
        workbook_factory, length, kind):
    children = [row("text", f"child_{i}") for i in range(length)]
    rows = [row(f"begin {kind}", "removed", disabled="yes"), *children,
            row(f"end {kind}"), row("text", "after")]
    result = convert_xlsform(make(workbook_factory, rows))
    assert_catalog(result, [
        (f"child_{i}", "text", f"/data/child_{i}", i + 3) for i in range(length)
    ] + [("after", "text", "/data/after", length + 4)])
    assert_flattened_like(result, convert_xlsform(make(workbook_factory, children + [rows[-1]])))


@pytest.mark.parametrize("length", [100, 1000])
def test_adjacent_disabled_containers_do_not_hide_each_others_enabled_children(workbook_factory, length):
    rows = [row("begin group", "outer")]
    children = []
    expected = [("outer", "begin group", "/data/outer", 2)]
    for index in range(length):
        kind = "group" if index % 2 == 0 else "repeat"
        child = row("text", f"child_{index}")
        rows += [row(f"begin {kind}", f"removed_{index}", disabled="yes"),
                 child, row(f"end {kind}")]
        children.append(child)
        expected.append((f"child_{index}", "text", f"/data/outer/child_{index}", 4 + 3 * index))
    rows += [row("end group"), row("text", "after")]
    expected.append(("after", "text", "/data/after", 3 * length + 4))
    result = convert_xlsform(make(workbook_factory, rows))
    assert_catalog(result, expected)
    baseline = convert_xlsform(make(workbook_factory, [rows[0], *children, *rows[-2:]]))
    assert_flattened_like(result, baseline)


ERRORS = [
    pytest.param(row("unrecognized_type", "bad"), ("type", "unknown", "support"), id="unsupported-type"),
    pytest.param(row("select_one missing_list", "bad"), ("missing_list", "choice", "list"), id="missing-single-list"),
    pytest.param(row("select_multiple missing_list", "bad"), ("missing_list", "choice", "list"), id="missing-multiple-list"),
    pytest.param(row("text", "bad", minimum_seconds=-1), ("minimum", "negative", "number", ">="), id="negative-minimum"),
    pytest.param(row("text", "bad", minimum_seconds="not-a-number"), ("minimum", "number", "numeric"), id="nonnumeric-minimum"),
    pytest.param(row("calculate", "bad", calculation="${absent_field} + 1"), ("absent_field", "reference", "exist"), id="missing-calculation-reference"),
    pytest.param(row("text", "bad", relevance="${absent_field} = 1"), ("absent_field", "reference", "exist"), id="missing-relevance-reference"),
]


@pytest.mark.parametrize("bad,clues", ERRORS)
@pytest.mark.parametrize("kind", ["group", "repeat"])
@pytest.mark.parametrize("gap_length", [0, 1000])
def test_errors_in_enabled_children_are_not_suppressed_by_disabled_parent(
        workbook_factory, bad, clues, kind, gap_length):
    rows = [row("text", "before"), row(f"begin {kind}", "removed", disabled="yes")]
    rows += [{} for _ in range(gap_length)]
    bad_row = len(rows) + 2
    rows += [bad, row(f"end {kind}"), row("text", "after")]
    path = make(workbook_factory, rows)
    with pytest.raises(ConversionError) as caught:
        convert_xlsform(path)
    message = str(caught.value)
    assert path.name in message
    assert_row_mentioned(message, bad_row)
    assert any(clue in message.casefold() for clue in clues), message


@pytest.mark.parametrize("kind", ["group", "repeat"])
@pytest.mark.parametrize("gap_length", [0, 1000])
def test_invalid_type_on_disabled_child_is_ignored_but_enabled_sibling_survives(
        workbook_factory, kind, gap_length):
    rows = [row(f"begin {kind}", "removed", disabled="yes")]
    rows += [{} for _ in range(gap_length)]
    rows += [row("unrecognized_type", "omitted", disabled="yes"), row("text", "kept"),
             row(f"end {kind}"), row("text", "after")]
    result = convert_xlsform(make(workbook_factory, rows))
    assert_catalog(result, [("kept", "text", "/data/kept", gap_length + 4),
                            ("after", "text", "/data/after", gap_length + 6)])
    assert_warnings(result, [(2, "removed", None), (gap_length + 3, "omitted", None)],
                    optional_closers=(gap_length + 5,))


@pytest.mark.parametrize("kind", ["group", "repeat"])
@pytest.mark.parametrize("bad_close", ["group", "repeat"])
def test_unmatched_enabled_end_after_removed_container_still_reports_its_physical_row(
        workbook_factory, kind, bad_close):
    rows = [row(f"begin {kind}", "removed", disabled="yes"),
            row("text", "kept"), row(f"end {kind}"), *[{} for _ in range(1000)],
            row(f"end {bad_close}")]
    path = make(workbook_factory, rows)
    with pytest.raises(ConversionError) as caught:
        convert_xlsform(path)
    assert path.name in str(caught.value)
    assert_row_mentioned(str(caught.value), 1005)


@pytest.mark.parametrize("kind", ["group", "repeat"])
def test_enabled_unclosed_container_after_flattening_is_still_rejected(workbook_factory, kind):
    rows = [row("begin group", "removed", disabled="yes"), row("text", "kept"),
            row("end group"), row(f"begin {kind}", "unclosed"), row("text", "child")]
    path = make(workbook_factory, rows)
    with pytest.raises(ConversionError) as caught:
        convert_xlsform(path)
    assert path.name in str(caught.value)
    assert_row_mentioned(str(caught.value), 5)
