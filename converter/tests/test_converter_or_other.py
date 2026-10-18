"""Spec 02 Amendment 6, through the public API and generated XForm only."""

import ast
from itertools import product
import posixpath
import re

import pytest

from ctosim_xlsform import ConversionError, FieldInfo, convert_xlsform
from xform_assertions import (
    assert_row_mentioned, bind_for, elements, instance_paths, local_name,
)
from test_converter_output import assert_catalog, assert_xml_written, run_cli
from test_converter_disabled_rows_amendment8 import assert_warnings


SELECT_TYPES = ("select_one", "select_multiple")


def _make(factory, rows, *, choices=None, settings=None):
    headers = list(dict.fromkeys(key for row in rows for key in row))
    return factory(
        survey=[headers] + [[row.get(key) for key in headers] for row in rows],
        choices=choices if choices is not None else [
            ["list_name", "name", "label"], ["items", "a", "Alpha"],
            ["items", "b", "Beta"]],
        settings=settings,
    )


def _question(kind, name="q", **columns):
    return {"type": f"{kind} items or_other", "name": name, "label": name, **columns}


def _control(result, path, tag):
    xml = result.xform if hasattr(result, "xform") else result
    matches = [node for node in elements(xml, tag)
               if node.get("ref") == "/data/" + path]
    assert len(matches) == 1, (tag, path)
    return matches[0]


def _choice_nodes(result, path, kind):
    """Resolve the list actually used, without guessing an augmented list ID.

    Amendment 1 fixes the base list representation. Amendment 6 does not say
    whether the appended Other is inline or part of a secondary instance.
    """
    control = _control(result, path, "select1" if kind == "select_one" else "select")
    choices = []
    for child in control:
        if local_name(child) == "itemset":
            match = re.fullmatch(
                r"\s*instance\(\s*(['\"])(.*?)\1\s*\)/root/item\s*",
                child.get("nodeset", ""))
            assert match, "These fixtures have no choice filter"
            instances = [node for node in elements(result.xform, "instance")
                         if node.get("id") == match.group(2)]
            assert len(instances) == 1
            for item in elements(instances[0], "item"):
                names = elements(item, "name")
                assert len(names) == 1
                choices.append((names[0].text, item))
        elif local_name(child) == "item":
            values = elements(child, "value")
            assert len(values) == 1
            choices.append((values[0].text, child))
    assert choices, "The select must offer choices"
    return choices


def _label(result, node, language=None):
    labels = elements(node, "label")
    translations = [t for t in elements(result.xform, "translation")
                    if t.get("lang") == language]
    if len(labels) == 1 and not labels[0].get("ref"):
        literal = "".join(labels[0].itertext())
        if not any(t.get("id") == literal for translation in translations
                   for t in elements(translation, "text")):
            return literal
    assert len(translations) == 1, language
    # Secondary choice itext references need not use a particular child tag.
    references = set()
    for child in node.iter():
        for value in [child.text or "", *child.attrib.values()]:
            references.add(value.strip())
            match = re.fullmatch(r"(?:jr:)?itext\(['\"](.*?)['\"]\)", value.strip())
            if match:
                references.add(match.group(1))
    texts = [t for t in elements(translations[0], "text") if t.get("id") in references]
    assert len(texts) == 1, (language, references)
    values = [v for v in elements(texts[0], "value") if v.get("form") is None]
    assert len(values) == 1
    return "".join(values[0].itertext())


def _assert_immediate_text_question(result, path, kind):
    body = elements(result.xform, "body")[0]
    select = _control(body, path, "select1" if kind == "select_one" else "select")
    other = _control(body, path + "_other", "input")
    parents = [node for node in body.iter() if select in list(node)]
    assert len(parents) == 1
    siblings = list(parents[0])
    assert siblings[siblings.index(select) + 1] is other
    binding = bind_for(result, path + "_other")
    assert binding.get("type", "").split(":")[-1] == "string"
    assert "calculate" not in binding.attrib
    assert binding.get("readonly", "false()").strip() != "true()"
    paths = instance_paths(result)
    question_count = paths.count("/data/" + path)
    assert question_count > 0
    # Repeat templates and initial instances can share the same absolute path.
    # The companion must occur as often as its source question.
    assert paths.count("/data/" + path + "_other") == question_count


