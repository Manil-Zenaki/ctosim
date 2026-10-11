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
