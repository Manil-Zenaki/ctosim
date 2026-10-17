"""Spec 02, Amendment 4: skipped rows must never terminate sheet traversal."""

from dataclasses import replace

import pytest

from ctosim_xlsform import ConversionError, FieldInfo, convert_xlsform
from xform_assertions import assert_choice_list, assert_row_mentioned, instance_paths


HEADERS = ["type", "name", "label", "disabled", "minimum_seconds"]
SETTINGS = [["form_id", "form_title", "version"], ["long_runs", "Long runs", "1"]]


def _gap(kind, length):
    """Return exactly length rows and (zero-based offset, name) omissions.

    Expectations are attached when constructing the fixture, rather than
    inferred by parsing its nesting or disabled flags.
    """
    if kind == "empty":
        return [[None] * len(HEADERS) for _ in range(length)], []
    if kind == "disabled":
        return (
            [["text", f"omitted_{i}", "Omitted", " YeS "] for i in range(length)],
            [(i, f"omitted_{i}") for i in range(length)],
        )
    if kind in ("group", "repeat"):
        assert length >= 2
        rows = [[f"begin {kind}", "omitted_block", "Omitted", "yes"]]
        rows += [["text", f"hidden_{i}", "Hidden"] for i in range(length - 2)]
        rows += [[f"end {kind}"]]
        return rows, [(0, "omitted_block")]
    assert kind == "mixed"
    # Each five-row unit mixes empty rows, a disabled question and a block
    # whose child is enabled but is still omitted with its parent.
    rows, omissions = [], []
    while len(rows) + 5 <= length:
        offset = len(rows)
        question, block = f"omitted_{offset}", f"block_{offset}"
        rows += [[], ["text", question, "Omitted", "yes"],
                 ["begin group", block, "Omitted", "yes"],
                 ["text", f"hidden_{offset}", "Hidden"], ["end group"]]
        omissions += [(offset + 1, question), (offset + 2, block)]
    rows += [[] for _ in range(length - len(rows))]
    return rows, omissions


def _assert_omission_warnings(result, omissions, first_row):
    assert len(result.warnings) == len(omissions)
    for message, (offset, name) in zip(result.warnings, omissions):
        assert name in message
        # Digits in generated names must not satisfy the row-number check.
        assert_row_mentioned(message.replace(name, ""), first_row + offset)


def _assert_same_form(actual, baseline):
    assert (actual.form_id, actual.title, actual.version) == (
        baseline.form_id, baseline.title, baseline.version)
    assert actual.xform == baseline.xform
    assert instance_paths(actual) == instance_paths(baseline)


@pytest.mark.parametrize("length", [1, 99, 100, 101, 999, 1000, 1001])
@pytest.mark.parametrize("kind", ["empty", "disabled"])
@pytest.mark.parametrize("position", ["leading", "between", "trailing"])
def test_long_row_runs_anywhere_preserve_the_complete_form(
        workbook_factory, length, kind, position):
    rows = [HEADERS, ["text", "first", "First"], ["integer", "last", "Last"]]
    baseline = convert_xlsform(workbook_factory(survey=rows, settings=SETTINGS))
    insertion = {"leading": 1, "between": 2, "trailing": 3}[position]
    gap, omissions = _gap(kind, length)
    result = convert_xlsform(workbook_factory(
        survey=rows[:insertion] + gap + rows[insertion:], settings=SETTINGS))

    _assert_same_form(result, baseline)
    expected = [FieldInfo("first", "text", "/data/first", 2),
                FieldInfo("last", "integer", "/data/last", 3)]
    assert result.fields == tuple(
        replace(field, row=field.row + length) if field.row > insertion else field
        for field in expected)
    _assert_omission_warnings(result, omissions, insertion + 1)


@pytest.mark.parametrize("length", [100, 136, 1000])
@pytest.mark.parametrize("kind", ["group", "repeat", "mixed"])
@pytest.mark.parametrize("position", ["leading", "between", "trailing"])
def test_long_omitted_blocks_anywhere_leave_later_fields_intact(
        workbook_factory, length, kind, position):
    rows = [HEADERS, ["text", "first", "First"], ["integer", "last", "Last"]]
    baseline = convert_xlsform(workbook_factory(survey=rows, settings=SETTINGS))
    insertion = {"leading": 1, "between": 2, "trailing": 3}[position]
    gap, omissions = _gap(kind, length)
    result = convert_xlsform(workbook_factory(
        survey=rows[:insertion] + gap + rows[insertion:], settings=SETTINGS))

    _assert_same_form(result, baseline)
    assert result.fields == tuple(
        replace(field, row=field.row + length) if field.row > insertion else field
        for field in (FieldInfo("first", "text", "/data/first", 2),
                      FieldInfo("last", "integer", "/data/last", 3)))
    _assert_omission_warnings(result, omissions, insertion + 1)


