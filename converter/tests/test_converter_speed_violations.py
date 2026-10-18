"""Spec 02 Amendment 3, tested solely through public results and XML.

All workbooks are small temporary fixtures built by workbook_factory.
"""

from io import StringIO
import xml.etree.ElementTree as ET

import pytest

from ctosim_xlsform import ConversionError, FieldInfo, convert_xlsform
from test_converter_disabled_rows_amendment8 import assert_warnings
from xform_assertions import (
    assert_row_mentioned, bind_for, elements, instance_paths, local_name,
    primary_root,
)


NAMESPACE = "urn:ctosim:xforms"
MINIMUM = f"{{{NAMESPACE}}}minimum-seconds"
SPEED = f"{{{NAMESPACE}}}speed-violations"
HERE = f"{{{NAMESPACE}}}calculate-here"


def root_namespaces(xml):
    namespaces = {}
    for event, value in ET.iterparse(StringIO(xml), events=("start-ns", "start")):
        if event == "start":
            break
        prefix, uri = value
        namespaces[prefix] = uri
    return namespaces


def custom_attributes(result):
    return [(node, key, value) for node in ET.fromstring(result.xform).iter()
            for key, value in node.attrib.items()
            if key.startswith(f"{{{NAMESPACE}}}")]


def minimum_form(workbook_factory, value, *, header="minimum_seconds"):
    return workbook_factory(survey=[
        ["type", "name", "label", header],
        ["text", "timed", "Timed question", value],
        ["text", "untimed", "Untimed question"],
    ])


@pytest.mark.parametrize("value, expected", [
    (0, "0"), (0.0, "0"), (1, "1"), (10.0, "10"),
    (2.5, "2.5"), (0.125, "0.125"),
    ("0", "0"), ("10", "10"), ("2.5", "2.5"),
])
def test_minimum_seconds_is_a_namespaced_bind_attribute(workbook_factory, value, expected):
    result = convert_xlsform(minimum_form(workbook_factory, value))
    assert bind_for(result, "timed").get(MINIMUM) == expected
    owners = custom_attributes(result)
    assert [(local_name(node), node.get("nodeset"), key, text)
            for node, key, text in owners] == [
        ("bind", "/data/timed", MINIMUM, expected)]
    assert root_namespaces(result.xform).get("ctosim") == NAMESPACE


@pytest.mark.parametrize("value", [None, "", " ", "\t\n ", "=5+5"])
def test_empty_or_uncached_minimum_seconds_changes_nothing(workbook_factory, value):
    baseline = convert_xlsform(minimum_form(workbook_factory, None))
    result = convert_xlsform(minimum_form(workbook_factory, value))
    assert result == baseline
    assert not custom_attributes(result)
    assert "ctosim" not in root_namespaces(result.xform)


def test_absent_minimum_seconds_column_matches_empty_column(workbook_factory):
    baseline = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label"],
        ["text", "timed", "Timed question"],
        ["text", "untimed", "Untimed question"],
    ]))
    assert convert_xlsform(minimum_form(workbook_factory, None)) == baseline


def test_minimum_seconds_header_is_trimmed(workbook_factory):
    result = convert_xlsform(minimum_form(workbook_factory, 2.5, header=" \tminimum_seconds "))
    assert bind_for(result, "timed").get(MINIMUM) == "2.5"


def test_minimum_seconds_header_is_case_sensitive(workbook_factory):
    result = convert_xlsform(minimum_form(workbook_factory, 10, header="Minimum_seconds"))
    assert not custom_attributes(result)
    assert "ctosim" not in root_namespaces(result.xform)


def test_minimum_seconds_adds_no_warning_or_catalog_entry(workbook_factory):
    result = convert_xlsform(minimum_form(workbook_factory, 10))
    assert result.fields == (
        FieldInfo("timed", "text", "/data/timed", 2),
        FieldInfo("untimed", "text", "/data/untimed", 3),
    )
    assert result.warnings == ()
    assert MINIMUM not in bind_for(result, "untimed").attrib


