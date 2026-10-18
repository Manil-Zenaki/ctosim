"""SurveyCTO normalizations, disabled blocks, translations, and expressions."""

import re

import pytest

from ctosim_xlsform import FieldInfo, convert_xlsform
from xform_assertions import assert_choice_list, assert_row_mentioned, bind_for, elements, instance_paths
from test_converter_disabled_rows_amendment8 import assert_warnings


EMPTY_CALCULATE_TYPES = [
    "caseid", "comments", "text audit", "audio audit", "speed violations count",
    "speed violations list", "speed violations audit", "sensor_statistic",
    "sensor_statistic light mean", "sensor_stream", "sensor_stream accelerometer",
]


@pytest.mark.parametrize("field_type", EMPTY_CALCULATE_TYPES)
def test_surveycto_metadata_types_become_empty_calculates(workbook_factory, field_type):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "calculation"], [field_type, "special", "Special", "42"],
        ["text", "q", "Question"]]))
    assert bind_for(result, "special").get("calculate") == "''"
    assert result.fields[0] == FieldInfo("special", field_type, "/data/special", 2)
    assert not [node for node in elements(result.xform, "input") if node.get("ref") == "/data/special"]
    assert len(result.warnings) == 1
    assert_row_mentioned(result.warnings[0], 2)
    assert "special" in result.warnings[0]
    assert field_type in result.warnings[0]


def test_calculate_here_preserves_its_calculation(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label", "calculation"],
        ["integer", "q", "Q"], ["calculate_here", "twice", None, "/data/q * 2"]]))
    binding = bind_for(result, "twice")
    assert "calculate" not in binding.attrib
    assert binding.get("{urn:ctosim:xforms}calculate-here").strip() == "/data/q * 2"
    assert result.fields[1].type == "calculate_here"
    assert len(result.warnings) == 1
    assert_row_mentioned(result.warnings[0], 3)
    assert "twice" in result.warnings[0] and "calculate_here" in result.warnings[0]


def test_enumerator_becomes_a_visible_text_question(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label"],
        ["enumerator", "enumerator_name", "Enumerator name"]]))
    assert bind_for(result, "enumerator_name").get("type", "").split(":")[-1] == "string"
    assert any(node.get("ref") == "/data/enumerator_name" for node in elements(result.xform, "input"))
    assert result.fields == (FieldInfo("enumerator_name", "enumerator", "/data/enumerator_name", 2),)
    assert len(result.warnings) == 1
    assert "enumerator_name" in result.warnings[0] and "enumerator" in result.warnings[0]
    assert_row_mentioned(result.warnings[0], 2)


@pytest.mark.parametrize("raw_type, normalized", [
    ("  text \t", "text"), ("\tcalculate_here\n", "calculate_here"),
    (" text \t audit ", "text audit"),
    ("sensor_statistic\t light   mean", "sensor_statistic light mean"),
    ("select_one \t opts", "select_one opts"),
])
def test_type_whitespace_is_trimmed_and_collapsed(workbook_factory, raw_type, normalized):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label", "calculation"],
        [raw_type, "q", "Q", "1" if normalized == "calculate_here" else None]],
        choices=[["list_name", "name", "label"], ["opts", "a", "A"]]))
    assert result.fields[0].type == normalized
    assert result.fields[0].xpath == "/data/q"


@pytest.mark.parametrize("field_type, bind_type", [
    ("text", "string"), ("integer", "int"), ("decimal", "decimal"),
    ("date", "date"), ("time", "time"), ("datetime", "dateTime"),
    ("geopoint", "geopoint"),
])
def test_standard_types_retain_their_type_and_runtime_binding(workbook_factory, field_type, bind_type):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label"], [field_type, "q", "Q"]]))
    assert result.fields[0].type == field_type
    assert bind_for(result, "q").get("type", "").split(":")[-1] == bind_type


@pytest.mark.parametrize("disabled", ["yes", "YES", "Yes", " \tyEs \n"])
def test_disabled_yes_omits_a_single_row_and_warns(workbook_factory, disabled):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label", "disabled"],
        ["text", "hidden", "Hidden", disabled], ["text", "kept", "Kept"]]))
    assert result.fields == (FieldInfo("kept", "text", "/data/kept", 3),)
    assert "/data/hidden" not in instance_paths(result)
    assert len(result.warnings) == 1
    assert "hidden" in result.warnings[0]
    assert_row_mentioned(result.warnings[0], 2)


