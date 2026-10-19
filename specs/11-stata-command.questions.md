# Spec 11 test questions and assumptions

Only the specifications (11 and its referenced 02/04/08/10 contracts) supplied expectations. No production implementation was opened, searched, or modified. Packaging is excluded as requested by spec 11.

## 1. Multiple simultaneous preflight errors

**Question:** Is the bullet order an error-precedence rule?

**Assumption:** No precedence is inferred. Each negative case isolates one invalid input; tests use `clear` when testing unrelated errors with changed data. The `replace`-without-`clear` case has no disk error because `replace` authorizes the existing run.

**Tests:** `test_preflight_*.do`, `test_overwrite.do`, `test_missing_*.do`.

## 2. Syntax's alternative return codes

**Question:** Which malformed option tokens may produce a `syntax` error other than 198?

**Assumption:** Numeric fractions, missing numeric values (`.`), and out-of-range counts require 198. Alphabetic tokens and empty parentheses require a nonzero error before simulator invocation, without selecting an undocumented `syntax` code. Numeric spelling variants such as `1e3` or `1.0` are not tested.

**Tests:** `test_preflight_numbers.do`.

## 3. Seed range and precision

**Question:** Does Stata's `seed()` accept the whole Java `long` range, and how should values beyond exact double precision be returned in `r(seed)`?

**Assumption:** Negative, zero, and positive integers beyond signed 32-bit bounds are valid, following the referenced CLI contract. Tests stay within exact double precision. No expectation is imposed on rounding, decimal display, or 64-bit extremes beyond 2^53 until clarified.

**Tests:** `test_preflight_numbers.do`, `test_options_paths.do`, `test_results_warnings.do`.

## 4. Report-derived results and the simulator test double

**Question:** May process-level tests substitute an executable jar through the documented `CTOSIM_JAR` override?

**Assumption:** Yes. `fixtures/SimulatorDouble.java` records argument boundaries, records the actual Java runtime, emits stdout/stderr, can exit with 1/2/17/255, and writes a spec-10-format report plus spec-08-format CSV fixtures. Its report values are controlled independently of the arguments so the tests detect results inferred from options instead of read from the report. It does not attempt to reimplement simulation or conversion. Real XML/XLSX tests separately verify config/default/override semantics. There are no assertions about production helper names, shell construction, or converter import mechanisms.

**Tests:** All non-`real` command tests; `test_real_*.do` checks the built simulator.

## 5. Message formatting

**Question:** What exact wording, indentation, or prefixes must diagnostics and warnings use?

**Assumption:** Wording and indentation are free. Simulator stdout/stderr must remain visible. Warning text must appear intact, in report order, once each and on distinct lines, following a line containing the count and the word `warning`. A prefix before an individual warning is allowed. Missing-program messages are checked for the program identity and flexible setup guidance rather than one exact sentence. This still assumes English `warning` and common English setup vocabulary; localized messages would require adapting those checks. No particular error message is required for ordinary file/numeric preflight failures.

**Tests:** `test_results_warnings.do`, `test_simulator_failures.do`, `test_dofile_success.do`, `test_missing_*.do`, `test_real_media.do`.

## 6. Paths and working directories

**Question:** Must returned absolute paths be canonical, use one slash style, or omit trailing separators? What happens if a do-file itself changes directory?

**Assumption:** No textual canonicalization is required. Stored paths must be absolute and identify the expected actual files/directories. Relative `dofile()` is resolved from the caller's original directory. The original directory is restored even when a successful or failing template itself runs `cd ..`, following "restored afterwards". Tests avoid unspecified tilde expansion, shell metacharacter escaping rules, UNC/network availability, and filesystem case sensitivity.

**Tests:** `test_options_paths.do`, `test_dofile_success.do`, `test_dofile_failure.do`, `ct_report_results` in `helpers.do`.

## 7. CSV naming/type inference

**Question:** Should tests hard-code Stata's version-dependent renamed variable names and storage types?

**Assumption:** No. The oracle is the installed Stata's exact specified `import delimited` invocation. Tests compare variable order, names, storage types, and all values. The CSV includes Unicode, commas, escaped quotes, embedded newlines, negatives, decimals, empty cells, numeric-looking text, an invalid-name character, and a long column name. A deliberately different long CSV catches importing the wrong file.

**Tests:** `test_plain_import.do`, `test_real_xml.do`.

## 8. Import failures and data preservation

**Question:** Must failed import do-files roll back their data edits? Does the command's preflight protection apply to zero-observation datasets?

**Assumption:** No rollback after a do-file error: its partial data and labels survive, as explicitly required. Before invocation and on simulator failure, values, variable count, labels, format, a dataset characteristic, and changed status survive. A zero-observation dataset with variables and `c(changed)==1` is protected just like other changed data. Errors from the plain importer itself and malformed/missing output reports are unspecified and are not assigned invented codes.

**Tests:** `test_dofile_failure.do`, `test_preflight_memory.do`, `test_simulator_failures.do`, `test_real_input_errors.do`.

## 9. Program discovery coverage limits

**Question:** How should a test safely create a Stata session with no configured Python, or a Stata Java home with no Java launcher?

**Assumption:** Tests must not damage the installed runtimes or persistently change user preferences. On this machine, Stata starts with Python 3.10 configured. `python set exec ""` is rejected; a program named `python` cannot intercept the builtin. The missing-Python test therefore configures a disposable copy of the venv launcher, removes that copy, and verifies 601 plus XML independence. Configured Python's precedence over an invalid `CTOSIM_PYTHON` is separately tested. The entirely-unconfigured fallback to `CTOSIM_PYTHON`, and the entirely-unconfigured/no-environment case with its exact `python set exec` guidance, remain unexercised here; they are not silently claimed covered.

Stata's Java-home setter validates the JVM and records a preference requiring restart; it is unsuitable for a hermetic missing-Java test. The missing-Java 601 branch likewise remains unexercised. Runtime selection is tested by comparing the child simulator's actual Java home with `c(java_home)`. The Java-home probe's original preference was restored. No permanent runtime changes are part of the delivered tests.

