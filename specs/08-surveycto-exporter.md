# Spec 08 — SurveyCtoExporter

## Purpose

Write simulated submissions (spec 07) as SurveyCTO data exports, so that
the cleaning and analysis code written for real data runs unchanged on
simulated data. Two layouts are produced, as SurveyCTO produces them with
its default export options:

- the **wide** CSV: one row per submission, repeated fields spread over
  numbered columns;
- the **long** CSVs: one file for the submissions, plus one file per
  repeat group with one row per repeat instance, linked by keys.

The layout described here was read from real SurveyCTO exports of the
coverage form, committed in `samples/coverage/wide/` and
`samples/coverage/long/` (see `samples/coverage/README.md`).

Language: Java. Package `ctosim`.

## Public API

```java
public final class FieldCatalog {
    public static FieldCatalog load(java.nio.file.Path json) throws java.io.IOException;
    public String formId();
    public String title();
    public String version();                       // null when absent
    public java.util.List<FieldInfo> fields();     // in catalog order
}

public record FieldInfo(String name, String type, String xpath, int row) { }

public final class SurveyCtoExporter {
    public SurveyCtoExporter(FieldCatalog catalog, LoadedForm form, String serverName);
    public String wideFileName();
    public String wide(java.util.List<Submission> submissions);
    public java.util.Map<String, String> longFiles(java.util.List<Submission> submissions);
    public void writeAll(java.util.List<Submission> submissions, java.nio.file.Path directory)
            throws java.io.IOException;
}
```

Addition to `Submission` (spec 07), so that submissions can be built from
an instance written elsewhere:

```java
public static Submission of(int number, String instanceXml,
                            java.time.LocalDateTime start, java.time.LocalDateTime end);
```

It returns a submission with these values, `answered()` 0 and no warnings.
A null argument throws `NullPointerException`; `number` < 1 throws
`IllegalArgumentException`. The XML is not checked at that point.

## Field catalog

`FieldCatalog.load` reads the JSON file written by the converter (spec 02,
"Writing results"): an object with `form_id`, `title`, `version` and
`fields`, each field an object with `name`, `type`, `xpath` and `row`.

- `formId()`, `version()` and the fields are the file's values; a
  missing or `null` `version` gives `null`.
- `title()` is the file's `title`; when it is missing, `null` or empty,
  the title is the form id.
- `fields()` is unmodifiable and keeps the file's order. Field `type`
  is kept exactly as written (`"select_multiple assets"`, `"text audit"`).
- A file that cannot be read, is not valid JSON, has no `form_id` or no
  `fields` array, or has a field missing `name`, `type`, `xpath` or `row`,
  throws `IOException` whose message names the file.
- Unknown keys are ignored.

## Exporter construction

`new SurveyCtoExporter(catalog, form, serverName)`:

- a null argument throws `NullPointerException`;
- a `serverName` that is empty or contains a character other than an
  ASCII letter, digit or `-` throws `IllegalArgumentException`;
- a catalog whose form id differs from `form.formId()` throws
  `IllegalArgumentException`.

`wideFileName()` is the title followed by `_WIDE.csv`
(`ctosim coverage test_WIDE.csv`). In every file name produced by the
exporter, each of the characters `\ / : * ? " < > |` in the title is
replaced by `_`.

## Inputs read from each submission

- The instance XML. Its root element must be the form's root, with an
  `id` attribute equal to the catalog's form id, and it must contain an
  `instanceID` element child of a `meta` element child of the root.
  Otherwise (including XML that is not well formed), `wide`, `longFiles`
  and `writeAll` throw `IllegalArgumentException` naming the submission
  number.
- `end()`, used for `SubmissionDate`.

A field's **stored value** in a given instance of its parent is the text
content of its element in the XML. A field whose element is absent from
the XML (the engine leaves out elements that are not relevant), or whose
element is empty, has an empty stored value. Columns always come from the
catalog, never from the elements present in the XML.

## Which fields give columns

