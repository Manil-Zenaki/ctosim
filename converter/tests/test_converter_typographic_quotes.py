"""Spec 02 Amendment 7: curly quotes are data, not XPath delimiters.

Fixtures and expectations use only the spec and the public conversion API.
"""

import json
import re
import subprocess
import sys

import pytest

from ctosim_xlsform import FieldInfo, convert_xlsform
from xform_assertions import assert_choice_list, bind_for, elements, primary_root


QUOTES = ("\u2018", "\u2019", "\u201c", "\u201d")
TEXT = "‘Bonjour’, l’argent, “東京” & <café>"
HERE = "{urn:ctosim:xforms}calculate-here"


def _resolved_text(result, reference, language):
    """Resolve generated itext IDs without prescribing their spelling."""
    match = re.fullmatch(r"\s*(?:jr:)?itext\(\s*(['\"])(.*?)\1\s*\)\s*", reference)
    assert match is not None, reference
    translation = next(node for node in elements(result.xform, "translation")
                       if node.get("lang") == language)
    texts = [node for node in elements(translation, "text")
             if node.get("id") == match.group(2)]
    assert len(texts) == 1
    return ["".join(node.itertext()) for node in elements(texts[0], "value")
            if node.get("form") is None]


@pytest.mark.parametrize("column", ["label", "hint", "constraint message", "required message"])
@pytest.mark.parametrize("translated", [False, True], ids=["literal", "translated"])
def test_typographic_quotes_survive_visible_text_and_message_columns(workbook_factory, column, translated):
    headers = ["type", "name", "label", "constraint", "required"]
    row = ["text", "q", "Question", "true()", "true()"]
    target = column + (":Français" if translated else "")
    if target in headers:
        row[headers.index(target)] = TEXT
    else:
        headers.append(target)
        row.append(TEXT)
    result = convert_xlsform(workbook_factory(survey=[headers, row]))
    if column in ("label", "hint"):
        control = next(node for node in elements(result.xform, "input")
                       if node.get("ref") == "/data/q")
        node, = elements(control, column)
        if node.get("ref"):
            assert _resolved_text(result, node.get("ref"), "Français" if translated else "default") == [TEXT]
        else:
            assert not translated
            assert "".join(node.itertext()) == TEXT
    else:
        attribute = "constraintMsg" if column == "constraint message" else "requiredMsg"
        value, = [value for key, value in bind_for(result, "q").attrib.items()
                  if key.rsplit("}", 1)[-1] == attribute]
        if re.match(r"\s*(?:jr:)?itext\(", value):
            assert _resolved_text(result, value, "Français" if translated else "default") == [TEXT]
        else:
            assert not translated
            assert value == TEXT


@pytest.mark.parametrize("quote", QUOTES, ids=["left-single", "right-single", "left-double", "right-double"])
@pytest.mark.parametrize("delimiter", ["'", '"'], ids=["ascii-single", "ascii-double"])
@pytest.mark.parametrize("field_type, attribute", [("calculate", "calculate"), ("calculate_here", HERE)])
def test_each_curly_quote_is_ordinary_text_inside_either_ascii_string_delimiter(workbook_factory, quote, delimiter, field_type, attribute):
    # Deliberately unpaired curly punctuation catches quote-state toggling.
    literal = f"l{quote}argent selected(/data/q, 7)"
    expression = f"concat({delimiter}{literal}{delimiter}, selected(/data/q, -99))"
    expected = f"concat({delimiter}{literal}{delimiter}, selected(/data/q, '-99'))"
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "calculation"],
        ["text", "q", "Q"], [field_type, "target", None, expression],
    ]))
    assert bind_for(result, "target").get(attribute) == expected


@pytest.mark.parametrize("field_type, attribute", [("calculate", "calculate"), ("calculate_here", HERE)])
def test_spec_apostrophe_example_preserves_string_and_expands_field_reference(workbook_factory, field_type, attribute):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "calculation"], ["integer", "x", "X"],
        [field_type, "target", None, "if(${x}=1, 'l’argent', '')"],
    ]))
    expression = bind_for(result, "target").get(attribute)
    assert expression is not None
    # pyxform may pad expanded paths with whitespace.
    assert re.fullmatch(r"if\(\s*/data/x\s*=1, 'l’argent', ''\)", expression)


@pytest.mark.parametrize("column, attribute", [
    ("relevance", "relevant"), ("constraint", "constraint"),
    ("required", "required"), ("calculation", "calculate"),
    ("choice_filter", "nodeset"), ("repeat_count", "calculate"),
])
def test_curly_quotes_and_numeric_selected_rewriting_coexist_in_every_expression_column(workbook_factory, column, attribute):
    expression = "contains('‘l’argent “東京”’ selected(/data/q, 7)', '’') and selected(/data/q, 2.5)"
    expected = expression.replace("selected(/data/q, 2.5)", "selected(/data/q, '2.5')")
    rows = [["type", "name", "label", column], ["select_multiple opts", "q", "Q"]]
    if column == "repeat_count":
        rows += [["begin repeat", "target", "Target", expression],
                 ["text", "child", "Child"], ["end repeat"]]
    else:
        field_type = "calculate" if column == "calculation" else "select_one opts" if column == "choice_filter" else "text"
        rows.append([field_type, "target", "Target", expression])
    result = convert_xlsform(workbook_factory(survey=rows, choices=[
        ["list_name", "name", "label"], ["opts", "2.5", "Two and a half"],
    ]))
    if column == "choice_filter":
        control = next(node for node in elements(result.xform, "select1")
                       if node.get("ref") == "/data/target")
        itemset, = elements(control, "itemset")
        assert itemset.get("nodeset") == f"instance('opts')/root/item[{expected}]"
    else:
        name = "target_count" if column == "repeat_count" else "target"
        assert bind_for(result, name).get(attribute) == expected


