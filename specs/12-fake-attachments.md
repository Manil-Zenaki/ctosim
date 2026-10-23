# Spec 12 — Fictitious attachments (`fakepreload`)

## Purpose

Most real forms read preloaded data: CSV files attached to the form,
read by `pulldata()` (spec 03, Amendment 3) and by `search()` choice
lists (spec 07, Amendment 3). Without those files every preloaded value
is blank, repeats counted from them are empty and the branches that test
them are never walked, so most of the form goes untested. The real
files are often not available (they hold personal data).

This module builds **fictitious attached files** from what the form
itself reads from them: the files, their columns, the keys it looks up
and the choice lists it loads. Every value is invented. The command
line (spec 10, Amendment 2) and the Stata command (spec 11, Amendment 5)
use it on request only, so that a real file left out by mistake is never
hidden.

Language: Java. Package `ctosim`.

## Public API

```java
public final class FakeAttachments {
    public static FakeAttachments of(LoadedForm form, long seed);
    public java.util.SortedMap<String, String> files();
    public java.util.SortedMap<String, java.util.List<String>> keyAnswers();
}
```

- `of(null, seed)` throws `NullPointerException`.
- `files()` maps the name of each fictitious file (as in
  `LoadedForm.missingAttachments()`, without `.csv`) to its content.
  There is one entry for each name of `form.missingAttachments()` that
  the form reads with `pulldata()` or `search()`, and no other.
- `keyAnswers()` maps question names to the values a question should be
  answered with so that the form finds its preloaded rows (below).
- Both maps are unmodifiable, ordered by natural string order, and so
  are their lists.
- The same form (loaded from the same bytes) and seed always give equal
  results, whatever the machine's locale or time zone.

## What the form reads

The module reads the form's own expressions: the `bind` attributes and
`setvalue` values listed in spec 01, and the `appearance` attributes of
its select controls.

- **pulldata.** A call `pulldata(F, V, K, E)` whose first three
  arguments are string literals reads value column `V` of file `F`, and
  looks up key column `K` with the value of expression `E`. A call whose
  file is not a literal is ignored; a call whose column names are not
  literals adds nothing for them.
- **search.** A select whose appearance holds `search(F, ...)` with a
  literal `F` (spec 07, Amendment 3) loads choices from file `F`: its
  **value column** is the column named by its list's placeholder
  (spec 07), and its **label column** is the label of that placeholder
  in the form's default language (as the engine returns it), when that
  label is not empty. With six arguments, the fifth (a literal) is a
  **filter column**. With four or six, the columns named by the third
  argument (literals) are **match columns**.

A file's **columns** are all those named for it above, in their order of
first appearance in the form (body order for appearances, document order
for binds), without repeats.

## Content of a file

The content is CSV as `pulldata()` reads it: UTF-8 without byte-order
mark, one header line with the column names, then the rows; lines end
with LF; a value is quoted (with `"` doubled) only when it holds a
comma, a quote or a line break. Every row has a value in every column.

### Rows

The **levels** of a file are the value columns of its `search()`
selects, in the order of their selects in the form's body, without
repeats. The rows enumerate a tree of codes over the levels:

- the first level has codes `1`, `2`, `3`;
- each code of a level has `b` child codes in the next level: for a
  parent code `p`, the codes `p1` ... `pb` (as text: `11`, `12`, `13`,
  then `111`...). Level by level, `b` is 3 when the number of codes of
  the new level stays at most 500, otherwise 2 under the same condition,
  otherwise 1;
- there is one row per code of the last level, holding in each level's
  column the code of that level on its path (its prefix), so a file has
  at most 500 rows.

A file without levels has 50 rows.

### Values

In the order below, the first rule that applies gives a column's values:

1. **Levels**: as above.
2. **Match and filter columns** that are not levels: the code of the
   level whose select's answer the call compares with that column (the
   select whose value is the call's `text` or `filterText` argument),
   when there is one; otherwise as rule 6.
3. **Label columns**: the label column of a level holds, on each row,
   the column's name, one space and the code of that level on the row
   (`VILLAGE 213`).
4. **Key columns**: when the key expression is a single reference to a
   select loaded from the same file, the key column holds, on each row,
   the code of that select's level. Otherwise each row has its own key: the row
   number (from 1) for a key looked up with a field of type `int`, and
   `K` followed by the row number on 4 digits (`K0001`) for any other.