@pytest.mark.parametrize("kind", SELECT_TYPES)
@pytest.mark.parametrize("value_column", ["name", "value"])
@pytest.mark.parametrize("values", [[("a", "Alpha")],
    [("a", "First"), ("a", "Duplicate"), ("b", "Caf\u00e9 & <tea>")]],
    ids=["one-choice", "duplicates-and-unicode"])
def test_or_other_appends_exactly_one_choice_after_every_original_choice(
        workbook_factory, kind, value_column, values):
    result = convert_xlsform(_make(workbook_factory, [_question(kind)], choices=[
        ["list_name", value_column, "label"],
        *[["items", value, label] for value, label in values]]))
    assert [(value, _label(result, node)) for value, node in _choice_nodes(result, "q", kind)] == [
        *values, ("other", "Other")]


@pytest.mark.parametrize("kind", SELECT_TYPES)
@pytest.mark.parametrize("scope", ["root", "group", "repeat", "nested"])
def test_companion_is_immediate_in_the_same_scope_and_catalog_uses_source_row(
        workbook_factory, kind, scope):
    opening = []
    closing = []
    prefix = ""
    if scope == "nested":
        opening.append(dict(type="begin group", name="outer", label="Outer"))
        closing.insert(0, dict(type="end group"))
        prefix = "outer/"
    if scope != "root":
        container = "repeat" if scope in ("repeat", "nested") else "group"
        opening.append(dict(type=f"begin {container}", name="section", label="Section"))
        closing.insert(0, dict(type=f"end {container}"))
        prefix += "section/"
    rows = [*opening, _question(kind), dict(type="text", name="after", label="After"),
            *closing, dict(type="text", name="outside", label="Outside")]
    result = convert_xlsform(_make(workbook_factory, rows))
    _assert_immediate_text_question(result, prefix + "q", kind)
    assert _label(result, _control(result, prefix + "q_other", "input")) == "Specify other."
    expected = []
    current = ""
    for row_number, row in enumerate(opening, 2):
        current += row["name"] + "/"
        expected.append(FieldInfo(row["name"], row["type"], "/data/" + current.rstrip("/"), row_number))
    source_row = len(opening) + 2
    expected.extend([
        FieldInfo("q", f"{kind} items or_other", "/data/" + prefix + "q", source_row),
        FieldInfo("q_other", "text", "/data/" + prefix + "q_other", source_row),
        FieldInfo("after", "text", "/data/" + prefix + "after", source_row + 1),
        FieldInfo("outside", "text", "/data/outside", len(rows) + 1),
    ])
    assert result.fields == tuple(expected)


@pytest.mark.parametrize("kind", SELECT_TYPES)
def test_type_whitespace_is_normalized_before_recognizing_or_other(workbook_factory, kind):
    result = convert_xlsform(_make(workbook_factory, [
        _question(kind, type=f" \t{kind}\t items \n  or_other  ")]))
    assert result.fields == (FieldInfo("q", f"{kind} items or_other", "/data/q", 2),
                             FieldInfo("q_other", "text", "/data/q_other", 2))
    _assert_immediate_text_question(result, "q", kind)


@pytest.mark.parametrize("kind", SELECT_TYPES)
@pytest.mark.parametrize("default_language", [None, "English"])
def test_generated_labels_are_english_in_every_form_language(
        workbook_factory, kind, default_language):
    settings = [["form_id", "default_language"], ["translated", default_language]]
    result = convert_xlsform(_make(workbook_factory, [_question(kind, **{
        "label": "Question", "label:French": "Question fran\u00e7aise",
        "label:\u65e5\u672c\u8a9e": "\u8cea\u554f", "hint:Haousa": "Hint only language"})],
        choices=[["list_name", "name", "label", "label:French", "label:\u65e5\u672c\u8a9e"],
                 ["items", "a", "Apple", "Pomme", "\u308a\u3093\u3054"]], settings=settings))
    languages = {t.get("lang") for t in elements(result.xform, "translation")}
    assert languages == {default_language or "default", "French", "\u65e5\u672c\u8a9e", "Haousa"}
    choices = _choice_nodes(result, "q", kind)
    assert [v for v, _ in choices] == ["a", "other"]
    for language in languages:
        assert _label(result, choices[-1][1], language) == "Other"
        assert _label(result, _control(result, "q_other", "input"), language) == "Specify other."
    for language, expected in [(default_language or "default", "Apple"),
                               ("French", "Pomme"), ("\u65e5\u672c\u8a9e", "\u308a\u3093\u3054")]:
        assert _label(result, choices[0][1], language) == expected