@pytest.mark.parametrize("disabled", [None, "", "no", "true", "1", "y", "yesterday"])
def test_only_yes_disables_a_row(workbook_factory, disabled):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label", "disabled"],
        ["text", "q", "Q", disabled]]))
    assert result.fields == (FieldInfo("q", "text", "/data/q", 2),)
    assert "/data/q" in instance_paths(result)


@pytest.mark.parametrize("outer, inner", [("group", "group"), ("group", "repeat"),
                                         ("repeat", "group"), ("repeat", "repeat")])
def test_disabled_nested_boundaries_keep_independently_enabled_children(workbook_factory, outer, inner):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label", "disabled"],
        [f"begin {outer}", "omitted", "Omitted", "YES"],
        [f"begin {inner}", "nested", "Nested", "yes"], ["caseid", "nested_case"],
        [f"end {inner}"], ["text", "still_omitted", "Gone"], [f"end {outer}"],
        ["text", "kept", "Kept"]]))
    assert result.fields == (
        FieldInfo("nested_case", "caseid", "/data/nested_case", 4),
        FieldInfo("still_omitted", "text", "/data/still_omitted", 6),
        FieldInfo("kept", "text", "/data/kept", 8))
    assert all(path not in instance_paths(result) for path in ["/data/omitted", "/data/omitted/nested"])
    assert bind_for(result, "nested_case").get("calculate") == "''"
    assert_warnings(result, [(2, "omitted", None), (3, "nested", None),
                             (4, "nested_case", "caseid")], optional_closers=(5, 7))


def test_disabled_inner_group_does_not_remove_parent_or_following_sibling(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label", "disabled"],
        ["begin group", "parent", "Parent"], ["begin group", "gone", "Gone", "yes"],
        ["text", "hidden", "Hidden"], ["end group"], ["text", "sibling", "Sibling"],
        ["end group"], ["text", "after", "After"]]))
    assert result.fields == (
        FieldInfo("parent", "begin group", "/data/parent", 2),
        FieldInfo("hidden", "text", "/data/parent/hidden", 4),
        FieldInfo("sibling", "text", "/data/parent/sibling", 6),
        FieldInfo("after", "text", "/data/after", 8))


def test_disabled_invalid_type_is_excluded_before_pyxform_conversion(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label", "disabled"],
        ["not_a_real_type", "hidden", "Hidden", "yes"], ["text", "q", "Q"]]))
    assert [field.name for field in result.fields] == ["q"]


def test_converted_and_disabled_warnings_follow_sheet_order(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label", "disabled"],
        ["caseid", "first_case"], ["text", "removed_text", "Remove", "yes"],
        ["enumerator", "interviewer", "Who"], ["begin group", "removed_group", "Gone", "yes"],
        ["audio audit", "inside"], ["end group"], ["comments", "last_comments"],
        ["text", "q", "Q"]]))
    expected = [(2, "first_case", "caseid"), (3, "removed_text", None),
                (4, "interviewer", "enumerator"), (5, "removed_group", None),
                (6, "inside", "audio audit"),
                (8, "last_comments", "comments")]
    assert_warnings(result, expected, optional_closers=(7,))


@pytest.mark.parametrize("column", ["note", "response_note", "publishable",
                                    "accuracy_threshold", "mediatype"])
def test_nonruntime_columns_have_no_effect(workbook_factory, column):
    baseline = convert_xlsform(workbook_factory())
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label", column],
        ["text", "answer", "Answer", "ignored arbitrary content"]]))
    assert result == baseline


@pytest.mark.parametrize("source, attribute, value", [
    ("relevance", "relevant", "/data/first = 'ok'"),
    ("read only", "readonly", "true()"),
])
def test_surveycto_column_aliases_produce_runtime_bindings(workbook_factory, source, attribute, value):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label", source],
        ["text", "first", "First"], ["text", "q", "Question", value]]))
    assert bind_for(result, "q").get(attribute).strip() == value


@pytest.mark.parametrize("column, condition, attribute", [
    ("constraint message", "constraint", "constraintMsg"),
    ("required message", "required", "requiredMsg"),
])
def test_unsuffixed_message_aliases_are_retained(workbook_factory, column, condition, attribute):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label", condition, column],
        ["text", "q", "Question", "true()", "Please answer café 東京"]]))
    values = [value for key, value in bind_for(result, "q").attrib.items()
              if key.rsplit("}", 1)[-1] == attribute]
    assert len(values) == 1
    if values[0] != "Please answer café 東京":
        reference = re.search(r"itext\(['\"]([^'\"]+)['\"]\)", values[0])
        assert reference is not None
        texts = [node for node in elements(result.xform, "text") if node.get("id") == reference.group(1)]
        assert any("Please answer café 東京" in "".join(node.itertext()) for node in texts)


