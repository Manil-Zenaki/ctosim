"""Spec 11 Amendment 3 oracles; no imports from, or inspection of, production.

Called by standalone Stata do-files. Only the public help artifact is read.
SMCL presentation is normalized before checking commands, prose and JSON.
"""
import datetime
import json
import math
import re
import sys
from pathlib import Path


TOP = set("seed submissions start fieldworkDays missingRate specialRate specialValues "
          "repeats integerRange decimalRange fields timeZone enumerators "
          "speedViolationRate profiles area textPools".split())
FIELD = set("value min max weights texts missingRate distribution expr area when".split())
AREA = set("minLatitude maxLatitude minLongitude maxLongitude".split())
HEADINGS = {"examples", "basic examples", "advanced examples", "configuration"}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def has(pattern, text):
    return re.search(pattern, text, re.I | re.S) is not None


def render(raw):
    """Keep literal JSON/XPath braces; remove only recognized SMCL directives.

    Command sentinels distinguish actual {cmd:} examples from prose mentions.
    JSON may use literal braces, {c -(}/{c )-}, or numeric character escapes.
    """
    raw = re.sub(r"\{\*[^\n]*?\}", "", raw)
    for token, value in {"-(": "\x01", ")-": "\x02", "123": "\x01", "125": "\x02",
                         "34": '"', "96": "`", "39": "'"}.items():
        raw = re.sub(r"\{(?:c|char)\s+" + re.escape(token) + r"\}",
                     lambda m: value, raw)
    # Repeatedly unwrap innermost formatting without interpreting JSON objects.
    pattern = re.compile(r"\{(cmd|input|bf|it|text|txt|res|result|err|error|hilite|"
                         r"opt|title|dlgtab|help|helpb|browse):([^{}]*)\}")
    # Protect literal balanced braces inside command blocks (loops/XPath/JSON).
    def commands(source):
        start = 0
        while True:
            at = source.find("{cmd:", start)
            if at < 0:
                return source
            depth, end = 1, at + 5
            while end < len(source) and depth:
                depth += (source[end] == "{") - (source[end] == "}")
                end += 1
            require(depth == 0, "Unclosed {cmd:} example")
            body = source[at + 5:end - 1]
            body = body.replace("{", "\x01").replace("}", "\x02")
            source = source[:at] + "\x03" + body + "\x04" + source[end:]
            start = at + len(body) + 2

    raw = commands(raw)
    def unwrap(match):
        tag, body = match.groups()
        if tag in {"help", "helpb", "browse"} and ":" in body:
            body = body.split(":", 1)[1]
        if tag in {"title", "dlgtab"} or body.strip().lower() in HEADINGS:
            return "\n\n" + body.strip() + "\n\n"
        return body
    while pattern.search(raw):
        raw = pattern.sub(unwrap, raw)
    raw = re.sub(r"\{(?:p|pstd|phang|phang2|p2col|p2colset|p2colreset|p_end|"
                 r"p2coldent|synopt|synoptset|synopthdr|break)(?:\s+[^{}]*)?\}",
                 "\n\n", raw)
    raw = re.sub(r"\{(?:smcl|hline(?:\s+\d+)?|sf|ul on|ul off|"
                 r"marker\s+[^{}]+|vieweralsosee\s+[^{}]+|vieweralsosee|"
                 r"viewerjumpto\s+[^{}]+)\}", "", raw)
    return raw.replace("\x01", "{").replace("\x02", "}")