@pytest.mark.parametrize("kind", SELECT_TYPES)
def test_translated_only_form_with_calculate_here_keeps_generated_labels(workbook_factory, kind):
    result = convert_xlsform(_make(workbook_factory, [
        {"type": f"{kind} items or_other", "name": "q", "label:French": "Question"},
        {"type": "calculate_here", "name": "checkpoint", "calculation": "42"}],
        choices=[["list_name", "name", "label:French"], ["items", "a", "Pomme"]]))
    for translation in elements(result.xform, "translation"):
        language = translation.get("lang")
        assert _label(result, _choice_nodes(result, "q", kind)[-1][1], language) == "Other"
        assert _label(result, _control(result, "q_other", "input"), language) == "Specify other."
    assert bind_for(result, "checkpoint").get("{urn:ctosim:xforms}calculate-here") == "42"
    _assert_immediate_text_question(result, "q", kind)


@pytest.mark.parametrize("first_kind, second_kind", list(product(SELECT_TYPES, repeat=2)))
def test_shared_list_has_one_other_per_opted_in_question_and_none_for_plain_selects(
        workbook_factory, first_kind, second_kind):
    result = convert_xlsform(_make(workbook_factory, [
        _question(first_kind, "plain_before", type=f"{first_kind} items"),
        _question(first_kind, "first"), _question(second_kind, "second"),
        _question(second_kind, "plain_after", type=f"{second_kind} items")]))
    for name, kind in [("plain_before", first_kind), ("first", first_kind),
                       ("second", second_kind), ("plain_after", second_kind)]:
        expected = ["a", "b"] + (["other"] if name in ("first", "second") else [])
        assert [v for v, _ in _choice_nodes(result, name, kind)] == expected
    assert [f.name for f in result.fields] == [
        "plain_before", "first", "first_other", "second", "second_other", "plain_after"]
    for name, kind in [("first", first_kind), ("second", second_kind)]:
        _assert_immediate_text_question(result, name, kind)


@pytest.mark.parametrize("kind", SELECT_TYPES)
def test_each_companion_depends_on_its_own_question(workbook_factory, kind):
    result = convert_xlsform(_make(workbook_factory, [
        _question(kind, "first"), _question(kind, "second")]))
    for first, second in product(("a", "other"), repeat=2):
        values = {"/data/first": first, "/data/second": second}
        assert _effective_relevant(result, "first_other", values) == (first == "other")
        assert _effective_relevant(result, "second_other", values) == (second == "other")


@pytest.mark.parametrize("kind", SELECT_TYPES)
def test_source_name_ending_in_other_still_gets_the_literal_other_suffix(workbook_factory, kind):
    result = convert_xlsform(_make(workbook_factory, [_question(kind, "q_other")]))
    assert result.fields == (
        FieldInfo("q_other", f"{kind} items or_other", "/data/q_other", 2),
        FieldInfo("q_other_other", "text", "/data/q_other_other", 2))
    _assert_immediate_text_question(result, "q_other", kind)


@pytest.mark.parametrize("kind", SELECT_TYPES)
def test_generated_field_does_not_shift_later_catalog_rows_or_conversion_warnings(workbook_factory, kind):
    result = convert_xlsform(_make(workbook_factory, [
        {}, _question(kind), {}, dict(type="speed violations count", name="speed"),
        _question(kind, "second", minimum_seconds=2.5)]))
    assert [(field.name, field.row) for field in result.fields] == [
        ("q", 3), ("q_other", 3), ("speed", 5), ("second", 6), ("second_other", 6)]
    assert len(result.warnings) == 1
    assert "speed" in result.warnings[0] and "speed violations count" in result.warnings[0]
    assert_row_mentioned(result.warnings[0], 5)
    assert bind_for(result, "second").get("{urn:ctosim:xforms}minimum-seconds") == "2.5"


