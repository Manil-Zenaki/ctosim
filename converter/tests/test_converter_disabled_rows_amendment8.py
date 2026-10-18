"""Amendment 8: disabled containers disappear, their enabled children do not.

All expectations come from the spec and public conversion results. Workbooks
are generated in pytest temporary directories; no implementation is inspected.
"""

import re
from dataclasses import replace
from types import SimpleNamespace

import pytest

from ctosim_xlsform import FieldInfo, convert_xlsform
from xform_assertions import (
    assert_row_mentioned, bind_for, instance_paths,
)
from test_converter_output import (
    assert_catalog as assert_written_catalog, assert_xml_written, run_cli,
)


CTOSIM = "{urn:ctosim:xforms}"
SETTINGS = [["form_id", "form_title"], ["rowwise", "Row-wise disabling"]]
CHOICES = [["list_name", "name", "label"], ["options", "1", "One"]]


def row(kind, name=None, **columns):
    return {"type": kind, "name": name, "label": name, **columns}


def make(factory, rows):
    headers = list(dict.fromkeys(["type", "name", "label", "disabled"] + [
        key for item in rows for key in item]))
    return factory(
        survey=[headers] + [[item.get(key) for key in headers] for item in rows],
        settings=SETTINGS, choices=CHOICES,
    )


def assert_catalog(result, expected):
    assert result.fields == tuple(FieldInfo(*entry) for entry in expected)
    paths = instance_paths(result)
    assert all(field.xpath in paths for field in result.fields)


def assert_flattened_like(result, baseline):
    """The spec explicitly says 'as if' the disabled boundaries were absent."""
    assert result.xform == baseline.xform
    assert [replace(field, row=0) for field in result.fields] == [
        replace(field, row=0) for field in baseline.fields]


def assert_warnings(result, expected, *, optional_closers=()):
    """Check once-per-row and order without deciding automatic-close warnings.

    expected is (physical row, name, optional original converted type).
    See question 45: a matching end may also be reported as an omitted row.
    """
    messages = []
    previous_row = 0
    patterns = [(number, re.compile(rf"(?<![\w.-]){re.escape(name)}(?![\w.-])"),
                 re.compile(rf"(?<!\d){number}(?!\d)"))
                for number, name, _ in expected]
    for message in result.warnings:
        candidates = []
        for number, name_pattern, row_pattern in patterns:
            # Match the whole identifier, without prescribing warning quoting.
            if name_pattern.search(message) and row_pattern.search(name_pattern.sub("", message)):
                candidates.append(number)
        candidates += [number for number in optional_closers if re.search(
            rf"(?i)\brow\s+{number}\b", message)]
        assert len(candidates) == 1, message
        number = candidates[0]
        assert number > previous_row, result.warnings
        previous_row = number
        if number not in optional_closers:
            messages.append(message)
    assert len(messages) == len(expected), result.warnings
    for message, (number, name, original_type) in zip(messages, expected):
        assert name in message
        assert_row_mentioned(message.replace(name, ""), number)
        if original_type:
            assert original_type in message


@pytest.mark.parametrize("quote", ["'", '"', ""])
def test_warning_helper_distinguishes_complete_names_from_numeric_suffixes(quote):
    result = SimpleNamespace(warnings=tuple(
        f"row {number}: disabled field {quote}{name}{quote} is left out"
        for number, name in [(6, "omitted_1"), (21, "omitted_16")]))
    assert_warnings(result, [(6, "omitted_1", None), (21, "omitted_16", None)])


@pytest.mark.parametrize("messages", [
    ("row 6: disabled field 'omitted_16' is left out",),
    ("row 21: disabled field 'omitted_1' is left out",),
    (),
    ("row 6: disabled field 'omitted_1' is left out",) * 2,
])
def test_warning_helper_still_rejects_wrong_name_row_missing_or_duplicate(messages):
    with pytest.raises(AssertionError):
        assert_warnings(SimpleNamespace(warnings=messages), [(6, "omitted_1", None)])


