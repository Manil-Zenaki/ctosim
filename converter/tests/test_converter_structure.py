"""Workbook input, settings, primary instance, and catalog contract."""

from dataclasses import FrozenInstanceError, is_dataclass
from pathlib import Path

import pytest

from ctosim_xlsform import ConversionResult, FieldInfo, convert_xlsform
from xform_assertions import elements, instance_paths, local_name, primary_root


@pytest.mark.parametrize("path_kind", [str, Path])
def test_accepts_string_and_pathlike_input(workbook_factory, path_kind):
    result = convert_xlsform(path_kind(workbook_factory()))
    assert isinstance(result, ConversionResult)
    assert isinstance(result.xform, str)
    assert isinstance(result.fields, tuple)
    assert isinstance(result.warnings, tuple)
    assert all(isinstance(field, FieldInfo) for field in result.fields)
    assert all(isinstance(warning, str) for warning in result.warnings)


@pytest.mark.parametrize("record", [FieldInfo("q", "text", "/data/q", 2),
    ConversionResult("<xml/>", "form", "Title", None, (), ())])
def test_public_records_are_frozen_dataclasses(record):
    assert is_dataclass(record)
    attribute = "name" if isinstance(record, FieldInfo) else "title"
    with pytest.raises(FrozenInstanceError):
        setattr(record, attribute, "changed")


def test_minimal_workbook_needs_neither_choices_nor_settings(workbook_factory):
    result = convert_xlsform(workbook_factory())
    assert result.form_id == "form"
    assert result.title == "form"
    assert result.version is None
    assert result.fields == (FieldInfo("answer", "text", "/data/answer", 2),)


def test_output_is_complete_xform_with_data_primary_root(workbook_factory):
    result = convert_xlsform(workbook_factory(settings=[
        ["form_id", "form_title", "version"], ["household", "Household", "v1"]]))
    assert elements(result.xform, "html")
    assert elements(result.xform, "model")
    assert elements(result.xform, "body")
    root = primary_root(result)
    assert local_name(root) == "data"
    assert root.get("id") == result.form_id == "household"
    assert root.get("version") == result.version == "v1"
    assert "Household" in [node.text for node in elements(result.xform, "title")]


@pytest.mark.parametrize("filename, expected", [
    ("house-hold_12.xlsx", "house-hold_12"),
    ("12 households.xlsx", "f_12_households"),
    ("_house.xlsx", "f__house"), ("-house.xlsx", "f_-house"),
    ("ménage.東京.xlsx", "m_nage___"), ("東京.xlsx", "f___"),
    ("a+b (c).xlsx", "a_b__c_"),
])
@pytest.mark.parametrize("settings", [None, [["form_id", "form_title"], [None, None]]])
def test_missing_form_id_is_sanitized_from_filename(workbook_factory, filename, expected, settings):
    result = convert_xlsform(workbook_factory(filename=filename, settings=settings))
    assert result.form_id == expected
    assert result.title == expected
    assert primary_root(result).get("id") == expected


def test_settings_are_read_only_from_row_two(workbook_factory):
    result = convert_xlsform(workbook_factory(settings=[
        ["form_id", "form_title", "version"], ["first", "First title", "001"],
        ["second", "Wrong title", "999"]]))
    assert (result.form_id, result.title, result.version) == ("first", "First title", "001")


def test_empty_title_defaults_to_explicit_form_id(workbook_factory):
    result = convert_xlsform(workbook_factory(settings=[["form_id", "form_title"], ["explicit", None]]))
    assert result.title == "explicit"


def test_explicit_title_survives_filename_id_fallback(workbook_factory):
    result = convert_xlsform(workbook_factory(settings=[["form_id", "form_title"], [None, "My title"]]))
    assert (result.form_id, result.title) == ("form", "My title")


@pytest.mark.parametrize("settings", [[], [["form_id", "form_title", "version"]],
                                       [["form_title"], [None]]])
