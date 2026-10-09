# SimulationConfig test questions and assumptions

The tests use only spec 04's public API and behavioral contract. No production
implementation was read. The following points identify interpretations or gaps;
they do not extend the production specification.

1. **Which dotted path should identify a cross-property failure, a missing key,
   an invalid name, or an invalid array element?**
   - **Assumption:** A reversed range, missing bound, mixed range types, or an
     override conflict may identify the containing object or a more specific
     member. Invalid field names must at least identify `fields`. Array errors
     must identify `specialValues` or `fields.age.texts`; bracket versus dotted
     indexes are unspecified. Single-value failures with an unambiguous path
     require that full path. Error wording is not fixed, but some explanation
     beyond the source and path must be present.
   - **Dependent tests:** `SimulationConfigValidationTest` validation methods;
     `SimulationConfigIoTest.loadAppliesValueValidationAndUsesThePathAsSource`
     and `loadIncludesTheFullNestedLocationForABadWeight`.

2. **Are duplicate object keys syntax errors or configuration-rule errors?**
   - **Assumption:** Both detection routes are acceptable. Duplicate-key tests
     require `ConfigException`, source attribution, and explanatory text, but
     do not choose a path/line/cause convention for this ambiguous category.
     Dedicated malformed-JSON tests independently require the actual failing
     line and a retained cause. Escaped keys are compared as decoded strings.
   - **Dependent tests:**
     `rejectsDuplicateObjectKeysIncludingIdenticalValuesAndEscapedNames`,
     `loadEnforcesRootShapeDuplicateNullUnknownAndTrailingContentRules`,
     `parseReportsTheActualFailingLineAndRetainsSyntaxCause`, and
     `loadReportsTheActualFailingLinePathAndSyntaxCause`.

3. **Does leading BOM acceptance also apply to `parse(String, String)`?**
   - **Assumption:** Yes. `load` accepts UTF-8 BOM bytes and `parse` accepts
     their decoded leading U+FEFF. The spec gives a shared file format and does
     not distinguish parsing entry points. Embedded or repeated BOM handling
     is not tested.
   - **Dependent tests:** `loadAcceptsALeadingUtf8ByteOrderMark` and
     `parseAcceptsALeadingDecodedByteOrderMark`.

4. **Can ranges, weights, and texts coexist in one override?**
   - **Assumption:** Yes. Only `value` is explicitly mutually exclusive with
     the other answer settings. Form compatibility and generator precedence
     belong to later modules. This test checks preservation, not precedence.
   - **Dependent test:** `nonValueOverrideKindsCanCoexist`.

5. **Are empty or whitespace-only strings accepted where no nonempty rule is
   stated, and are field names restricted to an XLSForm identifier grammar?**
   - **Assumption:** Fixed strings and text entries may be empty or whitespace
     only; text duplicates are allowed. `specialValues` may be an empty array,
     but its individual strings must be nonempty and unique. Nonempty whitespace
     strings are not the same as empty strings. Field names are subject only to
     the stated nonempty/no-edge-whitespace rules; Unicode and internal spaces
     are accepted without consulting a form. Unknown choice names are allowed.
   - **Dependent tests:** `fixedStringsArePreservedExactly`,
     `textsKeepOrderDuplicatesEmptyStringsWhitespaceAndUnicode`,
     `specialValuesCanBeEmpty`, `specialValuesAreExactStringsInFileOrder`,
     `arbitraryNonemptyNamesAreAcceptedWithoutConsultingAForm`, and
     `onePositiveWeightIsSufficientAndChoiceCodesNeedNotBeKnown`.
   - **Unresolved:** The precise Unicode definition of edge whitespace, and
     whether empty choice-weight keys are intended to be valid. No assertions
     depend on those cases. Name rejection covers space, tab, CR, and LF.

6. **Are integral JSON numbers checked before floating-point rounding?**
   - **Assumption:** Yes. The explicit definition is a JSON number with no
     fractional part, and the seed domain is the entire Java `long` range.
     Thus `42.0` and `4.2e1` are valid, a tiny nonzero fractional part remains
     invalid even if rounded away by a double, and 64-bit seed endpoints must
     not be truncated, rounded, or wrapped. Fractional bounds are permitted in
     `integerRange` because its documented type is number, not integer.
   - **Dependent tests:** `seedAcceptsIntegralJsonNumbersWithoutDoubleRounding`,
     integer-form acceptance tests, `defaultNumericRangesAcceptNegativeFractionalAndEqualBounds`,
     and `rejectsOutOfRangeOrInvalidTopLevelValuesWithTheirLocation`.

