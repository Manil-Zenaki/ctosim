# Disputes — spec 02 (XLSForm converter)

The implementation passes 211 of the 223 tests. The 12 failures fall into
two disputes. In both, the tests require an XForm representation that the
spec did not prescribe and that `pyxform` 4.5.0, to which the spec
delegates the conversion, does not produce. The spec now documents
pyxform's representation in "Amendment 1: XForm representation produced
by pyxform".

## Dispute 1: choices expected inline in the select control

**Tests:** `test_choices_accept_name_or_value_and_duplicates_within_a_list`
(4 cases), `test_choice_lists_remain_separate_when_values_overlap`,
`test_empty_choices_and_settings_rows_do_not_create_entries`,
`test_cached_formula_result_is_used_for_choices`.

**Expectation in the tests:** `<item>` elements with a `<value>` child
inside the `<select1>`/`<select>` control.

**Argument:** pyxform 4.5.0 always writes choice lists as secondary
instances and itemsets. Observed output for list `opts` with a duplicate:

```xml
<instance id="opts"><root>
  <item><name>a</name><label>A</label></item>
  <item><name>a</name><label>A2</label></item>
</root></instance>
...
<select1 ref="/data/q"> ... <itemset nodeset="instance('opts')/root/item"> ...
```

Both forms are valid XForms; the behaviours under test (duplicates kept
in order, `name` or `value` column accepted, empty rows ignored, lists kept
apart, cached formula values used) are all observable in this
representation. **Requested change:** assert on the items of the list's
secondary instance (`<name>` / `<label>`) and on the control's itemset
reference, instead of inline items.

## Dispute 2: rewritten repeat_count expected on the repeat element

**Tests:** `test_numeric_selected_argument_is_quoted_in_every_expression_column`,
the five `repeat_count-count` cases.

**Expectation in the tests:** the rewritten expression in an attribute of
the `<repeat>` element.

**Argument:** pyxform moves a `repeat_count` expression into a generated
calculate node. Observed output:

```xml
<bind nodeset="/data/target_count" type="string" readonly="true()" calculate="selected(/data/q, '1')"/>
<repeat nodeset="/data/target" jr:count=" /data/target_count ">
```

The rewrite is applied; it is just not on the `<repeat>` element.
**Requested change:** for `repeat_count`, assert on the `calculate` of the
bind for `/data/target_count` (and that the repeat's `jr:count` refers to
that node).

## Adjudication

Both disputes are upheld against the tests. The current spec, including
Amendment 1, is the authority for these decisions; no implementation source
was read. The required behaviors remain tested, but the original XML-location
expectations contradict the amendment and have been corrected.

### Dispute 1 decision: correct all seven choice cases

Amendment 1 explicitly requires a secondary instance for each choice list,
with ordered `<item>` children under `<root>`, values in `<name>`, and labels
in `<label>` for these untranslated fixtures. It also requires select controls
to reference their list through an itemset instead of containing inline items.

- `test_choices_accept_name_or_value_and_duplicates_within_a_list` (four
  cases): retain both column aliases and both select types. Check the `opts`
  secondary instance for all three value/label pairs in order, including both
  `a` entries and their distinct labels, and check the control's itemset
  reference. Keep the original catalog-type assertion.
- `test_choice_lists_remain_separate_when_values_overlap`: check the `left`
  and `right` instances independently, including their distinct labels and
  ordered values, and require each control to reference its own list.
- `test_empty_choices_and_settings_rows_do_not_create_entries`: require
  exactly the single expected choice in the `opts` instance and the correct
  itemset reference, preserving the check that blank rows add no choices.
- `test_cached_formula_result_is_used_for_choices`: require the cached `a`
  value and its `Alpha` label in the `opts` instance, with the correct itemset
  reference. Searching for an inline `<value>` was not justified.

These checks share a test-only assertion helper that also verifies the absence
of inline items. No fixture or conversion expectation has been relaxed.

### Dispute 2 decision: correct all five repeat-count cases

For `test_numeric_selected_argument_is_quoted_in_every_expression_column`,
the five `repeat_count-count` cases retain their original expected quoting
for `0`, `1`, `-99`, `2.5`, and `-0.5`. Amendment 1 explicitly places the
expression in the `calculate` attribute of the bind for `/data/target_count`;
expecting that expression in the repeat's count attribute was incorrect.

The corrected cases compare that bind's calculation to the expected rewritten
expression and verify that the `/data/target` repeat's `jr:count` refers to
`/data/target_count`. They also check that this generated node exists in the
primary instance and is excluded from `fields`, as the amendment requires.
Assertions for the other expression columns are retained.

### Validation

Ran `.venv\Scripts\python -m pytest converter/tests -q --tb=no`: all 223
tests collected and ran without import errors; 3 passed and 220 failed.
A focused rerun of
`test_minimal_workbook_needs_neither_choices_nor_settings` with `--tb=line`
reported `NotImplementedError: not implemented` from the public
`convert_xlsform` entry point. Thus this checkout does not reproduce the
implementation state described in the dispute's opening paragraph, and a
passing behavioral run cannot be confirmed here. Traceback source display
was suppressed; implementation code was not read or changed.
`git diff --check` passed.

## Dispute 3: body position compared across two parses of the XForm

`test_converter_calculate_here.py::test_calculate_here_keeps_its_body_position_and_catalog_path`
(both `group` and `repeat` cases) asserts
`input_for(result, path) in elements(parent, "input")`. `input_for`
parses `result.xform` again, while `parent` comes from an earlier parse,
and `xml.etree` elements compare by identity: an element of one parse is
never `in` a list of elements of another parse, whatever the XForm. The
converted XForm does place the read-only `checkpoint` input inside the
group or repeat body element. Requested change: look the control up in
the same parse as `parent` (for example, check that `parent` has an
`input` whose `ref` is the path).

## Dispute 4: generated count node for a plain `${size}` repeat count

`test_converter_boundaries.py::test_nested_repeat_count_is_a_sibling_node_excluded_from_catalog`
expects a `members_count` node for `repeat_count` = `${size}`. pyxform
4.5.0 adds such a node only when the count is an expression; for a single
field reference, `jr:count` refers to the field itself. The spec's
Amendment 1 said otherwise and has been corrected (see its "Correction"
paragraph). Requested change: either use an expression such as
`${size} + 1` to test the generated node, or, for `${size}`, expect
`jr:count` to be `/data/household/size` and no `members_count` node; in
both cases the catalog must not list a count node.
