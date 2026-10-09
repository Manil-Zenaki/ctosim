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