@pytest.mark.parametrize("length", [100, 1000])
@pytest.mark.parametrize("kind", ["empty", "disabled", "group", "repeat", "mixed"])
@pytest.mark.parametrize("insertion", [2, 3, 4], ids=["before-child", "before-close", "after-close"])
@pytest.mark.parametrize("container", ["group", "repeat"])
def test_long_runs_preserve_enclosing_container_and_sibling_paths(
        workbook_factory, length, kind, insertion, container):
    rows = [HEADERS, [f"begin {container}", "outer", "Outer"],
            ["text", "child", "Child"], [f"end {container}"],
            ["integer", "after", "After"]]
    baseline = convert_xlsform(workbook_factory(survey=rows, settings=SETTINGS))
    gap, omissions = _gap(kind, length)
    result = convert_xlsform(workbook_factory(
        survey=rows[:insertion] + gap + rows[insertion:], settings=SETTINGS))

    _assert_same_form(result, baseline)
    expected = [FieldInfo("outer", f"begin {container}", "/data/outer", 2),
                FieldInfo("child", "text", "/data/outer/child", 3),
                FieldInfo("after", "integer", "/data/after", 5)]
    assert result.fields == tuple(
        replace(field, row=field.row + length) if field.row > insertion else field
        for field in expected)
    _assert_omission_warnings(result, omissions, insertion + 1)


@pytest.mark.parametrize("kind", ["group", "repeat"])
@pytest.mark.parametrize("length", [100, 1000])
def test_long_disabled_block_skips_nested_blocks_until_its_own_closing_row(
        workbook_factory, kind, length):
    rows = [HEADERS, ["begin group", "outer", "Outer"],
            ["text", "before", "Before"],
            [f"begin {kind}", "discard", "Discard", "yes"]]
    # Both kinds are nested inside either disabled container; a nested close
    # must not prematurely resume emitting fields or conversion warnings.
    rows += [["begin group", "nested_group", "Nested", "yes"],
             ["begin repeat", "nested_repeat", "Nested"]]
    rows += [["caseid", f"hidden_{i}"] for i in range(length)]
    rows += [["end repeat"], ["end group"],
             ["enumerator", "hidden_tail", "Hidden"], [f"end {kind}"]]
    after_row = len(rows) + 1
    rows += [["text", "after", "After"], ["end group"],
             ["integer", "outside", "Outside"]]
    result = convert_xlsform(workbook_factory(survey=rows, settings=SETTINGS))
    baseline = convert_xlsform(workbook_factory(survey=[HEADERS,
        ["begin group", "outer", "Outer"], ["text", "before", "Before"],
        ["text", "after", "After"], ["end group"],
        ["integer", "outside", "Outside"]], settings=SETTINGS))

    _assert_same_form(result, baseline)
    assert result.fields == (
        FieldInfo("outer", "begin group", "/data/outer", 2),
        FieldInfo("before", "text", "/data/outer/before", 3),
        FieldInfo("after", "text", "/data/outer/after", after_row),
        FieldInfo("outside", "integer", "/data/outside", after_row + 2))
    _assert_omission_warnings(result, [(0, "discard")], 4)


def test_1448_row_survey_with_136_row_disabled_group_keeps_every_later_row(workbook_factory):
    prefix = [HEADERS, ["begin group", "outer", "Outer"],
              ["text", "before", "Before"]]
    gap, omissions = _gap("group", 136)
    # 1,448 physical rows, including the header and both outer boundaries.
    suffix = [["text", f"later_{i}", f"Later {i}"] for i in range(1307)]
    suffix += [["end group"], ["integer", "outside", "Outside"]]
    rows = prefix + gap + suffix
    assert len(rows) == 1448
    baseline = convert_xlsform(workbook_factory(survey=prefix + suffix, settings=SETTINGS))
    path = workbook_factory(survey=rows, settings=SETTINGS)
    before_bytes = path.read_bytes()
    result = convert_xlsform(path)

    _assert_same_form(result, baseline)
    expected = [FieldInfo("outer", "begin group", "/data/outer", 2),
                FieldInfo("before", "text", "/data/outer/before", 3)]
    expected += [FieldInfo(f"later_{i}", "text", f"/data/outer/later_{i}", 140 + i)
                 for i in range(1307)]
    expected += [FieldInfo("outside", "integer", "/data/outside", 1448)]
    assert result.fields == tuple(expected)
    _assert_omission_warnings(result, omissions, 4)
    assert convert_xlsform(path) == result
    assert path.read_bytes() == before_bytes


@pytest.mark.parametrize("length", [100, 1000])
def test_multiple_long_runs_preserve_warning_order_and_physical_rows(workbook_factory, length):
    rows = [HEADERS, ["caseid", "first_case"]]
    rows += [[] for _ in range(length)]
    first_block_row = len(rows) + 1
    block, _ = _gap("group", length)
    rows += block
    rows += [[] for _ in range(length)]
    last_field_row = len(rows) + 1
    rows += [["enumerator", "last_enum", "Enumerator"]]
    last_omission_row = len(rows) + 1
    rows += [["text", "last_omission", "Omitted", "yes"],
             ["text", "tail", "Tail"]]
    result = convert_xlsform(workbook_factory(survey=rows, settings=SETTINGS))

    assert result.fields == (
        FieldInfo("first_case", "caseid", "/data/first_case", 2),
        FieldInfo("last_enum", "enumerator", "/data/last_enum", last_field_row),
        FieldInfo("tail", "text", "/data/tail", last_omission_row + 1))
    expected = [(2, "first_case", "caseid"),
                (first_block_row, "omitted_block", None),
                (last_field_row, "last_enum", "enumerator"),
                (last_omission_row, "last_omission", None)]
    assert len(result.warnings) == len(expected)
    for message, (row, name, original_type) in zip(result.warnings, expected):
        assert name in message
        assert_row_mentioned(message, row)
        if original_type:
            assert original_type in message


