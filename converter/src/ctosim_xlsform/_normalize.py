"""Reading a SurveyCTO workbook and normalising it into an ODK XLSForm for pyxform."""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any

from ._expressions import quote_numeric_selected_arguments

COLUMN_RENAMES = {
    "relevance": "relevant",
    "read only": "read_only",
    "constraint message": "constraint_message",
    "required message": "required_message",
}

IGNORED_COLUMNS = {"note", "response_note", "publishable", "disabled",
                   "accuracy_threshold", "mediatype"}

CALCULATE_HERE_COLUMN = "bind::ctosim:calculate-here"
MINIMUM_SECONDS_COLUMN = "bind::ctosim:minimum-seconds"
SPEED_VIOLATIONS_COLUMN = "bind::ctosim:speed-violations"
SPEED_VIOLATION_FIELDS = {"speed violations count": "count", "speed violations list": "list"}
CTOSIM_NAMESPACE = 'ctosim="urn:ctosim:xforms"'

EXPRESSION_COLUMNS = ("relevant", "constraint", "calculation", "required", "choice_filter", "repeat_count",
                      CALCULATE_HERE_COLUMN)

# Columns of a calculate_here row that its read-only question does not carry over (spec 02, Amendment 2).
_CALCULATE_HERE_DROPPED = ("required", "constraint", "default", "appearance", "choice_filter",
                           "constraint_message", "required_message")

EMPTY_CALCULATE_TYPES = {"caseid", "comments", "text audit", "audio audit", "speed violations count",
                         "speed violations list", "speed violations audit"}
SENSOR_TYPE_WORDS = {"sensor_statistic", "sensor_stream"}

# Settings passed through to pyxform; the others (encryption keys, submission URLs) have no
# bearing on a simulation.
PASSED_SETTINGS = ("default_language", "instance_name")

_STRUCTURE = re.compile(r"(begin|end)[ _](group|repeat)")
_SELECT = re.compile(r"(select_one|select_multiple) (\S+)")


class NormalizationError(Exception):
    """A problem in the spreadsheet itself; the message names the row when there is one."""


@dataclass
class Sheet:
    headers: list[str]
    rows: list[list[Any]]  # rows[k] is sheet row k + 2; empty rows are kept (as all-None)
    # rows the converter adds after rows[k], written with the user's row number k + 2
    added_after: dict[int, list[list[Any]]] = field(default_factory=dict)

    def column(self, name: str) -> int | None:
        return self.headers.index(name) if name in self.headers else None


@dataclass
class Normalized:
    survey: Sheet
    choices: Sheet | None
    settings: dict[str, Any]
    fields: list[tuple[str, str, str, int]] = field(default_factory=list)  # name, type, xpath, row
    warnings: list[str] = field(default_factory=list)
    uses_ctosim_namespace: bool = False
    or_other_lists: dict[str, str] = field(default_factory=dict)  # list name -> its copy with 'other'


def read_sheet(worksheet) -> Sheet:
    rows = [list(r) for r in worksheet.iter_rows(values_only=True)]
    if not rows:
        return Sheet([], [])
    headers = [str(h).strip() if h is not None else "" for h in rows[0]]
    body = []
    for r in rows[1:]:
        r = r + [None] * (len(headers) - len(r))
        body.append(r[: len(headers)] if len(headers) else r)
    return Sheet(headers, body)


def is_empty(row: list[Any]) -> bool:
    return all(v is None or (isinstance(v, str) and v.strip() == "") for v in row)


def text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value)