@pytest.mark.parametrize("field_type, calculation", [
    ("text", None), ("integer", None), ("note", None),
    ("start", None), ("calculate", "42"), ("calculate_here", "42"),
    ("caseid", None), ("enumerator", None),
])
def test_minimum_seconds_applies_to_each_node_producing_row(workbook_factory, field_type, calculation):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "calculation", "minimum_seconds"],
        [field_type, "target", "Target", calculation, 2.5],
        ["text", "other", "Other"],
    ]))
    assert bind_for(result, "target").get(MINIMUM) == "2.5"
    assert result.fields[0] == FieldInfo("target", field_type, "/data/target", 2)
    assert MINIMUM not in bind_for(result, "other").attrib


@pytest.mark.parametrize("container", ["group", "repeat"])
def test_minimum_seconds_on_container_and_child_uses_their_own_paths(workbook_factory, container):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "minimum_seconds"],
        [f"begin {container}", "section", "Section", 10],
        ["text", "timed", "Timed", 2.5],
        ["text", "untimed", "Untimed"],
        [f"end {container}"],
        ["text", "after", "After"],
    ]))
    assert bind_for(result, "section").get(MINIMUM) == "10"
    assert bind_for(result, "section/timed").get(MINIMUM) == "2.5"
    owners = {node.get("nodeset") for node, key, _ in custom_attributes(result)
              if key == MINIMUM}
    assert owners == {"/data/section", "/data/section/timed"}
    assert result.fields[:2] == (
        FieldInfo("section", f"begin {container}", "/data/section", 2),
        FieldInfo("timed", "text", "/data/section/timed", 3),
    )


@pytest.mark.parametrize("value", [-1, -0.001, "-2.5", "ten", "1 second", "1+2", "${untimed}"])
def test_invalid_minimum_seconds_raises_conversion_error_with_file_and_physical_row(workbook_factory, value):
    path = workbook_factory(survey=[
        ["type", "name", "label", "minimum_seconds"],
        [], ["text", "untimed", "Untimed"], [],
        ["text", "bad", "Bad", value],
    ], filename="invalid-timing.xlsx")
    before = path.read_bytes()
    with pytest.raises(ConversionError) as caught:
        convert_xlsform(path)
    message = str(caught.value)
    assert path.name in message
    assert_row_mentioned(message, 5)
    assert any(clue in message.casefold() for clue in ("minimum", "second", "number", "numeric", "nonnegative", "non-negative"))
    assert path.read_bytes() == before


@pytest.mark.parametrize("cached, expected", [(10.0, "10"), (2.5, "2.5"), (0, "0"), ("2.5", "2.5")])
def test_minimum_seconds_uses_cached_formula_value(workbook_factory, set_formula_cache, cached, expected):
    path = minimum_form(workbook_factory, "=100+100")
    set_formula_cache(path, 1, "D2", cached)
    before = path.read_bytes()
    result = convert_xlsform(path)
    assert bind_for(result, "timed").get(MINIMUM) == expected
    assert path.read_bytes() == before


@pytest.mark.parametrize("cached", [-1, "not numeric"])
def test_invalid_cached_minimum_seconds_is_rejected(workbook_factory, set_formula_cache, cached):
    path = minimum_form(workbook_factory, "=10")
    set_formula_cache(path, 1, "D2", cached)
    with pytest.raises(ConversionError) as caught:
        convert_xlsform(path)
    assert path.name in str(caught.value)
    assert_row_mentioned(str(caught.value), 2)


