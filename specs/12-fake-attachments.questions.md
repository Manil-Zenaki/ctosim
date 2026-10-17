# Spec 12 test-author questions and assumptions

These tests derive expectations from spec 12 and the contracts it references in
specs 01, 03, 04, 05 and 07. No production source was opened or searched.

1. **Question:** Does “and so are their lists” require key-answer lists to be
   naturally sorted, despite the explicit requirement for keys in row order?
   **Assumption:** The specific key-answer rule controls: lists are unmodifiable,
   preserve CSV row order, and preserve duplicates. Integer keys are `1,2,...,50`,
   not `1,10,...,2`. **Tests:** `FakeAttachmentsKeysTest`'s integer-key and
   repeated-level-key cases; `FakeAttachmentsContractTest`'s list immutability case.

2. **Question:** In what order are columns first encountered within one call or
   select? How are binds, actions, and body appearances interleaved?
   **Assumption:** Within a `pulldata` call, value then key follows argument order.
   Within a select, value then label follows the description. Binds use document
   order; selects use body order. The relative ordering of these different kinds
   of sites, multiple attributes of one bind, and nested calls is not asserted.
   **Tests:** discovery header-order cases; search body-order and duplicate-level
   cases. Mixed-source tests use column membership and values, not full header order.

3. **Question:** Is the final CSV record terminated by LF?
   **Assumption:** Yes, following “lines end with LF.” CR and LF inside a quoted
   field are retained; record separators are LF. Minimal quoting also applies to
   headers. **Tests:** the independent CSV oracle used by all table assertions,
   especially unusual-column and unusual-label parameterizations.

4. **Question:** What are the actual built-in firstName and lastName pools?
   **Assumption:** Their contents are intentionally unspecified (spec 05 says so),
   and the configuration accessor exposes overrides rather than the built-ins.
   Tests therefore require the stated full-name shape, including for `firstname`,
   `prenom`, `surname`, and `lastname`, but do not hard-code pool membership or
   inspect implementation constants. **Tests:** full-name alias parameterization.
   Exact pool-membership verification remains untested without a public oracle.

5. **Question:** Must random integer, phone, name, area, and compared-value draws
   be uniform, and which PRNG/file-name seed derivation is required?
   **Assumption:** No algorithm or exact sequence is specified. Tests check ranges,
   formats, reproducibility, different-seed behavior on many phone/coordinate draws,
   and file isolation. Only latitude/longitude uniformity is tested statistically,
   since their rule explicitly references spec 05's uniform distribution. The
   fixed schedule yields 2,000 draws per coordinate; tolerances are broad (mean
   within 0.08, each quartile 350–650). **Tests:** contract determinism tests,
   numeric-family tests, and `coordinateDrawsHaveBroadUniformCoverageWithAFixedSeedSchedule`.

6. **Question:** Does a “single reference” include relative XPath references and
   references surrounded by conversions such as `string(...)`?
   **Assumption:** A relative node reference such as `../key` qualifies; a function
   wrapping a reference does not. The key-answer map uses the question's leaf
   name. **Tests:** relative-key and compound-key cases in `FakeAttachmentsKeysTest`.

7. **Question:** How should key conflicts be resolved when one column is looked
   up through incompatible types or several different select levels, or when one
   question uses several key columns in the same file?
   **Assumption:** Unspecified; no conflict winner is asserted. A fixture testing
   rule precedence may contain several columns for one question, but does not
   inspect that question's key answers. The first-file rule is tested with exactly
   one eligible key column per file. **Tests:** shared-question first-file case;
   match/filter precedence case (key-answer selection deliberately unasserted).

8. **Question:** Is a numeric select eligible for INTEGER key answers, and does
   a `long` input use integer or K-prefixed keys under rule 4's literal `int` wording?
   **Assumption:** Selects are select answers, not TEXT/INTEGER answers (spec 05),
   and do not receive key answers. The `long` versus `int` ambiguity is not tested.
   **Tests:** same-file and other-file select key cases. Tests for unsupported
   input types use decimal, date, time, dateTime, and geopoint only.