Every catalog field gives columns except:

- `begin group` rows: groups give no column; their fields give columns
  as if the group were not there;
- `note` fields;
- `begin repeat` rows, which are handled as repeat groups (below).

A field's type is the first word of its catalog `type`, compared in lower
case; `text audit`, `audio audit`, `speed violations ...` and
`sensor_statistic ...` keep their full type.

## Cell values

| Field type | Columns | Value |
|---|---|---|
| `date`, `today` | one | date as `MMM d, yyyy` (`Oct 7, 2026`) |
| `time` | one | time as `h:mm:ss a` (`8:30:20 AM`) |
| `datetime`, `start`, `end` | one | `MMM d, yyyy h:mm:ss a` (`Oct 15, 2026 8:30:27 AM`) |
| `select_multiple` | the field, then one per choice | see below |
| `geopoint` | four | see below |
| `image`, `audio`, `video`, `file` | one | attachment URL of the stored value |
| `text audit` | one | attachment URL of `TA_<uuid>.csv` |
| any other type | one | the stored value, unchanged |

- Month names are the English three-letter abbreviations (`Jan` ... `Dec`,
  with `Sep`), and `AM` / `PM` are in capitals. Hours have no leading
  zero; minutes and seconds have two digits; `12:05:00 AM` is five minutes
  past midnight.
- Dates, times and date-times are written as the wall-clock time stored in
  the XML; a UTC offset in the stored value is dropped, not applied
  (`2026-10-15T08:30:27.000+03:00` gives `Oct 15, 2026 8:30:27 AM`).
  Fractions of a second are dropped.
- A stored value that cannot be read as the field's type, and an empty
  stored value, are written unchanged (an empty value gives an empty
  cell).
- `calculate` fields are always written unchanged, whatever their value
  looks like.

### `select_multiple`

The field's own column holds the stored value (space-separated choice
values). It is followed by one **dummy column** per choice of the
question's choice list, in the order the form lists them, with no
choice filter applied; a value listed more than once gets one column, at
its first position. The dummy column of choice value `v` is named
`<name>_<v'>`, where `v'` is `v` with every `-` replaced by `_`
(`assets_96`, `assets__99`).

A dummy cell is `1` when `v` is one of the stored value's items and `0`
otherwise. When the stored value is empty, every dummy cell is empty.

### `geopoint`

Four columns, named `<name>-Latitude`, `<name>-Longitude`,
`<name>-Altitude`, `<name>-Accuracy`, holding the first to fourth
space-separated items of the stored value; a missing item gives an empty
cell. There is no column for the combined value.

### Attachment URLs

The URL of file `f` in a submission whose instance id is `i` is

```
https://<serverName>.surveycto.com/view/submission-attachment/?uuid=<enc(i)>&file=<enc(f)>
```

where `enc` is UTF-8 `application/x-www-form-urlencoded` encoding
(`uuid:77ee...` becomes `uuid%3A77ee...`).

- Media fields (`image`, `audio`, `video`, `file`): the URL of the stored
  value; an empty stored value gives an empty cell.
- `text audit` fields: when the field's element is present in the XML,
  the URL of `TA_<u>.csv`, where `u` is the instance id without a leading
  `uuid:`; when it is absent, an empty cell.

### CSV text

- Fields are separated by `,`; rows by a single line feed (`\n`), with no
  line feed after the last row.
- A cell is enclosed in double quotes only when it contains a comma, a
  double quote, a carriage return or a line feed; double quotes inside are
  doubled.
- Text is UTF-8 with no byte-order mark.
- The header row is always written, also when there are no submissions.

## Repeat groups

A repeat group's **instances** in a parent are its elements in the XML
under that parent (the submission root, or an instance of the enclosing
repeat), in document order, numbered from 1.

A repeat is **counted** when the form definition gives it a count
(`jr:count`). A counted repeat named `r` has a **count column** `r_count`,
placed at the repeat's position: its cell is the number of instances of
`r` in the parent, or empty when the element that should contain them is
absent from the XML (for example a group that is not relevant).
Repeats without a count have no count column.

