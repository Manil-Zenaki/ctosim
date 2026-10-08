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
