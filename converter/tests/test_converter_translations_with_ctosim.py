"""Spec 02 Amendment 5: translations survive custom bind normalization.

Only public converter results are inspected. Workbooks are generated in pytest's
temporary directory; no production helpers or implementation details are used.
"""

from io import StringIO
import re
import xml.etree.ElementTree as ET

import pytest

from ctosim_xlsform import ConversionError, FieldInfo, convert_xlsform
from xform_assertions import assert_row_mentioned, bind_for, elements
from test_converter_disabled_rows_amendment8 import assert_warnings


CTOSIM = "urn:ctosim:xforms"
HERE = f"{{{CTOSIM}}}calculate-here"
MINIMUM = f"{{{CTOSIM}}}minimum-seconds"
SPEED = f"{{{CTOSIM}}}speed-violations"
FEATURES = ("calculate_here", "empty_calculate_here", "minimum", "count", "list", "all")
LANGUAGES = ("French", "Haousa", "Zarma")


def _feature_rows(feature):
    rows = {
        "calculate_here": [dict(type="calculate_here", name="checkpoint", calculation="42")],
        "empty_calculate_here": [dict(type="calculate_here", name="checkpoint")],
        "minimum": [{"type": "text", "name": "timed", "minimum_seconds": 2.5,
                     "label:French": "Question chronométrée"}],
        "count": [dict(type="speed violations count", name="speed_count")],
        "list": [dict(type="speed violations list", name="speed_list")],
    }
    if feature == "all":
        return [row for key in ("calculate_here", "minimum", "count", "list")
                for row in rows[key]]
    return rows[feature]


def _make(workbook_factory, rows, *, settings=None, padded=False):
    # Keep physical rows, and exercise both sides of the custom columns in the
    # header-order case without changing the actual form's field order.
    headers = list(dict.fromkeys(key for row in rows for key in row))
    if padded:
        headers.reverse()
    return workbook_factory(
        survey=[[f" \t{key} " if padded else key for key in headers]]
        + [[row.get(key) for key in headers] for row in rows],
        settings=settings,
    )


def _translations(result):
    translations = elements(result.xform, "translation")
    languages = [node.get("lang") for node in translations]
    assert len(languages) == len(set(languages)), "One translation per language"
    return dict(zip(languages, translations))


def _itext_id(reference):
    match = re.fullmatch(r"\s*(?:jr:)?itext\(\s*(['\"])(.*?)\1\s*\)\s*", reference or "")
    assert match is not None, f"Expected an itext reference, got {reference!r}"
    return match.group(2)


def _control_reference(result, path, tag="label"):
    controls = [node for node in elements(result.xform, "input")
                if node.get("ref") == f"/data/{path}"]
    assert len(controls) == 1, f"Expected one input for {path}"
    labels = elements(controls[0], tag)
    assert len(labels) == 1, f"Expected one {tag} for {path}"
    return _itext_id(labels[0].get("ref"))


def _values(result, language, text_id, form=None):
    translations = _translations(result)
    assert language in translations
    texts = [node for node in elements(translations[language], "text")
             if node.get("id") == text_id]
    assert len(texts) == 1, f"Missing or duplicate {text_id!r} in {language!r}"
    return ["".join(node.itertext()) for node in elements(texts[0], "value")
            if node.get("form") == form]


def _assert_text(result, path, language, expected, tag="label"):
    assert _values(result, language, _control_reference(result, path, tag)) == [expected]


def _assert_feature(result, feature, prefix=""):
    if feature == "all":
        for part in ("calculate_here", "minimum", "count", "list"):
            _assert_feature(result, part, prefix)
        return
    if feature in ("calculate_here", "empty_calculate_here"):
        binding = bind_for(result, prefix + "checkpoint")
        assert "calculate" not in binding.attrib
        assert binding.get("readonly", "").strip() == "true()"
        if feature == "calculate_here":
            assert binding.get(HERE, "").strip() == "42"
        else:
            assert HERE not in binding.attrib
    elif feature == "minimum":
        assert bind_for(result, prefix + "timed").get(MINIMUM) == "2.5"
    else:
        binding = bind_for(result, prefix + "speed_" + feature)
        assert binding.get(SPEED) == feature
        assert binding.get("calculate") == "''"
    namespaces = {}
    for event, value in ET.iterparse(StringIO(result.xform), events=("start-ns", "start")):
        if event == "start":
            break
        namespaces[value[0]] = value[1]
    assert namespaces.get("ctosim") == CTOSIM