7. **How are extreme fixed numbers and non-finite double results handled?**
   - **Assumption:** Ordinary integral fixed values lose a fractional suffix;
     fractional values use `Double.toString`'s shortest form, including its
     scientific notation. No expectation is imposed on signed zero, integers
     beyond exact double precision used as fixed values, numeric underflow,
     or valid JSON exponents that overflow a double such as `1e400`.
     Unquoted `NaN` and `Infinity` are rejected because they are not JSON.
   - **Dependent tests:** `numericFixedValuesUseWholeNumberOrShortestDoubleText`
     and `malformedJsonAlwaysUsesConfigException`. Extreme representation cases
     remain untested pending clarification.

8. **Which ISO date-time extensions are allowed?**
   - **Assumption:** Both explicitly documented minute and second forms are
     accepted, with strict calendar validation. Offset/UTC designators are
     rejected because the type is a local date-time. Date bounds follow the
     explicit `YYYY-MM-DD` format. Fractional seconds, expanded/signed years,
     and alternate ISO forms are left untested because the spec does not
     settle them clearly.
   - **Dependent tests:** `startAcceptsMinuteAndSecondPrecisionAndLeapDays`,
     date-range acceptance tests, top-level validation, and range validation.

9. **Must null API arguments throw `NullPointerException`, or is that merely
   permitted?**
   - **Assumption:** The stated exception for null arguments documents the
     intended rejection: each null `parse` argument and a null `load` path
     produces `NullPointerException`. Java null is separate from JSON `null`,
     which must produce `ConfigException`.
   - **Dependent tests:** `parseNullJsonThrowsNullPointerException`,
     `parseNullSourceNameThrowsNullPointerException`, and
     `loadNullPathThrowsNullPointerException`.
   - **Unresolved:** `field(null)` is unspecified and is not tested.

10. **What does unmodifiable mean for collection views and no-op mutations?**
    - **Assumption:** Java collection mutation attempts that would change a
      returned collection throw `UnsupportedOperationException`, including
      entry mutation, iterators, and sorted-set/list views. The same applies
      after copying. Empty collections are checked using additions, avoiding
      implementation-dependent behavior for no-op `clear` or `remove` calls.
    - **Dependent tests:** Collection tests in `SimulationConfigCopiesTest`.

11. **Does 'a new configuration' require a distinct object when the value is
    unchanged?**
    - **Assumption:** Yes, following the explicit copy wording. Both copy tests
      include the receiver's current setting among their parameters and check
      distinct identity, all retained settings, and the unchanged receiver.
    - **Dependent tests:** `withSeedReturnsANewConfigurationAndChangesOnlyTheSeed`
      and `withSubmissionsReturnsANewConfigurationAndChangesOnlyTheCount`.

12. **Which additional file-system and size limits should be tested?**
    - **Assumption:** Missing files and directory paths exercise deterministic
      I/O failures and retained I/O causes. No unreadable-permissions fixture is
      assumed portable across Windows accounts. Malformed UTF-8 replacement
      policy, maximum file size, maximum number of fields, and nesting limits
      are unspecified and are not tested. No random draws or statistical rate
      tests are appropriate here: the spec assigns generation to modules 05
      and 06. This suite checks rates as configuration and repeated parsing as
      deterministic observable data.
    - **Dependent tests:** I/O tests and
      `repeatedParsingProducesTheSameObservableConfiguration`; no tests assert
      undocumented size limits or generation behavior.

## Amendment 1: time zone and enumerators

The original questions and tests above are preserved. Amendment coverage is in
new `SimulationConfigTimeZoneTest`, `SimulationConfigEnumeratorsTest`,
`SimulationConfigAmendmentIoTest`, and `SimulationConfigAmendmentCopiesTest`
classes. Only the spec and existing tests/fixtures were read; no production code
was inspected.

13. **Does a zone ID have to retain its input spelling, or can it normalize?**
    - **Assumption:** The specified `ZoneId.of(text)` is the exact oracle. Tests
      compare `ZoneId` equality with that result, not the original spelling or
      merely the current offset. For example, `UTC`, `Z`, and `Etc/UTC` must not
      be collapsed just because they can have the same offset; compact offsets
      may normalize exactly as `ZoneId.of` does. Region rules, prefix IDs,
      fractional-hour offsets, offset seconds, and the +/-18-hour bounds are
      covered. No alias map, trimming, or case folding is implied. Named region
      tests use long-established IDs; no particular time-zone database version
      or historical offset is asserted.
    - **Dependent tests:** `acceptsRegionPrefixAndOffsetFormsWithExactlyZoneIdOfSemantics`,
      `rejectsInvalidIdsWrongTypesAndNullAtTimeZone`,
      `loadAcceptsTheSameZoneIdFormsAsParse`, and the amendment copy tests.