**Tests:** `test_runtime_selection.do`, `test_missing_python.do`, `test_xml_no_converter.do`, `test_missing_simulator.do`, `test_missing_converter.do`, `test_ado_fallback.do`, `test_ado_unset.do`. Empty and unset jar/converter overrides, ado-path fallbacks, and invalid nonempty overrides taking precedence over valid ado-path files are covered.

## 10. Help prose versus executable checks

**Question:** How should automated tests establish that free-form help text adequately explains every option, stored result, prerequisite, and both examples without prescribing its SMCL layout?

**Assumption:** A smoke test requires a discoverable `ctosim.sthlp` and a successful `help ctosim`. Semantic completeness of the prose and examples requires review; no implementation-specific text fragments or formatting are invented as contractual requirements.

**Tests:** `test_help.do`.

## 11. Harness and runtime prerequisites

**Question:** How can standalone do-files change environment variables and still run under the provided Windows batch runner?

**Assumption:** A test may start an isolated child Stata session. Windows Stata 16 ignores `shell`/`winexec` in batch, and embedding this machine's Python 3.10 crashed Stata 16. The checked-in, Java-8-compatible `stata-test-support.jar` bridges to a hidden PowerShell process. Its complete source and `build-test-fixtures.ps1` are included; it does not depend on simulator code or Stata internals. PowerShell compiles the separate simulator double with the existing `JAVA_HOME` compiler. Each child signals success only after its do-file runs to completion; failures propagate to the parent with a nonzero Stata error and a log. A three-minute child timeout is a harness safeguard, not a command performance requirement.

All harness intermediates and generated data are under `.codex-runs/stata-command/`. The required, unmodified repository runner additionally writes its own logs under `target/stata-tests/`. Source fixtures are read-only during tests; no software is installed. `STATA_EXE` is respected, matching the supplied runner.

**Tests:** All isolated command tests and `launch.do`/`launch.ps1`.

## Validation

The supplied runner successfully starts Stata 16.1. The requested command was run over the complete suite and reported **23 tests, 23 failed**. A setup-order problem found in `test_simulator_failures.do` was corrected and that test was rerun with the same runner's `-Filter` option. The latest per-test results are **22 command tests reaching `ctosim: not implemented`**, plus **`test_help.do` failing with 601 because `ctosim.sthlp` is absent**. These failures are expected for the current stub; later assertions will become reachable when the command is implemented.

All 23 do-files were also successfully parsed as Stata program definitions. Separate Stata helper checks completed successfully and exercised memory assertions, process argument checks, absolute report paths, report parsing, Unicode/multiline CSV comparison, warning-log checks, and both template fixtures. The Java fixture sources compiled successfully. The built real simulator accepted the XML/config and media fixtures, producing one submission and zero warnings for each. The missing-Python fixture setup also completed successfully before reaching the command stub.

Runner logs: `target/stata-tests/<test>/runner.log`; isolated command logs: `.codex-runs/stata-command/<test>/child.log`. Helper and syntax checks: `.codex-runs/stata-command/helper-check/check.log` and `.codex-runs/stata-command/syntax-check.log`. No production files or build configuration were changed, and no commit was made.

## Amendment 1: additional questions and assumptions

### A1. Controlling Java versions without changing installed runtimes

**Question:** May executable test launchers advertise controlled Java versions, analogous to the approved simulator jar double?

**Assumption:** Yes. The new native Windows launcher fixture reports a standard OpenJDK version on `-version`/`--version`, records simulator invocations, and delegates actual jar execution to the installed build JDK. It supplies Java 8, 11, 16, exactly 17, and newer 21/25 cases without modifying any real runtime. Tests distinguish the selected executable by its invocation record, not by the delegate's actual Java version. These doubles assume discovery uses a launcher version query; a different method, such as executing a compiled version-inspection class, would need an equivalent fixture adaptation. Tests do not prescribe a probe count or order of version probes. Both conventional stderr output and stdout version output are accepted test inputs. Early-access versions, malformed version output, permission-denied executables, and executables failing during a version probe are unspecified and are not assigned invented behavior.

**Tests:** `test_amendment_java_override.do`, `test_amendment_java_stata_fallback.do`, `test_amendment_java_home_path.do`, `test_amendment_java_unavailable.do`; fixture `AmendmentJavaLauncher.cs`.

### A2. Stata's own Java cannot be replaced hermetically

**Question:** How should both eligible and too-old Stata-Java branches be tested without changing user preferences or installed files?

**Assumption:** Use actual installed Stata editions. Unless `STATA_EXE` explicitly selects one executable, the new launcher runs each scenario against every installed executable matching the runner's Stata discovery pattern. This machine has Stata 16 with Java 11 and Stata 19 with Java 21. The embedded JVM's public `java.specification.version` property determines the conditional oracle. A valid override always wins; otherwise Stata's Java wins when eligible, and only an older Stata Java permits HOME/PATH fallback. On an installation with only eligible Stata Java, the no-eligible-Java negative branch is explicitly marked inapplicable and that scenario checks successful Stata fallback instead. It does not pretend to test missing Java there. A machine with an older Stata Java is required for complete negative and lower-priority fallback coverage.

The original question 9's Java assumption is superseded by Amendment 1. The committed `test_runtime_selection.do` still contains the original unconditional Stata-Java expectation; it is retained unchanged as explicitly requested. New tests use the amended order.

**Tests:** All four `test_amendment_java_*.do` files; `amendment-launch.ps1`, `amendment-helpers.do`.

### A3. Empty variables, unavailable candidates, and diagnostic wording

**Question:** Are empty and unset environment variables observably different on Windows, and what exact Java diagnostic is required?

**Assumption:** Both must permit fallback. Separate profiles assign an empty value and remove the variable, although Windows/PowerShell can normalize these to the same process environment. A nonempty missing executable or an old version is skipped, following "first ... that exists and is a Java 17 or newer." If no candidate qualifies, 601 must precede simulator/template execution; existing data and cwd remain intact. Version probes are necessarily allowed by this preflight rule. Wording remains free: the diagnostic checks for Java 17 plus minimum/newer language, Stata plus its Java major version, and either `CTOSIM_JAVA` or Temurin installation guidance. It does not require the full patch version, exact capitalization, or punctuation. As with the previously confirmed message assumption, these checks assume English. Only one eligible simulator invocation is expected; unsuccessful version probes are not simulator invocations.

