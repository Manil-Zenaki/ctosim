"""Spec 02 Amendment 7: hidden calculates discard every label and hint."""

import pytest

from ctosim_xlsform import ConversionError, FieldInfo, convert_xlsform
from xform_assertions import assert_row_mentioned, bind_for, elements, instance_paths


HIDDEN_TYPES = (
    "calculate", "caseid", "comments", "text audit", "audio audit",
    "speed violations count", "speed violations list", "speed violations audit",
    "sensor_statistic", "sensor_statistic light mean", "sensor_stream",
    "sensor_stream accelerometer",
)
TEXT_COLUMNS = ("label", "hint", "label:French", "hint:French", "label:日本語", "hint:日本語")


def _survey(field_type, hidden_text=None):
    return [
        ["type", "name", "calculation", *TEXT_COLUMNS],
        ["text", "q", None, "Question", "Help", "Question FR", "Aide", "質問", "ヒント"],
        [field_type, "hidden", "42", *[hidden_text.get(column) if hidden_text else None
                                      for column in TEXT_COLUMNS]],
    ]


@pytest.mark.parametrize("field_type", HIDDEN_TYPES)
@pytest.mark.parametrize("column", TEXT_COLUMNS)
def test_each_hidden_type_ignores_missing_references_in_each_label_and_hint(workbook_factory, field_type, column):
    baseline = convert_xlsform(workbook_factory(survey=_survey(field_type)))
    result = convert_xlsform(workbook_factory(survey=_survey(
        field_type, {column: "Discard l’argent “ici” ${emiscode}"})))
    # Same headers, settings, filename and physical rows: only this cell differs.
    assert result == baseline
    assert "emiscode" not in result.xform
    assert result.fields[-1] == FieldInfo("hidden", field_type, "/data/hidden", 3)
    assert "/data/hidden" in instance_paths(result)
    assert not any(node.get("ref") == "/data/hidden"
                   for node in elements(result.xform, "body")[0].iter())
    assert bind_for(result, "hidden").get("calculate") == ("42" if field_type == "calculate" else "''")
    assert len(result.warnings) == (0 if field_type == "calculate" else 1)
    if result.warnings:
        assert "hidden" in result.warnings[0] and field_type in result.warnings[0]
        assert_row_mentioned(result.warnings[0], 3)


@pytest.mark.parametrize("text", ["discarded ordinary text", "${q}", "${emiscode} and ${another_missing}"])
def test_all_hidden_text_cells_are_absent_even_when_their_references_exist(workbook_factory, text):
    baseline = convert_xlsform(workbook_factory(survey=_survey("calculate")))
    result = convert_xlsform(workbook_factory(survey=_survey(
        "calculate", {column: text for column in TEXT_COLUMNS})))
    assert result == baseline


@pytest.mark.parametrize("container", [None, "group", "repeat"])
def test_discarding_text_preserves_calculation_relevance_timing_scope_and_following_rows(workbook_factory, container):
    headers = ["type", "name", "label", "hint", "calculation", "relevance", "minimum_seconds"]
    rows = [headers, ["text", "source", "Source"]]
    prefix = ""
    if container:
        rows.append([f"begin {container}", "section", "Section"])
        prefix = "section/"
    row_number = len(rows) + 1
    rows += [
        ["calculate", "hidden", "${emiscode}", "${missing_hint}",
         "if(selected(${source}, 1), 'l’argent', '')", "selected(${source}, -99)", 2.5],
        ["text", "after", "Visible after", "Visible help"],
    ]
    if container:
        rows.append([f"end {container}"])
    baseline_rows = [list(row) for row in rows]
    baseline_rows[row_number - 1][2:4] = [None, None]
    baseline = convert_xlsform(workbook_factory(survey=baseline_rows))
    result = convert_xlsform(workbook_factory(survey=rows))
    assert result == baseline
    binding = bind_for(result, prefix + "hidden")
    assert "'l’argent'" in binding.get("calculate", "")
    assert "'1'" in binding.get("calculate", "")
    assert "/data/source" in binding.get("calculate", "")
    assert "'-99'" in binding.get("relevant", "")
    assert "/data/source" in binding.get("relevant", "")
    assert "${" not in binding.get("calculate", "") + binding.get("relevant", "")
    assert binding.get("{urn:ctosim:xforms}minimum-seconds") == "2.5"
    assert result.fields[-2:] == (
        FieldInfo("hidden", "calculate", f"/data/{prefix}hidden", row_number),
        FieldInfo("after", "text", f"/data/{prefix}after", row_number + 1),
    )


@pytest.mark.parametrize("field_type, speed", [("speed violations count", "count"), ("speed violations list", "list")])
def test_discarding_speed_field_text_keeps_custom_bind_attributes(workbook_factory, field_type, speed):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "hint", "minimum_seconds", "relevance"],
        ["text", "q", "Q"],
        [field_type, "speed", "${emiscode}", "${missing_hint}", 0, "selected(${q}, 1)"],
    ]))
    binding = bind_for(result, "speed")
    assert binding.get("{urn:ctosim:xforms}speed-violations") == speed
    assert binding.get("{urn:ctosim:xforms}minimum-seconds") == "0"
    assert binding.get("calculate") == "''"
    assert "'1'" in binding.get("relevant", "")