@pytest.mark.parametrize("feature", FEATURES)
@pytest.mark.parametrize("padded", [False, True], ids=["ordinary-headers", "reversed-padded-headers"])
def test_first_note_and_later_question_keep_all_translated_labels(
        workbook_factory, feature, padded):
    first = {"type": "note", "name": "intro", **{
        f"label:{language}": f"{language} — début, café & <accueil>"
        for language in LANGUAGES}}
    last = {"type": "text", "name": "answer", **{
        f"label:{language}": f"{language} — réponse différente"
        for language in LANGUAGES}}
    # There is deliberately no unsuffixed label or hint column, including on
    # the first note: this is the failure described in Amendment 5.
    result = convert_xlsform(_make(workbook_factory,
                                  [first, *_feature_rows(feature), last], padded=padded))
    for language in LANGUAGES:
        _assert_text(result, "intro", language, first[f"label:{language}"])
        _assert_text(result, "answer", language, last[f"label:{language}"])
    _assert_feature(result, feature)


@pytest.mark.parametrize("feature", FEATURES)
def test_one_translated_language_is_sufficient_with_custom_binds(workbook_factory, feature):
    result = convert_xlsform(_make(workbook_factory, [
        {"type": "note", "name": "intro", "label:French": "Une seule langue"},
        *_feature_rows(feature),
    ]))
    _assert_text(result, "intro", "French", "Une seule langue")
    _assert_feature(result, feature)


@pytest.mark.parametrize("feature", ["calculate_here", "minimum", "count", "list", "all"])
@pytest.mark.parametrize("column, condition, attribute", [
    ("hint", None, None),
    ("constraint message", "constraint", "constraintMsg"),
    ("required message", "required", "requiredMsg"),
])
def test_translated_hints_and_message_aliases_keep_their_field_links(
        workbook_factory, feature, column, condition, attribute):
    rows = []
    for name in ("first", "second"):
        row = {"type": "text", "name": name, "label:French": f"Libellé {name}"}
        for language in ("French", "日本語"):
            row[f"{column}:{language}"] = f"{name}: {language} café & selected(/data/first, 1)"
        if condition:
            row[condition] = "true()"
        rows.append(row)
    result = convert_xlsform(_make(workbook_factory, [rows[0], *_feature_rows(feature), rows[1]]))
    for row in rows:
        if column == "hint":
            text_id = _control_reference(result, row["name"], "hint")
        else:
            references = [value for key, value in bind_for(result, row["name"]).attrib.items()
                          if key.rsplit("}", 1)[-1] == attribute]
            assert len(references) == 1
            text_id = _itext_id(references[0])
        for language in ("French", "日本語"):
            assert _values(result, language, text_id) == [row[f"{column}:{language}"]]
    _assert_feature(result, feature)


@pytest.mark.parametrize("setting, expected", [(None, "default"), ("", "default"), ("English", "English")])
def test_unsuffixed_default_and_languages_found_only_in_hints_or_messages_survive(
        workbook_factory, setting, expected):
    settings = [["form_id"], ["translated"]] if setting is None else [
        ["form_id", "default_language"], ["translated", setting]]
    row = {"type": "text", "name": "q", "label": "Default label",
           "label:French": "Libellé", "hint": "Default hint", "hint:Haousa": "Haousa hint",
           "constraint": "true()", "constraint message": "Default constraint",
           "constraint message:Zarma": "Zarma constraint", "required": "true()",
           "required message": "Default required", "required message:日本語": "必須です"}
    result = convert_xlsform(_make(workbook_factory, [row, *_feature_rows("all")], settings=settings))
    translations = _translations(result)
    assert set(translations) == {expected, "French", "Haousa", "Zarma", "日本語"}
    assert [lang for lang, node in translations.items() if node.get("default") == "true()"] == [expected]
    _assert_text(result, "q", expected, "Default label")
    _assert_text(result, "q", "French", "Libellé")
    _assert_text(result, "q", expected, "Default hint", "hint")
    _assert_text(result, "q", "Haousa", "Haousa hint", "hint")
    for attribute, language, default, translated in [
        ("constraintMsg", "Zarma", "Default constraint", "Zarma constraint"),
        ("requiredMsg", "日本語", "Default required", "必須です"),
    ]:
        references = [value for key, value in bind_for(result, "q").attrib.items()
                      if key.rsplit("}", 1)[-1] == attribute]
        assert len(references) == 1
        text_id = _itext_id(references[0])
        assert _values(result, expected, text_id) == [default]
        assert _values(result, language, text_id) == [translated]