def _evaluate_relevant(expression, values, context):
    """Evaluate only the XPath subset used in these small truth-table fixtures.

    Normalize node paths and equality, then interpret a restricted Python AST;
    this accepts parentheses/spacing changes without calling production code.
    Unknown syntax fails explicitly instead of silently skipping an assertion.
    """
    def node_value(match):
        path = match.group()
        absolute = path if path.startswith("/") else posixpath.normpath(context + "/" + path)
        assert absolute in values, (absolute, expression)
        return repr(values[absolute])

    expression = re.sub(r"/data(?:/[A-Za-z_][\w-]*)+|(?:\.\./)+[A-Za-z_][\w/-]*",
                        node_value, expression)
    expression = re.sub(r"(?<![!<>=])=(?!=)", "==", expression)
    expression = re.sub(r"\bnormalize-space\s*\(", "normalize_space(", expression)
    tree = ast.parse(expression.strip(), mode="eval")

    def evaluate(node):
        if isinstance(node, ast.Constant) and isinstance(node.value, (str, bool)):
            return node.value
        if isinstance(node, ast.BoolOp):
            values_ = [bool(evaluate(value)) for value in node.values]
            if isinstance(node.op, ast.And):
                return all(values_)
            if isinstance(node.op, ast.Or):
                return any(values_)
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.Not):
            return not evaluate(node.operand)
        if isinstance(node, ast.Compare) and len(node.ops) == 1:
            left, right = evaluate(node.left), evaluate(node.comparators[0])
            if isinstance(node.ops[0], ast.Eq):
                return left == right
            if isinstance(node.ops[0], ast.NotEq):
                return left != right
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and not node.keywords:
            args = [evaluate(arg) for arg in node.args]
            if node.func.id == "selected" and len(args) == 2:
                return args[1] in args[0].split()
            if node.func.id == "contains" and len(args) == 2:
                return args[1] in args[0]
            if node.func.id == "concat":
                return "".join(args)
            if node.func.id == "normalize_space" and len(args) == 1:
                return " ".join(args[0].split())
            if node.func.id in ("true", "false") and not args:
                return node.func.id == "true"
        raise AssertionError(f"Unsupported truth-table expression: {expression!r}")

    return bool(evaluate(tree.body))


def _effective_relevant(result, path, values):
    # Relevance inherited from group/repeat ancestors counts, too.
    parts = path.split("/")
    for length in range(1, len(parts) + 1):
        scope = "/data/" + "/".join(parts[:length])
        bindings = [b for b in elements(result.xform, "bind") if b.get("nodeset") == scope]
        for binding in bindings:
            if binding.get("relevant") and not _evaluate_relevant(binding.get("relevant"), values, scope):
                return False
    return True


@pytest.mark.parametrize("kind", SELECT_TYPES)
@pytest.mark.parametrize("scope", ["root", "group", "repeat"])
@pytest.mark.parametrize("condition", [None, "${gate} = 'yes'",
    "${gate} = 'yes' or ${override} = 'yes'"], ids=["no-condition", "simple", "disjunction"])
def test_companion_relevance_is_question_relevance_and_token_membership(
        workbook_factory, kind, scope, condition):
    rows = [dict(type="text", name=name, label=name) for name in ("gate", "override", "parent")]
    if scope != "root":
        rows.append(dict(type=f"begin {scope}", name="section", label="Section",
                         relevance="${parent} = 'yes'"))
    rows.append(_question(kind, relevance=condition))
    if scope != "root":
        rows.append(dict(type=f"end {scope}"))
    result = convert_xlsform(_make(workbook_factory, rows))
    prefix = "" if scope == "root" else "section/"
    answers = ["", "a", "other", "another", "otherness"]
    if kind == "select_multiple":
        answers += ["a b", "a other", "other b", "a other b", "a another"]
    for gate, override, parent, answer in product(("yes", "no"), ("yes", "no"), ("yes", "no"), answers):
        values = {"/data/gate": gate, "/data/override": override, "/data/parent": parent,
                  "/data/" + prefix + "q": answer}
        question_relevant = condition is None or gate == "yes" or (
            "override" in condition and override == "yes")
        expected = (scope == "root" or parent == "yes") and question_relevant and "other" in answer.split()
        assert _effective_relevant(result, prefix + "q_other", values) == expected, values