def test_empty_settings_produce_filename_defaults(workbook_factory, settings):
    result = convert_xlsform(workbook_factory(settings=settings))
    assert (result.form_id, result.title, result.version) == ("form", "form", None)


@pytest.mark.parametrize("value, expected", [
    (None, None), ("", None), (2407241039, "2407241039"),
    (2407241039.0, "2407241039"), (0, "0"), (12.5, "12.5"),
    ("002407241039", "002407241039"), ("2407241039.0", "2407241039.0"),
    ("release-é", "release-é"), (" 001 ", " 001 "),
])
def test_version_preserves_text_and_formats_whole_numbers(workbook_factory, value, expected):
    result = convert_xlsform(workbook_factory(settings=[["form_id", "version"], ["form", value]]))
    assert result.version == expected
    assert primary_root(result).get("version") == expected
    if expected is None:
        assert "version" not in primary_root(result).attrib


def test_header_whitespace_is_trimmed_on_all_sheets(workbook_factory):
    result = convert_xlsform(workbook_factory(
        survey=[[" type ", "\tname ", " label "], ["select_one opts", "q", "Question"]],
        choices=[[" list_name ", " value ", " label "], ["opts", "a", "A"]],
        settings=[[" form_id ", " form_title ", " version "], ["trimmed", "Title", "12"]]))
    assert (result.form_id, result.title, result.version) == ("trimmed", "Title", "12")
    assert result.fields == (FieldInfo("q", "select_one opts", "/data/q", 2),)


def test_unicode_and_xml_special_characters_survive_conversion(workbook_factory):
    title = "Café & 東京 <household>"
    label = 'Question <one> & "two" — café 東京'
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label"], ["text", "q", label]],
        settings=[["form_id", "form_title"], ["unicode_form", title]]))
    assert result.title == title
    assert title in [node.text for node in elements(result.xform, "title")]
    assert label in ["".join(node.itertext()) for node in elements(result.xform, "label")]


def test_unrelated_sheets_are_ignored(workbook_factory):
    path = workbook_factory()
    expected = convert_xlsform(path)
    path = workbook_factory(extra_sheets={"help-survey": [["not", "valid"], ["=1+1", "stuff"]],
                                         "arbitrary": [["begin group", "bad"]]})
    assert convert_xlsform(path) == expected


def test_empty_rows_are_ignored_but_original_row_numbers_remain(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label"], [], ["text", "first", "First"], [], [],
        ["integer", "last", "Last"], []]))
    assert result.fields == (FieldInfo("first", "text", "/data/first", 3),
                             FieldInfo("last", "integer", "/data/last", 6))


def test_empty_choices_and_settings_rows_do_not_create_entries(workbook_factory):
    result = convert_xlsform(workbook_factory(
        survey=[["type", "name", "label"], ["select_one opts", "q", "Q"]],
        choices=[["list_name", "name", "label"], [], ["opts", "a", "A"], []],
        settings=[["form_id", "form_title"], ["form", "Title"], []]))
    values = [node.text for item in elements(result.xform, "item") for node in elements(item, "value")]
    assert values == ["a"]


def test_catalog_follows_sheet_order_including_groups_repeats_and_metadata(workbook_factory):
    rows = [
        ["type", "name", "label", "calculation"],
        ["start", "started"], ["end", "ended"], ["deviceid", "device"],
        ["text", "outside", "Outside"], ["begin group", "household", "Household"],
        ["note", "intro", "Read this"], ["begin repeat", "members", "Members"],
        ["integer", "age", "Age"], ["begin group", "details", "Details"],
        ["calculate", "doubled", None, "${age} * 2"], ["end group"],
        ["end repeat"], ["end group"], ["text", "after", "After"],
    ]
    result = convert_xlsform(workbook_factory(survey=rows))
    expected = [
        ("started", "start", "/data/started", 2), ("ended", "end", "/data/ended", 3),
        ("device", "deviceid", "/data/device", 4), ("outside", "text", "/data/outside", 5),
        ("household", "begin group", "/data/household", 6),
        ("intro", "note", "/data/household/intro", 7),
        ("members", "begin repeat", "/data/household/members", 8),
        ("age", "integer", "/data/household/members/age", 9),
        ("details", "begin group", "/data/household/members/details", 10),
        ("doubled", "calculate", "/data/household/members/details/doubled", 11),
        ("after", "text", "/data/after", 15),
    ]
    assert result.fields == tuple(FieldInfo(*item) for item in expected)
    assert all(field.xpath in instance_paths(result) for field in result.fields)
    assert all("[" not in field.xpath for field in result.fields)