class Help:
    def __init__(self, raw):
        self.text = render(raw)
        self.raw = raw

    def section(self, title, until=()):
        heading = re.search(r"(?im)^\s*" + re.escape(title) + r"\s*$", self.text)
        require(heading, "Missing section/part titled " + title)
        body = self.text[heading.end():]
        if until:
            end = re.search(r"(?im)^\s*(?:" + "|".join(map(re.escape, until)) +
                            r")\s*$", body)
            if end:
                body = body[:end.start()]
        # Do not borrow examples or explanations from another top-level section.
        other_titles = re.findall(r"\{title:([^{}]+)\}", self.raw)
        for other in other_titles:
            if other.lower() in {title.lower(), "basic examples", "advanced examples"}:
                continue
            end = re.search(r"(?im)^\s*" + re.escape(other.strip()) + r"\s*$", body)
            if end:
                body = body[:end.start()]
        return body

    def basic(self):
        examples = self.section("Examples")
        require(has(r"(?m)^\s*Basic examples\s*$", examples),
                "Basic examples must be inside Examples")
        return self.section("Basic examples", ("Advanced examples",))

    def advanced(self):
        examples = self.section("Examples")
        require(has(r"(?m)^\s*Advanced examples\s*$", examples),
                "Advanced examples must be inside Examples")
        return self.section("Advanced examples", ("Basic examples",))


def commands(text):
    return re.findall("\x03(.*?)\x04", text, re.S)


def command_text(text):
    return "\n".join(commands(text)).replace("///", " ")


def simulations(text):
    # A command may span multiple {cmd:} lines using Stata's continuation marker.
    joined = "\n".join(commands(text))
    joined = re.sub(r"///\s*\n", " ", joined)
    return re.findall(r"(?im)^\s*\.?\s*ctosim\s+using\b[^\n]*", joined)


def option(cmd, name):
    return has(r"\b" + name + r"\s*\(\s*[^)\s][^)]*\)", cmd)


def structure(doc):
    for name, text in (("Basic", doc.basic()), ("Advanced", doc.advanced())):
        blocks = list(re.finditer("\x03.*?\x04", text, re.S))
        require(blocks, name + " examples need {cmd:} commands")
        previous = 0
        described = False
        for block in blocks:
            prose = text[previous:block.start()].strip()
            if prose:
                # A description may wrap physical lines; punctuation is not fixed.
                described = len(re.findall(r"[A-Za-z]+", prose)) >= 3
                require(described, name + " example lacks a preceding explanatory sentence")
            require(described, name + " commands precede their explanation")
            previous = block.end()


def basic(doc):
    text = doc.basic()
    sims = simulations(text)
    require(any(option(c, "n") and option(c, "seed") and has(r"\bclear\b", c)
                and not option(c, "dofile") for c in sims),
            "Basic: plain import needs n(), seed(), clear on a ctosim command")
    require(any(option(c, "dofile") and has(r"\breplace\b", c) for c in sims),
            "Basic: SurveyCTO import example needs dofile() and replace")
    for name in ("saving", "media"):
        require(any(option(c, name) for c in sims), "Basic: missing " + name + "() example")
    cmds = command_text(text)
    require(has(r"\breturn\s+list\b", cmds), "Basic: missing {cmd:return list}")
    for result in ("N", "warnings"):
        require(re.search(r"\br\(" + result + r"\)", cmds),
                "Basic: missing command reading r(" + result + ")")
    require(re.search(r'''\btype\s+"`r\(report\)'"''', cmds),
            'Basic: missing command opening the report with type "`r(report)\'"')


def unique_object(pairs):
    obj = {}
    for key, value in pairs:
        require(key not in obj, "Duplicate JSON key: " + key)
        obj[key] = value
    return obj


def json_blocks(text):
    # JSON object starts are unambiguous even when literal XPath ${field} occurs.
    decoder = json.JSONDecoder(object_pairs_hook=unique_object,
                               parse_constant=lambda s: (_ for _ in ()).throw(
                                   AssertionError("Non-JSON constant " + s)))
    blocks, end = [], 0
    for match in re.finditer(r'(?<!\$)\{\s*(?:"|\})', text):
        if match.start() < end:
            continue
        try:
            obj, length = decoder.raw_decode(text[match.start():])
        except ValueError as error:
            raise AssertionError("Example configuration is not complete JSON: " + str(error))
        end = match.start() + length
        require(isinstance(obj, dict), "Configuration must be a JSON object")
        blocks.append((match.start(), end, obj))
    return blocks


def keys(obj, allowed, path, required=()):
    require(isinstance(obj, dict), path + " must be an object")
    require(not (set(obj) - allowed), path + " has unknown keys: " + str(set(obj) - allowed))
    require(set(required) <= set(obj), path + " is incomplete; missing " + str(set(required) - set(obj)))