@pytest.mark.parametrize("kind", ["group", "repeat"])
@pytest.mark.parametrize("parent_flag", ["yes", "YES", " YeS \t"])
@pytest.mark.parametrize("child_flag", [None, "", "no", " NO ", "true", 1])
def test_enabled_text_and_calculate_survive_disabled_container(
        workbook_factory, kind, parent_flag, child_flag):
    rows = [row("text", "before"),
            row(f"begin {kind}", "removed", disabled=parent_flag),
            row("text", "answer", disabled=child_flag),
            row("calculate", "computed", disabled=child_flag, calculation="1 + 2"),
            row("text", "omitted", disabled="yes"), row(f"end {kind}"),
            row("text", "after")]
    result = convert_xlsform(make(workbook_factory, rows))
    assert_catalog(result, [
        ("before", "text", "/data/before", 2),
        ("answer", "text", "/data/answer", 4),
        ("computed", "calculate", "/data/computed", 5),
        ("after", "text", "/data/after", 8),
    ])
    baseline = convert_xlsform(make(workbook_factory, [rows[i] for i in (0, 2, 3, 6)]))
    assert_flattened_like(result, baseline)
    assert_warnings(result, [(3, "removed", None), (6, "omitted", None)],
                    optional_closers=(7,))


@pytest.mark.parametrize("kind", ["group", "repeat"])
@pytest.mark.parametrize("end_flag", [None, "no", "yes", " YeS ", "unexpected"])
def test_matching_end_is_removed_regardless_of_its_disabled_cell(
        workbook_factory, kind, end_flag):
    rows = [row("begin group", "outer"),
            row(f"begin {kind}", "removed", disabled="yes"),
            row("text", "child"), row(f"end {kind}", disabled=end_flag),
            row("text", "sibling"), row("end group"), row("text", "outside")]
    result = convert_xlsform(make(workbook_factory, rows))
    assert_catalog(result, [
        ("outer", "begin group", "/data/outer", 2),
        ("child", "text", "/data/outer/child", 4),
        ("sibling", "text", "/data/outer/sibling", 6),
        ("outside", "text", "/data/outside", 8),
    ])
    baseline = convert_xlsform(make(workbook_factory, [rows[i] for i in (0, 2, 4, 5, 6)]))
    assert_flattened_like(result, baseline)


@pytest.mark.parametrize("outer", ["group", "repeat"])
@pytest.mark.parametrize("removed", ["group", "repeat"])
@pytest.mark.parametrize("inner", ["group", "repeat"])
@pytest.mark.parametrize("inner_disabled", [False, True])
def test_nested_boundaries_match_by_nesting_and_keep_nearest_enabled_scope(
        workbook_factory, outer, removed, inner, inner_disabled):
    rows = [row(f"begin {outer}", "outer"),
            row(f"begin {removed}", "removed", disabled="yes"),
            row("text", "before_inner"),
            row(f"begin {inner}", "inner", disabled="yes" if inner_disabled else "no"),
            row("text", "inside"), row(f"end {inner}"),
            row("text", "after_inner"), row(f"end {removed}"),
            row("text", "after_removed"), row(f"end {outer}"),
            row("text", "outside")]
    result = convert_xlsform(make(workbook_factory, rows))
    expected = [("outer", f"begin {outer}", "/data/outer", 2),
                ("before_inner", "text", "/data/outer/before_inner", 4)]
    if not inner_disabled:
        expected.append(("inner", f"begin {inner}", "/data/outer/inner", 5))
    inside = "/data/outer/inside" if inner_disabled else "/data/outer/inner/inside"
    expected += [("inside", "text", inside, 6),
                 ("after_inner", "text", "/data/outer/after_inner", 8),
                 ("after_removed", "text", "/data/outer/after_removed", 10),
                 ("outside", "text", "/data/outside", 12)]
    assert_catalog(result, expected)
    discarded = {1, 7} | ({3, 5} if inner_disabled else set())
    baseline = convert_xlsform(make(workbook_factory, [
        item for index, item in enumerate(rows) if index not in discarded]))
    assert_flattened_like(result, baseline)


@pytest.mark.parametrize("kind", ["group", "repeat"])
@pytest.mark.parametrize("contents", [[], [row("text", "omitted", disabled="yes")]])
def test_empty_or_fully_disabled_container_does_not_consume_following_sibling(
        workbook_factory, kind, contents):
    rows = [row(f"begin {kind}", "removed", disabled="yes"), *contents,
            row(f"end {kind}"), row("text", "after")]
    result = convert_xlsform(make(workbook_factory, rows))
    assert_catalog(result, [("after", "text", "/data/after", len(rows) + 1)])
    assert_flattened_like(result, convert_xlsform(make(workbook_factory, [rows[-1]])))