@pytest.mark.parametrize("column, condition, attribute", [
    ("constraint message", "constraint", "constraintMsg"),
    ("required message", "required", "requiredMsg"),
])
@pytest.mark.parametrize("padding", ["", " \t"])
def test_translated_message_aliases_are_linked_to_the_correct_bind(workbook_factory, column, condition, attribute, padding):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", condition, column, padding + column + ":Français" + padding],
        ["text", "q", "Q", "true()", "Default message", "Message français"]]))
    values = [value for key, value in bind_for(result, "q").attrib.items()
              if key.rsplit("}", 1)[-1] == attribute]
    assert len(values) == 1
    reference = re.search(r"itext\(['\"]([^'\"]+)['\"]\)", values[0])
    assert reference is not None
    for language, expected in [("default", "Default message"), ("Français", "Message français")]:
        translation = next(node for node in elements(result.xform, "translation") if node.get("lang") == language)
        text = next(node for node in elements(translation, "text") if node.get("id") == reference.group(1))
        assert expected in "".join(text.itertext())


@pytest.mark.parametrize("default_language, expected", [(None, "default"), ("", "default"),
                                                        ("English", "English")])
def test_translations_include_every_language_and_unsuffixed_default(workbook_factory, default_language, expected):
    settings = [["form_id"], ["form"]] if default_language is None else [
        ["form_id", "default_language"], ["form", default_language]]
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "label:Français", "hint:Amharic", "constraint", "constraint message:日本語",
         "required", "required message:Español"],
        ["text", "q", "Default label", "Libellé", "ፍንጭ", "true()", "制約", "true()", "Obligatorio"]],
        settings=settings))
    translations = elements(result.xform, "translation")
    assert {node.get("lang") for node in translations} == {expected, "Français", "Amharic", "日本語", "Español"}
    assert [node.get("lang") for node in translations if node.get("default") == "true()"] == [expected]
    for language, text in [(expected, "Default label"), ("Français", "Libellé"),
                           ("Amharic", "ፍንጭ"), ("日本語", "制約"), ("Español", "Obligatorio")]:
        translation = next(node for node in translations if node.get("lang") == language)
        assert text in "".join(translation.itertext())


def test_translation_language_names_are_case_sensitive_and_kept_as_written(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label", "label:French", "label:french"],
        ["text", "q", "Q", "Upper", "Lower"]]))
    assert {node.get("lang") for node in elements(result.xform, "translation")} == {"default", "French", "french"}


@pytest.mark.parametrize("choice_column", ["name", "value"])
@pytest.mark.parametrize("select_type, element", [("select_one", "select1"), ("select_multiple", "select")])
def test_choices_accept_name_or_value_and_duplicates_within_a_list(workbook_factory, choice_column, select_type, element):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label"], [f"{select_type} opts", "q", "Q"]],
        choices=[["list_name", choice_column, "label"], ["opts", "a", "First A"],
                 ["opts", "a", "Second A"], ["opts", "b", "B"]]))
    assert_choice_list(result, "q", element, "opts",
                       [("a", "First A"), ("a", "Second A"), ("b", "B")])
    assert result.fields[0].type == f"{select_type} opts"


def test_choice_lists_remain_separate_when_values_overlap(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label"],
        ["select_one left", "one", "One"], ["select_one right", "two", "Two"]],
        choices=[["list_name", "value", "label"], ["left", "a", "Left A"],
                 ["right", "a", "Right A"], ["right", "b", "Right B"]]))
    assert_choice_list(result, "one", "select1", "left", [("a", "Left A")])
    assert_choice_list(result, "two", "select1", "right", [("a", "Right A"), ("b", "Right B")])


@pytest.mark.parametrize("column, attribute", [("relevance", "relevant"), ("constraint", "constraint"),
    ("calculation", "calculate"), ("required", "required"), ("choice_filter", "nodeset"),
    ("repeat_count", "count")])
