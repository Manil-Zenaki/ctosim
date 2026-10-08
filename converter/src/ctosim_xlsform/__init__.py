"""Convert SurveyCTO XLSForms to XForms. See specs/02-xlsform-converter.md."""

from __future__ import annotations

import os
from dataclasses import dataclass


class ConversionError(Exception):
    """Raised when a spreadsheet form cannot be converted."""


@dataclass(frozen=True)
class FieldInfo:
    name: str
    type: str
    xpath: str
    row: int


@dataclass(frozen=True)
class ConversionResult:
    xform: str
    form_id: str
    title: str
    version: str | None
    fields: tuple[FieldInfo, ...]
    warnings: tuple[str, ...]

    def write(self, xform_path: str | os.PathLike, catalog_path: str | os.PathLike | None = None) -> None:
        raise NotImplementedError("not implemented")


def convert_xlsform(path: str | os.PathLike) -> ConversionResult:
    raise NotImplementedError("not implemented")


__all__ = ["ConversionError", "ConversionResult", "FieldInfo", "convert_xlsform"]
