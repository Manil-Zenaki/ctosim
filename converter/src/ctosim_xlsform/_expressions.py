"""Rewrites of SurveyCTO expression syntax that the ODK engine does not accept."""

from __future__ import annotations

import re

_NUMBER = re.compile(r"-?\d+(?:\.\d+)?")
# Characters that can continue an XPath name or ${...} reference; a function name
# preceded by one of these is part of a longer name and is not a call to `selected`.
_NAME_CHAR = re.compile(r"[A-Za-z0-9_.:\-]")


def quote_numeric_selected_arguments(expression: str) -> str:
    """Quote a bare numeric second argument of every selected() call.

    ``selected(${q}, 1)`` becomes ``selected(${q}, '1')``. String literals are never
    rewritten, and an argument that is anything other than a bare number is left alone.
    """
    edits: list[tuple[int, int, str]] = []
    i = 0
    n = len(expression)
    while i < n:
        c = expression[i]
        if c in "'\"":
            i = _skip_string(expression, i)
            continue
        if expression.startswith("selected", i) and (i == 0 or not _NAME_CHAR.match(expression[i - 1])):
            j = i + len("selected")
            while j < n and expression[j].isspace():
                j += 1
            if j < n and expression[j] == "(":
                edit = _numeric_second_argument(expression, j)
                if edit is not None:
                    edits.append(edit)
            i += len("selected")
            continue
        i += 1

    for start, end, literal in reversed(edits):
        expression = expression[:start] + "'" + literal + "'" + expression[end:]
    return expression


def _skip_string(text: str, start: int) -> int:
    """Index just past the string literal opening at ``start`` (XPath has no escapes)."""
    end = text.find(text[start], start + 1)
    return len(text) if end < 0 else end + 1


def _numeric_second_argument(text: str, open_paren: int) -> tuple[int, int, str] | None:
    """For a call whose '(' is at ``open_paren``, locate a bare numeric second argument.

    Returns (start, end, literal) of the number to quote, or None when the call does not
    have exactly two arguments or its second argument is not a bare number.
    """
    depth = 0
    commas: list[int] = []
    i = open_paren
    while i < len(text):
        c = text[i]
        if c in "'\"":
            i = _skip_string(text, i)
            continue
        if c in "([":
            depth += 1
        elif c in ")]":
            depth -= 1
            if depth == 0:
                break
        elif c == "," and depth == 1:
            commas.append(i)
        i += 1
    else:
        return None  # unbalanced call: leave the expression as written

    if len(commas) != 1:
        return None
    arg_start, arg_end = commas[0] + 1, i
    argument = text[arg_start:arg_end]
    stripped = argument.strip()
    if not _NUMBER.fullmatch(stripped):
        return None
    start = arg_start + (len(argument) - len(argument.lstrip()))
    return start, start + len(stripped), stripped
