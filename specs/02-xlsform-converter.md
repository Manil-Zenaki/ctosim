# Spec 02 — XLSForm converter

## Purpose

Convert a SurveyCTO spreadsheet form definition (XLSForm, `.xlsx`) into an
XForm (XML) that the form engine of module 01 accepts, together with a
**field catalog** that records, for every field, the SurveyCTO type it had
in the spreadsheet. SurveyCTO XLSForms differ from the ODK XLSForms that
`pyxform` expects; this module normalises those differences and then
delegates the conversion itself to `pyxform`.

Language: Python 3.10+. Package `ctosim_xlsform`, in `converter/src/`.
Dependencies: `pyxform` 4.5.0, `openpyxl`.

## Public API

```python
from ctosim_xlsform import convert_xlsform, ConversionResult, FieldInfo, ConversionError

result: ConversionResult = convert_xlsform(path)       # path: str or os.PathLike
```

```python
@dataclass(frozen=True)
class FieldInfo:
    name: str            # the row's `name`
    type: str            # the row's SurveyCTO `type`, as written (see "Type text")
    xpath: str           # absolute path of the node in the XForm primary instance
    row: int             # 1-based row number on the `survey` sheet

@dataclass(frozen=True)
class ConversionResult:
    xform: str                   # the XForm document, as text
    form_id: str
    title: str
    version: str | None
    fields: tuple[FieldInfo, ...]
    warnings: tuple[str, ...]

    def write(self, xform_path, catalog_path=None) -> None: ...

class ConversionError(Exception): ...
```

Command line:

```
python -m ctosim_xlsform INPUT.xlsx -o OUTPUT.xml
```

## Behaviour

### Input

- The input is an `.xlsx` workbook with a `survey` sheet and, usually, a
  `choices` and a `settings` sheet. Other sheets (for example SurveyCTO's
  `help-survey`) are ignored.
- The first row of each sheet holds the column headers. Header text is
  matched after removing leading and trailing white space; matching is
  case-sensitive except where stated.
- Cells hold literal values. When a cell holds an Excel formula, the value
  Excel stored with the file (the cached result) is used. A formula with
  no stored result counts as an empty cell.
- Completely empty rows are ignored.
- The input file is never modified.

### Column names

SurveyCTO column names on the `survey` sheet are understood as their ODK
equivalents. The rename applies to the column name with or without a
language suffix (`relevance` and `relevance:Amharic` alike):

| SurveyCTO column | ODK equivalent |
|---|---|
| `relevance` | `relevant` |
| `read only` | `read_only` |
| `constraint message` | `constraint_message` |
| `required message` | `required_message` |

Translations given as `column:Language` (single colon) are kept: the
XForm contains one translation per language that appears in any label,
hint or message column, using the language name as written. When the form
has at least one translated column, the unsuffixed columns form the
translation named by the `default_language` setting, or `default` when
that setting is absent or empty, and that translation is the XForm's
default (`default="true()"`).

SurveyCTO columns with no runtime meaning are ignored: `note`,
`response_note`, `publishable`, `minimum_seconds`, `disabled` (see below),
`accuracy_threshold`, `mediatype`.

### Disabled rows

A `survey` row whose `disabled` cell is `yes` (case-insensitive, ignoring
surrounding white space) is left out of the form. When that row is a
`begin group` or `begin repeat`, every row up to and including its
matching `end group` / `end repeat` is left out as well. Each block left
out produces one warning naming its first row and its `name`.

### Field types

`type` text is read after trimming and collapsing runs of white space to
one space.

SurveyCTO-only types are converted as follows. All other types are passed
to `pyxform` unchanged.

| SurveyCTO `type` | Becomes | Value in the XForm |
|---|---|---|
| `calculate_here` | `calculate` | keeps its `calculation` |
| `caseid`, `comments`, `text audit`, `audio audit`, `speed violations count`, `speed violations list`, `speed violations audit` | `calculate` | empty string (`calculation` = `''`) |
| any type whose first word is `sensor_statistic` or `sensor_stream` | `calculate` | empty string |
| `enumerator` | `text` | (a visible text question) |

Each converted row produces one warning naming its row, `name` and
original type. In the field catalog, these rows keep their original type.

### Expressions