**Tests:** `test_amendment_java_stata_fallback.do`, `test_amendment_java_home_path.do`, `test_amendment_java_unavailable.do`; `AmendmentTestSupport.diagnostic`.

### A4. Batch parity and test harness boundaries

**Question:** Does verifying batch parity require driving an interactive GUI session as well?

**Assumption:** No. New tests explicitly launch `/e do child.do` and apply the same spec-defined data, report, output, error, and template oracles in that batch process. The harness's Java bridge launches only the test process; it does not execute ctosim's simulator on behalf of production code. Tests require real simulator-double invocation and generated output, catching Windows `shell` being silently ignored. They cover plain import, quoted program/input/output paths, stored results, stdout/stderr, warning count/lines, template loading and suppression, simulator statuses 2/1/17/255, memory preservation, and template error propagation/cwd restoration. Interactive GUI automation is not added. XML fixtures isolate this amendment from Python/converter discovery; the existing XLSX batch tests remain unchanged.

New tests and helpers are separate files. The small new bridge has source, a checked-in Java-8-compatible jar, and `build-amendment-fixtures.ps1` for reproduction. Generated executables, classes, process logs, and outputs live under `.codex-runs/stata-amendment/`. The unmodified required runner continues to write its own files under `target/stata-tests/`. Child timeouts are harness safeguards, not specified performance requirements.

**Tests:** `test_amendment_batch_import.do`, `test_amendment_batch_template.do`, `test_amendment_batch_failures.do`.

### Amendment 1 validation

Ran the requested `powershell -ExecutionPolicy Bypass -File scripts/stata-tests.ps1`: **30 tests, 30 failed**, including the seven new do-files. All **42 new child scenarios** (21 profiles across Stata 16/Java 11 and Stata 19/Java 21) successfully started and reached `ctosim: not implemented`. These are expected command failures, not launcher or fixture compilation failures. Later assertions remain unreachable until production implements the command. The existing help test still fails with 601 for the absent help file. After the final harness changes, `test_amendment_batch_failures.do` was also rerun with the same runner's `-Filter` option and reached the stub on both editions with failures correctly propagated.

The new Java bridge compiled with `--release 8`; the native C# launcher compiled for each scenario. A separate fixture check passed for Java version reporting, quoted argument forwarding, stdout/stderr forwarding, statuses 1/2/17/255, embedded-JVM version reading, launcher-selection assertions, and positive/negative diagnostic assertions. All seven new test do-files were successfully parsed as Stata program definitions. This checks test infrastructure independently of the command stub; it does not claim that the unimplemented command passes any assertions.

Logs: `target/stata-tests/test_amendment_*/runner.log`, `.codex-runs/stata-amendment/<test>-<profile>-<edition>/child.log`, and `.codex-runs/stata-amendment/helper-check/check.log`. No sandbox startup blocker occurred. Existing test files, production files, specifications, and build configuration were kept unchanged; only new test/support/fixture files and this questions file were written. No commit was made.

## Dispute adjudication update (2026-10-11)

The decisions in `11-stata-command.disputes.md` supersede the historical
descriptions above where applicable. Question 9 and A2 no longer describe
an unconditional Stata-Java assertion: it has been removed, and the
amendment tests cover the current selection rule. Question 11 and A4 no
longer describe checked-in support jars: the required runner now builds
both from source into `.codex-runs/stata-support/` before launching Stata.
The distinct default-output basenames in `test_options_paths.do` now also
honor question 6's exclusion of filesystem case-sensitivity assumptions.

## Amendment 2: converter installed-name tests

### B1. Observing which converter was selected

**Question:** How can tests distinguish two discoverable, importable archives without prescribing the command's Python-path plumbing?

**Assumption:** Use small converter doubles at the public spec-10 boundary, `python -m ctosim_xlsform FORM -o OUTPUT`. Each archive records its distinct identity and copies the existing valid XML/catalog fixtures to OUTPUT. The real simulator must successfully import and invoke the chosen archive; tests do not inspect production code or assert the contents of PYTHONPATH. This follows the approved process-double approach and spec 10's converter-double clarification. One separate scenario uses the actual built converter archive, copied unchanged from `.zip` to `.jar`, with the real XLSForm fixture. Building/distributing the package and inspecting its manifest remain outside unit-test scope, as specified.

**Tests:** `test_converter_name_jar_fallback.do`, `test_converter_name_zip_precedence.do`, `test_converter_name_override.do`; fixture `build_converter_name_archives.py`.

### B2. Search priority across different ado-path entries

**Question:** Does an earlier ado-path jar outrank a zip in a later directory?

**Assumption:** No. The amended rule searches for `ctosim_converter.zip` along the ado-path first, and considers `ctosim_converter.jar` only otherwise. Tests cover zip alone, both names in one directory, and a jar in an earlier directory than the zip. Within-extension duplicate-file priority is left to normal Stata lookup and is not assigned new semantics.

**Tests:** `test_converter_name_zip_precedence.do`.

### B3. Override names and invalid overrides

**Question:** Are override files restricted to either standard basename or suffix, and may a missing override fall through to valid ado-path archives?

**Assumption:** The file explicitly named by a nonempty `CTOSIM_CONVERTER` wins, regardless of name, following "Whatever its name." Tests use custom `.jar`, `.zip`, and `.archive` names, including spaces. Relative overrides are resolved from the caller's working directory, interpreting the spec's relative-path allowance to include the named converter file. A missing nonempty override yields 601 even when both fallback archives exist, consistent with the confirmed original discovery assumption. Corrupt archives, whitespace-only overrides, and directory-valued overrides are unspecified and are not assigned new error behavior.

**Tests:** `test_converter_name_override.do`, `test_converter_name_missing.do`.