def test_conversion_is_deterministic_and_never_modifies_input(workbook_factory):
    path = workbook_factory(survey=[["type", "name", "label", "disabled"],
        ["text", "q", "Café 東京"], ["caseid", "case"], ["text", "omitted", "Omitted", "yes"]])
    before = path.read_bytes()
    first = convert_xlsform(path)
    second = convert_xlsform(path)
    assert first.xform == second.xform
    assert first.fields == second.fields
    assert first.warnings == second.warnings
    assert path.read_bytes() == before


def test_cached_formula_result_is_used_for_survey_cells(workbook_factory, set_formula_cache):
    path = workbook_factory(survey=[["type", "name", "label"], ["text", "q", '=CONCAT("Cache", "d")']])
    set_formula_cache(path, 1, "C2", "Cached café 東京")
    before = path.read_bytes()
    result = convert_xlsform(path)
    assert "Cached café 東京" in "".join(elements(result.xform, "body")[0].itertext())
    assert "CONCAT" not in result.xform
    assert path.read_bytes() == before


def test_formula_without_cache_counts_as_empty_setting(workbook_factory):
    path = workbook_factory(settings=[["form_id", "form_title", "version"],
                                      ["form", "Title", "=1+1"]])
    result = convert_xlsform(path)
    assert result.version is None
    assert "version" not in primary_root(result).attrib


def test_cached_numeric_formula_result_is_used_for_version(workbook_factory, set_formula_cache):
    path = workbook_factory(settings=[["form_id", "version"], ["form", "=2407241038+1"]])
    set_formula_cache(path, 2, "B2", 2407241039)
    assert convert_xlsform(path).version == "2407241039"


def test_cached_formula_result_is_used_for_choices(workbook_factory, set_formula_cache):
    path = workbook_factory(survey=[["type", "name", "label"], ["select_one opts", "q", "Q"]],
        choices=[["list_name", "value", "label"], ["opts", '="a"', "Alpha"]])
    set_formula_cache(path, 2, "B2", "a")
    result = convert_xlsform(path)
    assert "a" in [node.text for node in elements(result.xform, "value")]


def test_cached_formula_result_controls_disabled_rows(workbook_factory, set_formula_cache):
    path = workbook_factory(survey=[["type", "name", "label", "disabled"],
        ["text", "gone", "Gone", '=IF(1=1,"yes","no")'], ["text", "kept", "Kept"]])
    set_formula_cache(path, 1, "D2", "yes")
    assert [field.name for field in convert_xlsform(path).fields] == ["kept"]


def test_uncached_formula_does_not_disable_a_row(workbook_factory):
    path = workbook_factory(survey=[["type", "name", "label", "disabled"],
        ["text", "q", "Q", '="yes"']])
    assert [field.name for field in convert_xlsform(path).fields] == ["q"]


def test_uncached_formula_row_is_completely_empty_and_ignored(workbook_factory):
    path = workbook_factory(survey=[["type", "name", "label"], ['="text"', '="ghost"', '="Ghost"'],
                                   ["text", "q", "Q"]])
    assert convert_xlsform(path).fields == (FieldInfo("q", "text", "/data/q", 3),)