def test_language_case_and_unicode_names_are_not_confused_with_bind_namespaces(workbook_factory):
    labels = {"French": "Majuscule", "french": "Minuscule", "Français": "Été",
              "日本語": "質問", "ctosim": "A language named ctosim"}
    row = {"type": "text", "name": "q", **{f"label:{key}": value for key, value in labels.items()}}
    result = convert_xlsform(_make(workbook_factory, [row, *_feature_rows("all")]))
    for language, label in labels.items():
        _assert_text(result, "q", language, label)
    _assert_feature(result, "all")


@pytest.mark.parametrize("feature", FEATURES)
@pytest.mark.parametrize("kind, extension", [("image", "png"), ("audio", "mp3"), ("video", "mp4")])
def test_media_columns_stay_attached_to_their_translated_labels(
        workbook_factory, feature, kind, extension):
    rows = [{"type": "note", "name": name, "label:French": f"Illustration {name}",
             f"media:{kind}": f"{name}.{extension}"} for name in ("before", "after")]
    result = convert_xlsform(_make(workbook_factory, [rows[0], *_feature_rows(feature), rows[1]]))
    for row in rows:
        _assert_text(result, row["name"], "French", row["label:French"])
        text_id = _control_reference(result, row["name"])
        # The spec promises preservation but does not prescribe a media URI
        # scheme or translation fallback. Find media on this label's itext ID
        # across languages without requiring replication into every language.
        media = ["".join(value.itertext())
                 for translation in _translations(result).values()
                 for text in elements(translation, "text") if text.get("id") == text_id
                 for value in elements(text, "value") if value.get("form") == kind]
        filename = row[f"media:{kind}"]
        assert any(value == filename or value.endswith("/" + filename) for value in media)
    assert not ({"image", "audio", "video"} & set(_translations(result)))
    _assert_feature(result, feature)


@pytest.mark.parametrize("container", ["group", "repeat"])
def test_nested_translations_and_all_custom_binds_keep_their_own_paths(workbook_factory, container):
    rows = [
        {"type": f"begin {container}", "name": "section", "label:French": "Section"},
        {"type": "note", "name": "intro", "label:French": "Dans la section"},
        *_feature_rows("all"),
        {"type": f"end {container}"},
        {"type": "text", "name": "after", "label:French": "Après la section"},
    ]
    result = convert_xlsform(_make(workbook_factory, rows))
    _assert_text(result, "section/intro", "French", "Dans la section")
    _assert_text(result, "section/timed", "French", "Question chronométrée")
    _assert_text(result, "after", "French", "Après la section")
    _assert_feature(result, "all", "section/")
    assert result.fields == tuple(
        FieldInfo(row["name"], row["type"],
                  "/data/" + ("section/" if 2 <= index <= 6 else "") + row["name"], index + 1)
        for index, row in enumerate(rows, 1) if not row["type"].startswith("end ")
    )