### B4. Isolating discovery without changing user settings

**Question:** How can tests guarantee no user-installed converter supplies an unintended fallback?

**Assumption:** Each profile runs in a fresh child Stata batch process, sets SITE/PLUS/PERSONAL/OLDPLACE to private empty directories for that process only, and adds controlled ado directories plus the command and test-support directories. No persistent settings or installed files are changed. Empty and unset environment profiles are separate even though Windows may normalize them. The configured Python and eligible Java are supplied to isolate Amendment 2 from other runtime-selection branches. The negative XLSForm scenarios use the existing simulator double to assert that preflight stops before simulator invocation; XML scenarios use the real simulator and assert successful import with no converter invocation.

New generated archives, compiled fixtures, subprocess temporary files, and logs are under `.codex-runs/stata-support/` and `.codex-runs/stata-converter-name/`. The mandated unmodified runner also writes its own logs under `target/stata-tests/`. A child timeout is a harness safeguard, not a performance requirement. All existing tests and helpers are retained unchanged.

**Tests:** All five `test_converter_name_*.do` files; new `converter-name-launch.do` / `.ps1`, `converter-name-helpers.do`, and `ConverterNameTestSupport.java`.

### Amendment 2 validation

The requested unmodified runner starts Stata successfully. The five new do-files execute all **15 child scenarios: 12 pass and 3 fail**, giving **4 passing do-files and 1 failing do-file**. All three failures belong to `test_converter_name_jar_fallback.do`: unset override, empty override, and the real renamed archive. Each reaches `ctosim` and receives error 601 with `the converter ctosim_converter.zip was not found; reinstall the package with net install ctosim`, even though `ctosim_converter.jar` is on the controlled ado-path. These are command discovery failures, not fixture or startup failures. Subsequent conversion assertions in these three scenarios remain unreachable.

Zip-only discovery, zip-before-jar precedence (including across directories), all four override variants, all three missing-converter cases, and both XML-independence cases pass through their final assertions. The Java bridge and simulator double compile successfully. A separate Python subprocess check also imports and executes each of the three tagged archives and the actual converter renamed to `.jar`, verifies the output catalog's form ID, and verifies each test archive's identity marker. No production source was opened or changed.

Logs: `target/stata-tests/test_converter_name_*/runner.log` and `.codex-runs/stata-converter-name/<test>-<profile>/child.log`. Independent archive-check outputs are under `.codex-runs/stata-converter-name/fixture-check/`.

The full requested command, `powershell -ExecutionPolicy Bypass -File scripts/stata-tests.ps1`, completed with **35 do-files, 8 failed** (exit status 1). Besides the new jar-fallback failure, the seven existing `test_amendment_*.do` files fail because their unchanged launcher cannot remove pre-existing `.codex-runs/stata-amendment/.../temp/hsperfdata_manil` directories (access denied). Those existing cleanup failures prevent their command assertions from running in this invocation and are not attributed to Amendment 2. The remaining 27 do-files pass. Existing tests, production files, specs, and build configuration were not modified; only new test/support/fixture files and this questions file were written. No commit was made.

## Amendment 3: help-file use cases

### C1. Automated checks of prose and SMCL presentation

**Question:** How can tests check this amendment's explicit documentation requirements without prescribing the author's exact sentences, paragraph layout, or example filenames?

**Assumption:** The named Examples, Basic examples, Advanced examples and Configuration headings are observable requirements. Tests normalize common SMCL headings, emphasis, paragraphs, character escapes and wrapped commands; examples must actually use `{cmd:}`. A preceding description may wrap physical lines, and consecutive commands may share one description. The tests check the presence of explanatory prose, not sentence length, punctuation, or writing quality. Basic and Advanced must belong to Examples; unrelated sections cannot supply their commands. English vocabulary checks for the constraint guarantee and automatic plausible texts allow several common paraphrases. These are necessarily a partial semantic check: human review is still needed to assess explanation accuracy and prose quality, and equivalent wording or other valid SMCL formatting outside the recognized vocabulary may require adapting the helper. This extends the original question 10 only for Amendment 3; the original smoke test remains unchanged.

**Tests:** All five `test_help_use_cases_*.do`; especially `structure` and `configuration`.

### C2. Running versus displaying the examples

**Question:** Must examples refer to bundled survey forms, import templates, analysis code and writable output directories, so that tests can execute every command literally?

**Assumption:** No. Amendment 3 requires examples in the help, not a collection of executable example projects. Tests inspect the displayed commands and JSON without executing the illustrative `ctosim`, `assert`, `type`, or loop commands. They impose no survey basename, field name, seed, sample size, number of examples, or exact assertion predicate. They require the listed basic options on actual `ctosim using` examples; the plain import example combines `n()`, `seed()` and `clear`, and the template example combines `dofile()` and `replace`. Stored-result/report examples must be command blocks. A loop must use its iterator in `seed()` with multiple distinct numeric seed values (ordinary `forvalues` or `foreach` examples are recognized). An assertion must refer to an answer, not merely `_N` or stored results. Whether that assertion matches the constraint of an unprovided example form remains a review item. Arbitrary Stata macro programs, indirect seed construction and all possible loop syntax are not interpreted.

**Tests:** `test_help_use_cases_basic.do`, `test_help_use_cases_advanced.do`.

### C3. Identifying complete example configurations

**Question:** Where must JSON appear, and how is a displayed object associated with a `config()` filename?

**Assumption:** A full object may appear anywhere in the help, including outside Examples. Its filename may be supplied in adjacent prose or in the adjacent, unambiguous `config()` command. The same displayed configuration may be reused by multiple calls, or explicitly labeled for multiple filenames; no particular caption or SMCL layout is required. Bare external links and ellipses are not full JSON. The helper checks every object shown in Examples and complete root-configuration objects elsewhere. Standalone nested-schema snippets outside Examples are not treated as complete configurations. Complex interleaving of multiple unlabeled objects/filenames is left ambiguous rather than guessing an association.

