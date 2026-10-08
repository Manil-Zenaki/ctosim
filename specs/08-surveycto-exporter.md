# Spec 08 — SurveyCtoExporter (DRAFT)

> **Status: draft, not ready for tests.** The column layout below must be
> checked against a real raw SurveyCTO export before this spec is
> finalised and handed to the test author. The points to confirm are
> listed at the end.

## Purpose

Write simulated submissions (spec 07) as a SurveyCTO data export, so that
the cleaning and analysis code written for the real data runs unchanged
on the simulated data. The first target is SurveyCTO's **wide CSV**
export: one row per submission, one column per field, repeated fields
spread over numbered columns.

Language: Java. Package `ctosim`.

## Inputs

- The **field catalog** written by the converter (spec 02,
  `form.fields.json`): every survey row's name, SurveyCTO type, instance
  path and row number, in form order. It carries the SurveyCTO-only types
  (`caseid`, `comments`, `text audit`, ...) that the XForm no longer shows.
- The **loaded form** (spec 01), for choice lists (dummy columns of
  `select_multiple` fields).
- The **submissions** (spec 07): their instance XML, start and end times.
  The XML leaves out non-relevant elements, so columns come from the
  catalog, not from the XML.

## Proposed public API

```java
public final class FieldCatalog {
    public static FieldCatalog load(java.nio.file.Path json) throws java.io.IOException;
    public String formId();
    public String version();                       // null when absent
    public java.util.List<FieldInfo> fields();     // in catalog order
}

public record FieldInfo(String name, String type, String xpath, int row) { }

public final class SurveyCtoExporter {
    public SurveyCtoExporter(FieldCatalog catalog, LoadedForm form);
    public java.util.List<String> columns(java.util.List<Submission> submissions);
    public void writeWideCsv(java.util.List<Submission> submissions, java.io.Writer out)
            throws java.io.IOException;
}
```

## Behaviour (what is settled)

- One header row, then one row per submission, in the order given.
- Values are the submission's stored values; a field that is absent from
  the XML (not relevant) or empty gives an empty cell.
- Groups produce no column; a field's column is named by the field's
  `name` alone (SurveyCTO field names are unique within a form).
- Notes (`note`) produce no column.
- Repeated fields are spread over numbered columns, one per instance, up
  to the largest number of instances among the exported submissions;
  submissions with fewer instances leave the extra cells empty.
- `select_multiple` fields give their space-separated value column.
- Output is deterministic: the same submissions give byte-identical CSV.
- CSV quoting follows RFC 4180 (a value containing a comma, a double quote
  or a line break is quoted, double quotes doubled).

## To confirm against a real raw SurveyCTO export

1. **Metadata columns**: names and positions (`SubmissionDate` first?
   `KEY`, `formdef_version`, `instanceName`, `review_*` columns at the
   end?) and how `KEY` relates to `instanceID`.
2. **Repeat column names**: `name_1`, `name_2`, ... for instances; nested
   repeats `name_1_1`? Is a repeat-count column added?
3. **`select_multiple` dummy columns**: present by default? Named
   `name_<value>`? How are negative values (`-99`) written in the column
   name?
4. **Geopoints**: split into `name-Latitude`, `name-Longitude`,
   `name-Altitude`, `name-Accuracy` (with or without the combined column)?
5. **Dates and times**: format of `date`, `time`, `datetime`, `start`,
   `end` and `SubmissionDate` cells (e.g. `Jan 5, 2026 8:00:00 AM`?).
6. **SurveyCTO-only types**: what `comments`, `text audit`, `audio audit`
   and media fields hold in the export (file names, URLs?).
7. **Encoding**: UTF-8 with or without a byte-order mark; line endings.
8. **Long format**: whether the long export (one file per repeat group,
   joined by `PARENT_KEY` / `KEY`) is needed as well.