@pytest.mark.parametrize("literal", ["0", "1", "-99", "2.5", "-0.5"])
def test_numeric_selected_argument_is_quoted_in_every_expression_column(workbook_factory, column, attribute, literal):
    original = f"selected(/data/q, {literal})"
    expected = f"selected(/data/q, '{literal}')"
    rows = [["type", "name", "label", column], ["select_multiple opts", "q", "Q"]]
    if column == "repeat_count":
        rows += [["begin repeat", "target", "Target", original], ["text", "child", "Child"], ["end repeat"]]
    else:
        field_type = "calculate" if column == "calculation" else "select_one opts" if column == "choice_filter" else "text"
        rows += [[field_type, "target", "Target", original]]
    result = convert_xlsform(workbook_factory(survey=rows,
        choices=[["list_name", "name", "label"], ["opts", "1", "One"], ["opts", "2", "Two"]]))
    if column == "repeat_count":
        assert bind_for(result, "target_count").get("calculate").strip() == expected
        repeats = [node for node in elements(result.xform, "repeat")
                   if node.get("nodeset") == "/data/target"]
        assert len(repeats) == 1
        assert repeats[0].get("{http://openrosa.org/javarosa}count", "").strip() == "/data/target_count"
        assert "/data/target_count" in instance_paths(result)
        assert "/data/target_count" not in [field.xpath for field in result.fields]
    elif column == "choice_filter":
        nodes = elements(result.xform, "itemset")
        values = [value for node in nodes for key, value in node.attrib.items() if key.rsplit("}", 1)[-1] == attribute]
        assert any(expected in value for value in values), values
    else:
        assert bind_for(result, "target").get(attribute).strip() == expected


@pytest.mark.parametrize("expression, expected", [
    ("selected( /data/q , -99 )", "selected( /data/q , '-99' )"),
    ("selected ( /data/q , 1 )", "selected ( /data/q , '1' )"),
    ("selected(/data/q,1) or selected(/data/q,2.5)", "selected(/data/q,'1') or selected(/data/q,'2.5')"),
    ("not(selected(/data/q, -1))", "not(selected(/data/q, '-1'))"),
    ("selected(/data/q, '1')", "selected(/data/q, '1')"),
    ('selected(/data/q, "-99")', 'selected(/data/q, "-99")'),
    ('concat("selected(/data/q, 1)", selected(/data/q, 2))', 'concat("selected(/data/q, 1)", selected(/data/q, \'2\'))'),
    ("concat('selected(/data/q, -99)', selected(/data/q, 2.5))", "concat('selected(/data/q, -99)', selected(/data/q, '2.5'))"),
    ('concat("it\'s selected(/data/q, 1)", selected(/data/q, 2))', 'concat("it\'s selected(/data/q, 1)", selected(/data/q, \'2\'))'),
    ("selected(concat(/data/q, 'x,y'), 1)", "selected(concat(/data/q, 'x,y'), '1')"),
    ("selected(/data/q, 1 + 2)", "selected(/data/q, 1 + 2)"),
    ("selected(/data/q, -1 * 2)", "selected(/data/q, -1 * 2)"),
    ("selected(/data/q, /data/other)", "selected(/data/q, /data/other)"),
    ("selected(/data/q, string(1))", "selected(/data/q, string(1))"),
    ("1 + 2.5", "1 + 2.5"),
])
def test_selected_rewriting_preserves_surroundings_literals_and_nonliteral_arguments(workbook_factory, expression, expected):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label", "calculation"],
        ["text", "q", "Q"], ["text", "other", "Other"], ["calculate", "target", None, expression]]))
    assert bind_for(result, "target").get("calculate").strip() == expected


def test_selected_rewrite_handles_xlsform_reference_syntax(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label", "calculation"],
        ["text", "q", "Q"], ["calculate_here", "target", None, "selected(${q}, -99)"]]))
    calculation = bind_for(result, "target").get("{urn:ctosim:xforms}calculate-here")
    assert "'-99'" in calculation
    assert "/data/q" in calculation
    assert "${q}" not in calculation


@pytest.mark.parametrize("column", ["label", "hint", "constraint message", "required message"])
def test_nonexpression_text_is_not_rewritten(workbook_factory, column):
    headers = ["type", "name", "label", "constraint", "required"]
    row = ["text", "q", "Q", "true()", "true()"]
    literal = "selected(/data/q, 1)"
    if column in headers:
        row[headers.index(column)] = literal
    else:
        headers.append(column)
        row.append(literal)
    result = convert_xlsform(workbook_factory(survey=[headers, row]))
    assert literal in result.xform