@pytest.mark.parametrize("length", [100, 1000])
@pytest.mark.parametrize("kind", ["empty", "disabled", "group", "repeat", "mixed"])
@pytest.mark.parametrize("failure", ["end_group", "end_repeat", "unclosed_group",
    "unclosed_repeat", "missing_choice", "invalid_type", "invalid_minimum"])
def test_errors_after_long_runs_report_the_original_survey_row(
        workbook_factory, length, kind, failure):
    gap, _ = _gap(kind, length)
    rows = [HEADERS, ["text", "before", "Before"]] + gap
    bad_row = len(rows) + 1
    cases = {
        "end_group": (["end group"], ("group", "match", "begin", "balance")),
        "end_repeat": (["end repeat"], ("repeat", "match", "begin", "balance")),
        "unclosed_group": (["begin group", "unclosed", "Unclosed"],
                           ("group", "close", "end", "balance")),
        "unclosed_repeat": (["begin repeat", "unclosed", "Unclosed"],
                            ("repeat", "close", "end", "balance")),
        "missing_choice": (["select_one absent", "bad", "Bad"], ("absent", "choice", "list")),
        "invalid_type": (["not_a_real_type", "bad", "Bad"], ("type", "unknown", "support")),
        "invalid_minimum": (["text", "bad", "Bad", None, -1], ("minimum", "negative", "number", ">=", "nonnegative")),
    }
    bad, clues = cases[failure]
    rows += [bad]
    if failure.startswith("unclosed"):
        rows += [["text", "child", "Child"]]
    path = workbook_factory(survey=rows, settings=SETTINGS)
    with pytest.raises(ConversionError) as caught:
        convert_xlsform(path)
    message = str(caught.value)
    assert path.name in message
    assert_row_mentioned(message, bad_row)
    assert any(clue in message.casefold() for clue in clues), message
    if failure == "invalid_type":
        assert isinstance(caught.value.__cause__, BaseException)


@pytest.mark.parametrize("length", [100, 1000])
def test_unclosed_group_before_a_long_empty_tail_is_still_an_error(workbook_factory, length):
    rows = [HEADERS, ["begin group", "outer", "Outer"], ["text", "q", "Q"]]
    rows += [[] for _ in range(length)]
    path = workbook_factory(survey=rows, settings=SETTINGS)
    with pytest.raises(ConversionError) as caught:
        convert_xlsform(path)
    assert path.name in str(caught.value)
    assert_row_mentioned(str(caught.value), 2)


@pytest.mark.parametrize("length", [100, 1000])
def test_long_run_of_uncached_formula_rows_counts_as_empty(workbook_factory, length):
    rows = [HEADERS, ["text", "first", "First"]]
    # openpyxl writes formulas without a cached result, so every cell below
    # is empty under the spec's input rules despite its stored formula text.
    rows += [['="text"', '="ghost"', '="Ghost"', '="yes"', '=10']
             for _ in range(length)]
    rows += [["integer", "last", "Last"]]
    result = convert_xlsform(workbook_factory(survey=rows, settings=SETTINGS))
    baseline = convert_xlsform(workbook_factory(survey=[HEADERS,
        ["text", "first", "First"], ["integer", "last", "Last"]], settings=SETTINGS))
    _assert_same_form(result, baseline)
    assert result.fields == (FieldInfo("first", "text", "/data/first", 2),
                             FieldInfo("last", "integer", "/data/last", length + 3))
    assert result.warnings == ()


@pytest.mark.parametrize("length", [100, 1000])
@pytest.mark.parametrize("column", ["name", "value"])
def test_long_empty_choice_runs_do_not_truncate_choices_or_drop_duplicates(
        workbook_factory, length, column):
    survey = [HEADERS, ["select_multiple opts", "q", "Question"]]
    choices = [["list_name", column, "label"], ["opts", "a", "First"],
               ["opts", "a", "Second"], ["opts", "b", "Last"]]
    baseline = convert_xlsform(workbook_factory(
        survey=survey, choices=choices, settings=SETTINGS))
    gap = [[None, None, None] for _ in range(length)]
    padded = choices[:1] + gap + choices[1:2] + gap + choices[2:] + gap
    result = convert_xlsform(workbook_factory(
        survey=survey, choices=padded, settings=SETTINGS))
    _assert_same_form(result, baseline)
    assert result.fields == baseline.fields
    assert result.warnings == baseline.warnings
    assert_choice_list(result, "q", "select", "opts",
                       [("a", "First"), ("a", "Second"), ("b", "Last")])
