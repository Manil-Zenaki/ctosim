"""UTF-8 output, exact JSON layout, and the public command line."""

import json
from pathlib import Path
import subprocess
import sys

import pytest

from ctosim_xlsform import ConversionError, ConversionResult, FieldInfo, convert_xlsform


@pytest.fixture
def result_record():
    return ConversionResult(
        xform='<?xml version="1.0" encoding="UTF-8"?><data id="form">Café 東京</data>\n',
        form_id="form", title="Café 東京", version="0012",
        fields=(FieldInfo("first", "text", "/data/first", 2),
                FieldInfo("audit", "audio audit", "/data/group/audit", 5)),
        warnings=("warning not included in catalog",))


def expected_catalog(result):
    return {"form_id": result.form_id, "title": result.title, "version": result.version,
            "fields": [{"name": field.name, "type": field.type, "xpath": field.xpath, "row": field.row}
                       for field in result.fields]}


def assert_catalog(path, result):
    raw = path.read_bytes()
    text = raw.decode("utf-8")
    expected = expected_catalog(result)
    assert json.loads(text) == expected
    # Unicode escaping is unspecified; both are valid UTF-8 JSON. All other
    # formatting, ordering, indentation, and the final newline are contractual.
    normalized = text.replace("\r\n", "\n")
    assert normalized in [json.dumps(expected, indent=2, ensure_ascii=ascii_only) + "\n"
                          for ascii_only in (True, False)]


def assert_xml_written(path, result):
    assert path.read_text(encoding="utf-8") == result.xform.replace("\r\n", "\n")


@pytest.mark.parametrize("filename, catalog_name", [
    ("form.xml", "form.fields.json"), ("form.final.xml", "form.final.fields.json"),
    ("form", "form.fields.json"), ("form.other", "form.fields.json"),
])
@pytest.mark.parametrize("path_kind", [str, Path])
def test_write_uses_default_catalog_path_and_exact_xml(tmp_path, result_record, filename, catalog_name, path_kind):
    destination = tmp_path / filename
    returned = result_record.write(path_kind(destination))
    assert returned is None
    assert_xml_written(destination, result_record)
    assert_catalog(tmp_path / catalog_name, result_record)


@pytest.mark.parametrize("path_kind", [str, Path])
def test_write_accepts_explicit_catalog_path(tmp_path, result_record, path_kind):
    xml = tmp_path / "result.xml"
    catalog = tmp_path / "custom-catalog.json"
    result_record.write(path_kind(xml), path_kind(catalog))
    assert_xml_written(xml, result_record)
    assert_catalog(catalog, result_record)
    assert not xml.with_suffix(".fields.json").exists()


def test_write_overwrites_both_existing_files_without_trailing_old_content(tmp_path, result_record):
    xml = tmp_path / "result.xml"
    catalog = xml.with_suffix(".fields.json")
    xml.write_bytes(b"old xml" * 1000)
    catalog.write_bytes(b"old json" * 1000)
    result_record.write(xml)
    assert_xml_written(xml, result_record)
    assert_catalog(catalog, result_record)


def test_catalog_represents_absent_version_as_null_and_empty_fields_as_array(tmp_path):
    result = ConversionResult("<data/>", "empty", "Empty", None, (), ())
    xml = tmp_path / "empty.xml"
    result.write(xml)
    assert_catalog(xml.with_suffix(".fields.json"), result)


def test_real_conversion_can_be_written_with_its_catalog(workbook_factory, tmp_path):
    result = convert_xlsform(workbook_factory(survey=[["type", "name", "label"],
        ["text", "q", "Café 東京"], ["caseid", "case"]]))
    xml = tmp_path / "result.xml"
    result.write(xml)
    assert_xml_written(xml, result)
    assert_catalog(xml.with_suffix(".fields.json"), result)


def run_cli(*arguments, cwd=None):
    completed = subprocess.run([sys.executable, "-m", "ctosim_xlsform", *map(str, arguments)],
                               capture_output=True, timeout=60, check=False, cwd=cwd)
    # Warning/error fixtures are ASCII so this also works with Windows' locale.
    return completed.returncode, completed.stdout.decode("utf-8", errors="replace"), completed.stderr.decode("utf-8", errors="replace")


def test_cli_converts_and_writes_both_files_like_write(workbook_factory, tmp_path):
    source = workbook_factory(filename="input with spaces.xlsx")
    expected = convert_xlsform(source)
    xml = tmp_path / "output with spaces.xml"
    status, _, stderr = run_cli(source, "-o", xml)
    assert status == 0, stderr
    assert_xml_written(xml, expected)
    assert_catalog(xml.with_suffix(".fields.json"), expected)


def test_cli_prints_each_warning_on_its_own_stderr_line_in_order(workbook_factory, tmp_path):
    source = workbook_factory(survey=[["type", "name", "label", "disabled"],
        ["caseid", "case"], ["text", "gone", "Gone", "yes"],
        ["enumerator", "who", "Who"]])
    expected = convert_xlsform(source)
    status, _, stderr = run_cli(source, "-o", tmp_path / "output.xml")
    assert status == 0
    assert stderr.splitlines() == list(expected.warnings)


@pytest.mark.parametrize("failure", ["missing", "invalid", "missing_survey", "missing_list"])
def test_cli_conversion_error_prints_message_exits_one_and_writes_no_file(workbook_factory, tmp_path, failure):
    if failure == "missing":
        source = tmp_path / "missing.xlsx"
    elif failure == "invalid":
        source = tmp_path / "broken.xlsx"
        source.write_bytes(b"not a workbook")
    elif failure == "missing_survey":
        source = workbook_factory(omit_survey=True)
    else:
        source = workbook_factory(survey=[["type", "name", "label"], ["select_one missing", "q", "Q"]])
    with pytest.raises(ConversionError) as caught:
        convert_xlsform(source)
    before = {path.name for path in tmp_path.iterdir()}
    xml = tmp_path / "output.xml"
    status, _, stderr = run_cli(source, "-o", xml)
    assert status == 1
    assert str(caught.value) in stderr
    assert "Traceback (most recent call last)" not in stderr
    assert {path.name for path in tmp_path.iterdir()} == before


def test_cli_conversion_failure_preserves_existing_output_files(tmp_path):
    xml = tmp_path / "output.xml"
    catalog = xml.with_suffix(".fields.json")
    xml.write_bytes(b"existing XML")
    catalog.write_bytes(b"existing catalog")
    status, _, _ = run_cli(tmp_path / "missing.xlsx", "-o", xml)
    assert status == 1
    assert xml.read_bytes() == b"existing XML"
    assert catalog.read_bytes() == b"existing catalog"


@pytest.mark.parametrize("arguments", [[], ["input.xlsx"], ["-o", "output.xml"],
    ["input.xlsx", "-o"], ["input.xlsx", "-o", "output.xml", "--unknown-option"]])
def test_cli_missing_arguments_and_unknown_options_print_usage_and_exit_two(arguments, tmp_path):
    status, _, stderr = run_cli(*arguments, cwd=tmp_path)
    assert status == 2
    assert "usage" in stderr.casefold()
