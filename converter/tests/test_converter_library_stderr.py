"""Amendment 6: library diagnostics do not leak into converter stderr.

The real workbook fixture contains an unsupported worksheet extension. Synthetic
diagnostics are injected only at the standard-library ZIP read boundary, never
into converter implementation details.
"""

from contextlib import contextmanager
import logging
from pathlib import Path
import sys
import warnings
import xml.etree.ElementTree as ET
from zipfile import ZipFile

from openpyxl import load_workbook
import pytest

from ctosim_xlsform import ConversionError, convert_xlsform
from test_converter_output import assert_catalog, assert_xml_written, run_cli
from xform_assertions import assert_row_mentioned


@contextmanager
def _warnings_to_stderr():
    # pytest normally intercepts warnings separately. Exercise the ordinary
    # Python stderr behavior explicitly, with no dependence on pytest filters.
    with warnings.catch_warnings():
        warnings.simplefilter("always")

        def show(message, category, filename, lineno, file=None, line=None):
            print(warnings.formatwarning(message, category, filename, lineno, line),
                  file=file or sys.stderr, end="")

        warnings.showwarning = show
        yield


@pytest.fixture
def unsupported_extension():
    def add(path):
        member = "xl/worksheets/sheet1.xml"
        namespace = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
        with ZipFile(path) as archive:
            contents = {info.filename: archive.read(info.filename) for info in archive.infolist()}
        root = ET.fromstring(contents[member])
        extensions = ET.SubElement(root, namespace + "extLst")
        # OOXML's data-validation extension: harmless to these literal cells,
        # but openpyxl warns that this unsupported feature will be removed.
        ET.SubElement(extensions, namespace + "ext", {
            "uri": "{CCE6A557-97BC-4B89-ADB6-D9C93CAAB3DF}"})
        contents[member] = ET.tostring(root, encoding="utf-8", xml_declaration=True)
        with ZipFile(path, "w") as archive:
            for name, content in contents.items():
                archive.writestr(name, content)

        # Establish that the test input really does trigger a dependency warning.
        # This prevents a silent/vacuous pass if the fixture stops exercising it.
        with warnings.catch_warnings(record=True) as emitted:
            warnings.simplefilter("always")
            workbook = load_workbook(path, data_only=True)
            workbook.close()
        assert any("extension" in str(w.message).lower() and
                   "not supported" in str(w.message).lower() for w in emitted)
        return path

    return add


def _source(factory, own_warnings=False, invalid=False):
    rows = [["type", "name", "label", "disabled"], ["text", "q", "Question"]]
    if own_warnings:
        rows += [["enumerator", "who", "Who"], ["text", "gone", "Gone", "yes"],
                 ["caseid", "case"]]
    if invalid:
        rows += [["select_one missing", "bad", "Bad"]]
    return factory(survey=rows)


def _assert_own_warnings(result):
    assert len(result.warnings) == 3
    for message, row, name, kind in zip(result.warnings, (3, 4, 5),
                                       ("who", "gone", "case"), ("enumerator", None, "caseid")):
        assert name in message
        assert_row_mentioned(message, row)
        if kind:
            assert kind in message


@pytest.mark.parametrize("own_warnings", [False, True])
def test_api_suppresses_real_spreadsheet_warning_and_preserves_own_warnings(
        workbook_factory, unsupported_extension, capfd, own_warnings):
    path = unsupported_extension(_source(workbook_factory, own_warnings))
    before = path.read_bytes()
    capfd.readouterr()
    with _warnings_to_stderr():
        result = convert_xlsform(path)
    stderr = capfd.readouterr().err
    # API warning printing is not specified; any printed lines must be its own.
    assert all(line in result.warnings for line in stderr.splitlines())
    if own_warnings:
        _assert_own_warnings(result)
    else:
        assert result.warnings == ()
        assert stderr == ""
    assert path.read_bytes() == before


@pytest.mark.parametrize("own_warnings", [False, True])
def test_cli_prints_only_own_warnings_when_workbook_triggers_library_warning(
        workbook_factory, unsupported_extension, tmp_path, own_warnings):
    path = unsupported_extension(_source(workbook_factory, own_warnings))
    with _warnings_to_stderr():
        expected = convert_xlsform(path)
    if own_warnings:
        _assert_own_warnings(expected)
    else:
        assert expected.warnings == ()
    destination = tmp_path / "converted.xml"
    status, _, stderr = run_cli(path, "-o", destination)
    assert status == 0, stderr
    assert stderr.splitlines() == list(expected.warnings)
    assert_xml_written(destination, expected)
    assert_catalog(destination.with_suffix(".fields.json"), expected)


def test_cli_keeps_conversion_error_but_suppresses_preceding_library_warning(
        workbook_factory, unsupported_extension, tmp_path):
    path = unsupported_extension(_source(workbook_factory, invalid=True))
    with _warnings_to_stderr(), pytest.raises(ConversionError) as caught:
        convert_xlsform(path)
    message = str(caught.value)
    assert path.name in message
    assert_row_mentioned(message, 3)
    destination = tmp_path / "failed.xml"
    before = path.read_bytes()
    status, _, stderr = run_cli(path, "-o", destination)
    assert status == 1
    assert stderr.strip() == message.strip()
    assert not destination.exists()
    assert not destination.with_suffix(".fields.json").exists()
    assert path.read_bytes() == before


@pytest.mark.parametrize("diagnostic", ["python-warning", "stderr-print", "logging"])
@pytest.mark.parametrize("invalid", [False, True], ids=["successful-conversion", "conversion-error"])
def test_dependency_diagnostics_are_suppressed_on_success_and_error(
        workbook_factory, monkeypatch, capfd, diagnostic, invalid):
    path = _source(workbook_factory, invalid=invalid)
    original = ZipFile.open
    triggered = []
    marker = "AMENDMENT_6_DEPENDENCY_DIAGNOSTIC"

    def noisy_open(archive, *args, **kwargs):
        if not triggered and archive.filename and Path(archive.filename).resolve() == path.resolve():
            triggered.append(True)
            if diagnostic == "python-warning":
                warnings.warn(marker, UserWarning)
            elif diagnostic == "stderr-print":
                print(marker, file=sys.stderr)
            else:
                logger = logging.getLogger("amendment6.dependency")
                handler = logging.StreamHandler(sys.stderr)
                logger.addHandler(handler)
                old_propagate = logger.propagate
                logger.propagate = False
                try:
                    logger.warning(marker)
                finally:
                    logger.removeHandler(handler)
                    handler.close()
                    logger.propagate = old_propagate
        return original(archive, *args, **kwargs)

    monkeypatch.setattr(ZipFile, "open", noisy_open)
    capfd.readouterr()
    with _warnings_to_stderr():
        if invalid:
            with pytest.raises(ConversionError) as caught:
                convert_xlsform(path)
            assert path.name in str(caught.value)
            assert_row_mentioned(str(caught.value), 3)
            assert marker not in str(caught.value)
        else:
            result = convert_xlsform(path)
            assert result.warnings == ()
        assert triggered, "The fixture must actually reach the dependency read boundary"
        assert marker not in capfd.readouterr().err
        # Suppression is scoped to conversion, including its exceptional exit.
        print("caller stderr survives", file=sys.stderr)
        warnings.warn("caller warning survives", UserWarning)
        stderr = capfd.readouterr().err
        assert "caller stderr survives" in stderr
        assert "caller warning survives" in stderr