@pytest.mark.parametrize("kind", SELECT_TYPES)
@pytest.mark.parametrize("disabled_scope", ["row", "group", "repeat"])
def test_or_other_is_disabled_only_by_its_own_row_and_plain_list_stays_unchanged(
        workbook_factory, kind, disabled_scope):
    rows = [_question(kind, "gone", disabled=" YeS ")]
    warning_name = "gone"
    if disabled_scope != "row":
        rows = [dict(type=f"begin {disabled_scope}", name="omitted", label="Omitted", disabled="yes"),
                _question(kind, "gone"), dict(type=f"end {disabled_scope}")]
        warning_name = "omitted"
    rows.append(_question(kind, "kept", type=f"{kind} items"))
    result = convert_xlsform(_make(workbook_factory, rows))
    expected = []
    if disabled_scope == "row":
        assert not any(path.endswith(("/gone", "/gone_other")) for path in instance_paths(result))
    else:
        expected += [FieldInfo("gone", f"{kind} items or_other", "/data/gone", 3),
                     FieldInfo("gone_other", "text", "/data/gone_other", 3)]
        _assert_immediate_text_question(result, "gone", kind)
        assert _label(result, _control(result, "gone_other", "input")) == "Specify other."
        assert [(value, _label(result, node)) for value, node in _choice_nodes(result, "gone", kind)] == [
            ("a", "Alpha"), ("b", "Beta"), ("other", "Other")]
        assert "/data/omitted" not in instance_paths(result)
    expected.append(FieldInfo("kept", f"{kind} items", "/data/kept", len(rows) + 1))
    assert result.fields == tuple(expected)
    assert [value for value, _ in _choice_nodes(result, "kept", kind)] == ["a", "b"]
    assert_warnings(result, [(2, warning_name, None)],
                    optional_closers=() if disabled_scope == "row" else (4,))


@pytest.mark.parametrize("kind", SELECT_TYPES)
@pytest.mark.parametrize("suffix", ["other", "or_other_extra", "OR_OTHER", "unknown",
                                     "or_other unknown", "unknown or_other"])
def test_unrecognized_trailing_words_raise_conversion_error_with_physical_row(
        workbook_factory, kind, suffix):
    path = _make(workbook_factory, [dict(type="text", name="before", label="Before"), {},
                                  _question(kind, type=f"{kind} items {suffix}")])
    with pytest.raises(ConversionError) as caught:
        convert_xlsform(path)
    assert path.name in str(caught.value)
    assert_row_mentioned(str(caught.value), 4)
    assert any(word in str(caught.value).lower() for word in ("type", "support", "invalid", "unknown", "other"))


@pytest.mark.parametrize("kind", SELECT_TYPES)
def test_or_other_does_not_make_a_missing_choice_list_valid(workbook_factory, kind):
    path = _make(workbook_factory, [_question(kind, type=f"{kind} absent or_other")])
    with pytest.raises(ConversionError) as caught:
        convert_xlsform(path)
    assert path.name in str(caught.value)
    assert_row_mentioned(str(caught.value), 2)
    assert any(word in str(caught.value).lower() for word in ("absent", "choice", "list"))


@pytest.mark.parametrize("kind", SELECT_TYPES)
def test_or_other_is_deterministic_does_not_modify_workbook_and_adds_no_warning(workbook_factory, kind):
    path = _make(workbook_factory, [_question(kind),
        dict(type="enumerator", name="who", label="Who")])
    before = path.read_bytes()
    first = convert_xlsform(path)
    second = convert_xlsform(path)
    assert (second.xform, second.fields, second.warnings) == (first.xform, first.fields, first.warnings)
    assert path.read_bytes() == before
    assert len(first.warnings) == 1
    assert "who" in first.warnings[0] and "enumerator" in first.warnings[0]
    assert_row_mentioned(first.warnings[0], 3)


@pytest.mark.parametrize("kind", SELECT_TYPES)
def test_cli_and_write_include_generated_companion_in_xml_and_json(workbook_factory, tmp_path, kind):
    path = _make(workbook_factory, [_question(kind)])
    expected = convert_xlsform(path)
    assert expected.fields[1] == FieldInfo("q_other", "text", "/data/q_other", 2)
    for destination in (tmp_path / "api.xml", tmp_path / "cli.xml"):
        if destination.stem == "api":
            expected.write(destination)
        else:
            status, _, stderr = run_cli(path, "-o", destination)
            assert status == 0, stderr
            assert stderr == ""
        assert_xml_written(destination, expected)
        assert_catalog(destination.with_suffix(".fields.json"), expected)
