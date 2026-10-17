# Spec 03 — SurveyCTO functions

## Purpose

SurveyCTO forms use XPath functions that the ODK engine does not provide.
Spec 01 made every such function evaluable by returning a fixed value.
That is not enough for functions that drive the form's logic: with
`index()` returning `""`, `if(${rank} = 1, index(), 0)` or a relevance on
`index() = 1` silently goes wrong. This module gives these functions their
SurveyCTO meaning in every entry session.

Language: Java. No new public API: the functions are available in every
session opened with `LoadedForm.newEntryController()` (spec 01).

## Functions

Unless stated otherwise, arguments may be any XPath expression; a field
reference is converted to the field's value as the engine converts
arguments of native functions (a string for text and select fields, the
number for numeric fields, `""` when the field has no value). "Number"
results are `Double`, "boolean" results `Boolean`, "string" results
`String`.

Calling a function in this module with a number of arguments it does not
accept makes the evaluation fail with an exception (as a native function
does); the arities are given in each signature.

### Position in repeats

| Function | Result |
|---|---|
| `index()` | number: the 1-based position of the repeat instance that contains the evaluation context node. The context node itself counts: for an expression evaluated on a repeat instance element (e.g. its relevance), the result is that instance's position. With nested repeats, the innermost enclosing repeat instance is used. Outside any repeat, the result is `1`. |

The context node is the node the engine evaluates the expression for: the
node of the `bind` for `calculate`, `relevant`, `constraint`, `required`
and `readonly` expressions.

### Choices and fields

| Function | Result |
|---|---|
| `choice-label(field, value)` | string: the label, in the session's current form language, of the choice whose value equals `value` (compared as strings) in the choice list of the select question bound to `field`. `field` must be a reference to a select question node (relative or absolute; inside a repeat it designates the instance in context). Result is `""` when no choice has that value, when `value` is `""`, or when `field` does not designate a select question. |
| `relevant(field)` | boolean: `true` when the node designated by `field` is currently relevant — its own relevance and that of all its ancestors hold — and `false` otherwise, including when `field` does not designate a node. The value reflects the state at the time of evaluation. |
| `empty(value)` | boolean: `true` when the string value of the argument is `""` (a field with no value, a non-relevant field, an empty string), `false` otherwise. `0` is not empty. |

### Ranking

| Function | Result |
|---|---|
| `rank-value(value, list)` | number: `1` plus the number of items of `list` that are numerically strictly greater than `value`. `list` is a string of items separated by one or more spaces; items that are not numbers are ignored. When `value` is not a number, the result is `999`. |
| `rank-index(position, repeated)` | number: the rank, highest value first, of the value at the 1-based `position` among the values of the repeated field `repeated` (a reference to a field inside a repeat, designating all its instances). Ties are ordered by position: of two equal values, the earlier instance ranks higher. The result is `999` when `position` is not an integer between 1 and the number of instances, or when the value at that position is not a number. Instances whose value is not a number are left out of the ranking. |

### Lists of items

`sep` is a string separator (one or more characters), `list` a string.
Splitting `list` by `sep` yields its items, empty items included
(`'a,,b'` has three items). An empty `list` has no items. When `sep` is
the empty string, a non-empty `list` is a single item.

| Function | Result |
|---|---|
| `count-items(sep, list)` | number: the number of items. |
| `item-at(sep, list, n)` | string: the item at zero-based position `n`, or `""` when `n` is not an integer in range. |
| `item-index(sep, list, value)` | number: the zero-based position of the first item equal to `value`, or `-1`. |
| `item-present(sep, list, value)` | boolean: whether some item equals `value`. |
| `de-duplicate(sep, list)` | string: the items with every repeat of an earlier item removed, in their original order, joined with `sep`. |

### Strings

| Function | Result |
|---|---|
| `linebreak()` | string: a single line feed, `"\n"`. |
| `lower(s)` | string: `s` in lower case, independent of the default locale. |
| `upper(s)` | string: `s` in upper case, independent of the default locale. |
| `format-number(n)` | string: `n` with comma thousands separators and at most three decimal places, rounded half-even, without trailing zeros; a value that rounds to zero is `"0"`, never `"-0"`. Examples: `1234567.5` → `"1,234,567.5"`, `2` → `"2"`, `0.12345` → `"0.123"`, `-1234.5678` → `"-1,234.568"`, `-0.0004` → `"0"`. `""` when `n` is not a number. Independent of the default locale. |
| `hash(v1, ..., vn)` | string: at least one argument; the lowercase hexadecimal SHA-256 digest of the UTF-8 encoding of the arguments' string values joined with the character U+001F. |