@pytest.mark.parametrize("container", [None, "group", "repeat"])
def test_timing_and_speed_fields_survive_unless_their_own_row_is_disabled(workbook_factory, container):
    rows = [["type", "name", "label", "minimum_seconds", "disabled"]]
    if container:
        rows.extend([
            [f"begin {container}", "omitted", "Omitted", 10, "yes"],
            ["text", "timed", "Timed", 2.5],
            ["speed violations count", "speed_count"],
            ["speed violations list", "speed_list"],
            [f"end {container}"],
        ])
    else:
        rows.append(["speed violations count", "omitted", None, 10, "yes"])
    rows.append(["text", "kept", "Kept"])
    result = convert_xlsform(workbook_factory(survey=rows))
    expected = []
    warnings = [(2, "omitted", None)]
    if container:
        expected += [FieldInfo("timed", "text", "/data/timed", 3),
                     FieldInfo("speed_count", "speed violations count", "/data/speed_count", 4),
                     FieldInfo("speed_list", "speed violations list", "/data/speed_list", 5)]
        assert bind_for(result, "timed").get(MINIMUM) == "2.5"
        for name, kind in [("speed_count", "count"), ("speed_list", "list")]:
            binding = bind_for(result, name)
            assert binding.get(SPEED) == kind
            assert binding.get("calculate") == "''"
            assert MINIMUM not in binding.attrib
            assert not [node for node in elements(result.xform, "input")
                        if node.get("ref") == "/data/" + name]
        assert root_namespaces(result.xform)["ctosim"] == NAMESPACE
        assert len(custom_attributes(result)) == 3
        warnings += [(4, "speed_count", "speed violations count"),
                     (5, "speed_list", "speed violations list")]
    else:
        assert not custom_attributes(result)
        assert "ctosim" not in root_namespaces(result.xform)
    expected.append(FieldInfo("kept", "text", "/data/kept", len(rows)))
    assert result.fields == tuple(expected)
    assert "/data/omitted" not in instance_paths(result)
    assert_warnings(result, warnings, optional_closers=(6,) if container else ())


@pytest.mark.parametrize("kind", ["count", "list"])
def test_speed_violation_fields_are_tagged_empty_hidden_calculates(workbook_factory, kind):
    field_type = f"speed violations {kind}"
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "calculation"],
        [field_type, "summary", "Ignored", "42"],
        ["text", "question", "Question"],
    ]))
    binding = bind_for(result, "summary")
    assert binding.get(SPEED) == kind
    assert binding.get("calculate") == "''"
    assert HERE not in binding.attrib
    node = next(node for node in primary_root(result) if local_name(node) == "summary")
    assert node.text in (None, "")
    body = elements(result.xform, "body")[0]
    assert not [node for node in body.iter() if node.get("ref") == "/data/summary"]
    assert result.fields[0] == FieldInfo("summary", field_type, "/data/summary", 2)
    assert len(result.warnings) == 1
    assert "summary" in result.warnings[0] and field_type in result.warnings[0]
    assert_row_mentioned(result.warnings[0], 2)
    assert root_namespaces(result.xform).get("ctosim") == NAMESPACE
    assert [(local_name(node), node.get("nodeset"), key, value)
            for node, key, value in custom_attributes(result)] == [
        ("bind", "/data/summary", SPEED, kind)]


@pytest.mark.parametrize("kind", ["count", "list"])
def test_speed_violation_type_whitespace_is_normalized_before_tagging(workbook_factory, kind):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label"],
        [f" \tspeed  violations\n{kind} ", "summary"],
        ["text", "q", "Question"],
    ]))
    assert bind_for(result, "summary").get(SPEED) == kind
    assert result.fields[0].type == f"speed violations {kind}"


@pytest.mark.parametrize("field_type", [
    "speed violations audit", "audio audit", "text audit", "caseid",
    "comments", "sensor_statistic light mean", "sensor_stream accelerometer",
])
def test_other_empty_calculate_types_do_not_acquire_speed_attributes(workbook_factory, field_type):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label"], [field_type, "audit"],
        ["text", "q", "Question"],
    ]))
    assert bind_for(result, "audit").get("calculate") == "''"
    assert not custom_attributes(result)
    assert "ctosim" not in root_namespaces(result.xform)