9. **Question:** Do reversed equality/inequality comparisons contribute literals?
   Does a calculate wrapping `pulldata` count as a calculate that “is a pulldata call”?
   **Assumption:** Equality and inequality contribute literals with the field on
   either side. A wrapping expression such as `concat(pulldata(...),'')` is not
   itself a pulldata call. Inequalities, string functions, and nonliteral operands
   do not contribute. **Tests:** comparison and wrapped-calculate cases in
   `FakeAttachmentsValuesTest`. Negative numeric constants, comparisons with empty
   strings, and lexical preservation of numeric literals are left open.

10. **Question:** When rule 2 cannot associate a match/filter column with a select,
    does it fall through rules 3–5 or jump directly to rule 6?
    **Assumption:** “Otherwise as rule 6” is a direct jump. The tests distinguish
    it from a phone-name heuristic or a label rule on the same column.
    **Tests:** `unmappedMatchColumnFallsToRuleSixRatherThanLabelOrNameRules` and
    `matchAndFilterColumnsWithoutReferencedSelectUseFallbackValues`.

11. **Question:** What happens when a label column belongs to several distinct
    levels, or when a match/filter expression is complex or refers to a select
    from a different file?
    **Assumption:** Those conflicts are unspecified and are not tested. A duplicate
    level with distinct label columns is tested because both labels have the same
    unambiguous level. **Tests:** duplicate-value-column case; mapped-match and
    mapped-filter cases use simple references to levels in the same file.

12. **Question:** What is the inventory/CSV shape for a literal source with no
    readable columns at all (e.g. a malformed call or two computed column names)?
    **Assumption:** No zero-column serialization or inventory is asserted. Malformed
    calls contribute no columns to an otherwise valid file and do not throw.
    One literal column is retained when the other is computed, as the spec says
    computed names add nothing “for them.” **Tests:** discovery malformed-call
    and single-nonliteral-column cases. Syntactically invalid XPath in binds is
    not supplied because FormLoader rejects it before this module can receive it;
    malformed search appearance text can be tested directly.

13. **Question:** Does spec 12 scan body/itemset refs and nodesets as well as the
    explicitly listed bind attributes, setvalue values, and select appearances?
    How are search `.csv` suffixes, whitespace in comma-separated column names,
    and static-only choice lists represented in missingAttachments?
    **Assumption:** The extra expression sites and normalization edge cases are
    not asserted. The suite uses the explicitly listed sites, suffix-free source
    names, and comma-separated names without padding. Static-choice tests assert
    that only the first nonnumeric choice contributes. The static-only case uses
    a file also read by pulldata, avoiding any missing-inventory ambiguity, and
    expects no level because spec 07 treats it as an ordinary select.
    **Tests:** discovery expression-site cases and search placeholder/match cases.

14. **Question:** Is tree row order required, beyond one row per terminal code?
    **Assumption:** The suite asserts the complete leaf set, row count, level
    cardinalities, and per-row prefix consistency without imposing an additional
    leaf ordering. Key answers always follow the actual CSV row order. Depths
    1–7 and 12 exercise the 3/2/1 branching transitions and continued cap.
    **Tests:** tree enumeration and key-answer cases.

15. **Question:** Should of() write media files, and should real attachments be
    used to manufacture suggested keys?
    **Assumption:** This content-returning API has no filesystem write side effect.
    Existing attachments are excluded from generated files and left byte-for-byte
    unchanged. No real-file key-answer policy is asserted. **Tests:** existing-file
    exclusion case in `FakeAttachmentsContractTest`.

## Build verification

The requested `.\mvnw.cmd -o -q test-compile` was attempted. The in-process
compiler failed with `java.nio.file.AccessDeniedException` for
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar` during the JDK zip
filesystem's `WindowsPath.toRealPath`/close operation. A diagnostic log is under
`.codex-runs/fake-attachments-compile.log`. No Maven repository was copied, no
drive was mapped, and no dependency or build configuration was changed.
The diagnostic retry with `-Dmaven.compiler.fork=true` also failed (exit 1).
It reported unresolved existing test dependencies such as `Question` and
`QuestionKind`, then the same JAR path `AccessDeniedException`. Its log is
`.codex-runs/fake-attachments-fork-compile.log`. The focused test command
`.\mvnw.cmd -o -q '-Dtest=FakeAttachments*Test' test` likewise stopped during
compilation. Consequently compilation success and runtime/fixture execution
could not be verified in this sandbox; no passing build is claimed.
