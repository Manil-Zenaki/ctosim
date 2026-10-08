"""Command line: python -m ctosim_xlsform INPUT.xlsx -o OUTPUT.xml"""

from __future__ import annotations

import argparse
import sys

from . import ConversionError, convert_xlsform


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="python -m ctosim_xlsform",
        description="Convert a SurveyCTO XLSForm to an XForm and a field catalog.")
    parser.add_argument("input", help="SurveyCTO form definition (.xlsx)")
    parser.add_argument("-o", "--output", required=True, help="XForm to write (.xml)")
    args = parser.parse_args(argv)  # exits with status 2 and a usage message on bad arguments

    try:
        result = convert_xlsform(args.input)
    except ConversionError as e:
        print(e, file=sys.stderr)
        return 1
    result.write(args.output)
    for warning in result.warnings:
        print(warning, file=sys.stderr)
    return 0


if __name__ == "__main__":
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="backslashreplace")
    sys.exit(main())