def test_all_custom_attributes_coexist_without_changing_catalog_or_warning_order(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "calculation", "minimum_seconds", "disabled"],
        ["begin group", "section", "Section"],
        ["text", "q", "Question", None, 0],
        ["calculate_here", "checkpoint", None, "selected(${q}, 1)", 2.5],
        ["speed violations count", "speed_count", None, "42", 10],
        ["text", "removed", "Removed", None, 10, "yes"],
        ["begin repeat", "members", "Members"],
        ["speed violations list", "speed_list", None, "42", 1],
        ["end repeat"], ["end group"],
        ["speed violations audit", "audit"],
    ]))
    here = bind_for(result, "section/checkpoint")
    assert here.get(MINIMUM) == "2.5"
    assert "'1'" in here.get(HERE, "")
    assert "/data/section/q" in here.get(HERE, "")
    assert "${" not in here.get(HERE, "")
    assert "calculate" not in here.attrib
    assert here.get("readonly", "").strip() == "true()"
    for path, kind, seconds in [
        ("section/speed_count", "count", "10"),
        ("section/members/speed_list", "list", "1"),
    ]:
        binding = bind_for(result, path)
        assert binding.get(SPEED) == kind
        assert binding.get(MINIMUM) == seconds
        assert binding.get("calculate") == "''"
    assert SPEED not in bind_for(result, "audit").attrib
    assert bind_for(result, "section/q").get(MINIMUM) == "0"
    assert result.fields == (
        FieldInfo("section", "begin group", "/data/section", 2),
        FieldInfo("q", "text", "/data/section/q", 3),
        FieldInfo("checkpoint", "calculate_here", "/data/section/checkpoint", 4),
        FieldInfo("speed_count", "speed violations count", "/data/section/speed_count", 5),
        FieldInfo("members", "begin repeat", "/data/section/members", 7),
        FieldInfo("speed_list", "speed violations list", "/data/section/members/speed_list", 8),
        FieldInfo("audit", "speed violations audit", "/data/audit", 11),
    )
    assert all(field.xpath in instance_paths(result) for field in result.fields)
    expected_warnings = [
        (4, "checkpoint", "calculate_here"), (5, "speed_count", "speed violations count"),
        (6, "removed", None), (8, "speed_list", "speed violations list"),
        (11, "audit", "speed violations audit"),
    ]
    assert len(result.warnings) == len(expected_warnings)
    for warning, (row, name, field_type) in zip(result.warnings, expected_warnings):
        assert_row_mentioned(warning, row)
        assert name in warning
        if field_type:
            assert field_type in warning
    assert root_namespaces(result.xform).get("ctosim") == NAMESPACE


def test_timed_empty_calculate_here_keeps_minimum_and_readonly_attributes(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "minimum_seconds"], ["calculate_here", "checkpoint", 2.5],
    ]))
    binding = bind_for(result, "checkpoint")
    assert binding.get(MINIMUM) == "2.5"
    assert binding.get("readonly", "").strip() == "true()"
    assert "calculate" not in binding.attrib and HERE not in binding.attrib
    assert root_namespaces(result.xform).get("ctosim") == NAMESPACE


def test_custom_attributes_are_deterministic_and_do_not_modify_input(workbook_factory):
    path = workbook_factory(survey=[
        ["type", "name", "label", "minimum_seconds"],
        ["text", "q", "Question", 2.5],
        ["speed violations count", "count"],
        ["speed violations list", "list"],
    ])
    before = path.read_bytes()
    first = convert_xlsform(path)
    assert convert_xlsform(path) == first
    assert path.read_bytes() == before


def test_namespace_does_not_leak_between_conversions(workbook_factory):
    plain = minimum_form(workbook_factory, None)
    baseline = convert_xlsform(plain)
    convert_xlsform(minimum_form(workbook_factory, 10))
    assert convert_xlsform(minimum_form(workbook_factory, None)) == baseline
