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
