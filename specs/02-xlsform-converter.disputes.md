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

## Follow-up adjudication (2026-10-09)

The current spec, including both amendments and the repeat-count correction,
is the sole authority. No implementation source or public stub bodies were
read, and no production files were changed.

### Dispute 1: retain the already-corrected choice tests

The seven cases now use `assert_choice_list` to check the ordered secondary
instance items, their names and labels, and the select control's itemset
reference, including the absence of inline items. These expectations match
Amendment 1. The prior decision stands; no further test changes are needed.

### Dispute 2: retain the already-corrected expression tests

The five `repeat_count-count` cases use `selected(...)` expressions, not
single field references. Their checks of the generated `target_count` node,
its rewritten calculation, the repeat's count reference, and exclusion from
the catalog remain required by the corrected Amendment 1. Keep these tests.

### Dispute 3: upheld; fix the containment assertion

Amendment 2 requires the read-only text question at the row's body position.
It does not require identity between elements from separately parsed XML
trees. The old membership assertion could never succeed for the group and
repeat cases because `input_for(result, ...)` parsed a new tree.

The corrected assertion finds the checkpoint by its `ref` among inputs in
the already-parsed parent and requires exactly one match. The existing
before/checkpoint/after order, catalog path and row, primary-instance path,
and warning assertions are retained, as is the uncontained case. This fixes
the test error while continuing to reject a control outside its container.

### Dispute 4: upheld; distinguish references from expressions

The corrected Amendment 1 explicitly says `${size}` uses the size field
directly and creates no count node. The original fixture therefore did not
justify its expectation of `/data/household/members_count`.

The existing sibling-node test now uses `${size} + 1`. It checks the generated
node's sibling path, calculation with the expanded size reference, the
repeat's count reference, and exclusion from the catalog. Whitespace between
expression tokens is normalized for the comparison; expression content is
still checked.

A separate `test_nested_repeat_count_field_reference_adds_no_generated_node`
keeps the original `${size}` input and checks that `jr:count` references
`/data/household/size`, that no `members_count` instance node or bind exists,
and that the catalog contains only the survey rows that produce nodes.

### Follow-up validation

Ran `.venv\Scripts\python -m pytest converter/tests -q --tb=no
-o cache_dir=.codex-runs/pytest-cache
--basetemp=.codex-runs/pytest-dispute-02` with bytecode writing disabled:
**270 passed in 22.15s**, with no import or collection errors. Traceback
source display was suppressed to avoid reading implementation code.
Temporary test artifacts were directed under `.codex-runs/`.

## Dispute 5: the companion of a question inside a repeat appears twice in the instance (Amendment 6)

`test_companion_is_immediate_in_the_same_scope_and_catalog_uses_source_row`
fails for the scopes `repeat` and `nested` (both select kinds) on the
last line of `_assert_immediate_text_question`:
`assert instance_paths(result).count("/data/" + path + "_other") == 1`.

An XForm instance holds a repeat twice: its template
(`<section jr:template="">`) and its first instance. Every field of the
repeat therefore appears twice in `instance_paths`, the question itself
included; the failure output shows `/data/section` twice. The converted
instance is:

```
<section jr:template=""><q/><q_other/><after/></section>
<section><q/><q_other/><after/></section>
```

The amendment asks that `<name>_other` follow the question "in the same
group or repeat", which this satisfies. The count should be the same as
the question's own count (or should ignore the template), not 1.

## Follow-up adjudication (2026-10-17)

Decisions below use only the current spec and the disputed tests. No
implementation source or public stub bodies were read; no production files
were changed.

### Dispute 1: keep the corrected choice tests

Amendment 1 requires ordered items in secondary instances and itemset
references from select controls. All seven disputed cases now use
`assert_choice_list`, which checks that representation, including values,
labels, order, and the absence of inline items. These tests are correct and
remain unchanged.

### Dispute 2: keep the corrected expression tests

The five disputed repeat-count cases use `selected(...)` expressions.
Amendment 1 requires their calculations on the generated `target_count`
bind and the repeat's count reference to that node. The tests check both,
plus the node's presence in the primary instance and absence from the
catalog. These tests are correct and remain unchanged.

### Dispute 3: keep the corrected body-position test

Amendment 2 requires the question at its survey position inside its group
or repeat. The current test looks up the checkpoint within the already
parsed parent and compares its reference path; it no longer compares
element identities across parses. Its containment, ordering, and catalog
checks are justified and remain unchanged.

### Dispute 4: keep both corrected repeat-count tests

The generated sibling-node test now uses `${size} + 1`, while the separate
single-reference test uses `${size}` and expects a direct reference to
the size field with no generated node. Both exclude generated count nodes
from the catalog. This matches the corrected Amendment 1; keep both tests.

### Dispute 5: upheld; fix the companion's instance-path count

Amendment 6 requires a text companion immediately after the question in
the same group or repeat, and one catalog entry immediately after its
source question with the source row number. It does not require its
absolute path to occur exactly once across the XML primary instance.
The Output section also describes repeat field paths without positional
predicates, using the repeated template node's path. Counting that path
globally as exactly one imposes an unsupported representation constraint.

`instance_paths` traverses all primary-instance nodes without distinguishing
repeat templates from initial instances. The helper
`_assert_immediate_text_question` now requires the source question to exist
and the companion's path to occur as many times as the question's path.
This permits template and initial-instance copies without accepting a
missing companion or an unequal number of companions. It does not impose
a fixed number of repeat copies. Existing checks for immediate body
adjacency in the same parent, text type, editability, label, catalog order,
path, and source row are retained. The correction applies to both select
types in all four existing scopes (root, group, repeat, and nested).

### Validation for this adjudication

Ran `.venv\Scripts\python -m pytest converter/tests -q --tb=no
-o cache_dir=.codex-runs/pytest-cache
--basetemp=.codex-runs/pytest-dispute-02-20261017` with
`PYTHONDONTWRITEBYTECODE=1`: **703 passed in 86.88s**, with no import or
collection errors. Traceback source display was suppressed, and temporary
test artifacts were directed under `.codex-runs/`. `git diff --check`
passed. No implementation code was read or changed, and no commit was made.