@pytest.mark.parametrize("kind,column,value", [
    (kind, column, value) for kind in ("group", "repeat") for column, value in [
    ("label", "Discarded café 東京 ‘label’"),
    ("relevance", "false()"), ("appearance", "field-list"),
    ("minimum_seconds", 10), ("repeat_count", "2 + 1"),
    ] if kind == "repeat" or column != "repeat_count"])
def test_disabled_container_properties_are_not_inherited_or_emitted(
        workbook_factory, kind, column, value):
    opener = row(f"begin {kind}", "removed", disabled="yes")
    opener[column] = value
    child = row("text", "child")
    result = convert_xlsform(make(workbook_factory, [opener, child, row(f"end {kind}")]))
    baseline = convert_xlsform(make(workbook_factory, [child]))
    assert_flattened_like(result, baseline)
    assert "/data/removed_count" not in instance_paths(result)


@pytest.mark.parametrize("kind", ["group", "repeat"])
def test_removed_container_text_and_relevance_references_are_not_validated(
        workbook_factory, kind):
    opener = row(f"begin {kind}", "removed", disabled="yes",
                 label="${nonexistent_label}", relevance="${nonexistent_condition} = 1")
    result = convert_xlsform(make(workbook_factory, [
        opener, row("text", "child"), row(f"end {kind}")]))
    assert_flattened_like(result, convert_xlsform(make(workbook_factory, [row("text", "child")])))


def test_removed_repeat_count_does_not_require_its_referenced_field(workbook_factory):
    child = row("text", "child")
    result = convert_xlsform(make(workbook_factory, [
        row("begin repeat", "removed", disabled="yes", repeat_count="${missing_size} + 1"),
        child, row("end repeat")]))
    assert_flattened_like(result, convert_xlsform(make(workbook_factory, [child])))


@pytest.mark.parametrize("kind", ["group", "repeat"])
def test_disabled_repeat_settings_do_not_remove_enabled_nested_repeat_settings(
        workbook_factory, kind):
    rows = [row(f"begin {kind}", "removed", disabled="yes", repeat_count="9 + 9"),
            row("begin repeat", "kept", repeat_count="2 + 1"),
            row("text", "child"), row("end repeat"), row(f"end {kind}"),
            row("text", "outside")]
    if kind == "group":
        del rows[0]["repeat_count"]
    result = convert_xlsform(make(workbook_factory, rows))
    baseline = convert_xlsform(make(workbook_factory, rows[1:4] + rows[5:]))
    assert_flattened_like(result, baseline)
    assert "/data/kept_count" in instance_paths(result)
    assert [field.name for field in result.fields] == ["kept", "child", "outside"]


SURVIVING_TYPES = [
    "text", "integer", "decimal", "note", "start", "end", "deviceid", "calculate",
    "calculate_here", "enumerator", "caseid", "comments", "text audit", "audio audit",
    "speed violations count", "speed violations list", "speed violations audit",
    "sensor_statistic accelerometer mean", "sensor_stream accelerometer",
    "select_one options", "select_multiple options",
]


@pytest.mark.parametrize("field_type", SURVIVING_TYPES)
@pytest.mark.parametrize("kind", ["group", "repeat"])
def test_row_by_row_rule_preserves_every_node_producing_child_type(
        workbook_factory, field_type, kind):
    child = row(field_type, "child", disabled="no")
    if field_type in ("calculate", "calculate_here"):
        child["calculation"] = "1 + 2"
    rows = [row(f"begin {kind}", "removed", disabled="yes"), child,
            row(f"end {kind}"), row("text", "after")]
    result = convert_xlsform(make(workbook_factory, rows))
    assert_catalog(result, [("child", field_type, "/data/child", 3),
                            ("after", "text", "/data/after", 5)])
    baseline = convert_xlsform(make(workbook_factory, [child, rows[-1]]))
    assert_flattened_like(result, baseline)


@pytest.mark.parametrize("field_type", SURVIVING_TYPES + ["select_one options or_other",
                                                       "select_multiple options or_other"])
