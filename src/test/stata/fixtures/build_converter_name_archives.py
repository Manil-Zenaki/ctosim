"""Distinct importable converter doubles at the spec-10 Python module boundary.

Only fixture XML/catalog files are copied. No production sources are inspected.
"""
import pathlib
import sys
import zipfile

destination = pathlib.Path(sys.argv[1])
destination.mkdir(parents=True, exist_ok=True)
for identity in ("zip", "jar", "override"):
    module = '''import argparse
import os
import pathlib
import shutil

parser = argparse.ArgumentParser()
parser.add_argument("form")
parser.add_argument("-o", required=True)
args = parser.parse_args()
assert pathlib.Path(args.form).is_file()
work = pathlib.Path(os.environ["CTOSIM_TEST_WORK"])
fixtures = pathlib.Path(os.environ["CTOSIM_TEST_FIXTURES"])
(work / "converter-selected.txt").write_text(IDENTITY + "\\n", encoding="utf-8")
output = pathlib.Path(args.o)
output.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(fixtures / "form.xml", output)
shutil.copyfile(fixtures / "form.fields.json", output.with_suffix(".fields.json"))
'''.replace("IDENTITY", repr(identity))
    with zipfile.ZipFile(destination / (identity + ".jar"), "w") as archive:
        archive.writestr("ctosim_xlsform/__init__.py", "")
        archive.writestr("ctosim_xlsform/__main__.py", module)