def number(value, path, minimum=-math.inf, maximum=math.inf, integer=False):
    require(type(value) in (int, float) and math.isfinite(value), path + " must be a finite number")
    require(minimum <= value <= maximum, path + " is outside its spec 04 bounds")
    require(not integer or int(value) == value, path + " must be an integer")


def strings(value, path, nonempty=True):
    require(isinstance(value, list) and (value or not nonempty), path + " must be an array of strings")
    require(all(isinstance(v, str) for v in value), path + " contains a non-string")


def weights(value, path):
    require(isinstance(value, dict) and value, path + " needs weights")
    for name, weight in value.items():
        number(weight, path + "." + name, 0)
    require(any(v > 0 for v in value.values()), path + " needs a positive weight")


def area(obj, path):
    keys(obj, AREA, path, AREA)
    for axis, bound in (("Latitude", 90), ("Longitude", 180)):
        for prefix in ("min", "max"):
            number(obj[prefix + axis], path + "." + prefix + axis, -bound, bound)
        require(obj["min" + axis] <= obj["max" + axis], path + " has reversed bounds")


def override(obj, path, rule=False):
    keys(obj, (FIELD - {"when"}) | {"if"} if rule else FIELD, path)
    require(set(obj) - {"if"}, path + " needs an override setting")
    if rule:
        require(isinstance(obj.get("if"), str) and obj["if"].strip(), path + " needs an if expression")
    for exclusive, conflicts in (("value", {"min", "max", "weights", "texts", "expr", "distribution"}),
                                  ("expr", {"value", "min", "max", "weights", "texts", "distribution"}),
                                  ("distribution", {"value", "weights", "texts", "expr"})):
        require(exclusive not in obj or not (set(obj) & conflicts),
                path + " combines incompatible settings with " + exclusive)
    if "value" in obj:
        require(type(obj["value"]) in (str, int, float), path + ".value must be a string or number")
    if "expr" in obj:
        require(isinstance(obj["expr"], str) and obj["expr"].strip(), path + ".expr needs an expression")
    if "min" in obj or "max" in obj:
        require({"min", "max"} <= set(obj), path + " needs both range bounds")
        lo, hi = obj["min"], obj["max"]
        if isinstance(lo, str) and isinstance(hi, str):
            datetime.date.fromisoformat(lo)
            datetime.date.fromisoformat(hi)
            require("distribution" not in obj, path + " distribution needs numeric bounds")
        else:
            number(lo, path + ".min")
            number(hi, path + ".max")
        require(lo <= hi, path + " range is reversed")
    if "weights" in obj:
        weights(obj["weights"], path + ".weights")
    if "texts" in obj:
        strings(obj["texts"], path + ".texts")
    if "missingRate" in obj:
        number(obj["missingRate"], path + ".missingRate", 0, 1)
    if "area" in obj:
        area(obj["area"], path + ".area")
    if "distribution" in obj:
        dist = obj["distribution"]
        keys(dist, {"normal", "lognormal"}, path + ".distribution")
        require(len(dist) == 1, path + " needs exactly one distribution")
        kind, params = next(iter(dist.items()))
        keys(params, {"mean", "sd"}, path + ".distribution." + kind, {"mean", "sd"})
        number(params["mean"], path + ".mean")
        number(params["sd"], path + ".sd", 0)
        require(kind != "lognormal" or params["mean"] > 0, path + " lognormal mean must be positive")
    if "when" in obj:
        require(isinstance(obj["when"], list), path + ".when must be an array")
        for i, item in enumerate(obj["when"]):
            override(item, path + ".when[" + str(i) + "]", rule=True)