def test_independently_disabled_children_leave_no_node_control_or_conversion_warning(
        workbook_factory, field_type):
    rows = [row("begin group", "removed", disabled="yes"),
            row(field_type, "omitted", disabled=" YeS ", calculation="1 + 2", minimum_seconds=4),
            row("text", "kept"), row("end group")]
    result = convert_xlsform(make(workbook_factory, rows))
    baseline = convert_xlsform(make(workbook_factory, [rows[2]]))
    assert_flattened_like(result, baseline)
    assert_warnings(result, [(2, "removed", None), (3, "omitted", None)], optional_closers=(5,))


@pytest.mark.parametrize("scope", [None, "group", "repeat"])
def test_retained_expressions_and_custom_binds_use_flattened_paths(workbook_factory, scope):
    rows = [row("select_multiple options", "answers"),
            row("begin group", "removed", disabled="yes", relevance="false()"),
            row("integer", "amount", minimum_seconds=2.5),
            row("calculate", "total", calculation="${amount} + 1"),
            row("calculate_here", "checkpoint", calculation="${amount} + 1",
                relevance="selected(${answers}, 1)", minimum_seconds=0),
            row("speed violations count", "violations"), row("end group"),
            row("calculate", "later", calculation="${amount} + ${total}")]
    baseline_rows = [item for index, item in enumerate(rows) if index not in (1, 6)]
    prefix = ""
    if scope:
        boundary = row(f"begin {scope}", "outer", relevance="true()")
        rows = [boundary, *rows, row(f"end {scope}")]
        baseline_rows = [boundary, *baseline_rows, row(f"end {scope}")]
        prefix = "outer/"
    result = convert_xlsform(make(workbook_factory, rows))
    baseline = convert_xlsform(make(workbook_factory, baseline_rows))
    assert_flattened_like(result, baseline)
    assert bind_for(result, prefix + "amount").get(CTOSIM + "minimum-seconds") == "2.5"
    checkpoint = bind_for(result, prefix + "checkpoint")
    assert checkpoint.get(CTOSIM + "calculate-here") == bind_for(result, prefix + "total").get("calculate")
    assert checkpoint.get(CTOSIM + "minimum-seconds") == "0"
    assert bind_for(result, prefix + "violations").get(CTOSIM + "speed-violations") == "count"


@pytest.mark.parametrize("select_type", ["select_one", "select_multiple"])
@pytest.mark.parametrize("kind", ["group", "repeat"])
def test_surviving_or_other_companion_uses_flattened_scope_and_source_row(
        workbook_factory, select_type, kind):
    rows = [row("begin group", "outer"),
            row(f"begin {kind}", "removed", disabled="yes"),
            row(f"{select_type} options or_other", "answer"),
            row(f"end {kind}"), row("text", "after"), row("end group")]
    result = convert_xlsform(make(workbook_factory, rows))
    assert_catalog(result, [
        ("outer", "begin group", "/data/outer", 2),
        ("answer", f"{select_type} options or_other", "/data/outer/answer", 4),
        ("answer_other", "text", "/data/outer/answer_other", 4),
        ("after", "text", "/data/outer/after", 6),
    ])
    baseline = convert_xlsform(make(workbook_factory, [rows[i] for i in (0, 2, 4, 5)]))
    assert_flattened_like(result, baseline)


def test_translated_children_keep_labels_hints_and_media_after_parent_is_removed(workbook_factory):
    child = row("text", "child", label="English", **{
        "label:French": "Étiquette ‘gardée’", "hint:French": "Indice conservé",
        "media:image": "kept.png", "minimum_seconds": 2})
    rows = [row("begin group", "removed", disabled="yes", **{
        "label:French": "Parent supprimé"}), child, row("end group")]
    result = convert_xlsform(make(workbook_factory, rows))
    baseline = convert_xlsform(make(workbook_factory, [child]))
    assert_flattened_like(result, baseline)
    assert "Étiquette ‘gardée’" in result.xform
    assert "Indice conservé" in result.xform
    assert "kept.png" in result.xform
    assert "Parent supprimé" not in result.xform


@pytest.mark.parametrize("kind", ["group", "repeat"])
def test_each_disabled_opener_uses_the_amendment_warning_text(workbook_factory, kind):
    result = convert_xlsform(make(workbook_factory, [
        row(f"begin {kind}", "removed", disabled="yes"),
        row("text", "kept"), row(f"end {kind}")]))
    expected = (f"row 2: disabled {kind} 'removed' is left out; "
                "the rows it holds are kept unless they are disabled themselves")
    assert result.warnings.count(expected) == 1