**Tests:** `test_help_use_cases_json.do`; the advanced feature checks reuse the parsed configurations. The positive fixture and helper checks cover JSON outside Examples and both literal and SMCL-escaped braces.

### C4. Schema scope and realistic-data examples

**Question:** Does "uses only keys of spec 04" include its amendments, nested object keys, and arbitrary question/profile/choice names? Must all realistic features be in one configuration?

**Assumption:** Spec 04 including Amendments 1–3 defines the schema: all 17 top-level keys, all 10 field-override keys, and nested rule `if` are in scope. Unknown and duplicate schema keys, truncated JSON, placeholder values, invalid object shapes, and basic type/range/conflict errors are rejected. Question, profile and choice names remain arbitrary at their documented mapping levels. The helper is independent of production parsing and does not attempt a second XPath engine or timezone database; expression syntax, timezone validity, form compatibility and simulated distributions are not re-tested here. The required fixed value, range, weights, missing rate, profiles, normal/lognormal distribution, when/if, expr, area and text pools may be demonstrated across configurations. No statistical draws or runtime determinism requirements are added to this documentation amendment.

**Tests:** `test_help_use_cases_json.do`, `test_help_use_cases_advanced.do`; schema and omission checks in `fixtures/help_use_cases_oracle_tests.py`.

### C5. Key descriptions and shared keys

**Question:** Must a description occupy exactly one source line, and must keys such as `area` and `missingRate` be repeated separately for global and field scope?

**Assumption:** "One line each on what it does" means an identifiable explanatory entry per key; physical line wrapping is free. Shared keys may have one entry explaining both uses, and paired `min`/`max` may share one entry as they do in spec 04. Merely including a key in JSON or in an unrelated sentence does not supply its reference entry. Automated checks require a named entry with explanatory words; accuracy of each description and complete explanation of both scopes remain review items. The Configuration section must also identify all four example text-question names and state both requested generation guarantees.

**Tests:** `test_help_use_cases_configuration.do`.

### C6. Test infrastructure and preservation of existing tests

**Question:** How can Stata batch tests parse JSON and inspect SMCL without depending on the command implementation or Stata's configured Python?

**Assumption:** A small Java-8-compatible fixture bridge may run the already supplied `.venv/Scripts/python.exe` as an external process. It reads only the public help artifact found by Stata `findfile`; it imports no production modules. This avoids Windows batch `shell` limitations and embedded-Python compatibility issues. The build hook compiles only test support, with outputs under `.codex-runs/stata-support/`. Assertions propagate through a nonzero Java return code to a Stata error; logs are under `.codex-runs/stata-help-use-cases/`. The 30-second helper timeout is a harness safeguard, not a command performance requirement. All five do-files and their helpers/fixtures are new. No existing test, production file, build configuration or specification was changed, and no commit was made.

**Tests:** All five new do-files; `help-use-cases-check.do`, `build-help-use-cases-fixtures.ps1`, and the fixtures alongside the independent checker.

### Amendment 3 validation (2026-10-12)

The requested `powershell -ExecutionPolicy Bypass -File scripts/stata-tests.ps1` completed with **40 do-files, 17 failed** (exit status 1). Stata starts successfully and the new Java bridge compiles. All **five new do-files** reach the documentation assertions and fail with propagated `r(9)`: the Basic/Advanced example parts are missing from Examples, no complete example JSON is shown, and the Configuration reference lacks `area`. These are missing Amendment 3 content, not test startup failures. Assertions after the first missing requirement remain pending on the actual help artifact.

The other **12 failures** are in the unchanged seven `test_amendment_*.do` and five `test_converter_name_*.do` files. Their launchers cannot delete pre-existing `temp/hsperfdata_manil` directories (access denied), preventing their command assertions. The remaining **23 do-files pass**. Existing cleanup code and file permissions were left unchanged.

The independent helper checks pass **14 unittest methods**, including per-feature/key subcases, against a synthetic help fixture and deliberately broken variants. Checks include all basic commands, omitted advanced features in otherwise valid JSON, missing descriptions, malformed/duplicate/unknown JSON keys, missing configurations, constant seeds, row-count-only assertions, and the two generation guarantees. Positive variants cover heading styles, wrapped commands, literal/escaped JSON braces, JSON outside Examples, normal/lognormal choices, and both loop forms. A separate Stata 16 batch probe runs all five new do-files to completion against the valid fixture and verifies that an invalid fixture produces a nonzero Stata error. This establishes both successful execution and failure propagation independently of the missing production documentation.

Logs: `.codex-runs/stata-help-use-cases/full-suite.log`, the five area logs in that directory, `.codex-runs/stata-help-use-cases/oracle-tests.log`, `.codex-runs/stata-help-use-cases/probe/probe.log`, and `target/stata-tests/test_help_use_cases_*/runner.log`. Only new test/support/fixture files and this questions file were written outside the permitted temporary-output locations. Production sources were not inspected; the help artifact was read only by the tests. Existing tests were retained, and no commit was made.

## Amendment 4: additional questions and assumptions

### D1. Valid reports and the meaning of the count

**Question:** Should an empty, malformed, duplicated, or zero-count section be accepted, and is the count questions or occurrences?

**Assumption:** Spec 10 Amendment 1 defines only a positive-count section, omitted when there are no unsatisfied constraints. Tests use that format and do not invent malformed-report behavior. `r(unsatisfied)` is the first-line question count, independent of submissions, warning count, number of section lines, and repeated occurrences. Fixtures cover 1, 3, and 12 questions; 25 submissions; optional row/reference lines; a 20-item truncated submission list; blank references; Unicode; and literal XPath `${limit}`. No statistical simulation expectations are added.

**Tests:** `test_unsatisfied_results.do`, `test_unsatisfied_display.do`, `test_unsatisfied_absent.do`.

### D2. Display color, layout, and ordering

**Question:** Must original indentation be retained, and does “before the warnings” also apply to the simulator's own stdout summary?

