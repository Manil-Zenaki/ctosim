# Spec 08: questions, assumptions, and validation

Expectations come from spec 08 and its referenced public contracts in
specs 01 and 07. No production source was opened, searched, or modified.
The suite uses six JUnit 5 test classes and one test support class.

1. **Question:** What should happen for JSON keys that are present but have
   the wrong scalar type, null required field attributes, duplicate object
   keys, negative row numbers, or an empty/null form id?
   **Assumption:** These cases have no explicit validation policy, so they
   are not asserted. Missing required keys are errors. A null/object/string
   `fields` value is not an array and is therefore an error; an empty array
   is valid. Title fallback is limited to missing/null/empty, with no extra
   trimming. Versions are tested as strings, including the empty string.
   **Tests:** `FieldCatalogTest`, especially
   `invalidCatalogThrowsIOExceptionNamingFile`,
   `everyFieldRequiresEverySpecifiedKeyIncludingLaterFields`, and
   `fileValuesUnicodeFullTypesAndArrayOrderArePreserved`.

2. **Question:** Must instance ids be nonempty, UUID-shaped, unique across
   submissions, or have a `uuid:` prefix? How do namespaces, multiple meta
   elements, and multiple instanceID children affect validation?
   **Assumption:** The spec requires the root/id and direct
   root/meta/instanceID structure, but specifies no extra id syntax or
   uniqueness constraints. Non-UUID ids are used to test escaping and keys;
   text-audit tests explicitly exercise ids without the prefix. Empty ids,
   duplicate metadata/ids, and namespace mismatch policies are not tested.
   Submission diagnostics must contain the decimal submission number;
   punctuation and complete prose are not prescribed.
   **Tests:** `invalidInstanceIsRejectedByEveryExportMethodAndNamesSubmissionNumber`,
   `attachmentUrlsUseUtf8FormEncodingOfBothIdentifierAndFilename`,
   `textAuditStripsOnlyALeadingUuidPrefix`, and repeat-key tests.

3. **Question:** Exactly which date/time input syntaxes are readable?
   **Assumption:** Tests use ordinary ISO calendar dates and times with
   seconds, ISO `T` date-times, optional fractional seconds, and `Z` or
   signed hour/minute offsets for time/date-time. They test all English
   months, leap day, midnight, noon, year boundaries, and deliberately
   unreadable input. No expectations are imposed for locale-specific input,
   a space replacing `T`, dates carrying offsets, missing seconds, leap
   seconds, 24:00, or lenient normalization of invalid calendar days.
   **Tests:** `datesAndTimesUseEnglishWallClockAndDiscardOffsetsAndFractions`,
   `allMonthAbbreviationsAreExactlyThreeEnglishLetters`,
   `unreadableAndEmptyTemporalValuesArePreserved`, and
   `formattingDoesNotDependOnDefaultLocaleOrTimeZone`.

4. **Question:** Does "space-separated items" include repeated spaces,
   tabs/newlines, or whitespace-only values? How are unexpected geopoint
   items interpreted?
   **Assumption:** Tokenization tests use single ordinary spaces; whitespace
   normalization outside those inputs is left open. Select items match
   whole values, repeated selected values do not change a binary dummy,
   and an unknown selected item is retained in the own column without
   creating a new column. Geopoint uses the first four items, preserving
   textual precision, and ignores a fifth item as implied by "first to
   fourth". No numeric validation is imposed on geopoint tokens.
   **Tests:** `dummySelectionUsesWholeItemsAndEmptySelectionsHaveEmptyDummies`,
   `geopointHasOnlyFourComponentColumnsWithMissingItemsEmpty`, and the
   nested select/geopoint tests.

5. **Question:** Is type case normalization also relevant to multiword
   special types? What happens when expanded column names or relative
   repeat paths collide?
   **Assumption:** The specified lowercase type comparison is independent
   of default locale and retains recognition of special `audio audit`.
   Tests preserve its value even with mixed case. Full catalog type strings
   remain unchanged. Duplicate *choice values* are tested as specified,
   including duplicate values in filtered itemsets. Distinct values that
   sanitize to the same name, duplicate catalog names, and repeat filename
   collisions are unspecified and not tested.
   **Tests:** `otherTypesAreStoredVerbatimIncludingSpecialMultiwordTypes`,
   `dummyColumnsUseAllChoicesInFirstOccurrenceOrderWithoutChoiceFilter`,
   `formattingDoesNotDependOnDefaultLocaleOrTimeZone`, and catalog tests.

