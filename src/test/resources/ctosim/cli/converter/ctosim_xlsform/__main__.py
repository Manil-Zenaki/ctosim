import json
from pathlib import Path
import shutil
import sys

# The .xlsx argument is a JSON scenario for this process double, not a workbook.
if len(sys.argv) != 4 or sys.argv[2] != "-o":
    print("unexpected converter arguments: " + repr(sys.argv), file=sys.stderr)
    sys.exit(91)
source = Path(sys.argv[1])
destination = Path(sys.argv[3])
scenario = json.loads(source.read_text(encoding="utf-8"))
(source.parent / "converter-invocation.json").write_text(
    json.dumps({"args": sys.argv[1:], "python": sys.executable}), encoding="utf-8")
for warning in scenario.get("warnings", []):
    print(warning, file=sys.stderr)
if scenario.get("exit", 0):
    print("controlled conversion failure for " + source.name, file=sys.stderr)
    sys.exit(scenario["exit"])
fixtures = Path(__file__).resolve().parents[2]
fixture = scenario.get("fixture", "simple")
shutil.copyfile(fixtures / (fixture + ".xml"), destination)
shutil.copyfile(fixtures / (fixture + ".fields.json"), destination.with_suffix(".fields.json"))