**Assumption:** Indentation and diagnostic prefixes are free. Each complete section line must occur once, in report order, on its own display line, with error styling, before step 3's warning count and individual warnings. The earlier simulator stdout count is allowed, as required by the unchanged execution step. SMCL logs expose `display as error` as error-style markup; the oracle tracks persistent error/text/result/input styles rather than requiring a color directive on every line. It does not prescribe terminal RGB values. Fixture lines fit Stata's 255-column test log, avoiding unspecified wrapping behavior. A report with no section must not fabricate one, and must return scalar zero even after a previous nonzero run.

**Tests:** `test_unsatisfied_display.do`, `test_unsatisfied_absent.do`, `test_unsatisfied_template.do`, `test_unsatisfied_template_failure.do`; `UnsatisfiedFixture.log`.

### D3. Loading and failed templates

**Question:** Does an unsatisfied section alter loading semantics, and must `r(unsatisfied)` be available after a template error?

**Assumption:** Both loading modes and all existing successful stored results remain unchanged. Plain import is compared with the specified native `import delimited` command, including blank cells and Unicode. A successful template may alter the dataset, cwd and `r()`; the command must still restore cwd and return report values. A failed template must retain its partial data and original error code after the section is shown. No stored results are asserted on an unsuccessful command, because the spec does not promise them.

**Tests:** `test_unsatisfied_plain_import.do`, `test_unsatisfied_template.do`, `test_unsatisfied_template_failure.do`.

### D4. Help prose

**Question:** How should automated checks recognize the explanation without prescribing its wording?

**Assumption:** The help test checks the stored-result name and broad English concepts: unsatisfied constraints, blank answers, impossibility, dependence on referenced answers, and checking the form. Equivalent wording may require extending the vocabulary. These are documentation smoke checks; they cannot prove the causal explanation or the “usually” qualification is correct. Semantic review remains necessary, consistent with the original question 10. No exact sentence or SMCL layout is required.

**Tests:** `test_unsatisfied_help.do`; `UnsatisfiedFixture.help`.

### D5. Independent fixtures and unchanged existing tests

**Question:** Can Amendment 4 reuse the existing process-isolation harness without modifying committed tests?

**Assumption:** Yes. The existing launcher creates a disposable simulator jar per child; a new helper replaces only that private copy with the new process fixture. The fixture writes a controlled spec-10 report and CSV and does not import or inspect production code. The new build hook compiles Java 8 compatible support into `.codex-runs/stata-support/`. The existing runner's own logs remain under its prescribed `target/stata-tests/`; all additional generated artifacts stay under `.codex-runs/`. New do-files are independent and propagate assertion/Java failures as nonzero Stata errors. Existing tests and helpers are preserved.

**Tests:** All seven `test_unsatisfied_*.do` files, `unsatisfied-helpers.do`, `build-unsatisfied-fixtures.ps1`, and new fixtures under `src/test/stata/fixtures/`.

## Amendment 5: additional questions and assumptions

### E1. Report names and empty results

**Question:** What separates filenames, what happens when the line is absent, and are spaces inside names or malformed report lines supported?

**Assumption:** The fixture follows spec 10 Amendment 2: one `Fictitious attachments:` line with comma-space-separated CSV filenames, omitted when no fictitious files were made. The returned macro preserves filename order, Unicode, and `.csv` suffixes, replacing the separators with single spaces and adding no directory prefixes. Cases cover zero, one and several names; the mixed-case names follow spec 12's natural string ordering (`Zebra`, `alpha`, `ménages`), which differs from case-insensitive alphabetic order. No behavior is invented for malformed/duplicated lines, internal spaces or commas in filenames, or unspecified size limits. The macro must come from the report, not the output directory (which also contains copied real media and stale files after `replace`). No assertion is made about `r(fakepreload)` when the option is omitted or the command fails; the amendment only promises it with the option on successful runs.

**Tests:** `test_fakepreload_results.do`, `test_fakepreload_opt_in.do`, `test_fakepreload_forwarding.do`, `test_fakepreload_real_media.do`.

### E2. Display order and styling

**Question:** Does "before the warnings" include the simulator's own warning summary, and what styling or ordering relative to unsatisfied constraints is required?

**Assumption:** As for confirmed D2, it means before step 3's count and warning details; step 2 still shows the simulator's own messages first. The report line is shown intact once, allowing indentation/prefixes, and no line is fabricated when absent. No color is required for this new line, and no relative order between this line and the unsatisfied section is imposed: both must precede warnings. All names fit within the test session's 255-column log setting. An independent text-log checker verifies report-derived content and ordering; its build-time positive/negative checks cover omission, duplication, misplaced/missing counts, reordered/missing warnings and invented lines for an absent report entry.

**Tests:** `test_fakepreload_display.do`, `test_fakepreload_opt_in.do`, `test_fakepreload_template.do`, `test_fakepreload_template_failure.do`; `FakepreloadFixture.log` and its self-checks.

### E3. Unchanged behavior with the option enabled

**Question:** Does permission to invent missing preload bypass other validation, alter loading, or change simulator/import failure handling?

**Assumption:** No, under "Everything not mentioned here is unchanged." Both XML and XLSX calls receive one valueless `--fake-attachments` argument; other simulator options retain their documented argument boundaries. Subsequent calls without `fakepreload` do not inherit it. Dirty-memory and existing-output protections, missing FORM/config/do-file checks, simulator status mappings, native CSV import, template execution/suppression, cwd restoration and partial data after template failure retain their existing contracts. Successful report results remain authoritative even when command options or the import template suggest different N/seed values. These are Amendment 5 interaction tests, not a rewrite of the existing regression suite.

**Tests:** `test_fakepreload_forwarding.do`, `test_fakepreload_opt_in.do`, `test_fakepreload_preflight.do`, `test_fakepreload_simulator_failures.do`, `test_fakepreload_plain_import.do`, `test_fakepreload_template.do`, `test_fakepreload_template_failure.do`.

### E4. Real simulation and reuse

**Question:** Which invented values should command-level integration tests expect, and how much of spec 12 belongs here?