6. **Question:** What is the count of a nested repeat when its ordinary
   containing group is absent, or when the entire outer instance is absent?
   **Assumption:** An absent containing group gives an empty count; a
   present but empty group gives zero. A padded outer wide block has all
   cells empty, including nested counts and select dummies. A present empty
   repeat element is still one instance. Counts use submitted elements,
   even when the form's count source holds a different value. The repeat
   fixture uses a valid `jr:count` path to an integer node containing 9;
   that uncatalogued node does not contribute a column.
   **Tests:** `absentContainingGroupAndPresentButEmptyGroupHaveDifferentCountCells`,
   `absentNestedContainingGroupLeavesCountEmptyWhilePresentEmptyGroupGivesZero`,
   `missingWideInstancesHaveOnlyEmptyCellsIncludingDummiesAndNestedCounts`,
   `presentEmptyRepeatElementCountsAsAnInstance`, and
   `countsComeFromStoredInstancesNotTheCountExpressionOrWideMaximum`.

7. **Question:** Must an exporter support catalogs inconsistent with the
   form beyond the explicitly rejected form-id mismatch, or define special
   treatment for repeat templates appearing in submitted XML?
   **Assumption:** No extra rejection/repair contract is inferred. Fixtures
   use valid form paths and include each repeat in their catalog. Ordinary
   uncatalogued instance elements are ignored as explicitly required. The
   submitted instances do not contain `jr:template` markers. Deep nesting
   and multiple ordinary groups follow the given recursive/path rules;
   no unstated capacity or performance limit is imposed.
   **Tests:** Catalog-driven header tests,
   `nestedOrdinaryGroupsAreFlattenedButFieldsAreResolvedByFullXpath`, and
   `threeRepeatLevelsHaveAllIndexSuffixesAndRecursivelyLinkedKeys`.

8. **Question:** What exact filesystem diagnostics, validation order,
   atomicity, or rollback behavior is required on failure? Should an export
   remove old repeat files that it no longer produces?
   **Assumption:** Only stated exception types and specified diagnostic
   content are asserted. Missing paths and directory-as-file inputs provide
   portable read failures. An output directory occupied by a file and an
   output filename occupied by a directory provide portable write failures.
   No OS ACL changes, atomic writes, exception identity, ordering among
   multiple invalid arguments, rollback, or stale-file deletion is required.
   Unrelated files must retain their bytes; replacing output files must
   truncate old content. Catalog error messages may use the basename or
   full path, since both name the file.
   **Tests:** Catalog I/O tests, invalid-input tests, and all writeAll tests
   in `SurveyCtoExporterContractTest`.

9. **Question:** Does "unmodifiable" prescribe an exception for no-op
   mutations or require a specific implementation? Must returned maps
   remain stable after another export?
   **Assumption:** Mutations that would change the collection throw
   `UnsupportedOperationException`, including through views/entries. No
   no-op, concrete class, object identity, or thread-safety requirement is
   imposed. A previously returned map retains the content for its original
   input after later calls, consistent with the deterministic result
   contract. Warnings from `Submission.of` retain the unmodifiable-list
   contract from spec 07.
   **Tests:** Field-list mutation tests,
   `longMapCannotBeChangedThroughMapEntriesOrViews`,
   `repeatedCallsSeparateLoadsAndPriorExportsCannotChangeResults`, and
   `warningsRemainUnmodifiableAsRequiredBySubmissionContract`.

10. **Question:** How can the acceptance test obtain XML when only real
    CSV exports are committed? Are byte comparisons allowed to normalize
    the original CSV text?
    **Assumption:** Three static XML fixtures were reconstructed once from
    the published main/member/job CSVs, restoring ISO timestamps, stored
    attachment filenames, combined geopoints, and repeat nesting. Empty
    exported scalar values use empty elements; text_audit is present.
    Ignored notes are omitted. These choices give the specified stored
    values; no invisible original XML serialization is assumed. Start/end
    accessors are explicitly the exported starttime/endtime. Original
    sample CSVs are read only and compared as exact UTF-8 bytes without
    trimming, newline conversion, BOM removal, or regeneration from the
    exporter. Missing/irrelevant fields and other repeat shapes are tested
    separately in synthetic fixtures.
    **Tests:** All three `SurveyCtoExporterRealExportTest` methods; fixtures
    `src/test/resources/ctosim/surveycto-exporter/coverage-submission-*.xml`.

## Validation