def normalize(survey: Sheet, choices: Sheet | None, settings: Sheet | None) -> Normalized:
    if "type" not in survey.headers or "name" not in survey.headers:
        missing = "type" if "type" not in survey.headers else "name"
        raise NormalizationError(f"the survey sheet has no '{missing}' column (it needs 'type' and 'name')")

    disabled_col = survey.column("disabled")
    out = Normalized(_renamed_survey(survey), choices, _settings(settings))
    s = out.survey
    t_col, n_col = s.column("type"), s.column("name")
    if s.column("calculation") is None:
        s.headers.append("calculation")
        for r in s.rows:
            r.append(None)
    c_col = s.column("calculation")

    list_names = _choice_list_names(choices)
    # (kind, row number, name, disabled): a disabled group stays on the stack so that its end row is
    # matched, but it is not part of the form and its name is not in the XPaths (Amendment 8)
    stack: list[tuple[str, int, str, bool]] = []

    for k, row in enumerate(s.rows):
        number = k + 2
        if is_empty(row):
            continue
        type_text = " ".join(text(row[t_col]).split())
        name = text(row[n_col]).strip()
        structure = _STRUCTURE.fullmatch(type_text)

        is_end = bool(structure) and structure.group(1) == "end"
        if not is_end and disabled_col is not None and text(survey.rows[k][disabled_col]).strip().lower() == "yes":
            if structure:
                kind = structure.group(2)
                out.warnings.append(f"row {number}: disabled {kind} '{name}' is left out; "
                                    f"the rows it holds are kept unless they are disabled themselves")
                stack.append((kind, number, name, True))
            else:
                out.warnings.append(f"row {number}: disabled field '{name}' is left out")
            _blank(row)
            continue

        row[t_col] = type_text
        if structure:
            kind = structure.group(2)
            if structure.group(1) == "begin":
                out.fields.append((name, type_text, _xpath(stack, name), number))
                stack.append((kind, number, name, False))
            else:
                if not stack:
                    raise NormalizationError(f"row {number}: 'end {kind}' has no matching 'begin {kind}' (groups are not balanced)")
                open_kind, open_row, open_name, open_disabled = stack.pop()
                if open_kind != kind:
                    raise NormalizationError(f"row {number}: 'end {kind}' does not match 'begin {open_kind}' "
                                             f"'{open_name}' on row {open_row} (groups and repeats are not balanced)")
                if open_disabled:
                    _blank(row)  # the end row of a disabled group goes with it
            continue

        select = _SELECT.match(type_text)
        if select and select.group(2) not in list_names:
            raise NormalizationError(f"row {number}: choice list '{select.group(2)}' used by '{name}' "
                                     f"does not exist on the choices sheet")

        if type_text == "calculate_here":
            _calculate_here(s, row, name)
            out.uses_ctosim_namespace = True
            out.warnings.append(f"row {number}: field '{name}' of SurveyCTO type 'calculate_here' becomes "
                                f"a read-only question computed when the form reaches it")
            out.fields.append((name, type_text, _xpath(stack, name), number))
            continue

        if type_text in SPEED_VIOLATION_FIELDS:
            _set(s, row, SPEED_VIOLATIONS_COLUMN, SPEED_VIOLATION_FIELDS[type_text])
            out.uses_ctosim_namespace = True

        converted = _convert_type(type_text)
        if converted is not None:
            new_type, empty_value = converted
            row[t_col] = new_type
            if empty_value:
                row[c_col] = "''"
            target = "a hidden calculate" if new_type == "calculate" else "a text question"
            out.warnings.append(f"row {number}: field '{name}' of SurveyCTO type '{type_text}' becomes {target}")

        if row[t_col] == "calculate":
            _drop_texts(s, row)
        out.fields.append((name, type_text, _xpath(stack, name), number))
        if select and type_text.split()[2:] == ["or_other"]:
            _or_other(out, k, row, select.group(1), select.group(2), name, list_names)
            out.fields.append((f"{name}_other", "text", _xpath(stack, f"{name}_other"), number))

    if stack:
        kind, number, name, _ = stack[-1]
        raise NormalizationError(f"row {number}: 'begin {kind}' '{name}' is never closed by an 'end {kind}' "
                                 f"(groups and repeats are not balanced)")

    if _minimum_seconds(s):
        out.uses_ctosim_namespace = True

    for col_name in EXPRESSION_COLUMNS:
        col = s.column(col_name)
        if col is not None:
            for row in s.rows:
                if isinstance(row[col], str):
                    row[col] = quote_numeric_selected_arguments(row[col])
    return out


def _renamed_survey(survey: Sheet) -> Sheet:
    keep = []
    headers = []
    for i, h in enumerate(survey.headers):
        base, sep, lang = h.partition(":")
        if base in IGNORED_COLUMNS or h == "":
            continue
        keep.append(i)
        headers.append(COLUMN_RENAMES.get(base, base) + (":" + lang if sep else ""))
    return Sheet(headers, [[r[i] for i in keep] for r in survey.rows])


def _drop_texts(s: Sheet, row: list[Any]) -> None:
    """A calculate is never displayed: its labels and hints, in every language, are left out."""
    for i, h in enumerate(s.headers):
        # label, hint, label:French, hint::French, and the hint_urdu spelling some forms use
        if re.match(r"(label|hint)([:_]|$)", h):
            row[i] = None


def _languages(*sheets: Sheet | None) -> set[str]:
    """The languages named by translated columns ('label::French', 'hint:French', ...)."""
    found = set()
    for sheet in sheets:
        for h in sheet.headers if sheet is not None else ():
            base, sep, rest = h.partition(":")
            if sep and base not in ("media", "bind", "body", "instance") and rest.lstrip(":"):
                found.add(rest.lstrip(":"))
    return found


def _label_columns(sheet: Sheet, languages: set[str]) -> list[int]:
    """The label columns of a sheet, adding one for each language that has none."""
    have = {}
    for i, h in enumerate(sheet.headers):
        base, sep, rest = h.partition(":")
        if base == "label":
            have[rest.lstrip(":") if sep else ""] = i
    for language in sorted(languages - set(have)):
        sheet.headers.append(f"label::{language}")
        for r in sheet.rows:
            r.append(None)
        for added in sheet.added_after.values():
            for r in added:
                r.append(None)
        have[language] = len(sheet.headers) - 1
    return list(have.values())


