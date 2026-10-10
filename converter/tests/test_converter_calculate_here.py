"""Amendment 2: calculate_here is a question computed at its body position.

Assertions use only the public API and XML. No converter internals are patched.
"""

from io import StringIO
import xml.etree.ElementTree as ET

import pytest

from ctosim_xlsform import FieldInfo, convert_xlsform
from xform_assertions import (
    assert_row_mentioned, bind_for, elements, instance_paths, local_name,
    primary_root,
)


CALCULATE_HERE = "{urn:ctosim:xforms}calculate-here"


def root_namespaces(xml):
    """Only declarations on the document root, not a descendant bind."""
    namespaces = {}
    for event, value in ET.iterparse(StringIO(xml), events=("start-ns", "start")):
        if event == "start":
            break
        prefix, uri = value
        namespaces[prefix] = uri
    return namespaces


def input_for(result, path):
    controls = [node for node in elements(result.xform, "input")
                if node.get("ref") == path]
    assert len(controls) == 1, f"Expected one text question for {path}"
    return controls[0]


def here_form(workbook_factory, calculation="1", **columns):
    return workbook_factory(survey=[
        ["type", "name", "label", "calculation", *columns],
        ["text", "before", "Before"],
        ["calculate_here", "checkpoint", "Ignored label", calculation,
         *columns.values()],
        ["text", "after", "After"],
    ])


def test_calculate_here_is_a_read_only_string_question_named_by_its_field(workbook_factory):
    result = convert_xlsform(here_form(workbook_factory))
    binding = bind_for(result, "checkpoint")
    assert binding.get("type", "").split(":")[-1] == "string"
    assert binding.get("readonly", "").strip() == "true()"
    control = input_for(result, "/data/checkpoint")
    labels = elements(control, "label")
    assert len(labels) == 1
    assert "".join(labels[0].itertext()) == "checkpoint"


@pytest.mark.parametrize("container", [None, "group", "repeat"])
def test_calculate_here_keeps_its_body_position_and_catalog_path(workbook_factory, container):
    rows = [["type", "name", "label", "calculation"]]
    prefix = "/data"
    if container:
        rows.append([f"begin {container}", "section", "Section"])
        prefix += "/section"
    rows.extend([
        ["text", "before", "Before"],
        ["calculate_here", "checkpoint", None, "once(duration())"],
        ["text", "after", "After"],
    ])
    if container:
        rows.append([f"end {container}"])
    result = convert_xlsform(workbook_factory(survey=rows))
    body = elements(result.xform, "body")[0]
    assert [node.get("ref") for node in elements(body, "input")] == [
        f"{prefix}/before", f"{prefix}/checkpoint", f"{prefix}/after"]
    # A globally correct traversal order is insufficient: the control must
    # actually be inside the corresponding group/repeat body container.
    if container:
        attribute = "nodeset" if container == "repeat" else "ref"
        parent = next(node for node in elements(body, container)
                      if node.get(attribute) == prefix)
        assert [node.get("ref") for node in elements(parent, "input")
                if node.get("ref") == f"{prefix}/checkpoint"] == [f"{prefix}/checkpoint"]
    field = next(field for field in result.fields if field.name == "checkpoint")
    assert field == FieldInfo("checkpoint", "calculate_here", f"{prefix}/checkpoint",
                              4 if container else 3)
    assert field.xpath in instance_paths(result)
    assert len(result.warnings) == 1
    assert_row_mentioned(result.warnings[0], field.row)
    assert "checkpoint" in result.warnings[0]
    assert "calculate_here" in result.warnings[0]


@pytest.mark.parametrize("expression", ["42", "once(duration())", "''"])
def test_calculate_here_uses_only_the_custom_calculation_attribute(workbook_factory, expression):
    result = convert_xlsform(here_form(workbook_factory, expression))
    binding = bind_for(result, "checkpoint")
    assert "calculate" not in binding.attrib
    assert binding.get(CALCULATE_HERE) == expression
    # Compare paths, since separate XML parses yield different Element objects.
    owners = [node.get("nodeset") for node in ET.fromstring(result.xform).iter()
              if CALCULATE_HERE in node.attrib]
    assert owners == ["/data/checkpoint"]


@pytest.mark.parametrize("calculation", [None, "", "=1+1"])
def test_empty_or_uncached_calculation_is_an_empty_read_only_question(workbook_factory, calculation):
    result = convert_xlsform(here_form(workbook_factory, calculation))
    binding = bind_for(result, "checkpoint")
    assert CALCULATE_HERE not in binding.attrib
    assert "calculate" not in binding.attrib
    assert binding.get("readonly", "").strip() == "true()"
    node = next(node for node in primary_root(result) if local_name(node) == "checkpoint")
    assert node.text in (None, "")
    input_for(result, "/data/checkpoint")
    assert root_namespaces(result.xform).get("ctosim") == "urn:ctosim:xforms"


def test_calculate_here_without_a_calculation_column_is_an_empty_question(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name"], ["calculate_here", "checkpoint"],
    ]))
    binding = bind_for(result, "checkpoint")
    assert CALCULATE_HERE not in binding.attrib
    assert "calculate" not in binding.attrib
    assert binding.get("readonly", "").strip() == "true()"
    input_for(result, "/data/checkpoint")


def test_calculate_here_uses_and_rewrites_the_cached_excel_calculation(workbook_factory, set_formula_cache):
    path = here_form(workbook_factory, '=CONCAT("selected(", "${before}, 1)")')
    set_formula_cache(path, 1, "D3", "selected(${before}, 1)")
    result = convert_xlsform(path)
    expression = bind_for(result, "checkpoint").get(CALCULATE_HERE)
    assert "CONCAT" not in expression
    assert "${before}" not in expression
    assert "/data/before" in expression
    assert "'1'" in expression