In the `relevance`, `constraint`, `calculation`, `required`,
`choice_filter` and `repeat_count` cells, a call to `selected()` whose
second argument is a bare numeric literal (optionally negative, with an
optional decimal part, e.g. `1`, `-99`, `2.5`) is rewritten with that
literal quoted: `selected(${q}, 1)` becomes `selected(${q}, '1')`.
White space inside the call is allowed. Nothing else in the expression
changes; in particular, text inside string literals is never rewritten,
and a numeric literal that is already quoted is left as it is.

### Choices

Duplicate `name`/`value` entries within one choice list are accepted
(SurveyCTO allows them, `pyxform` does not by default). SurveyCTO's
`value` column on the `choices` sheet is accepted as the choice name
column.

### Settings

- `form_id`, `form_title` and `version` come from row 2 of the
  `settings` sheet.
- When there is no `settings` sheet, or `form_id` is empty, the form id is
  the input file name without its extension, with every character that is
  not an ASCII letter, digit, `_` or `-` replaced by `_`, prefixed with
  `f_` when it does not start with a letter. When `form_title` is empty,
  the title is the form id.
- `version` is returned as text exactly as stored (a number such as
  `2407241039.0` read from Excel is given as `"2407241039"`; a whole
  number never carries a decimal part). An empty version gives `None` and
  the XForm then has no `version` attribute.

### Output

- `xform` is a complete XForm document (UTF-8 text) whose primary instance
  root element is `data`, with attributes `id` = `form_id` and, when
  present, `version`. It loads with module 01's `FormLoader`.
- `fields` lists every `survey` row that produces a node in the primary
  instance, in sheet order: questions, notes, calculates, metadata fields
  (`start`, `end`, `deviceid`, ...), converted SurveyCTO-only types, and
  `begin group` / `begin repeat` rows (whose node is the group element).
  `end group` / `end repeat` rows and disabled rows are not listed.
- `FieldInfo.xpath` is the node's absolute path in the instance, from the
  root: `/data/<group>/.../<name>`, with no positional predicates; for
  fields inside repeats it is the path of the repeated template node, e.g.
  `/data/members/age`.
- `warnings` contains the messages described above, in sheet order.
- Conversion is deterministic: converting the same file twice gives
  identical `xform` text and identical `fields` and `warnings`.

### Writing results

`write(xform_path, catalog_path=None)` writes the XForm as UTF-8 to
`xform_path` and the field catalog to `catalog_path`. When
`catalog_path` is `None`, it is `xform_path` with its extension replaced
by `.fields.json` (`out/form.xml` → `out/form.fields.json`). Existing
files are overwritten. The catalog is UTF-8 JSON:

```json
{
  "form_id": "household",
  "title": "IOM Household",
  "version": "2407241039",
  "fields": [
    {"name": "caseid", "type": "caseid", "xpath": "/data/caseid", "row": 8}
  ]
}
```

with the keys in that order, indented by two spaces, ending with a newline.

### Command line

- `python -m ctosim_xlsform INPUT.xlsx -o OUTPUT.xml` converts and writes
  both files as `write(OUTPUT.xml)` does, prints each warning on its own
  line to standard error, and exits with status 0.
- On a `ConversionError`, it prints the error message to standard error,
  writes no file, and exits with status 1.
- Missing arguments or an unknown option: a usage message on standard
  error and exit status 2.

### Errors

`convert_xlsform` raises `ConversionError` — never another exception
type — when:

- the file does not exist, cannot be read, or is not an `.xlsx` workbook;
- there is no `survey` sheet, or it has no `type` or no `name` column;
- groups or repeats are not balanced (an `end` without a matching
  `begin`, or a `begin` never closed);
- a `select_one` / `select_multiple` refers to a choice list that does
  not exist;
- `pyxform` rejects the form for any other reason.

The message names the input file and says what is wrong; when the problem
is tied to a row, the message includes the row number. When the failure
comes from an underlying exception, it is kept as the cause (`__cause__`).

## Clarifications (answers to `02-xlsform-converter.questions.md`)

Each of the fifteen points was reviewed on its own. Assumptions 1–13 and
15 are confirmed. Point 14 is right that pytest cannot prove the XForm
loads in the Java `FormLoader`; that check belongs to a later Java
integration test over the converted sample forms, not to this module's
unit tests.

## Amendment 1: XForm representation produced by pyxform

The conversion itself is delegated to `pyxform` 4.5.0, and the XForm keeps
pyxform's representation. Two consequences that tests on the XML must
take into account:

- **Choice lists.** Every choice list is written as a secondary instance
  `<instance id="LIST_NAME">` whose `<root>` holds one `<item>` per choice,
  in sheet order, with the choice value in a `<name>` child and its label
  in a `<label>` child (or an `itext` reference when the form is
  translated). Select controls do not contain inline `<item>` elements;
  they reference their list with `<itemset nodeset="instance('LIST_NAME')/root/item...">`
  (followed by the choice filter predicate, when there is one). Duplicate
  values appear as separate `<item>` elements.
- **Repeat counts.** When `repeat_count` is a single field reference
  (`${size}`), the `<repeat>` element's `jr:count` refers to that field
  directly and no node is added. Otherwise the expression is not written
  on the `<repeat>` element itself: pyxform adds a hidden calculated node named
  `<repeat name>_count` next to the repeat (for a repeat at
  `/data/target`, the node `/data/target_count`), whose `bind` carries the
  expression in its `calculate` attribute; the `<repeat>` element's
  `jr:count` refers to that node. The `selected()` rewriting therefore
  shows in that bind's `calculate`. This generated node is not a survey
  row and is not listed in `fields`.

## Amendment 2: `calculate_here` is computed where it stands

SurveyCTO computes a `calculate_here` field when the enumerator reaches
its position in the form, not when the form opens (so
`once(duration())` there records the time spent up to that point). A
plain `calculate` cannot express that, so the conversion of
`calculate_here` changes; the row of the "Field types" table for
`calculate_here` is replaced by this section. Other types are unchanged.

A `calculate_here` row becomes, in the XForm:

- a **read-only text question** at the row's position in the body, with
  the row's `name`, the row's `relevance` (rewritten as any relevance), and
  a label equal to its `name`;
- **no `calculate` attribute**: its bind carries instead the attribute
  `ctosim:calculate-here`, whose value is the row's `calculation`,
  rewritten by the rules of "Expressions" and with `${field}` references
  replaced by paths exactly as pyxform replaces them in a `calculate`
  (an empty calculation gives no attribute: the field is then an empty
  read-only question);
- the root element declares the prefix `ctosim` for the namespace
  `urn:ctosim:xforms`, in addition to the namespaces the form's own
  settings declare.

The row's other columns (`required`, `constraint`, `default`,
`appearance`, `hint`, `read only`, `choice_filter`, `label`) are not
carried over. The field catalog still lists the row with type
`calculate_here`, its own `xpath` and `row`. The conversion warning for
the row (one per converted row, naming its row, `name` and original type)
is kept.

Only `calculate_here` rows produce the `ctosim:calculate-here` attribute
or the `ctosim` namespace declaration; a form without such rows gives the
same XForm as before this amendment.

Correction (Amendment 1, repeat counts): the generated `<repeat>_count`
node exists only when `repeat_count` is an expression other than a single
field reference, as pyxform 4.5.0 does (checked on `${size}` and
`${size} + 1`).

Clarification (Amendment 2): assumptions 16–20 of the questions file are
reviewed one by one and confirmed. In particular, an enabled
`calculate_here` row with no calculation still declares the `ctosim`
namespace, while a disabled one does not (17).

## Amendment 3: minimum times and speed-violation fields

SurveyCTO records a **speed violation** when the enumerator tries to leave
a field before its `minimum_seconds` have passed; `speed violations count`
and `speed violations list` fields hold the result. So that the simulator
can reproduce them, the converter keeps that information in the XForm,
as bind attributes in the namespace of Amendment 2. Everything else is
unchanged; in particular these rows are still listed in the catalog with
their own type.

- **`minimum_seconds` column.** A survey row whose `minimum_seconds` cell
  is not empty (after trimming) gets the bind attribute
  `ctosim:minimum-seconds` holding that number as text, written like the
  form version (a whole number has no decimal part: `10.0` gives `"10"`;
  `2.5` gives `"2.5"`). A value that is not a number ≥ 0 raises
  `ConversionError` naming the row. Rows of a disabled block keep no
  attribute (they are left out). The column is otherwise ignored, as
  before.
- **Speed-violation fields.** A `speed violations count` row's bind gets
  `ctosim:speed-violations="count"`, a `speed violations list` row's bind
  `ctosim:speed-violations="list"`; both stay hidden calculates with an
  empty value. `speed violations audit` rows are unchanged.