def configuration_object(obj):
    keys(obj, TOP, "config")
    # Explicit schema walk keeps arbitrary field/profile/choice names unrestricted.
    for name, bounds in {"seed": (-2**63, 2**63 - 1), "submissions": (1, 1000000),
                         "fieldworkDays": (1, 3650), "enumerators": (1, 10000)}.items():
        if name in obj:
            number(obj[name], name, *bounds, integer=True)
    for name in ("missingRate", "specialRate", "speedViolationRate"):
        if name in obj:
            number(obj[name], name, 0, 1)
    for name in ("start", "timeZone"):
        if name in obj:
            require(isinstance(obj[name], str), name + " must be a string")
    if "start" in obj:
        datetime.datetime.fromisoformat(obj["start"])
    if "specialValues" in obj:
        strings(obj["specialValues"], "specialValues", nonempty=False)
        require(all(obj["specialValues"]) and len(set(obj["specialValues"])) == len(obj["specialValues"]),
                "specialValues entries must be nonempty and unique")
    for name in ("repeats", "integerRange", "decimalRange"):
        if name in obj:
            bounds = obj[name]
            keys(bounds, {"min", "max"}, name, {"min", "max"})
            for bound in ("min", "max"):
                if name == "repeats":
                    number(bounds[bound], name + "." + bound, 0, 1000, integer=True)
                else:
                    number(bounds[bound], name + "." + bound)
            require(bounds["min"] <= bounds["max"], name + " bounds are reversed")
    if "profiles" in obj:
        weights(obj["profiles"], "profiles")
        require(all(k and k == k.strip() for k in obj["profiles"]), "Invalid profile name")
    if "area" in obj:
        area(obj["area"], "area")
    if "textPools" in obj:
        keys(obj["textPools"], {"firstName", "lastName", "place", "comment"}, "textPools")
        for name, pool in obj["textPools"].items():
            strings(pool, "textPools." + name)
            require(all(pool), "Empty textPools entry")
    if "fields" in obj:
        require(isinstance(obj["fields"], dict), "fields must be an object")
        for name, value in obj["fields"].items():
            require(name and name == name.strip(), "Invalid field name")
            override(value, "fields." + name)


def configs(doc):
    text = doc.section("Examples")
    refs = re.findall(r'\bconfig\(\s*(?:"([^"\n]+)"|([^\s)]+))\s*\)', command_text(text), re.I)
    names = {a or b for a, b in refs}
    require(names, "Examples must use config()")
    # A full configuration can also be displayed elsewhere in the help file.
    # Non-root schema snippets outside Examples are not complete configurations.
    blocks = [b for b in json_blocks(doc.text)
              if set(b[2]) & TOP or not b[2]]
    example_blocks = json_blocks(text)
    for _, _, obj in example_blocks:
        configuration_object(obj)
    require(blocks, "Examples must show complete JSON configurations")
    for _, _, obj in blocks:
        configuration_object(obj)
    # Associate filenames with adjacent objects, allowing captions before or
    # after JSON. Reusing the same displayed object for multiple commands is fine.
    for name in names:
        associated = False
        for i, (start, end, _) in enumerate(blocks):
            before = blocks[i - 1][1] if i else 0
            after = blocks[i + 1][0] if i + 1 < len(blocks) else len(doc.text)
            context = doc.text[before:start] + doc.text[end:after]
            # Require a prose filename label, or a sole config() reference in
            # this object's neighborhood. Multiple commands alone are ambiguous.
            prose = re.sub("\x03.*?\x04", "", context, flags=re.S)
            local_refs = {a or b for a, b in re.findall(
                r'\bconfig\(\s*(?:"([^"\n]+)"|([^\s)]+))\s*\)',
                command_text(context), re.I)}
            if name in prose or local_refs == {name}:
                associated = True
        require(associated, "No identifiable complete JSON for config(" + name + ")")
    return [obj for _, _, obj in blocks]