@pytest.mark.parametrize("column, value", [
    ("calculation", "${missing_calculation} + 1"),
    ("relevance", "${missing_relevance} = 1"),
    ("minimum_seconds", "not a number"),
])
def test_ignored_label_reference_does_not_hide_errors_in_other_columns(workbook_factory, column, value):
    headers = ["type", "name", "label", "calculation", "relevance", "minimum_seconds"]
    row = ["calculate", "hidden", "${emiscode}", "42", None, None]
    row[headers.index(column)] = value
    path = workbook_factory(survey=[headers, ["text", "q", "Q"], [], row])
    with pytest.raises(ConversionError) as caught:
        convert_xlsform(path)
    message = str(caught.value)
    assert path.name in message
    assert_row_mentioned(message, 4)
    if column == "minimum_seconds":
        assert "minimum" in message.lower() or "number" in message.lower()
    else:
        assert "missing_" + column in message or column in message.lower()


@pytest.mark.parametrize("field_type", ["text", "note", "enumerator"])
@pytest.mark.parametrize("column", ["label", "hint", "label:French", "hint:French"])
def test_visible_rows_still_validate_references_in_their_text(workbook_factory, field_type, column):
    headers = ["type", "name", "label"]
    row = [field_type, "visible", "Visible"]
    if column == "label":
        row[2] = "${missing_visible}"
    else:
        headers.append(column)
        row.append("${missing_visible}")
    path = workbook_factory(survey=[headers, row])
    with pytest.raises(ConversionError) as caught:
        convert_xlsform(path)
    assert "missing_visible" in str(caught.value)
    assert path.name in str(caught.value)
    assert_row_mentioned(str(caught.value), 2)


def test_cached_hidden_label_and_hint_references_are_discarded(workbook_factory, set_formula_cache):
    baseline = convert_xlsform(workbook_factory(survey=_survey("calculate")))
    path = workbook_factory(survey=_survey("calculate", {
        "label": '=CONCAT("label")', "hint:French": '=CONCAT("hint")',
    }))
    set_formula_cache(path, 1, "D3", "${emiscode}")
    set_formula_cache(path, 1, "G3", "l’argent ${missing_hint}")
    before = path.read_bytes()
    assert convert_xlsform(path) == baseline
    assert convert_xlsform(path) == baseline
    assert path.read_bytes() == before


def test_single_hidden_calculate_needs_no_displayable_label(workbook_factory):
    baseline = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "hint", "calculation"],
        ["calculate", "only", None, None, "1"],
    ]))
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "hint", "calculation"],
        ["calculate", "only", "${emiscode}", "${missing_hint}", "1"],
    ]))
    assert result == baseline
    assert result.fields == (FieldInfo("only", "calculate", "/data/only", 2),)


@pytest.mark.parametrize("column", ["label:French", "hint:日本語"])
def test_language_used_only_by_hidden_text_behaves_as_an_empty_cell(workbook_factory, column):
    rows = [
        ["type", "name", "label", "calculation", column],
        ["text", "visible", "Visible"], ["calculate", "hidden", None, "42"],
    ]
    baseline = convert_xlsform(workbook_factory(survey=rows))
    rows[2].append("l’argent ${emiscode}")
    result = convert_xlsform(workbook_factory(survey=rows))
    # No policy for an otherwise empty translation is invented: use the same
    # header with an empty cell as the explicitly specified reference result.
    assert result == baseline


@pytest.mark.parametrize("raw_type", ["  calculate\t", " speed\tviolations   count ", "sensor_stream\t accelerometer"])
def test_hidden_text_is_discarded_after_type_whitespace_normalization(workbook_factory, raw_type):
    baseline = convert_xlsform(workbook_factory(survey=_survey(raw_type)))
    result = convert_xlsform(workbook_factory(survey=_survey(
        raw_type, {column: "${emiscode}" for column in TEXT_COLUMNS})))
    assert result == baseline
    assert result.fields[-1].type == " ".join(raw_type.split())


def test_hidden_text_removal_does_not_remove_calculate_here_question(workbook_factory):
    rows = [
        ["type", "name", "label", "hint", "calculation"],
        ["text", "before", "Before", "Keep this hint"],
        ["calculate", "hidden", "${emiscode}", "${missing_hint}", "1"],
        ["calculate_here", "checkpoint", None, None, "if(${hidden}=1, 'l’argent', '')"],
        ["text", "after", "After"],
    ]
    baseline_rows = [list(row) for row in rows]
    baseline_rows[2][2:4] = [None, None]
    baseline = convert_xlsform(workbook_factory(survey=baseline_rows))
    result = convert_xlsform(workbook_factory(survey=rows))
    assert result == baseline
    assert [node.get("ref") for node in elements(result.xform, "input")] == [
        "/data/before", "/data/checkpoint", "/data/after"]
    binding = bind_for(result, "checkpoint")
    assert "calculate" not in binding.attrib
    assert "'l’argent'" in binding.get("{urn:ctosim:xforms}calculate-here", "")