### Geography

A geopoint is a string `"latitude longitude [altitude [accuracy]]"` with
items separated by spaces.

| Function | Result |
|---|---|
| `distance-between(p1, p2)` | number: the great-circle (haversine) distance in metres between the two points, on a sphere of radius 6 371 000 m. `NaN` when either argument does not start with a valid latitude (−90 to 90) and longitude (−180 to 180). |
| `short-geopoint(p)` | string: the first two items of `p` (latitude and longitude) separated by one space; `""` when `p` has fewer than two items. |
| `geo-scatter(p, range)` | string: `p` unchanged (no random displacement in simulated data). |

### Simulated device and enumerator

| Function | Result |
|---|---|
| `enumerator-name()` | string `"simulated"` |
| `enumerator-id()` | string `"simulated"` |
| `device-info()` | string `"ctosim"` |
| `collect-is-phone-app()` | boolean `false` |

## Relationship with spec 01

- The functions of this spec are no longer stubs: `LoadedForm.stubbedFunctions()`
  does not list them.
- The fixed stubs of spec 01 are unchanged (`pulldata`, `plug-in-metadata`,
  `username`, `duration`, `phone-call-log`, `phone-call-duration`), and so
  is the catch-all for any other unknown function. In particular, the
  SurveyCTO functions `count-if`, `sum-if`, `join-if`, `min-if`, `max-if`
  and `rank-index-if` are **not** implemented by this module: they keep
  the catch-all behaviour and appear in `stubbedFunctions()`.
- Functions the engine implements natively keep their native behaviour.

## Recalculation

The functions are evaluated whenever the engine evaluates an expression
that calls them; when that happens is decided by the engine (for example,
a `calculate` is recomputed when a field referenced in its arguments
changes, and expressions inside a new repeat instance are evaluated when
the instance is created).

## Acceptance on real forms

`samples/xforms/` holds XForms converted from real SurveyCTO forms (see
`samples/SOURCES.md`). Each of them loads with `FormLoader`, and for each,
`newEntryController()` returns an initialised session.
`ethiopia_migration_household.xml` uses `index()`, `rank-value()` and
`choice-label()` to select a household member at random. Its repeat
`/data/member_interviewed/hhroster` (count given by the integer field
`/data/hh_count_06`) has a field `instancenumber` calculated as `index()`.
In a session where that repeat has N ≥ 1 instances created through the
engine, the `instancenumber` of the k-th instance holds the number k for
every k from 1 to N (see Amendment 1 for how the engine stores it).

## Clarifications (answers to `03-surveycto-functions.questions.md`)

All fourteen assumptions were reviewed one by one and are confirmed as the
intended behaviour. In particular: `index()` gives the structural position
of the repeat instance, whatever the relevance of its siblings (13); list
separators are literal and item comparison is exact (3); `short-geopoint`
and `geo-scatter` do not validate their input (5, 6); a one-millimetre
tolerance for `distance-between` is appropriate (11); any exception from
evaluation satisfies the arity rule (10).

## Amendment 1: how the engine stores numeric results

The acceptance paragraph first said that `instancenumber` holds the
string `"k"` because the field is declared as a string. That was wrong.
JavaRosa stores a `calculate` result through `IAnswerData.wrapData`, which
turns any whole-number result into an `IntegerData` whatever the field's
declared type (only boolean fields are treated differently). `index()`
returns a whole number, so the k-th `instancenumber` holds `IntegerData`
with value k (`getValue()` returns the `Integer` k). Formatting values as
text in SurveyCTO's export layout is the job of the exporter (module 07).

## Amendment 2: select functions on numeric values