def test_nested_omission_and_surviving_conversion_warnings_follow_physical_rows(workbook_factory):
    rows = [row("caseid", "first_case"), row("begin group", "outer", disabled="yes"),
            row("enumerator", "worker"), row("text", "omitted_question", disabled="yes"),
            row("begin repeat", "inner", disabled="yes"),
            row("speed violations list", "speed_list"),
            row("calculate_here", "omitted_here", disabled="yes", calculation="1"),
            row("end repeat"), row("end group"), row("calculate_here", "checkpoint", calculation="1")]
    result = convert_xlsform(make(workbook_factory, rows))
    assert_warnings(result, [
        (2, "first_case", "caseid"), (3, "outer", None), (4, "worker", "enumerator"),
        (5, "omitted_question", None), (6, "inner", None),
        (7, "speed_list", "speed violations list"), (8, "omitted_here", None),
        (11, "checkpoint", "calculate_here"),
    ], optional_closers=(9, 10))


@pytest.mark.parametrize("parent_cache,child_cache,expected_path", [
    ("yes", "no", "/data/child"), ("no", "no", "/data/parent/child"),
    (None, "no", "/data/parent/child"), ("yes", None, "/data/child"),
    ("yes", "yes", None),
])
def test_cached_disabled_flags_are_evaluated_independently_per_row(
        workbook_factory, set_formula_cache, parent_cache, child_cache, expected_path):
    path = make(workbook_factory, [row("begin group", "parent", disabled='="yes"'),
        row("text", "child", disabled='="yes"'), row("end group"), row("text", "after")])
    # make() fixes disabled as column D; these are physical worksheet rows.
    if parent_cache is not None:
        set_formula_cache(path, 1, "D2", parent_cache)
    if child_cache is not None:
        set_formula_cache(path, 1, "D3", child_cache)
    result = convert_xlsform(path)
    children = [field for field in result.fields if field.name == "child"]
    assert children == ([] if expected_path is None else [FieldInfo("child", "text", expected_path, 3)])
    assert result.fields[-1] == FieldInfo("after", "text", "/data/after", 5)


@pytest.mark.parametrize("kind", ["group", "repeat"])
@pytest.mark.parametrize("raw_type", ["begin {kind}", "  begin\t {kind}  "])
def test_type_whitespace_normalization_precedes_disabled_container_matching(
        workbook_factory, kind, raw_type):
    result = convert_xlsform(make(workbook_factory, [
        row(raw_type.format(kind=kind), "removed", disabled="yes"),
        row("text", "kept"), row(f"  end\t{kind}  "), row("text", "after")]))
    assert_catalog(result, [("kept", "text", "/data/kept", 3),
                            ("after", "text", "/data/after", 5)])


@pytest.mark.parametrize("kind", ["group", "repeat"])
def test_flattening_is_deterministic_and_never_modifies_the_workbook(workbook_factory, kind):
    path = make(workbook_factory, [row(f"begin {kind}", "removed", disabled="yes"),
        row("text", "kept", label="Café 東京"), row("caseid", "case"),
        row("text", "omitted", disabled="yes"), row(f"end {kind}"), row("text", "after")])
    before = path.read_bytes()
    first = convert_xlsform(path)
    assert convert_xlsform(path) == first
    assert path.read_bytes() == before
    assert [field.name for field in first.fields] == ["kept", "case", "after"]


@pytest.mark.parametrize("output_method", ["write", "cli"])
def test_written_xml_catalog_and_cli_warnings_reflect_independent_disabled_rows(
        workbook_factory, tmp_path, output_method):
    source = make(workbook_factory, [row("begin repeat", "removed", disabled="yes"),
        row("text", "kept"), row("text", "omitted", disabled="yes"),
        row("caseid", "case"), row("end repeat"), row("text", "after")])
    result = convert_xlsform(source)
    assert_catalog(result, [("kept", "text", "/data/kept", 3),
                            ("case", "caseid", "/data/case", 5),
                            ("after", "text", "/data/after", 7)])
    destination = tmp_path / "flattened.xml"
    if output_method == "write":
        result.write(destination)
    else:
        status, _, stderr = run_cli(source, "-o", destination)
        assert status == 0, stderr
        assert stderr.splitlines() == list(result.warnings)
    assert_xml_written(destination, result)
    assert_written_catalog(destination.with_suffix(".fields.json"), result)
