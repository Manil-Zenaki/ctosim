"""Additional public contract boundaries, with no implementation introspection."""

import pytest

from ctosim_xlsform import FieldInfo, convert_xlsform
from xform_assertions import bind_for, elements, instance_paths, local_name


def test_accepts_a_custom_os_pathlike_object(workbook_factory):
    path = workbook_factory()

    class WorkbookPath:
        def __fspath__(self):
            return str(path)

    assert convert_xlsform(WorkbookPath()) == convert_xlsform(path)


@pytest.mark.parametrize("header", ["Form_id", "Form_title", "Version"])
def test_settings_headers_are_case_sensitive(workbook_factory, header):
    baseline = convert_xlsform(workbook_factory())
    result = convert_xlsform(workbook_factory(settings=[[header], ["ignored"]]))
    assert (result.form_id, result.title, result.version) == (
        baseline.form_id, baseline.title, baseline.version)


def test_blank_settings_row_two_does_not_promote_row_three(workbook_factory):
    result = convert_xlsform(workbook_factory(settings=[
        ["form_id", "form_title", "version"], [], ["wrong", "Wrong", "999"],
    ]))
    assert (result.form_id, result.title, result.version) == ("form", "form", None)


def test_cached_formula_headers_are_read_as_their_stored_values(workbook_factory, set_formula_cache):
    path = workbook_factory(survey=[['="type"', '="name"', "label"],
                                    ["text", "q", "Question"]])
    set_formula_cache(path, 1, "A1", "type")
    set_formula_cache(path, 1, "B1", "name")
    assert convert_xlsform(path).fields == (FieldInfo("q", "text", "/data/q", 2),)


def test_blank_choice_rows_preserve_duplicate_entries_and_sheet_order(workbook_factory):
    result = convert_xlsform(workbook_factory(
        survey=[["type", "name", "label"], ["select_multiple opts", "q", "Question"]],
        choices=[["list_name", "value", "label"], [], ["opts", "1", "First"],
                 [], ["opts", "1", "Second"], [], ["opts", "2", "Third"], []],
    ))
    instance = next(node for node in elements(result.xform, "instance") if node.get("id") == "opts")
    assert [node.text for node in elements(instance, "name")] == ["1", "1", "2"]
    assert [node.text for node in elements(instance, "label")] == ["First", "Second", "Third"]


def test_nested_repeat_count_is_a_sibling_node_excluded_from_catalog(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "repeat_count"],
        ["begin group", "household", "Household"],
        ["integer", "size", "Size"],
        ["begin repeat", "members", "Members", "${size} + 1"],
        ["text", "person", "Person"], ["end repeat"], ["end group"],
    ]))
    path = "/data/household/members_count"
    assert path in instance_paths(result)
    assert path not in [field.xpath for field in result.fields]
    calculation = bind_for(result, "household/members_count").get("calculate", "")
    assert " ".join(calculation.split()) == "/data/household/size + 1"
    repeat = next(node for node in elements(result.xform, "repeat")
                  if node.get("nodeset") == "/data/household/members")
    assert repeat.get("{http://openrosa.org/javarosa}count", "").strip() == path
    assert "calculate" not in repeat.attrib
    assert [field.name for field in result.fields] == ["household", "size", "members", "person"]


def test_nested_repeat_count_field_reference_adds_no_generated_node(workbook_factory):
    result = convert_xlsform(workbook_factory(survey=[
        ["type", "name", "label", "repeat_count"],
        ["begin group", "household", "Household"],
        ["integer", "size", "Size"],
        ["begin repeat", "members", "Members", "${size}"],
        ["text", "person", "Person"], ["end repeat"], ["end group"],
    ]))
    path = "/data/household/members_count"
    assert path not in instance_paths(result)
    assert not [node for node in elements(result.xform, "bind")
                if node.get("nodeset", node.get("ref")) == path]
    repeat = next(node for node in elements(result.xform, "repeat")
                  if node.get("nodeset") == "/data/household/members")
    assert repeat.get("{http://openrosa.org/javarosa}count", "").strip() == "/data/household/size"
    assert [field.name for field in result.fields] == ["household", "size", "members", "person"]


def test_translated_choices_keep_separate_secondary_items_and_label_references(workbook_factory):
    result = convert_xlsform(workbook_factory(
        survey=[["type", "name", "label", "label:French"],
                ["select_one opts", "q", "Question", "Question française"]],
        choices=[["list_name", "name", "label", "label:French"],
                 ["opts", "a", "Apple", "Pomme"], ["opts", "b", "Pear", "Poire"]],
    ))
    instance = next(node for node in elements(result.xform, "instance") if node.get("id") == "opts")
    items = elements(instance, "item")
    assert len(items) == 2
    assert [next(node.text for node in item if local_name(node) == "name")
            for item in items] == ["a", "b"]
    translations = {node.get("lang"): node for node in elements(result.xform, "translation")}
    for item, english, french in zip(items, ["Apple", "Pear"], ["Pomme", "Poire"]):
        default_ids = {node.get("id") for node in elements(translations["default"], "text")
                       if english in [value.text for value in elements(node, "value")]}
        french_ids = {node.get("id") for node in elements(translations["French"], "text")
                      if french in [value.text for value in elements(node, "value")]}
        shared_ids = default_ids & french_ids
        assert len(shared_ids) == 1
        reference = shared_ids.pop()
        assert reference is not None
        # pyxform may use a dedicated itext-id child; do not invent its tag.
        assert any(reference in (node.text or "") or reference in node.attrib.values()
                   for node in item.iter())
    select = next(node for node in elements(result.xform, "select1") if node.get("ref") == "/data/q")
    assert not elements(select, "item")
    assert elements(select, "itemset")[0].get("nodeset") == "instance('opts')/root/item"