def _or_other(out: Normalized, k: int, row: list[Any], kind: str, list_name: str, name: str,
              list_names: set[str]) -> None:
    """
    SurveyCTO's or_other: the question offers its list followed by the choice 'other' (label
    'Other'), and a text question <name>_other follows it (spec 02, Amendment 6).
    """
    s, choices = out.survey, out.choices
    languages = _languages(s, choices)
    copy_name = f"{list_name}_or_other"
    while copy_name in list_names and not out.or_other_lists.get(list_name) == copy_name:
        copy_name += "_"
    if list_name not in out.or_other_lists:
        out.or_other_lists[list_name] = copy_name
        list_names.add(copy_name)
        out.choices = choices = Sheet(list(choices.headers), [list(r) for r in choices.rows], dict(choices.added_after))
        l_col = choices.column("list_name")
        v_col = choices.column("name") if choices.column("name") is not None else choices.column("value")
        labels = _label_columns(choices, languages)
        copied = []
        for r in choices.rows:
            if not is_empty(r) and text(r[l_col]).strip() == list_name:
                c = list(r)
                c[l_col] = copy_name
                copied.append(c)
        other = [None] * len(choices.headers)
        other[l_col], other[v_col] = copy_name, "other"
        for i in labels:
            other[i] = "Other"
        choices.rows.extend(copied + [other])
    row[s.column("type")] = f"{kind} {out.or_other_lists[list_name]}"

    labels = _label_columns(s, languages)
    companion = [None] * len(s.headers)
    companion[s.column("type")], companion[s.column("name")] = "text", f"{name}_other"
    for i in labels:
        companion[i] = "Specify other."
    if s.column("relevant") is None:
        s.headers.append("relevant")
        for r in s.rows:
            r.append(None)
        for added in s.added_after.values():
            for r in added:
                r.append(None)
        companion.append(None)
    r_col = s.column("relevant")
    own = text(row[r_col]).strip()
    selected = f"selected(${{{name}}}, 'other')"
    companion[r_col] = f"({own}) and {selected}" if own else selected
    s.added_after.setdefault(k, []).append(companion)


def _settings(settings: Sheet | None) -> dict[str, Any]:
    if settings is None or not settings.rows:
        return {}
    row = settings.rows[0]
    return {h: row[i] for i, h in enumerate(settings.headers) if h}


def _choice_list_names(choices: Sheet | None) -> set[str]:
    if choices is None or choices.column("list_name") is None:
        return set()
    col = choices.column("list_name")
    return {text(r[col]).strip() for r in choices.rows if not is_empty(r)}


def _convert_type(type_text: str) -> tuple[str, bool] | None:
    """(new type, whether its value is the empty string) for SurveyCTO-only types."""
    if type_text in EMPTY_CALCULATE_TYPES or type_text.split(" ")[0] in SENSOR_TYPE_WORDS:
        return "calculate", True
    if type_text == "enumerator":
        return "text", False
    return None


def _set(s: Sheet, row: list[Any], column: str, value: Any) -> None:
    if s.column(column) is None:
        s.headers.append(column)
        for r in s.rows:
            r.append(None)
    row[s.column(column)] = value


def _minimum_seconds(s: Sheet) -> bool:
    """Moves minimum_seconds into a ctosim bind attribute; True when any row has one."""
    col = s.column("minimum_seconds")
    if col is None:
        return False
    found = False
    for k, row in enumerate(s.rows):
        raw = row[col]
        if raw is None or (isinstance(raw, str) and raw.strip() == ""):
            continue
        value = text(raw).strip()
        try:
            number = float(value)
        except ValueError:
            number = -1.0
        if not number >= 0 or number == float("inf"):
            raise NormalizationError(f"row {k + 2}: minimum_seconds must be a number of at least 0, not '{value}'")
        _set(s, row, MINIMUM_SECONDS_COLUMN, str(int(number)) if number.is_integer() else repr(number))
        found = True
    del s.headers[col]
    for row in s.rows:
        del row[col]
    return found


def _calculate_here(s: Sheet, row: list[Any], name: str) -> None:
    """A read-only text question labelled with its name, whose calculation moves to a bind attribute."""
    has_label = any(h.partition(":")[0] == "label" for h in s.headers if not h.startswith("bind::"))
    for column in ("read_only", CALCULATE_HERE_COLUMN) + (() if has_label else ("label",)):
        if s.column(column) is None:
            s.headers.append(column)
            for r in s.rows:
                r.append(None)
    calculation = row[s.column("calculation")]
    row[s.column("type")] = "text"
    row[s.column("read_only")] = "yes"
    row[s.column(CALCULATE_HERE_COLUMN)] = calculation if text(calculation).strip() != "" else None
    row[s.column("calculation")] = None
    for i, header in enumerate(s.headers):
        base = header.partition(":")[0] if not header.startswith("bind::") else header
        if base == "label":
            row[i] = name
        elif base == "hint" or base in _CALCULATE_HERE_DROPPED:
            row[i] = None


def _xpath(stack: list[tuple[str, int, str, bool]], name: str) -> str:
    return "/data/" + "/".join([entry[2] for entry in stack if not entry[3]] + [name])


def _blank(row: list[Any]) -> None:
    for i in range(len(row)):
        row[i] = None