**Assumption:** Do not prescribe invented strings, distributions, or the internal generation algorithm. A minimal existing pulldata form needs `people.csv`; enabling the option must create that file under output `attachments/`, report it, and suppress its missing-file warning. Identical options and seed must produce identical files and data. The generated directory can be edited and supplied to `media()`, with or without `fakepreload`; an existing real file must supply its actual value and must not be reported as fictitious. Different seeds need not change every invented file, so no unsupported differing-seed assertion is added. Full fake-file generation rules remain spec 12's responsibility.

**Tests:** `test_fakepreload_real_media.do`.

### E5. Help semantics and independent test infrastructure

**Question:** How can automated tests establish free-form help completeness without imposing exact wording, and can the existing isolation harness be reused?

**Assumption:** The help checker tests the option name and broad English vocabulary for unavailable preload, invented values, inference from form usage, testing, output `attachments/`, reading/editing and reuse through `media()`. These are smoke checks: semantic review is still needed to establish the "only follow the form's own use" limitation and that the concepts are causally connected. Synonyms may need extending; no exact sentence or SMCL layout is required. The help artifact is read only by the test, never inspected to derive expectations. The existing child launcher may be reused unchanged, replacing only its disposable simulator jar with a new independent fixture. All new build outputs and extra logs are under `.codex-runs/`; the prescribed unchanged runner itself writes under `target/stata-tests/`. All eleven do-files and their fixture/helper sources are new; committed tests and helpers are preserved, and production code is neither read nor changed.

**Tests:** `test_fakepreload_help.do`, all new `test_fakepreload_*.do`, `fakepreload-helpers.do`, `build-fakepreload-fixtures.ps1`, `fixtures/FakepreloadFixture.java`, `fixtures/fakepreload_import.do`.

## Amendment 6: form problems shown last, in red

### F1. Meaning and lifetime of the two problem counts

**Question:** Does the new count describe fields, records, submissions, or warning lines? Is `r()` promised after an import do-file fails?

**Assumption:** `r(formerrors)` is the report header's field count, following spec 10 Amendment 3; it is independent of submission and warning counts. `r(unsatisfied)` remains the report's question count. Both are zero for absent sections, including after earlier nonzero runs. Report-derived results survive an import template changing `r()` and the observation count. No returned-result assertion is made after command failure: only the final message's report-derived counts are checked then. No artificial zero-count section or malformed report is generated, since the referenced report format only includes nonempty sections.

**Tests:** `test_form_problems_results.do`, `test_form_problems_absent.do`, `test_form_problems_template.do`, `test_form_problems_template_failure.do`.

### F2. Section rendering and unspecified ordering

**Question:** Must the two red sections and the fictitious-attachments line appear in report order relative to one another? Does "before the warnings" include simulator stdout?

**Assumption:** Each section's lines appear once, on separate lines, in their own report order and entirely in error style, following the confirmed Amendment 4 contract. No order between the two problem sections is imposed. They precede step 3's warning count and details; the simulator's own stdout is still shown first. Indentation is free. Identical submission lines for different fields are legitimate and counted with multiplicity. Tests include optional row numbers, omitted references, repeated lines, a truncated twenty-submission list, literal XPath braces, quotes, Unicode, and the adjacent fictitious-attachments and Files sections. They do not require warning color or a particular indentation.

**Tests:** `test_form_problems_display.do`, the full-log checks in `test_form_problems_final_plain.do`, `test_form_problems_absent.do`, `test_form_problems_template.do`, `test_form_problems_template_failure.do`; `FormProblemsFixture.checkLog`.

### F3. Final message wording, line wrapping, and error trailers

**Question:** What exact words or layout identify the final diagnostic? Is Stata's own `r(459);` error trailer part of the command's "last output"?

**Assumption:** There is no mandated sentence. The checker accepts a multiline red final message identifying form problems, naming both counts (including a zero count), explaining that form errors block an enumerator, and containing the actual absolute report path. It uses broad English wording and accepts slash/case differences for Windows paths; synonyms beyond the recognized vocabulary may require extending the smoke check. The test paths fit within the 255-column log setting. Stata's own error-code trailer may follow the message; unrelated output may not. No exact count of final-message physical lines is imposed. Simulator summaries naming only `run-report.txt` cannot substitute for the post-load message. For zero/zero counts, no form-problem final message is expected, even if warnings or fictitious attachments exist.

**Tests:** `test_form_problems_final_plain.do`, `test_form_problems_absent.do`, `test_form_problems_template.do`, `test_form_problems_template_failure.do`; `FormProblemsOracleChecks.java` covers valid logs and mutations for missing/wrong counts, color, path, content, late output and misplaced error trailers.

### F4. Observable timing and unchanged failure behavior

**Question:** How can tests establish that a message follows a quiet template, and does it also appear after a simulator failure that leaves an old report on disk?

**Assumption:** Templates execute as `run` does, may change the directory, data, and `r()`, and may operate a caller-created named log. Each new template enables a second log only at its end, after its import/data edits and directory change. The final diagnostic must occur in this tail log, including when the next template command raises error 459. The full log checks report sections and warnings. Both successful and failing templates preserve the existing directory/data/error contracts. The new final message concerns a completed simulation's load stage; a simulator failure still preserves memory and its existing error mapping and must not display stale report diagnostics. Plain-import failures and malformed reports remain unspecified and untested.

**Tests:** `test_form_problems_template.do`, `test_form_problems_template_failure.do`, `test_form_problems_simulator_failure.do`; `fixtures/form_problems_import.do`, `fixtures/form_problems_import_failure.do`.

### F5. Help and test infrastructure

**Question:** What depth of help prose is required by "documents ... the section and the final message", and may existing test isolation be reused without modifying it?

**Assumption:** Help smoke checks require `r(formerrors)`, the FORM ERRORS section, its enumerator-blocking meaning, and the final red message's timing and diagnostic/report content. They do not prescribe headings or sentences; semantic review may still be needed to establish connections between these concepts. Existing `launch.do`, `launch.ps1`, and `helpers.do` are reused unchanged, substituting only the launcher's disposable simulator jar through the confirmed `CTOSIM_JAR` mechanism. All Amendment 6 tests, helper/fixture sources and build script are new. Temporary fixture build outputs and additional logs use `.codex-runs/`; the prescribed unchanged runner itself uses `target/stata-tests/`. No production implementation was opened, searched, or modified, and no existing test was rewritten.