### Wide layout

At the position of repeat group `r` in the catalog, the wide header has
`r`'s count column (when counted), then, for each instance number `k`
from 1 to `N(r)`, the columns of `r`'s fields for instance `k`, in
catalog order; a repeat nested in `r` is laid out in the same way inside
each instance's block.

- `N(r)` is the largest number of instances of `r` in any one parent,
  over all exported submissions (and, for a nested repeat, over all
  instances of its parents). Every block of the parent has the same
  `N(r)` instance blocks. When `N(r)` is 0 the repeat contributes no
  instance column (its count column, if any, remains).
- A column of a field inside repeats is the column name the field would
  have outside repeats, followed by `_<k1>` for the outermost repeat
  instance, `_<k2>` for the next, and so on (`member_name_2`,
  `job_title_1_2` for the second job of the first member,
  `member_activities__97_1`, `gps-Latitude_1`). A nested count column is
  named in the same way (`job_count_1`).
- Cells of instances that a submission does not have are empty.

The wide file has, in this order: `SubmissionDate`, the columns of the
catalog fields (with repeat groups laid out as above), `instanceID`,
`formdef_version`, `KEY`.

- `SubmissionDate`: `end()` formatted as a date-time above.
- `instanceID` and `KEY`: the instance id.
- `formdef_version`: the catalog's version, empty when `null`.

Rows follow the order of the submissions list.

### Long layout

`longFiles` returns an unmodifiable map from file name to file content,
iterated in this order: the main file, then one file per repeat group, in
catalog order of the `begin repeat` rows. Every repeat group of the form
has a file, even when no submission has an instance of it (the file then
holds only its header).

**Paths.** A repeat's path is the names of the groups and of the repeat
itself between the nearest enclosing repeat (or the root) and the repeat,
joined by `-`: for `/data/main/member` it is `main-member`, for
`/data/main/member/job` it is `job`.

**Keys.** The key of a submission is its instance id. The key of instance
`k` of repeat `r` in a parent with key `P` is `P/<path of r>[k]`
(`uuid:77ee.../main-member[1]/job[2]`).

**Main file**, named `<title>.csv`: the same header as the wide file,
except that each repeat group not inside another repeat contributes its
count column (when counted) followed by a column `SET-OF-<r>`, instead of
its instance blocks. Its cell is `<submission key>/<path of r>` when the
submission has at least one instance of `r`, and empty otherwise. One row
per submission, in list order.

**Repeat file** for repeat `r`, named `<title>-<path of r>.csv`: columns
for `r`'s own fields in catalog order, cells as above with no `_<k>`
suffix; at the position of each repeat `s` directly nested in `r`, `s`'s
count column (when counted) then `SET-OF-<s>`, whose cell is
`<instance key>/<path of s>` when the instance has at least one instance
of `s`, and empty otherwise; then `PARENT_KEY`, `KEY` and `SET-OF-<r>`.

- One row per instance of `r`, ordered by submission (list order), then
  by parent instance, then by instance number.
- `PARENT_KEY` is the parent's key (the submission key for a repeat not
  inside another repeat); `KEY` is the instance's key; `SET-OF-<r>` is
  `<parent key>/<path of r>`.

### Writing files

`writeAll(submissions, directory)` creates `directory` (and missing
parents) when needed and writes the wide file under `wideFileName()` and
every long file under its name, with exactly the text that `wide` and
`longFiles` return, as UTF-8 without byte-order mark, replacing existing
files of the same names. Other files in the directory are left alone.

## Determinism

The output depends only on the catalog, the form, the server name and the
submissions (their XML, numbers and end times), in list order: the same
inputs give byte-identical text.

## Errors

- `wide`, `longFiles` and `writeAll` throw `NullPointerException` for a
  null list or a null element.
- `writeAll` throws `NullPointerException` for a null directory and lets
  file-system `IOException`s through.