@pytest.mark.parametrize("column, value", [
    ("required", "true()"), ("constraint", ". = 'must not survive'"),
    ("default", "must not survive"), ("appearance", "hidden"),
    ("hint", "must not survive"), ("read only", "false()"),
    ("choice_filter", "selected(/data/before, 1)"),
])
def test_calculate_here_discards_each_other_column(workbook_factory, column, value):
    baseline = convert_xlsform(here_form(workbook_factory))
    result = convert_xlsform(here_form(workbook_factory, **{column: value}))
    assert result == baseline


def test_calculate_here_source_label_does_not_override_the_name_label(workbook_factory):
    result = convert_xlsform(here_form(workbook_factory))
    assert "Ignored label" not in result.xform
    assert "".join(elements(input_for(result, "/data/checkpoint"), "label")[0].itertext()) == "checkpoint"


@pytest.mark.parametrize("literal", ["0", "-99", "2.5", "-0.5"])
def test_calculate_here_relevance_is_retained_and_rewritten(workbook_factory, literal):
    result = convert_xlsform(here_form(
        workbook_factory, relevance=f"selected(${{before}}, {literal})"))
    binding = bind_for(result, "checkpoint")
    relevant = binding.get("relevant")
    assert relevant is not None
    assert "${before}" not in relevant
    assert "/data/before" in relevant
    assert f"'{literal}'" in relevant


@pytest.mark.parametrize("container", [None, "group", "repeat"])
def test_calculate_here_expands_references_exactly_like_a_normal_calculate(workbook_factory, container):
    rows = [["type", "name", "label", "calculation"], ["integer", "outer", "Outer"]]
    prefix = ""
    if container:
        rows.append([f"begin {container}", "section", "Section"])
        prefix = "section/"
    expression = "if(selected(${source}, -99), ${outer}, ${source})"
    rows.extend([
        ["text", "source", "Source"],
        ["calculate", "ordinary", None, expression],
        ["calculate_here", "checkpoint", None, expression],
    ])
    if container:
        rows.append([f"end {container}"])
    result = convert_xlsform(workbook_factory(survey=rows))
    ordinary = bind_for(result, prefix + "ordinary")
    here = bind_for(result, prefix + "checkpoint")
    assert "calculate" not in here.attrib
    assert CALCULATE_HERE not in ordinary.attrib
    assert here.get(CALCULATE_HERE) == ordinary.get("calculate")
    assert "'-99'" in here.get(CALCULATE_HERE)
    assert "${" not in here.get(CALCULATE_HERE)


@pytest.mark.parametrize("expression, expected", [
    ("selected( /data/before , -0.5 )", "selected( /data/before , '-0.5' )"),
    ("selected(/data/before, '1')", "selected(/data/before, '1')"),
    ('selected(/data/before, "2.5")', 'selected(/data/before, "2.5")'),
    ("selected(/data/before, 1 + 2)", "selected(/data/before, 1 + 2)"),
    ('concat("selected(/data/before, 1)", selected(/data/before, -99))',
     'concat("selected(/data/before, 1)", selected(/data/before, \'-99\'))'),
])
def test_calculate_here_rewriting_preserves_literals_and_other_expression_text(workbook_factory, expression, expected):
    result = convert_xlsform(here_form(workbook_factory, expression))
    assert bind_for(result, "checkpoint").get(CALCULATE_HERE) == expected


def test_calculate_here_declares_the_required_prefix_on_the_root(workbook_factory):
    result = convert_xlsform(here_form(workbook_factory))
    assert root_namespaces(result.xform).get("ctosim") == "urn:ctosim:xforms"


def test_form_without_calculate_here_has_no_custom_attribute_or_namespace(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "calculation"], ["text", "q", "Question"],
        ["calculate", "normal", None, "42"], ["caseid", "case"],
    ]))
    assert "ctosim" not in root_namespaces(result.xform)
    assert all(CALCULATE_HERE not in node.attrib for node in ET.fromstring(result.xform).iter())
    assert bind_for(result, "normal").get("calculate") == "42"
    assert bind_for(result, "case").get("calculate") == "''"


def test_disabled_calculate_here_adds_only_an_omission_warning(workbook_factory):
    result = convert_xlsform(here_form(workbook_factory, disabled="yes"))
    assert [field.name for field in result.fields] == ["before", "after"]
    assert "/data/checkpoint" not in instance_paths(result)
    assert "ctosim" not in root_namespaces(result.xform)
    assert len(result.warnings) == 1
    assert "checkpoint" in result.warnings[0]
    assert_row_mentioned(result.warnings[0], 3)


def test_multiple_calculate_here_rows_keep_separate_expressions_and_warning_order(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "calculation"],
        ["calculate_here", "first", None, "1"],
        ["text", "between", "Between"],
        ["calculate_here", "second", None, "2"],
    ]))
    assert bind_for(result, "first").get(CALCULATE_HERE) == "1"
    assert bind_for(result, "second").get(CALCULATE_HERE) == "2"
    assert len(result.warnings) == 2
    for warning, name, row in zip(result.warnings, ["first", "second"], [2, 4]):
        assert name in warning and "calculate_here" in warning
        assert_row_mentioned(warning, row)


def test_calculate_here_conversion_is_deterministic_and_preserves_input(workbook_factory):
    path = here_form(workbook_factory, "once(duration())", relevance="${before} != ''")
    original = path.read_bytes()
    assert convert_xlsform(path) == convert_xlsform(path)
    assert path.read_bytes() == original