- Requested command: `mvnw.cmd -o -q test-compile`. An initial run before
  adding sources exited successfully without needing to recompile. Once
  the new tests required compilation, the command failed in Maven's
  `testCompile` phase with `java.nio.file.AccessDeniedException` for
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. The stack trace
  locates the error in Windows `toRealPath` during javac ZIP filesystem
  cleanup. The final exact-command retry still failed. Logs:
  `.codex-runs/exporter-test-compile.log` (stack trace) and
  `.codex-runs/exporter-test-compile-final.log` (final retry).
- Independent compilation: `.codex-runs/CompileExporter.java`, adapted
  from the existing repository diagnostic probe, compiled all seven new
  Java sources successfully with `--release 17`. It reads existing class
  files and cached dependency JARs into an in-memory compiler file manager;
  no Maven repository was copied, no drive was mapped, and no dependency
  or build configuration was modified. Result:
  `.codex-runs/exporter-probe-compile.log`.
- JUnit discovery/execution through the separate diagnostic runner found
  and started **256 cases** with no skipped/aborted tests or failed
  containers. All 256 fail against the unimplemented APIs: 237 directly
  with `UnsupportedOperationException`, 18 assertions expecting the
  specified exception instead of that stub exception, and one grouped
  null-argument assertion with the same cause. No form-loading errors
  remain. The run exposed and helped correct a fixture's literal
  `jr:count`: it now references a valid integer node. Log:
  `.codex-runs/exporter-junit.log`.
- These probe results establish source compilation and test discovery,
  **not a successful Maven build or a passing implementation**. The
  prescribed Maven command still needs to be rerun in an environment
  where its cached JAR path can be resolved.

Temporary compiler probes and logs are confined to `.codex-runs/`;
production source, pom.xml, the original spec, and published samples are
unchanged. No commit was made.

## Amendment 1: second real export

This run adds five new test classes and three static coverage2 submission
fixtures. All previously committed tests and fixtures are preserved. No
production source was opened, searched, or changed; expectations come from
spec 08 Amendment 1 and the unchanged rules it incorporates.

11. **Question:** What should happen to the original assertions that audio
    audits are stored verbatim, now that Amendment 1 explicitly changes them?
    **Assumption:** The amendment is the current contract. The user explicitly
    requires keeping existing tests and adding only new classes, so the old
    cases are not edited or disabled. The `audio audit` and `AuDiO audit`
    invocations of `SurveyCtoExporterValueTest.otherTypesAreStoredVerbatimIncludingSpecialMultiwordTypes`
    contradict the amended contract and cannot pass together with the new
    audit URL assertions once the amendment is implemented. Original
    assumption 5 above describes that superseded behavior; the new tests
    retain its confirmed case-insensitive type comparison.
    **Tests:** `SurveyCtoExporterAudioAuditAmendmentTest`, especially
    `presentAudioAuditGeneratesAnAaFilenameRegardlessOfStoredValue` and
    `audioAuditRecognitionRetainsCaseInsensitiveTypeComparison`.

12. **Question:** How are the second export's original instance XML and audit
    stored values obtained when only the published CSVs are available?
    **Assumption:** As in confirmed assumption 10, static XML fixtures are
    transcribed from the committed long exports, preserving their actual
    values rather than the planned scenarios in the coverage2 README. ISO
    timestamps restore the displayed wall-clock times; geopoint components
    retain their textual precision and are joined with single spaces.
    Empty scalar cells use empty elements, both audit elements are present
    and empty, and the refused-consent instance omits `main` so its count is
    empty. Meta contains the exported instanceID and instanceName. The
    factory's start/end/submitted arguments are explicit transcriptions of
    starttime/endtime/SubmissionDate, respectively. XML serialization and
    invisible original audit contents are not asserted. Golden CSVs are read
    directly as bytes without trimming, newline normalization, or regeneration.
    **Tests:** All `SurveyCtoExporterSecondRealExportTest` methods; fixtures
    `src/test/resources/ctosim/surveycto-exporter/coverage2-submission-*.xml`.

13. **Question:** Does audio-audit generation depend on its stored text,
    grouping, repeat keys, or unobserved audit start parameters?
    **Assumption:** The amendment explicitly makes element presence the
    trigger and uses the submission instance ID for the URL and filename.
    The unchanged XPath/repeat rules apply inside groups and repeats;
    absent fields and padded instances remain empty. Only a leading `uuid:`
    is stripped, and both URL parameters use the existing UTF-8 form encoding
    rule. No behavior is inferred for nonzero start parameters, multiple
    audio segments, attachment availability, or real comments filenames.
    Synthetic forms test export mapping of catalog type `audio audit`, without
    requiring device recording or a particular stored marker. The real form
    supplies the observed audit setting. Ordinary `audio` and `text audit`
    retain their existing filename rules.
    **Tests:** `SurveyCtoExporterAudioAuditAmendmentTest`.