## Acceptance against the real exports

With the catalog `samples/xforms/ctosim_coverage.fields.json`, the form
`samples/xforms/ctosim_coverage.xml` and server name `example`, three
submissions whose instance XML holds the values of the three exported
submissions (with `end()` equal to their `endtime`) give:

- a wide text equal to `samples/coverage/wide/ctosim coverage test_WIDE.csv`;
- long files equal to the four CSV files in `samples/coverage/long/`,
  with the same names.

## Not observed in the real exports

These follow the rules above but were not seen in an export, and are to
be checked when a form exercising them is exported: geopoint columns in a
repeat (`gps-Latitude_1`); dummy columns of a select_multiple in a nested
repeat; `instanceName` and `review_*` columns (not produced); what the
`comments`, `audio audit` and `speed violations ...` fields hold in a
real export (here written unchanged).

## Clarifications (answers to `08-surveycto-exporter.questions.md`)

All ten assumptions were reviewed one by one and are confirmed. In
particular: an unknown selected item stays in the field's own column and
adds no dummy column, and a fifth geopoint item is ignored (4); a present
but empty repeat element is an instance, and counts come from the
submitted elements, not from the count expression (6); `writeAll` does not
delete files left by earlier exports (8); the acceptance fixtures are
instances transcribed from the real exports (10).

## Amendment 1: second real export

From the exports of the second coverage form (`samples/coverage2/`,
filled with SurveyCTO Collect). Everything not mentioned here is
unchanged.

### Confirmed

These rules, listed as "not observed" above, are confirmed by that
export: geopoint columns in a repeat (`member_gps-Latitude_1`); dummy
columns of a select_multiple in a nested repeat (`job_days_6_1_2`: choice,
then instance numbers from the outermost repeat); the count column of a
counted repeat inside a repeat (`child_count_1`, at the repeat's
position); every `-` of a choice value replaced by `_` in dummy names
(`transport_moto_taxi`).

### API addition

```java
// added to Submission:
public static Submission of(int number, String instanceXml, java.time.LocalDateTime start,
                            java.time.LocalDateTime end, java.time.LocalDateTime submitted);
```

It behaves as the four-argument `of`, with `submitted()` (spec 07,
Amendment 2) equal to `submitted`; the four-argument `of` gives
`submitted()` equal to `end`. A null `submitted` throws
`NullPointerException`.

### Changes

- **`SubmissionDate`** is `submitted()` formatted as a date-time (it was
  `end()`).
- **`instanceName`.** When the form's primary instance has an element
  `instanceName` child of its `meta` element (a form with an
  `instance_name` setting), the wide file and the long main file have a
  column `instanceName` between `instanceID` and `formdef_version`; its
  cell is the stored value of that element (empty when absent from the
  XML). Forms without that element have no such column.
- **`audio audit` fields** are written like `text audit` fields: when
  the field's element is present in the XML, the attachment URL of
  `AA_<u>_AFTER_0S.m4a` (`u` as for text audits), otherwise an empty
  cell. (The `AFTER_0S` part was observed with the start parameter
  `s=0`; other start values have not been observed.)

### Acceptance against the second export

With the catalog `samples/xforms/ctosim_coverage2.fields.json`, the form
`samples/xforms/ctosim_coverage2.xml` and server name `example`, three
submissions whose instance XML holds the values of the three exported
submissions, built with the five-argument `Submission.of` (`end()` equal
to their `endtime`, `submitted()` to their `SubmissionDate`), give a wide
text equal to `samples/coverage2/wide/ctosim coverage test 2_WIDE.csv`
and long files equal to the five CSV files of `samples/coverage2/long/`,
with the same names.

### Still not observed

A relevant select_multiple left blank (its dummy cells, empty by the rule
above); what a `comments` field holds (per SurveyCTO's documentation, the
URL of a per-submission CSV of comments, whose file name has not been
observed; written unchanged until then); `speed violations` fields when
no violation occurred; `review_*` columns.