5. **Value columns** read by `pulldata()`: by the column's name, split
   into words at `_`, `-`, digits and lower-to-upper case changes and
   compared in lower case, the first line that applies:

   | Words (any of) | Values |
   |---|---|
   | `phone`, `tel`, `telephone`, `mobile`, `cell` | `0` followed by 9 random digits |
   | `lat`, `latitude` | a decimal between −1 and 1 with 6 decimals, as spec 05 draws latitudes without an area |
   | `lon`, `lng`, `long`, `longitude` | likewise, a longitude |
   | `name`, `nom`, `nm`, `prenom`, `firstname`, `surname`, `lastname`, `fullname` | `<first> <last>` from the `firstName` and `lastName` text pools of the default configuration (spec 04) |
   | `age` | an integer from 1 to 80 |
   | `area`, `surface`, `superficie` | a decimal from 0.25 to 5.00 with 2 decimals |
   | `nb`, `num`, `nbr`, `number`, `count`, `total` | an integer from 1 to 4 |

6. **Compared values**: otherwise, when the form compares a field whose
   `calculate` is a `pulldata()` call reading that column with `=` or
   `!=` against literals (`${f} = 1`, `${f} != 'yes'`), a value drawn
   among those literals; otherwise an integer from 1 to 3.

Values drawn at random are drawn with a random sequence seeded by `seed`
and the file's name, so that adding a file does not change the others.

## Key answers

For each `pulldata()` key looked up with an expression that is a single
reference to a question (not a calculate) answered as `TEXT` or
`INTEGER`, `keyAnswers()` maps the
question's name to the file's keys in that column, in row order. When
several files are looked up with the same question, its list is the
keys of the first file in natural order.

## Errors

The module never throws because of the form's content: a call it cannot
read adds nothing.

## Clarification

Assumptions 1–15 of the questions file are reviewed one by one and
confirmed. In particular, key lists follow the rows' order (1); within a
call or a select, columns follow the order of the arguments, value then
key, value then label (2); the last line ends with LF (3); a relative
reference is a single reference, a function around it is not (6); only
`TEXT` and `INTEGER` questions get key answers, not selects (8); an
unmapped match or filter column goes straight to rule 6 (10); `of()`
writes nothing (15).

The tests also read files used only by `search()` as fictitious files,
which is what this spec means: `missingAttachments()` (spec 03) lists
only the sources of `pulldata()` calls, so the files of `files()` are
those the form reads with `pulldata()` or `search()` (as described
above) whose `<name>.csv` the media directory does not hold, whether or
not `missingAttachments()` lists them. A `search()` call that spec 07
cannot read (another number of arguments, an unknown type) reads nothing.

## Amendment 1: enough rows for distinct survey ids

With the fieldwork plan (spec 13), each submission should get its own
survey id. When that id is chosen from a fictitious file, the file must
hold at least as many ids as there are submissions; with three codes a
level, a district-village-household file has 27 households. Everything
not mentioned here is unchanged.

### API addition

```java
public static FakeAttachments of(LoadedForm form, long seed, String idField, int submissions);
```

- `of(form, seed)` is `of(form, seed, null, 0)`.
- `of(null, ...)` throws `NullPointerException`; a negative `submissions`
  throws `IllegalArgumentException`.

### The id's level

When `idField` names a `select_one` question whose choices are loaded by
`search()` from a fictitious file and whose value column is a level of
that file (its **id level**), the codes of the id level are built so
that the file has at least `submissions` rows:

- the levels before the id level are built as in "Rows";
- each code of the level before the id level (or the id level itself,
  when it is the first level) has `b` child codes in the id level, where
  `b` is the number of the rule of "Rows", or the number of parent codes
  divided into `submissions` and rounded up when that is larger (for the
  first level, `b` is the number of its codes: 3, or `submissions` when
  larger);
- when `b` is larger than 9, the index of a child is written with as many
  digits as `b`, padded with zeros (`p01` ... `p12`), so that codes stay
  distinct;
- the levels after the id level have one child code per code;
- the limit of 500 rows does not apply to such a file: it has as many
  rows as the id level has codes.

Otherwise (no `idField`, a field that is not such a question, or
`submissions` of at most the number of rows "Rows" gives), the files are
as before.