def advanced(doc):
    text = doc.advanced()
    require(any(option(c, "config") for c in simulations(text)), "Advanced: missing config() command")
    objects = configs(doc)
    require(objects, "Advanced examples must display configuration JSON")
    overrides = [v for obj in objects for v in obj.get("fields", {}).values()]
    for name in ("value", "weights"):
        require(any(name in v for v in overrides), "Advanced: no field " + name + " example")
    require(any("min" in v and "max" in v for v in overrides), "Advanced: no restricted field range")
    require(any("missingRate" in obj for obj in objects) or any("missingRate" in v for v in overrides),
            "Advanced: no missing-rate setting")
    for name in ("profiles", "textPools"):
        require(any(obj.get(name) for obj in objects), "Advanced: no " + name + " example")
    require(any(obj.get("area") for obj in objects) or any(v.get("area") for v in overrides),
            "Advanced: no survey area")
    all_overrides = overrides + [rule for v in overrides for rule in v.get("when", [])]
    require(any(set(v.get("distribution", {})) & {"normal", "lognormal"} for v in all_overrides),
            "Advanced: no normal/lognormal distribution")
    require(any(v.get("when") and all(rule.get("if") for rule in v["when"]) for v in overrides),
            "Advanced: missing when/if conditional example")
    require(any(v.get("expr") for v in all_overrides), "Advanced: missing computed expr answer")
    cmds = command_text(text)
    loops = re.findall(r"\b(forvalues|forval|foreach)\s+(\w+)\s+([^\n{]+)\{(.*?)\}", cmds, re.S | re.I)
    valid_loop = False
    for kind, variable, values, body in loops:
        seed = re.search(r"\bseed\(\s*`" + re.escape(variable) + r"'\s*\)", body)
        if not seed or not has(r"\bctosim\s+using\b", body):
            continue
        numbers = re.findall(r"-?\d+", values)
        if len(set(numbers)) >= 2:
            valid_loop = True
    require(valid_loop, "Advanced: loop must simulate several data sets with distinct seeds")
    assertions = re.findall(r"(?im)^\s*\.?\s*assert\s+([^\n]+)", cmds)
    def answer_predicate(expr):
        answer = re.sub(r"\b[rc]\([^)]*\)|\b_N\b|\b_n\b", "", expr)
        answer = re.sub(r"\b\w+\s*(?=\()", "", answer)
        return re.search(r"\b[A-Za-z]\w*\b", answer) and re.search(
            r"[<>=!]|\b(?:inrange|inlist)\s*\(", expr)
    require(any(answer_predicate(expr) for expr in assertions),
            "Advanced: missing assert of an answer's constraint (a row count is insufficient)")


def configuration(doc):
    text = doc.section("Configuration")
    # Do not count keys mentioned only in a JSON sample as key documentation.
    for start, end, _ in reversed(json_blocks(text)):
        text = text[:start] + text[end:]
    paragraphs = re.split(r"\n\s*\n", text)
    for key in sorted(TOP | FIELD | {"if"}):
        candidates = [p for p in paragraphs if
                      "\x03" + key + "\x04" in p or
                      re.search(r"(?:^|[,;/]|\band\b)\s*(?:[-*]\s*)?[`'\"]?" +
                                key + r"(?![\w])", p)]
        require(candidates, "Configuration: undocumented key " + key)
        require(any(len([w for w in re.findall(r"[A-Za-z]+", p)
                         if w not in TOP | FIELD | {"if"}]) >= 3 for p in candidates),
                "Configuration: " + key + " needs an explanation, not just a name")
    require(any(has(r"constraint", p) and has(r"satisf|respect|obey|meet|honou?r|enforc|adhere", p)
                and has(r"\b(?:always|every|all|whenever)\b", p)
                and has(r"\bsatisfiable\b|can be satisf|\bpossible\b|\bfeasible\b", p)
                for p in paragraphs),
            "Configuration must state answers always satisfy satisfiable question constraints")
    prose = " ".join(text.split())
    for name in ("resp_name", "phone_number", "email", "other_specify"):
        require(name in prose, "Configuration: missing automatic text example " + name)
    require(has(r"plausible|realistic|natural", prose) and
            has(r"without|no (?:extra |additional |special )?(?:setting|config)|automati|by default", prose),
            "Configuration must explain plausible text answers without settings")


CHECKS = {"structure": structure, "basic": basic, "advanced": advanced,
          "json": configs, "configuration": configuration}


if __name__ == "__main__":
    try:
        CHECKS[sys.argv[1]](Help(Path(sys.argv[2]).read_text(encoding="utf-8-sig")))
    except (AssertionError, ValueError, OSError) as error:
        print("FAIL:", error)
        sys.exit(1)
    print("PASS:", sys.argv[1])