@pytest.mark.parametrize("feature", FEATURES)
def test_custom_children_of_disabled_group_keep_features_and_translations(workbook_factory, feature):
    rows = [
        {"type": "note", "name": "intro", "label:French": "Avant"},
        {"type": "begin group", "name": "omitted", "disabled": "yes", "label:French": "Omis"},
        *_feature_rows(feature),
        {"type": "end group"},
        {"type": "text", "name": "after", "label:French": "Après"},
    ]
    result = convert_xlsform(_make(workbook_factory, rows))
    _assert_text(result, "intro", "French", "Avant")
    _assert_text(result, "after", "French", "Après")
    children = _feature_rows(feature)
    assert result.fields == (FieldInfo("intro", "note", "/data/intro", 2),
        *(FieldInfo(child["name"], child["type"], "/data/" + child["name"], index + 4)
          for index, child in enumerate(children)),
        FieldInfo("after", "text", "/data/after", len(rows) + 1))
    _assert_feature(result, feature)
    if feature in ("minimum", "all"):
        _assert_text(result, "timed", "French", "Question chronométrée")
    baseline = convert_xlsform(_make(workbook_factory, [rows[0], *children, rows[-1]]))
    assert result.xform == baseline.xform
    expected = [(3, "omitted", None)] + [
        (index + 4, child["name"], child["type"])
        for index, child in enumerate(children) if child["type"] != "text"]
    assert_warnings(result, expected, optional_closers=(len(rows),))


def test_combined_translations_are_deterministic_and_do_not_modify_the_workbook(workbook_factory):
    rows = [
        {"type": "note", "name": "intro", "label:French": "Bienvenue",
         "label:Haousa": "Haousa intro", "label:Zarma": "Zarma intro", "media:image": "intro.png"},
        *_feature_rows("all"),
    ]
    path = _make(workbook_factory, rows)
    before = path.read_bytes()
    first = convert_xlsform(path)
    second = convert_xlsform(path)
    assert second.xform == first.xform
    assert second.fields == first.fields
    assert second.warnings == first.warnings
    assert path.read_bytes() == before
    assert len(first.warnings) == 3
    for warning, row, name, field_type in zip(first.warnings, (3, 5, 6),
                                             ("checkpoint", "speed_count", "speed_list"),
                                             ("calculate_here", "speed violations count", "speed violations list")):
        assert_row_mentioned(warning, row)
        assert name in warning and field_type in warning


@pytest.mark.parametrize("seconds, expected", [(0, "0"), (10.0, "10"), (2.5, "2.5")])
def test_translated_question_keeps_its_own_minimum_and_its_labels(workbook_factory, seconds, expected):
    result = convert_xlsform(_make(workbook_factory, [
        {"type": "text", "name": "timed", "label:French": "Durée",
         "label:Haousa": "Haousa durée", "minimum_seconds": seconds},
        *_feature_rows("calculate_here"),
    ]))
    _assert_text(result, "timed", "French", "Durée")
    _assert_text(result, "timed", "Haousa", "Haousa durée")
    assert bind_for(result, "timed").get(MINIMUM) == expected
    _assert_feature(result, "calculate_here")


def test_custom_attributes_on_the_same_row_do_not_erase_other_rows_translations(workbook_factory):
    result = convert_xlsform(_make(workbook_factory, [
        {"type": "note", "name": "intro", "label:French": "Avant", "minimum_seconds": 0},
        {"type": "calculate_here", "name": "checkpoint", "calculation": "42", "minimum_seconds": 2.5},
        {"type": "speed violations count", "name": "speed_count", "minimum_seconds": 10},
        {"type": "speed violations list", "name": "speed_list", "minimum_seconds": 1},
        {"type": "text", "name": "after", "label:French": "Après"},
    ]))
    _assert_text(result, "intro", "French", "Avant")
    _assert_text(result, "after", "French", "Après")
    for feature in ("calculate_here", "count", "list"):
        _assert_feature(result, feature)
    for name, expected in [("intro", "0"), ("checkpoint", "2.5"), ("speed_count", "10"), ("speed_list", "1")]:
        assert bind_for(result, name).get(MINIMUM) == expected


@pytest.mark.parametrize("invalid", [-1, -0.5, "not a number"])
def test_invalid_minimum_in_translated_form_reports_the_timing_row(workbook_factory, invalid):
    path = _make(workbook_factory, [
        {"type": "note", "name": "intro", "label:French": "Bienvenue"},
        *_feature_rows("calculate_here"),
        {"type": "text", "name": "bad", "label:French": "Durée", "minimum_seconds": invalid},
    ])
    with pytest.raises(ConversionError) as caught:
        convert_xlsform(path)
    message = str(caught.value)
    assert path.name in message
    assert_row_mentioned(message, 4)
    assert any(word in message.casefold() for word in ("minimum", "second", "number", "numeric", "nonnegative", "non-negative"))