@pytest.mark.parametrize("choice_column", ["name", "value"])
def test_typographic_quotes_in_choice_values_and_labels_are_not_normalized(workbook_factory, choice_column):
    choices = [(f"v{quote}x", f"Label {quote} café") for quote in QUOTES]
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label"], ["select_one opts", "q", "Q"],
    ], choices=[["list_name", choice_column, "label"]]
        + [["opts", value, label] for value, label in choices]))
    assert_choice_list(result, "q", "select1", "opts", choices)


def test_translated_choice_labels_keep_quotes_in_their_own_language(workbook_factory):
    default_label = "‘Other’ “choice”"
    french_label = "L’argent d’aujourd’hui “oui”"
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "label:French"],
        ["select_one opts", "q", "Question", "Question FR"],
    ], choices=[
        ["list_name", "name", "label", "label:French"],
        ["opts", "one", default_label, french_label],
    ]))
    instance = next(node for node in elements(result.xform, "instance") if node.get("id") == "opts")
    item, = elements(instance, "item")
    # Amendment 1 specifies an itext reference but not its child tag. Resolve
    # an ID actually present in the item against each language's text entries.
    references = {value for node in item.iter()
                  for value in [node.text, *node.attrib.values()] if value}
    for language, expected in [("default", default_label), ("French", french_label)]:
        translation = next(node for node in elements(result.xform, "translation")
                           if node.get("lang") == language)
        matches = [node for node in elements(translation, "text")
                   if node.get("id") in references]
        assert len(matches) == 1
        assert ["".join(node.itertext()) for node in elements(matches[0], "value")
                if node.get("form") is None] == [expected]


def test_read_only_expression_keeps_curly_quotes(workbook_factory):
    expression = "contains('‘l’argent “東京”’', '’')"
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "read only"], ["text", "q", "Q", expression],
    ]))
    assert bind_for(result, "q").get("readonly") == expression


def test_typographic_quotes_in_default_values_survive_xml_escaping(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "default"], ["text", "q", "Q", TEXT],
    ]))
    node = next(node for node in primary_root(result) if node.tag.rsplit("}", 1)[-1] == "q")
    assert node.text == TEXT


def test_curly_quotes_in_language_names_are_preserved(workbook_factory):
    language = "Langue ‘locale’ “A”"
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", f"label:{language}"], ["text", "q", "Q", TEXT],
    ]))
    assert {node.get("lang") for node in elements(result.xform, "translation")} == {"default", language}
    control = next(node for node in elements(result.xform, "input") if node.get("ref") == "/data/q")
    label, = elements(control, "label")
    assert _resolved_text(result, label.get("ref"), language) == [TEXT]


def test_cached_formula_text_keeps_typographic_quotes(workbook_factory, set_formula_cache):
    path = workbook_factory(survey=[
        ["type", "name", "label", "calculation"], ["text", "q", '=CONCAT("label")'],
        ["calculate", "target", None, '=CONCAT("expression")'],
    ])
    set_formula_cache(path, 1, "C2", TEXT)
    set_formula_cache(path, 1, "D3", "concat('l’argent “oui”', '‘fin’')")
    result = convert_xlsform(path)
    label, = elements(elements(result.xform, "input")[0], "label")
    assert "".join(label.itertext()) == TEXT
    assert bind_for(result, "target").get("calculate") == "concat('l’argent “oui”', '‘fin’')"


@pytest.mark.parametrize("entry_point", ["write", "cli"])
def test_quotes_survive_title_original_type_catalog_and_utf8_output(workbook_factory, tmp_path, entry_point):
    original_type = "sensor_statistic ‘light’ “mean”"
    path = workbook_factory(survey=[
        ["type", "name", "label"], ["text", "q", TEXT], [original_type, "sensor"],
    ], settings=[["form_id", "form_title"], ["quotes", TEXT]])
    before = path.read_bytes()
    result = convert_xlsform(path)
    assert result.title == TEXT
    assert result.fields[1] == FieldInfo("sensor", original_type, "/data/sensor", 3)
    assert original_type in result.warnings[0]
    assert convert_xlsform(path) == result
    assert path.read_bytes() == before
    output = tmp_path / "quotes.xml"
    if entry_point == "write":
        result.write(output)
    else:
        process = subprocess.run([sys.executable, "-m", "ctosim_xlsform", str(path), "-o", str(output)],
                                 capture_output=True, timeout=60)
        assert process.returncode == 0, process.stderr
    xml = output.read_text(encoding="utf-8")
    assert "".join(elements(xml, "title")[0].itertext()) == TEXT
    assert "".join(elements(elements(xml, "input")[0], "label")[0].itertext()) == TEXT
    catalog = json.loads(output.with_suffix(".fields.json").read_text(encoding="utf-8"))
    assert catalog["title"] == TEXT
    assert catalog["fields"][1] == {"name": "sensor", "type": original_type, "xpath": "/data/sensor", "row": 3}