**Tests:** `test_form_problems_help.do`, all eight new `test_form_problems_*.do` files; `build-form-problems-fixtures.ps1`, `form-problems-helpers.do`, `fixtures/FormProblemsFixture.java`, `fixtures/FormProblemsOracleChecks.java`.

## Amendment 7: additional questions and assumptions

### G1. The meaning of a single word

**Question:** Does `id(name)` impose Stata variable-name restrictions, allow a quoted word, or trim outer whitespace? Are punctuation, duplicate options, Unicode whitespace, or names over 32 characters defined?

**Assumption:** The amendment requires one nonempty word, not a varlist or an existing Stata variable. Tests accept a one-character name, mixed case, underscores/digits, a 32-character name, and an accented Unicode name, preserving the name exactly as a separate `--id` argument. Quoting a single word is ordinary Stata string-option syntax and does not become part of the name. Empty parentheses, an empty quoted string, whitespace-only content, and two or more words separated by spaces, a tab, or a newline require exactly 198 before simulator invocation. The amendment gives no alternative syntax-error code for `id()`. No expectation is imposed on trimming outer whitespace around an otherwise valid word, punctuation, repeated `id()` options, Unicode whitespace classification, or an undocumented maximum length. If "word" is intended to mean a narrower ASCII identifier grammar, the Unicode expectation needs clarification.

**Tests:** `test_id_forwarding.do`, `test_id_empty.do`, `test_id_multiple_words.do`; helpers `a7_id_argument` and `a7_reject_id`.

### G2. Configuration fallback and limits on distinctness

**Question:** Does the option replace a configured id, and does the unconditional wording about distinct ids supersede spec 13's unavailable/exhausted-pool warnings?

**Assumption:** Follow the explicitly referenced contracts: spec 10 Amendment 4 makes the option replace configuration `id`; without it the original configuration applies. No CLI override is invented on omission or retained from an earlier call. Spec 13 qualifies uniqueness: tests asserting distinct ids supply enough possible values; an unknown question and an exhausted finite pool produce warnings and still load data. Optional id questions are populated even when their missing rate is 1, per spec 13's clarification. The other optional question stays blank, making override versus fallback observable without probabilistic duplicate assumptions. No specific randomized permutation or generated string format is required.

**Tests:** `test_id_omission.do`, `test_id_real_config.do`, `test_id_real_xlsx.do`, `test_id_real_warnings.do`, `test_id_real_determinism.do`.

### G3. Scope of fieldwork-plan integration checks

**Question:** Which plan guarantees belong in the Stata tests, and how can one village a day be observed when village centres are not exported?

**Assumption:** Command integration tests inspect exported start/end, username and GPS fields using spec 13's public consequences, without checking internal scheduling or random-number algorithms. Fixed-seed visits must not overlap (including across days), must start between 08:00 and 17:00 for the fixture, and must be separated by at least ten minutes. Pairwise GPS distances on the same enumerator/day may be up to 3,060 metres: two 1.5 km household offsets plus two 30 m answer offsets; a 0.1 metre allowance covers exported coordinate precision. The test requires multiple visits on at least one enumerator/day so this is not vacuous. This follows the current spec 13, including its default daily quota amendment, without hard-coding a quota value. It does not require interviews to end within working hours. The overflow case requests 60 visits per enumerator in one planned day, which cannot fit even on minimum travel time alone, and requires data for all submissions and the plan warning to reach Results. Times are parsed from the spec 08 export format. Same-seed outputs are compared in full, including after `replace`; no single-value different-seed inequality is assumed.

The schedule test deliberately omits id from both the command and configuration, so it also checks that planning is unconditional.

**Tests:** `test_id_real_schedule.do`, `test_id_real_overflow.do`, `test_id_real_determinism.do`; `fixtures/id_plan.xml`, `.fields.json`, `.json`, and `id_plan_schedule.json`.

### G4. Unchanged command behavior with id enabled

**Question:** Does the new option relax checks or change importing, results, failure codes, or interaction with `fakepreload`?

**Assumption:** No, under "Everything not mentioned here is unchanged." New interaction tests cover XML/XLSX argument forwarding with all valued simulator options; dirty memory and output protections; missing files; status 2 versus other simulator failures; successful/failing import templates; report-derived results; and simultaneous `id()`/`fakepreload`. Invalid id cases use valid files, clean output paths and `clear`, so they do not assume precedence among unrelated errors. The successful simulator double deliberately reports a different N/seed from requested options, and does not pretend to allocate ids; real-simulator tests establish the data behavior.

**Tests:** `test_id_forwarding.do`, `test_id_preflight.do`, `test_id_failures.do`, `test_id_template.do`, `test_id_fakepreload.do`.

### G5. Help prose and independent infrastructure

**Question:** How should help completeness be tested without prescribing exact English sentences, and can the committed isolation harness be reused?

**Assumption:** New help smoke checks strip SMCL and look for the option, its survey-id/question meaning, distinct ids, enumerator interviews without overlap, starts within working hours, one village a day, and extension beyond `fieldworkDays` when interviews do not fit, with a warning. Broad English alternatives are accepted, but semantic review remains necessary and equivalent vocabulary may require extending the checker. No exact heading or sentence is prescribed. Positive and per-clause omission tests exercise the oracle independently. The committed launcher/helpers are reused unchanged. A small new Java adapter supports the valueless fake-attachments flag while delegating ordinary report production to the existing test double and recording the original arguments. Build outputs and additional logs live under `.codex-runs/`; the required unchanged runner writes its own logs under `target/stata-tests/`. No production implementation or help text was read to derive expectations; the help artifact is read only by the automated checks. Existing tests and fixtures remain untouched.

**Tests:** `test_id_help_option.do`, `test_id_help_plan.do`, `id-help-check.do`, `fixtures/IdTestSupport.java`, `build-id-fixtures.ps1`; all new `test_id_*.do` files.