SurveyCTO stores every field value as text, so `selected()` works on any
field. A common pattern assigns a treatment arm at random and branches on
it: `calculate` `arm` = `once(int(2*random())+1)`, then
`relevance` = `selected(${arm}, '1')`. In the engine, `arm` holds a number
(see Amendment 1), and the engine's native `selected()` fails with a
`ClassCastException` on a numeric first argument. The real form
`ethiopia_migration_household.xml` does exactly this
(`/data/interview_begin/direct_or_list`, `/data/interview_begin/intervention_or_control`).

In every session, the first argument of `selected(a, b)`,
`selected-at(a, n)` and `count-selected(a)`, in any expression of the form
(the `bind` attributes and `setvalue` values listed in spec 01), is used
through its string value, as XPath `string()` gives it: a number `1` is
`"1"`, `2.5` is `"2.5"`. Consequently:

- `selected(a, '1')` is `true` when `a` holds the number 1, and `false`
  when it holds 2;
- `count-selected(a)` is `1` when `a` holds a number;
- `selected-at(a, 0)` is the string value of the number `a` holds;
- a field holding no value behaves as the empty string (not selected,
  count 0), as before.

Behaviour for arguments that already have a string value (select
questions, text fields) is unchanged. Calls typed directly into an
XPath expression that is not part of the form (for example evaluated by a
test through the session's evaluation context) are not affected.

Acceptance: in a session of `ethiopia_migration_household.xml` in which
`/data/interview_begin/direct_or_list` holds the number 1 or 2, evaluating
the relevance of the questions that test it with `selected()` raises no
exception and follows the stored value.

Clarification: assumptions 15–21 of the questions file were reviewed one
by one and are confirmed (in particular 19: a call parsed outside the form
keeps the engine's native outcome, since the change applies to the form's
own expressions).

## Amendment 3: `pulldata()` and attached files

SurveyCTO forms preload data with `pulldata()` from CSV files attached to
the form. `pulldata` is no longer a fixed stub: it reads those files from
a **media directory**.

### API additions

```java
// added to FormLoader (spec 01):
public static LoadedForm load(java.nio.file.Path xform, java.nio.file.Path mediaDirectory)
        throws FormLoadException;

// added to LoadedForm (spec 01):
public java.util.SortedSet<String> missingAttachments();
```

- `load(xform, mediaDirectory)` loads the form as `load(xform)` does and
  takes attached files from `mediaDirectory`. A `mediaDirectory` that does
  not exist or is not a directory throws `FormLoadException` naming it; a
  null argument throws `NullPointerException`.
- `load(xform)` uses the directory that contains `xform` as its media
  directory.
- `load(in, sourceName)` has no media directory: every attached file is
  missing.

### `pulldata(source, column, keyColumn, key)`

- The four arguments are converted to strings as XPath `string()` does
  (a whole number gives no decimal part: `5` gives `"5"`).
- The attached file is `<source>.csv` in the media directory (exact file
  name).
- The result is a string: the value in column `column` of the **first**
  data row whose value in column `keyColumn` equals `key` exactly (no
  trimming, case-sensitive).
- The result is `""` when there is no media directory, no such file, no
  column named `column` or `keyColumn` in the header, or no matching row.
- A call with a number of arguments other than four fails to evaluate, as
  a call to any function with the wrong number of arguments does.
- `pulldata` works in every session, ordinary or simulated, and is no
  longer listed by `stubbedFunctions()`.

### CSV files

- UTF-8, with or without a byte-order mark. The first row is the header
  (column names); the following rows are data rows.
- Fields are separated by commas and rows by LF or CRLF. A field may be
  enclosed in double quotes, inside which commas, line breaks and doubled
  double quotes (`""` for `"`) are literal. Values are not trimmed.
- A data row with fewer fields than the header has empty values for the
  missing columns; extra fields are ignored. An empty line at the end of
  the file is not a data row.
- A file is read the first time a call needs it and kept for the life of
  the `LoadedForm`; later changes to the file are not seen.
- A file that cannot be read, or whose quoting is not closed, makes the
  `pulldata` call fail to evaluate, with an error naming the file.

### `missingAttachments()`

Lists, in natural string order and without duplicates, the `source`
arguments written as string literals (`pulldata('hh_preload', ...)`) in
the form's expressions whose file `<source>.csv` does not exist in the
media directory (all of them when there is no media directory). Calls
whose source is not a literal are not listed. The set is unmodifiable;
it is empty when nothing is missing or the form never calls `pulldata`
with a literal source. It reflects the media directory at load time.

Clarification (Amendment 3): assumptions 22–28 of the questions file are
reviewed one by one and confirmed. In particular, literal sources are
found in every expression site of spec 01, including itemset references
and actions (22); an existing path `<source>.csv` that cannot be read as a
file makes the call fail (26); loading the form never reads an attached
file, even to check that the form initialises (27). Amendment 3 supersedes
the earlier rule that `pulldata` accepts any arguments and is listed by
`stubbedFunctions()` (28): tests written before it that expect this are
out of date.

## Amendment 4: third real export

From the export of the third coverage form (`samples/coverage3/`), whose
group `oracle` computes SurveyCTO functions on constant inputs, filled
with SurveyCTO Collect on Android and on the web. Everything not
mentioned here is unchanged.

### Conditional repeat functions

`count-if`, `sum-if`, `join-if`, `min-if`, `max-if` and `rank-index-if`
are implemented; they are no longer stubs and no longer listed by
`stubbedFunctions()`.

| Function | Result |
|---|---|
| `count-if(repeatgroup, expression)` | number: what `count(repeatgroup)` gives, counting only the **included** instances. |
| `sum-if(repeatedfield, expression)` | what `sum(repeatedfield)` gives on the nodes of the included instances only. |
| `min-if(repeatedfield, expression)` | what `min(repeatedfield)` gives on the nodes of the included instances only. |
| `max-if(repeatedfield, expression)` | what `max(repeatedfield)` gives on the nodes of the included instances only. |
| `join-if(sep, repeatedfield, expression)` | what `join(sep, repeatedfield)` gives on the nodes of the included instances only. |
| `rank-index-if(position, repeatedfield, expression)` | number: what `rank-index(position, repeatedfield)` gives when the ranking is made among the included instances only. `position` still counts every instance (1 is the first instance of the repeat, included or not); a `position` that designates an instance that is not included gives `999`. |

- `repeatgroup` is a reference to a repeat; `repeatedfield` a reference
  to a field inside a repeat. They designate the same instances, or
  fields of the same instances, as they do in the non-conditional
  function (in the same evaluation context).
- An instance is **included** when `expression`, evaluated for that
  instance, is true (as XPath `boolean()` converts its result). In that
  evaluation, a reference to the repeat or to a node inside it designates
  the node of that instance; other references designate what they
  designate in the call's evaluation context. The instances are taken in
  document order.
- With no included instance, the result is what the non-conditional
  function gives on an empty node-set.
- A call with a number of arguments other than the signature's fails to
  evaluate.

Example (the form's `oracle/auto` repeat has three instances whose
`a_value` is 10, 20, 30 and `a_kind` is `odd`, `even`, `odd`):
`count-if(${auto}, ${a_kind} = 'odd')` is 2, `sum-if(${a_value}, ${a_kind} = 'odd')`
is 40, `join-if(' ', ${a_value}, ${a_kind} = 'odd')` is `10 30`,
`min-if` 10, `max-if` 30, and
`rank-index-if(1, ${a_value}, ${a_kind} = 'even')` is 999.

### `hash`

`hash(v1, ..., vn)` (at least one argument) is the lowercase hexadecimal
**MD5** digest of the UTF-8 encoding of the arguments' string values
concatenated with no separator. `hash('abc')` is
`900150983cd24fb0d6963f7d28e17f72`. (Only one-argument calls have been
observed; the rule for several arguments is an assumption.) This replaces
the SHA-256 rule above.

### `format-date-time`

The day and month names written by `%a` and `%b` are the English
three-letter abbreviations (`Mon` ... `Sun`, `Jan` ... `Dec`, with
`Sep`), whatever the default locale. Everything else about the function
is unchanged. `format-date-time(date('2026-01-05'), '%Y/%m/%d %a %b %e %n')`
is `2026/01/05 Mon Jan 5 1`.

### `decimal-time`

`decimal-time(v)` accepts, besides a time field's value, a string holding
a time (`HH:MM:SS`, optionally with a fraction of a second and a UTC
offset) or a date-time (`YYYY-MM-DDTHH:MM:SS`, same options). The result
is the time of day written in `v`, as a fraction of a day (the offset is
not applied): `decimal-time('2013-01-31T18:00:00')` is `0.75`,
`decimal-time('06:00:00')` is `0.25`. A value that is neither gives
`NaN`.

### Dates stored by calculations

When a `calculate` field's expression evaluates to a date or date-time
(`today()`, `now()`, `date(...)`, `date-time(...)`), the field stores
the **date only**, as `YYYY-MM-DD`: the calendar date of that instant in
the default time zone (during a walk, the session's time zone, spec 07
Amendment 1). `today()` and `now()` on 11 October 2026 both store
`2026-10-11`; `date(20000)` stores `2024-10-04`. Expressions that use the
field read that stored value. A date or date-time wrapped in another
function (`format-date-time(now(), ...)`, `decimal-date-time(...)`) is
not affected.

### Where these rules apply

The conditional functions' per-instance conditions, and the new
behaviour of `format-date-time`, `decimal-time` and of dates stored by
calculations, apply to the form's own expressions: the `bind`
attributes and `setvalue` values listed in spec 01, and `calculate_here`
expressions (spec 02, Amendment 2). In those expressions, a relative
reference in a condition starts from the node the expression is
evaluated for, as any reference in that expression does. A call typed
directly into an XPath expression that is not part of the form (for
example parsed and evaluated by a test through the session's evaluation
context) is evaluated as the engine evaluates any function call: every
argument once, in the call's context, before the function runs. For a
conditional function, the condition then has one value, which includes
every instance or none (and a condition that designates several nodes
fails to evaluate); `format-date-time` and `decimal-time` keep the
engine's own behaviour. `hash` behaves as above everywhere.

Two further limits come from the engine. Inside a condition, a reference
that applies its own predicate to a step of the repeat
(`/data/row[kind = 'odd']`) is not restricted to the instance being
checked: the engine binds a step to the current instance only when the
step has no predicate, so such a reference designates every instance
that passes its predicate. And a calculation whose result is `NaN`
(`decimal-time('garbage')`, `number('abc')`) stores no value, as SurveyCTO
does (`f_not_a_number` is empty in the third export); `NaN` is observable
as the value of the expression itself, not of the field.

Clarification (Amendment 4): assumptions 29–37 of the questions file are
reviewed one by one and confirmed, within the scope just stated. In
particular, results for empty and non-numeric values are the native
functions' (29); relative references are those of the expression's own
node (30); recalculation is unchanged (31); the multi-argument `hash`
rule is normative (32); the time grammar and NaN cases of 33 are right;
date storage applies whatever the field's declared type (34).

### Acceptance against the third export

`samples/xforms/ctosim_coverage3.xml` loads with media directory
`samples/coverage3`. In the instance XML of any submission walked from
it with `FormWalker` and the default configuration (spec 07), every field
of the group `/data/oracle` (outside its repeats) whose value does not
depend on the date, the time, the device or chance holds the value shown
in the column of the same name in
`samples/coverage3/wide/ctosim coverage test 3_WIDE.csv` (the same in
its four rows; a field whose element is absent from the XML, or empty,
matches an empty cell). This covers every field of the group except `f_today`,
`f_now`, `f_date_before_today`, `f_decimal_date_time`, `f_decimal_time`,
`f_version`, `f_uuid`, `f_random` and `f_format_number` (whose value on
the phones followed the phone's language).

## Amendment 5: a number as the second argument of `selected()`

Real forms test a multiple answer against a position computed in a
repeat: `selected(${valid_members}, ${member_index})`, where
`member_index` is `index()`, a number. SurveyCTO accepts it; the engine's
native `selected()` stops the session with an error ("The second
parameter to the selected() function must be in quotes"). Everything not
mentioned here is unchanged.

In every session, the second argument of `selected(a, b)`, in any
expression of the form (as for the first argument above), is used
through its string value, as XPath `string()` gives it: a number `3` is
`"3"`, `2.5` is `"2.5"`. Consequently `selected('1 3 4', n)` is `true`
when `n` holds the number 3 and `false` when it holds 2, and a field
holding no value behaves as the empty string (not selected). Behaviour
for a second argument that already has a string value is unchanged, and
calls outside the form's expressions are not affected, as above.