14. **Should `timeZone` change or impose DST validity constraints on `start`?**
    - **Assumption:** No. Amendment 1 says everything else is unchanged;
      `start()` remains a local date-time and the config module only reads and
      validates settings. A valid local time within a DST gap or overlap remains
      unchanged. Resolving that time to an instant/offset belongs to the consumer
      module and is not tested here.
    - **Dependent test:**
      `zoneDoesNotConvertOrRejectTheLocalStartDuringDstTransitions`.

15. **May the enumerator count exceed the number of submissions?**
    - **Assumption:** Yes. The only enumerator constraint is the independent
      inclusive interval 1..10000. Neither parsing nor `withSubmissions` may
      silently clamp or reject an otherwise valid count because there are fewer
      submissions. Usernames, device IDs, allocation, and random distributions
      belong to spec 07 and are intentionally not tested here.
    - **Dependent tests:** `enumeratorsMayOutnumberSubmissions`,
      `withSubmissionsPreservesBothNewSettingsEvenWhenEnumeratorsOutnumberTheNewCount`,
      and `copyChainsPreserveBothSettingsAtEveryStep`.

16. **Do exact-integer validation and existing diagnostic conventions extend to
    the new keys? Must invalid zone IDs retain a particular exception cause?**
    - **Assumption:** The confirmed exact-JSON-number rule applies to
      `enumerators`: integral decimals/exponents are accepted, fractional values
      that would round to integers are rejected, and overflow cannot wrap into
      the accepted interval. `1e400` is unambiguously out of range for this key,
      unlike the unspecified double-valued settings in question 7. Wrong types
      and JSON null report the new key's path. Duplicate-key diagnostics retain
      the confirmed flexibility from question 2. Syntax failures must retain a
      cause and report the failing line, without prescribing parser wording.
    - **Unresolved:** Whether a rejected zone must have the original
      `DateTimeException` as its cause is not explicitly addressed. Zone-value
      rejection tests require `ConfigException`, source, location, and an
      explanation, but do not prescribe a cause type or presence.
    - **Dependent tests:** `acceptsInclusiveLimitsAndExactIntegralDecimalAndExponentForms`,
      `rejectsOutOfRangeFractionalUnrepresentableWrongTypeAndNullCounts`,
      `rejectsInvalidIdsWrongTypesAndNullAtTimeZone`, and the invalid-value,
      duplicate-key, and syntax-diagnostic tests in `SimulationConfigAmendmentIoTest`.

17. **Do the new names become reserved outside the top-level config object?**
    - **Assumption:** No. The amendment adds top-level keys only. These names
      remain unknown as members of range/repeat/field-override schema objects;
      they remain legal arbitrary field names under `fields`, whose documented
      naming constraints are unchanged.
    - **Dependent tests:** `newKeysAreCaseSensitiveAndOnlyExtendTheTopLevelSchema`,
      `loadRejectsMisspelledAndMisplacedNewKeys`, and
      `newTopLevelKeyNamesRemainValidAsUnrelatedFieldNames`.

### Amendment build verification (2026-10-09)

- Ran the requested `mvnw.cmd -o -q test-compile`. Maven reached
  `maven-compiler-plugin:3.15.0:testCompile` but failed with
  `java.nio.file.AccessDeniedException` for
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar` while javac was
  closing its archive file manager. The detailed retry is saved in
  `.codex-runs/04-amendment-test-compile.log`.
- A diagnostic retry with `-Dmaven.compiler.fork=true` also failed. It reported
  unresolved production types in existing tests (including `QuestionKind`,
  `SimulationConfig`, and `ConfigException`), then the same archive access
  exception. Its output is in
  `.codex-runs/04-amendment-forked-test-compile.log`.
- Successful compilation could not be verified in this environment. No Maven
  repositories were copied, no drives were mapped, and no production code,
  existing tests, build configuration, or dependency permissions were changed.
  The build needs to be rerun in the calling environment with accessible cached
  dependencies and the public API classes available. Runtime tests were not run
  because test compilation did not complete.