- **Namespace.** The root declares the prefix `ctosim` for
  `urn:ctosim:xforms` whenever the XForm has any `ctosim:` attribute
  (from this amendment or Amendment 2), and only then.

Clarification (Amendment 3): the assumptions recorded for Amendment 3 in
the questions file are reviewed one by one and confirmed: every enabled
row with a minimum time gets the attribute, whatever its type; only
ordinary numbers ≥ 0 are accepted; disabled rows keep none.

## Amendment 4: long runs of empty or disabled rows

A real form (a follow-up survey with 1 448 rows) disables a group of
136 rows. The converter then lost every row after that group and
reported its enclosing group as unmatched. Completely empty rows and
rows left out (disabled rows and the contents of disabled groups) are
ignored **wherever they are and however many follow each other**: a
form converts the same with any number of consecutive empty or
left-out rows (100, 1 000) as without them. Row numbers in error
messages and warnings remain those of the user's `survey` sheet.
Everything else is unchanged.

## Amendment 5: translations together with ctosim's bind columns

The same form has labels only in translated columns (`label:French`,
`label:Haousa`, `label:Zarma`) and `calculate_here` fields. Its first
note was rejected as having "no label or hint". Translated columns
written `column:Language` (and `media:image`, `media:audio`,
`media:video`) are kept as stated in "Column names" **whatever other
rows the form has**, including `calculate_here` fields (Amendment 2),
minimum times and speed-violation fields (Amendment 3): such a form
converts, and each translation holds its labels.

Clarification (Amendments 4 and 5): the assumptions recorded in the
questions file for these amendments are reviewed one by one and
confirmed. In particular, the lengths named in Amendment 4 are examples,
not limits; an error in an enabled row after a long gap still names that
row; Amendment 5 changes no other column rule.

## Amendment 6: `or_other`, and only the converter's own warnings

SurveyCTO forms often write `select_one LIST or_other` (or
`select_multiple LIST or_other`): the question offers a last choice
"Other", and a text question asks to specify it. The data exported by
SurveyCTO, and the import do-files it generates, then hold a column
`<name>_other`. Everything not mentioned here is unchanged.

### `or_other`

When the type of a `select_one` or `select_multiple` row is followed by
the word `or_other` (after the list name, separated by spaces):

- the question offers the choices of its list followed by one more
  choice, of value `other` and label `Other` (the same label in every
  language of the form);
- a `text` question named `<name>_other`, labelled `Specify other.` in
  every language, follows the question immediately, in the same group or
  repeat; it is relevant exactly when the question is relevant and its
  answer includes `other`;
- the field catalog lists `<name>_other` right after the question, with
  type `text` and the row of the question.

A row whose type has any other word after the list name is rejected as
before.

### Warnings

The converter's standard error holds only its own warnings and errors:
messages that the libraries it uses would print (for example about
spreadsheet features they do not support) are not written.

Clarification (Amendment 6): assumptions 34–39 of the questions file are
reviewed one by one and confirmed. In particular, a question with
`or_other` offers its list followed by one `other`, while plain
questions using the same list keep their choices (34); collisions with
an existing `other` value or `<name>_other` field are not specified (35);
the companion is relevant when the question is relevant and its answer
holds the token `other` (36); `Other` and `Specify other.` are given in
every language of the form, including one introduced only by a hint
(37); the catalog entry has the question's row, and no warning is added
(38); library messages are kept off stderr whether they come as Python
warnings, printing or logging, and only during a conversion (39).

## Amendment 7: typographic quotes, and texts of hidden calculates

Two kinds of real forms that SurveyCTO accepts failed to convert.
Everything not mentioned here is unchanged.

### Typographic quotes

Typographic quotes (`‘` U+2018, `’` U+2019, `“` U+201C, `”` U+201D)
are kept as written in every cell, expressions included: SurveyCTO does
not replace them, and in an expression they are ordinary characters, so
`if(${x}=1, 'l’argent', '')` is a string holding `l’argent`. The
converted XForm and the field catalog hold them unchanged.

### Labels and hints of `calculate` rows

A `calculate` row (and a row converted to a hidden calculate, spec 02)
is never displayed, so its label and hint columns, in every language,
are not part of the converted form: a reference such as `${emiscode}`
written there does not have to name a field, and the form converts as
if those cells were empty. Other columns of the row (the calculation,
relevance, ...) are converted as before.