14. **Question:** Is instanceName detection based on form structure, a setting
    expression, a default value, the catalog, or the submitted XML?
    **Assumption:** The amendment explicitly uses a direct instanceName child
    of the primary instance's direct meta child. Its presence enables the
    column even when empty and even with no submissions. No catalog field or
    expression is needed. The cell uses the submitted direct meta child,
    never a definition default, root/nested decoy, or secondary instance.
    Missing and present-empty names both give empty cells; stored whitespace,
    Unicode and CSV-special characters are preserved. Duplicate meta/name
    elements and catalog fields colliding with this metadata column remain
    unspecified and are not tested.
    **Tests:** instanceName cases in `SurveyCtoExporterAmendmentMetadataTest`,
    and `SurveyCtoExporterSecondRepeatRulesTest.instanceNameMetadataNeverAppearsInRepeatFiles`.

15. **Question:** Does the new factory impose chronology or XML validation,
    and does changing submitted also change stored end fields or row order?
    **Assumption:** The five-argument factory inherits the four-argument
    argument-preservation, null, number, unvalidated-XML, answered and warnings
    contracts, plus the explicit submitted null check. No new chronology,
    upload-delay distribution, or four-argument factory validation is inferred.
    Dates used in distinct-time tests are after end. SubmissionDate alone
    uses submitted; ordinary end fields still use XML and rows remain in list
    order. Nanoseconds are preserved by the accessor and dropped only during
    formatting. Metadata and repeat tests that do not need a distinct upload
    time use the four-argument factory to isolate them from the new overload.
    **Tests:** `SubmissionSubmittedFactoryTest` and the submitted-date cases
    in `SurveyCtoExporterAmendmentMetadataTest`. Upload-time randomness belongs
    to spec 07, not these exporter tests.

16. **Question:** Do the newly confirmed repeat naming rules replace any
    empty-value, maximum-width, or count semantics?
    **Assumption:** No: the amendment leaves those rules unchanged. Focused
    tests use the second form with synthetic submitted instances to cover
    partial/empty/absent geopoints, empty and absent nested selections, missing
    inner and outer repeat blocks, unequal child counts, zero global child
    maximum, and present-empty child instances. Counts come from actual
    elements, even when n_children contains 9. Every hyphen is replaced in
    dummy names, including repeated and leading/trailing hyphens, while
    stored choices are unchanged. Non-numeric or multi-hyphen choice values
    do not introduce any new validation policy.
    **Tests:** `SurveyCtoExporterSecondRepeatRulesTest`. No new expectations
    are imposed for the amendment's remaining unobserved review columns or
    speed-violation/comment formats.

### Amendment 1 validation

- `mvnw.cmd -o -q test-compile` reached `testCompile` but failed resolving the
  cached `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar` path. The
  diagnostic `-e` rerun identifies `java.nio.file.AccessDeniedException` in
  Windows `toRealPath` during javac ZIP filesystem cleanup, the same build
  environment issue documented for the original tests above. Logs are
  `.codex-runs/exporter-amendment-test-compile.log` and
  `.codex-runs/exporter-amendment-test-compile-detail.log`.
- The independent in-memory compiler probe compiles all five new classes
  and the seven existing exporter test/support classes with `--release 17`.
  It reads cached classes/JARs without changing production, the Maven
  repository, or build configuration. This verifies source compilation, not
  a successful execution of the prescribed Maven command. Log:
  `.codex-runs/exporter-amendment-probe-compile.log`.
- Independent JUnit discovery/execution ran **80 new cases** in five classes:
  18 passed and 62 failed, with no skipped/aborted tests, failed containers,
  or form-loading errors. The failures consist of 26 direct
  `UnsupportedOperationException`s from unimplemented additions and 36
  assertion failures (including grouped assertions and expected-exception
  mismatches), reflecting missing factory/instanceName/audio-audit behavior.
  This is a diagnostic test run, not a passing suite. Log:
  `.codex-runs/exporter-amendment-junit.log`.
- The final exact-command Maven retry still failed with the cached-JAR
  access error. Git verification shows that the only modified tracked file
  is this questions file; the five Java classes and three XML fixtures are
  new files. Existing tests, production, published samples, and build files
  remain unchanged.

All temporary transcription/diagnostic scripts and logs are under
`.codex-runs/`. No dependency was installed, Maven repository copied, drive
mapped, or commit made.
