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

## Amendment 2: speed violations

Existing questions, tests, and fixtures are preserved. New coverage is in
`SimulationConfigSpeedViolationRateTest`, `SimulationConfigSpeedViolationIoTest`,
and `SimulationConfigSpeedViolationCopiesTest`. Expectations come only from
spec 04; no production source was read.

18. **Must the three probability settings sum to at most one or depend on a
    form containing minimum-time fields?**
    - **Assumption:** No. Each rate has its own inclusive 0..1 constraint; this
      module has no form knowledge. A config with rates summing above one is
      valid. No random draws, speed-violation events, or statistical frequency
      assertions belong here; those are behavior of spec 07.
    - **Dependent tests:** `allThreeRatesAreIndependentAndNeedNotSumToOne`;
      the acceptance tests parse/load the rate without a form.

19. **What numeric precision and extreme-number behavior should rate tests
    require?**
    - **Assumption:** Ordinary fractional and exponent JSON numbers are valid
      and exposed as the corresponding Java double, including representable
      values immediately below/above one. Negative zero is accepted as zero,
      without requiring a particular sign bit. No rounding to a fixed number
      of decimal places or replacement of zero with the default is implied.
    - **Unresolved:** Exact-decimal validation for values which round onto an
      endpoint, underflow, and exponents overflowing a double remain untested,
      consistent with question 7. Tests do not extrapolate the confirmed
      exact-integer rule to this number-valued key. Unquoted NaN/Infinity are
      invalid JSON and are tested separately as syntax failures.
    - **Dependent tests:** `acceptedRates` and `invalidRates` in
      `SimulationConfigSpeedViolationRateTest`, reused by the load/copy tests;
      syntax-diagnostic tests in `SimulationConfigSpeedViolationIoTest`.

20. **Does the new name become valid in nested schema objects or reserved as
    an arbitrary field name/choice code?**
    - **Assumption:** No. Only the top-level schema is extended. It is unknown
      in repeat, numeric-range, and field-override objects, but remains a legal
      name in the arbitrary `fields` and choice-weight mappings. JSON escape
      decoding occurs before recognizing or comparing keys.
    - **Dependent tests:** `theNewKeyRemainsLegalAsAnArbitraryFieldNameAndChoiceCode`,
      `anEscapedJsonKeyIsRecognizedAsTheNewSetting`, and the unknown/duplicate
      key tests in `SimulationConfigSpeedViolationIoTest`.

21. **Do established diagnostic and copy conventions apply to the new key?**
    - **Assumption:** Yes. Invalid values require source, `speedViolationRate`,
      and a reason, without prescribed wording or cause type. Syntax errors
      require the failing line and a retained cause. Duplicate-key diagnostics
      keep question 2's confirmed flexibility. Copies, including unchanged
      seed/count copies, retain the rate and return distinct objects. This is
      the unchanged base contract, not additional behavior for the amendment.
    - **Dependent tests:** Diagnostic tests in `SimulationConfigSpeedViolationIoTest`
      and `SimulationConfigSpeedViolationRateTest`; all tests in
      `SimulationConfigSpeedViolationCopiesTest` and
      `copiesOfALoadedConfigurationRetainItsRate`.

### Amendment 2 build verification (2026-10-10)

- Ran `.\mvnw.cmd -o -q test-compile` with the supplied `JAVA_HOME` and
  `MAVEN_USER_HOME`. It reached `maven-compiler-plugin:3.15.0:testCompile`
  and failed on `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`.
- Retried with `-e` to identify the failure. The cause is
  `java.nio.file.AccessDeniedException` while javac closes its archive file
  manager (`WindowsPath.toRealPath` / `ZipFileSystemProvider.removeFileSystem`).
  Logs are `.codex-runs/04-speed-test-compile.log` and
  `.codex-runs/04-speed-test-compile-detail.log`.
- **Question:** Can the calling environment rerun the requested compilation
  with accessible cached dependencies? **Assumption:** This is an environment
  blocker; no production source, dependency permissions, Maven configuration,
  or existing tests should be changed to work around it. **Dependent tests:**
  Compilation of all three new Amendment 2 test classes remains unverified.
  Runtime tests were not run because compilation did not complete.

## Amendment 3: realistic data

Existing tests, fixtures, and questions are preserved. New coverage is in
`SimulationConfigRealisticParsingTest`, `SimulationConfigRealisticRulesTest`,
`SimulationConfigRealisticValidationTest`, `SimulationConfigRealisticCopiesTest`,
and `SimulationConfigRealisticIoTest`, with `SimulationConfigRealisticSupport`
and `amendment-realistic.json`. Only the spec, build configuration, and existing
tests were inspected; no production source was read.

22. **Which override combinations are permitted with `area`, and does a rule
    conflict with the parent override's settings?**
    - **Assumption:** The explicit exclusion lists govern combinations. `area`
      alone is a setting; it can coexist with `expr`, `distribution`, or `value`
      and `missingRate`. A parent's settings and each rule body are validated
      separately: a parent `value` does not prohibit a rule's `expr` or
      `distribution`. No settings are merged or evaluated by this module.
    - **Dependent tests:** `areaCanAccompanySettingsWithNoExplicitAreaConflict`,
      `ruleAndParentAnswerKindsAreValidatedSeparately`,
      `whenAloneIsAValidOverrideAndDoesNotPromoteRuleSettingsToTheParent`, and
      `incompatibleOverrideSettings` (also exercised through `load`).

23. **Are absent settings inherited into accessors, and which empty containers
    are valid?**
    - **Assumption:** An absent field area remains an empty optional even with
      a top-level area. `textPools()` contains only explicitly overridden pools,
      not the generator's built-in pools. An empty `textPools` object is valid:
      only its individual arrays have a nonempty constraint. Empty `profiles`
      and empty individual text pools are explicitly invalid.
    - **Unresolved:** `when: []` has no stated minimum length, but the base rule
      requires at least one override setting. No test requires acceptance or
      rejection of an empty top-level `when` array. A nested `when: []` inside
      a rule is rejected because nesting is expressly forbidden.
    - **Dependent tests:** `fieldAreaDoesNotRequireOrOverwriteGlobalArea`,
      `customTextPoolsContainOnlySpecifiedPoolsAndPreserveExactEntries`,
      `emptyTextPoolMappingDoesNotInventOverrides`, and `invalidRules`.

24. **What expression validation and preservation are required?**
    - **Assumption:** Parseable expressions are preserved exactly, including
      whitespace. The parser accepts `${name}` references to absent fields and
      `profile()` without a profile declaration; no form lookup, evaluation,
      boolean-result requirement for `if`, or profile-name resolution occurs.
      Tests use basic XPath syntax, plus the explicitly supported extensions.
      Expressions in false or later rules are still validated. Rule order and
      duplicate conditions are retained in the returned list.
    - **Unresolved:** Unknown function handling, detailed `${name}` grammar,
      quoted literal `${...}` substitution, and whitespace-only `if`/`expr`
      strings are not tested. Invalid XPath tests require source, key, and
      explanation; they do not prescribe an exception cause type or parser
      wording. JSON syntax failures independently require line and cause.
    - **Dependent tests:** All expression and order tests in
      `SimulationConfigRealisticRulesTest`, their load counterparts, and the
      false-condition case in `invalidRules`.

25. **How specific must paths be for compound errors?**
    - **Assumption:** Existing confirmed question 1 still governs missing
      members, reversed bounds, invalid names, and conflicts: the containing
      object or a more specific path is accepted. A value with one unambiguous
      location must name its full path. Amendment 3 explicitly illustrates
      bracket indexes, so malformed second-rule conditions require
      `fields.answer.when[1].if` and invalid third pool entries require
      `textPools.place[2]`. An out-of-domain coordinate that also reverses the
      bounds may report the area object or a member. Duplicate keys retain
      question 2's flexibility about syntax versus validation classification.
    - **Dependent tests:** The validation matrices, malformed XPath tests,
      duplicate-key tests, and their corresponding load tests.

26. **Are text duplicates, Unicode, internal spaces, and extreme numbers valid?**
    - **Assumption:** Profile names permit Unicode and internal spaces under
      the stated nonempty/no-edge-whitespace rule; names are case-sensitive.
      Text pool entries preserve Unicode, leading/trailing spaces, newlines,
      order, and duplicates. Only zero-length pool entries are expressly
      forbidden. Profile weights remain relative, unnormalized values.
      Normal means may be negative or zero; lognormal means must be positive;
      zero standard deviation and standard deviations exceeding the mean are
      allowed. Numeric distribution bounds need not contain the mean.
    - **Unresolved:** Whitespace-only pool entries, Unicode edge-whitespace
      classification, exact-decimal rounding at numeric limits, underflow,
      and double overflow remain untested, consistent with questions 7 and 19.
      No constructor validation is inferred for the new public records.
    - **Dependent tests:** Profile/text/distribution acceptance tests,
      `distributionAllowsEqualFractionalNumericBoundsAndMissingRate`, and the
      corresponding rejection matrices. Record constructors only build valid
      expected values.

27. **How do immutability, copies, and generation requirements extend?**
    - **Assumption:** Every new collection is unmodifiable, including nested
      pool lists and rule-body collections. Mutations that change a collection
      throw `UnsupportedOperationException`, as confirmed in question 10.
      Copies preserve all new settings and their order, including nested rule
      bodies, without requiring identity sharing or copying of immutable
      values. Loading reads a snapshot rather than a live view of the file.
      Repeated parsing gives the same observable data. No sampling,
      statistical-rate, geometry-generation, rule-selection, or seed-diversity
      behavior is tested: the amendment assigns use to specs 05 and 07.
    - **Dependent tests:** All tests in `SimulationConfigRealisticCopiesTest`,
      `loadedSettingsSurviveCopiesAndReplacementOfTheSourceFile`, and the
      parse/load fixture comparison. No undocumented size limits are asserted.

28. **Do new schema words become reserved in arbitrary-name mappings?**
    - **Assumption:** No. The explicit schemas accept each new key only at its
      documented location, with case-sensitive spelling after JSON escape
      decoding. Arbitrary field names, choice codes, and profile names keep
      their documented naming rules. In particular, `if` is allowed only in a
      rule schema and is not itself a field-override setting.
    - **Dependent tests:** `newWordsRemainLegalArbitraryFieldAndChoiceNames`,
      `escapedNewKeysAndNamesAreDecodedBeforeRecognition`, `unknownKeys`, and
      `duplicateKeys` (including their load counterparts).

### Amendment 3 build verification (2026-10-12)

- Ran the requested `.\mvnw.cmd -o -q test-compile`, then repeated with `-e`
  for diagnosis. Both failed at `maven-compiler-plugin:3.15.0:testCompile`.
  The detailed cause is `java.nio.file.AccessDeniedException` for
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar` while javac closes
  its archive file manager (`WindowsPath.toRealPath` /
  `ZipFileSystemProvider.removeFileSystem`). Logs are
  `.codex-runs/04-realistic-test-compile.log` and
  `.codex-runs/04-realistic-test-compile-detail.log`.
- A direct javac check limited to the new tests, existing test support,
  `target/classes`, and cached JUnit dependencies could not resolve production
  types such as `SimulationConfig` and `FieldOverride`; it also encountered
  the archive-close access error on a cached `opentest4j` jar. Its log is
  `.codex-runs/04-realistic-javac.log`.
- As a separate source check, all six new Java files plus the existing test
  support **compiled successfully with Java release 17** against temporary
  signatures transcribed solely from spec 04 and the actual cached JUnit API.
  This probe lives entirely in `.codex-runs/04-realistic-probe/`. It uses the
  compiler API and leaves the archive manager open until process exit to
  avoid the sandbox's archive-close error; compiler diagnostics still fail
  the probe. Result: `.codex-runs/04-realistic-signature-compile.log`.
  This checks Java syntax, types, and JUnit overloads against the documented
  contract; it does **not** establish that the repository Maven build passes.
- **Question:** Can the calling environment rerun the requested Maven command
  with accessible cached dependencies and compiled public API classes?
  **Assumption:** This environment limitation does not authorize edits to
  production code, dependency permissions, or the build. No Maven repository
  was copied, no drive was mapped, and no dependencies were installed.
  **Dependent tests:** Repository compilation and runtime execution of all
  Amendment 3 test classes remain unverified. Existing tests are unchanged.

## Amendment 4: survey id

Existing tests, fixtures, and questions are preserved, including the committed
`FieldworkIdConfigTest`. New coverage is in `SimulationConfigSurveyIdTest`,
`SimulationConfigSurveyIdIoTest`, and `SimulationConfigSurveyIdCopiesTest`, with
the minimal UTF-8 fixture `amendment-survey-id.json`. Expectations come only from
spec 04. No production source was opened, read, searched, or changed.

29. **Does the id name have an identifier grammar, normalization rule, or size
    limit beyond the three stated constraints?**
    - **Assumption:** No additional constraint is inferred. A nonempty string
      without edge whitespace is preserved exactly after JSON decoding,
      including case, internal spaces, punctuation, non-ASCII characters,
      supplementary Unicode characters, and distinct composed/decomposed
      spellings. It need not appear in `fields`, and parsing does not create
      a field override. No undocumented maximum length is tested.
    - **Dependent tests:** `acceptedNames` and its parse/load consumers,
      `escapedKeyAndEscapedValueAreDecodedBeforeBeingExposed`,
      `loadDecodesEscapesInTheKeyAndValue`, and
      `independentParsesCannotChangePreviouslyReturnedIdsOrDefaults`.

30. **What counts as leading or trailing white space?**
    - **Assumption:** Space, tab, CR, LF, and form feed are whitespace and are
      rejected at either edge, including whitespace-only strings. They must
      be rejected rather than silently trimmed. Escaped whitespace is checked
      after JSON decoding; the test inputs contain valid JSON escapes.
    - **Unresolved:** The specification does not define Unicode whitespace
      classification (such as nonbreaking spaces) or handling of other
      control characters. No assertions depend on those cases. Internal
      ordinary spaces are covered, but internal control characters are not.
    - **Dependent tests:** `invalidValues` and its parse/load consumers;
      `rejectsAnEscapedEdgeSpaceAfterDecodingTheString`.

31. **Does id become a reserved name elsewhere or interact with field
    overrides for the named question?**
    - **Assumption:** Only the top-level schema gains the key. The spelling is
      case-sensitive after JSON escape decoding. It remains unknown inside
      closed nested schemas, and remains legal in arbitrary field, profile,
      and choice-name mappings. An override for the named question is retained
      without a new conflict rule. An override named `id` does not supply the
      top-level setting when that setting is omitted. These tests check storage
      only, not the precedence of id generation over an answer override.
    - **Dependent tests:** `unknownKeys` and its parse/load consumers,
      `idRemainsALegalArbitraryFieldProfileAndChoiceName`,
      `omittedIdStaysAbsentEvenWithAFieldOverrideNamedId`, and
      `idDoesNotConflictWithOrReplaceAnOverrideForTheSameName`.

32. **Do existing diagnostics and I/O conventions apply to id?**
    - **Assumption:** Yes, under "everything else is unchanged". Invalid id
      values require `ConfigException`, the source, the `id` key, and a reason;
      no exact wording or validation-cause type is required. Duplicate keys
      retain question 2's confirmed syntax/schema flexibility. Malformed JSON
      retains its underlying cause and names the failing line. The UTF-8/BOM
      contract applies to the new setting as it does to existing settings.
    - **Dependent tests:** `assertIdDiagnostic`, duplicate-key tests, and the
      syntax/UTF-8/BOM/root-shape tests in `SimulationConfigSurveyIdIoTest`.

33. **Must copying preserve id, including absence, and does configuration
    testing need to verify generated survey-id uniqueness?**
    - **Assumption:** The existing immutable-copy contract preserves id and
      every other setting, including copies with unchanged seed/count. Adding
      id does not reset defaults or previously configured settings. Independent
      configurations and loaded snapshots cannot change one another's ids.
      Generated values, uniqueness, form existence checks, and plan warnings
      belong to spec 13 and are not tested in this module.
    - **Dependent tests:** All `SimulationConfigSurveyIdCopiesTest` methods;
      `loadedIdAndItsCopiesRemainUnchangedAfterTheFileIsReplaced`.

### Amendment 4 build verification (2026-10-18)

- Ran the requested `.\mvnw.cmd -o -q test-compile`, then repeated with `-e`
  to diagnose the failure. Both reached
  `maven-compiler-plugin:3.15.0:testCompile` and failed with
  `java.nio.file.AccessDeniedException` for
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar` while javac closed
  its archive file manager. Logs are
  `.codex-runs/04-survey-id-test-compile.log` and
  `.codex-runs/04-survey-id-test-compile-detail.log`.
- As an independent compilation check, all three new test classes and the
  two existing support classes they use **compiled successfully with Java
  release 17 against the actual `target/classes` binaries and cached JUnit
  5.11.4 API**. The temporary compiler probe reads dependency class entries
  in memory to avoid the archive-close failure, without copying repositories,
  changing permissions, or inspecting production implementation. It is saved
  at `.codex-runs/SurveyIdCompileProbe.java`; the result is
  `.codex-runs/04-survey-id-probe-compile.log` (`compiled=true`). This confirms
  compilation of the new sources, but does not claim the Maven command passed.
- **Question:** Can the calling environment rerun the requested Maven command
  with accessible cached dependencies? **Assumption:** The access error is an
  environment blocker; production code, Maven configuration, and dependencies
  should remain unchanged. **Dependent tests:** Full Maven test compilation
  remains unverified. Runtime tests were not requested and were not run.

## Amendment 5: interviews per day

Existing tests, fixtures, and questions are preserved. New coverage is in
`SimulationConfigInterviewsPerDayTest`, `SimulationConfigInterviewsPerDayIoTest`,
and `SimulationConfigInterviewsPerDayCopiesTest`, with the minimal UTF-8 fixture
`amendment-interviews-per-day.json`. Expectations come solely from spec 04;
no production source was opened, read, searched, or changed.

34. **Does the exact-integer rule extend to interviewsPerDay?**
    - **Assumption:** Yes. The new key is an integer, explicitly validated as
      enumerators is. Integral decimal and exponent forms are accepted;
      fractional values remain invalid even when double rounding would make
      them integral. Values outside 1..1000 cannot wrap, truncate, clamp, or
      fall back to the default. Extreme exponents are unambiguously outside
      this integer domain; they do not extend the unresolved double-valued
      behavior discussed in question 7.
    - **Dependent tests:** `acceptedCounts` and `invalidCounts`, and their
      parse/load consumers.

35. **Does expected fieldwork duration impose a capacity constraint on config?**
    - **Assumption:** No. Amendment 5 assigns warnings about longer fieldwork
      to the plan. Parsing retains the independently valid submissions,
      enumerators, fieldworkDays, and interviewsPerDay values, even when
      submissions exceed their nominal capacity or the daily limit exceeds
      submissions. Copies likewise do not recalculate or clamp those values.
      Scheduling, warnings, allocation, and generated interview counts belong
      to spec 13; no statistical or generation tests are added here.
    - **Dependent tests:**
      `aWorkloadBeyondExpectedFieldworkDaysIsStillValidConfiguration`,
      `dailyCapacityMayExceedSubmissionsWithoutClamping`,
      `omissionUsesFourRegardlessOfOtherSettings`, and the submission-copy tests.

36. **Does interviewsPerDay extend or reserve names in nested schemas?**
    - **Assumption:** Only the top-level schema changes. Keys are recognized
      case-sensitively after JSON decoding. Closed nested schemas reject the
      new word; arbitrary field, profile, and choice-name mappings, and the
      id name, can still use it. A same-named field override does not supply
      the top-level value. This follows the previously confirmed conventions
      for amendments 1 through 4.
    - **Dependent tests:** `onlyTheExactTopLevelSchemaKeyIsAdded`,
      `theNewNameRemainsLegalInArbitraryNameMappings`, escaped/duplicate-key
      tests, the omitted-setting test, and their load counterparts.

37. **Which diagnostic, copy, and input conventions apply to the new key?**
    - **Assumption:** The unchanged base contract applies: invalid values
      name the source and interviewsPerDay location and explain the failure,
      without prescribed wording or validation-cause type. Duplicate-key
      diagnostics retain question 2's confirmed flexibility. JSON syntax
      errors require the failing line and underlying cause. UTF-8 and a
      leading BOM are accepted. Copies preserve the daily limit and all
      other settings, including unchanged seed/count copies, and loaded
      configurations remain snapshots. No new API setter is inferred.
    - **Dependent tests:** Diagnostic and input tests in the parsing/I/O
      classes; all tests in `SimulationConfigInterviewsPerDayCopiesTest`;
      `loadedConfigurationAndItsCopiesRetainTheirSnapshotAfterFileReplacement`.

### Amendment 5 build verification (2026-10-18)

- Ran the requested `.\mvnw.cmd -o -q test-compile` and a diagnostic retry
  with `-e`. Both reached `maven-compiler-plugin:3.15.0:testCompile` and
  failed with `java.nio.file.AccessDeniedException` on
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar` while javac closed
  its archive file manager. Logs are
  `.codex-runs/04-interviews-test-compile.log` and
  `.codex-runs/04-interviews-test-compile-detail.log`.
- All three new test classes and their two existing support classes
  **compiled successfully with Java release 17 against the actual
  target/classes binaries and cached JUnit 5.11.4 API**. The temporary
  compiler probe reads dependency class entries in memory to avoid the
  archive-close error, without inspecting production implementation,
  copying repositories, changing permissions, or installing dependencies.
  Probe: `.codex-runs/InterviewsPerDayCompileProbe.java`; result:
  `.codex-runs/04-interviews-probe-compile.log` (`compiled=true`). This
  verifies compilation of the new sources, but does not make the requested
  Maven command successful. Runtime tests were not requested and were not run.
- **Question:** Can the calling environment rerun Maven with accessible
  cached dependencies? **Assumption:** The archive access failure is an
  environment blocker and does not authorize changes to production code,
  dependencies, or build configuration. **Dependent tests:** Full Maven
  test compilation remains unverified. All existing tests are unchanged.

## Amendment 6: injected problems

Existing tests, fixtures, and questions are preserved. New coverage is in
`ProblemRatesAmendment6Test`, `SimulationConfigProblemsParsingTest`,
`SimulationConfigProblemsCopiesTest`, and `SimulationConfigProblemsIoTest`,
with `SimulationConfigProblemsSupport` and `amendment-problems.json`.
Expectations come solely from spec 04. No production source was opened,
read, searched, or changed.

38. **Must the problem rates sum to at most one, or require a form, id,
    area, or sufficient interview capacity?**
    - **Assumption:** No. Each rate has its own inclusive 0..1 constraint.
      All seven may be one, independently of existing probability settings.
      This module reads configuration without form knowledge. Tests do not
      impose scheduling, feasibility, generation, answer-key, or statistical
      requirements; those belong to spec 14. An all-zero object remains a
      present setting, distinct from an omitted key or `withProblems(null)`.
    - **Dependent tests:** `allOneRatesAreValidWithoutASumConstraint`,
      `problemRatesAreIndependentOfOtherProbabilitiesAndFieldworkCapacity`,
      `allRatesCanBeAtTheSameEndpointWithoutDroppingTheOptionalOrNormalizing`,
      and absent/empty/zero parsing, loading, and copy tests.

39. **Which numeric representations and precision rules apply to rates?**
    - **Assumption:** As for earlier number-valued rates, ordinary decimal
      and exponent forms are accepted as Java doubles without normalization
      or fixed-decimal rounding. Either sign of zero is numerically zero;
      no sign-bit preservation is required. Representable values just below
      and above one exercise the boundary. Constructor validation also covers
      NaN, infinities, and the smallest positive and negative doubles in
      every component, since its domain is explicitly Java doubles.
    - **Unresolved:** Exact-decimal validation when JSON numbers round onto
      an endpoint, JSON numeric underflow, and overflowing exponents remain
      untested, consistent with questions 7 and 19. Unquoted NaN and Infinity
      are instead tested as invalid JSON. No constructor message or cause
      requirement is inferred.
    - **Dependent tests:** `acceptedRates`, `invalidRates`, their parse/load
      consumers, the syntax-diagnostic tests, and constructor parameterized
      tests in `ProblemRatesAmendment6Test`.

40. **Does the amendment reserve the new names elsewhere in the schema?**
    - **Assumption:** Only top-level `problems` and its seven exact member
      names are added. Closed nested schemas still reject misplaced keys;
      arbitrary field, profile, choice, and id names remain legal. JSON
      escape decoding precedes key recognition and duplicate detection.
    - **Dependent tests:** `unknownKeys`, `duplicateKeys`, their parse/load
      consumers, escaped-key tests, and
      `newSchemaNamesRemainLegalInArbitraryNameMappingsWithoutEnablingProblems`.

41. **Do existing diagnostic and immutable-copy conventions apply, including
    an unchanged or null problems setting?**
    - **Assumption:** Yes. `withProblems` explicitly returns a new configuration
      as `withSeed` does, so every call returns a distinct configuration,
      including null-to-null, equal-rate, and same-instance assignments.
      ProblemRates object identity itself is not prescribed. Copies retain
      all other settings and collection immutability. `withProblems(null)`
      clears the setting, while JSON null is a configuration error.
      Validation errors name source and path and explain the problem without
      prescribed wording or cause. Duplicate errors retain question 2's
      confirmed flexibility; syntax errors require a line and retained cause.
      UTF-8, BOM, and loaded-snapshot rules continue to apply.
    - **Dependent tests:** All tests in `SimulationConfigProblemsCopiesTest`;
      validation, duplicate, syntax, UTF-8/BOM, and snapshot tests in the
      parsing and I/O classes. No tests require distinct `ProblemRates.defaults()`
      identities, since no such requirement is stated.

### Amendment 6 build verification (2026-10-23)

- Ran the requested `.\mvnw.cmd -o -q test-compile`, followed by an `-e`
  diagnostic retry. Both reached `maven-compiler-plugin:3.15.0:testCompile`
  and failed with `java.nio.file.AccessDeniedException` for
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar` while javac closed
  its archive file manager. Logs are `.codex-runs/04-problems-test-compile.log`
  and `.codex-runs/04-problems-test-compile-detail.log`.
- The five new Java source files and the two existing support classes they
  use compiled successfully with Java release 17 against the actual
  `target/classes` binaries and cached JUnit 5.11.4 API. The temporary probe
  reads class entries in memory to avoid the archive-close failure; it does
  not inspect implementation, copy repositories, alter permissions, or
  install dependencies. Probe: `.codex-runs/ProblemsCompileProbe.java`;
  result: `.codex-runs/04-problems-probe-compile.log` (`compiled=true`).
  This verifies the new sources compile, but does not make Maven successful.
- **Question:** Can the calling environment rerun the requested Maven command
  with accessible cached dependencies? **Assumption:** This is an environment
  blocker, not a reason to change production code, dependencies, or build
  configuration. **Dependent tests:** Full Maven test compilation remains
  unverified. Runtime tests were not requested and were not run.

## Amendment 7: faulty enumerators and outliers

Existing tests, fixtures, and questions are preserved. New coverage is in
`ProblemRatesAmendment7Test`, `SimulationConfigAmendment7ParsingTest`,
`SimulationConfigAmendment7IoTest`, and `SimulationConfigAmendment7CopiesTest`,
with `SimulationConfigAmendment7Support` and the minimal fixture
`amendment-7-faults-outliers.json`. Expectations come solely from spec 04;
no production source was opened, read, searched, or changed.

42. **Does Amendment 7's reference to defaults enable problems in an otherwise
    default SimulationConfig?**
    - **Assumption:** No. It refers to `ProblemRates.defaults()` and the
      explicitly present `"problems": {}`. The unchanged Amendment 6 rule
      keeps `SimulationConfig.defaults().problems()` and an omitted problems
      key empty. The seven-rate constructor deliberately differs from the
      defaults factory: it sets outliers to zero and faults to an empty list.
    - **Dependent tests:** `defaultsIncludeOutliersAndAllFaultsInTheSpecifiedOrder`,
      `sevenRateConstructorPreservesItsArgumentsAndDisablesBothNewProblemKinds`,
      `omittedProblemsRemainAbsentInsteadOfEnablingNewDefaults`, the empty and
      partial parsing tests, and the load default/absence test.

43. **Are fault names exact, and which location identifies an invalid or
    repeated array entry?**
    - **Assumption:** Names are compared exactly after JSON string decoding;
      no trimming, case folding, sorting, or silent deduplication is allowed.
      An invalid entry reports its zero-based bracket index, following the
      amendment's example and Amendment 3's indexed paths. For duplicates,
      the second occurrence is the offending entry (including nonadjacent
      duplicates and differently escaped spellings of the same name).
      Duplicate object keys retain question 2's confirmed flexibility about
      syntax versus schema diagnostics.
    - **Dependent tests:** `invalidValues`, its parse/load consumers, the
      escaped-name test, constructor duplicate/name tests, and all sixteen
      ordered-subset tests.

44. **Must the constructor copy a caller-owned list, and what mutation
    failures are required of faults()?**
    - **Assumption:** Yes: retaining a mutable caller list would violate the
      immutable configuration contract once it is passed to `withProblems`.
      Tests check defensive snapshots and mutation attempts which actually
      change a list, including iterator and sublist mutation. No identity
      requirements are imposed on lists or ProblemRates objects. The empty
      list is tested with addition, avoiding no-op mutation ambiguity.
      Null list/element behavior is explicitly NPE; invalid typed String
      entries are IAE. Passing raw, heap-polluted non-String Java lists is
      left untested because the public constructor accepts `List<String>`;
      wrong JSON array-element types are comprehensively tested instead.
    - **Dependent tests:** Constructor defensive-copy and mutation tests;
      parsing immutability tests; `mutatingCallerListCannotChangeConfigurationOrCopies`
      and the other copy tests.

45. **Do the existing number, schema, independence, and copy rules apply to
    the two new components?**
    - **Assumption:** Yes. Outliers follows prior number-valued rates, including
      inclusive endpoints and ordinary exponent forms. Constructor NaN and
      infinities are rejected, and its eight rates are validated independently.
      JSON rounding onto endpoints, numeric underflow, and overflowing
      exponents remain untested as in questions 7, 19, and 39. Faults may be
      empty or any ordered subset independently of all rates and capacity.
      Only the problems object gains these keys; arbitrary field/profile/
      choice/id names remain legal. Existing diagnostics, UTF-8/BOM handling,
      immutable copies (including unchanged assignments), and snapshots apply.
      Fault assignment, outlier generation, feasibility, and statistical
      frequencies belong to spec 14 and are not asserted here.
    - **Dependent tests:** Outlier acceptance/rejection and syntax tests,
      `canonicalConstructorValidatesEveryRateIncludingOutliers`, independence
      tests, `unknownKeys`, arbitrary-name tests, and all copy/I/O tests.

### Amendment 7 build verification (2026-10-24)

- Ran the requested `.\mvnw.cmd -o -q test-compile`, then an `-e` diagnostic
  retry. Both reached `maven-compiler-plugin:3.15.0:testCompile` and failed
  with `java.nio.file.AccessDeniedException` for
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar` while javac closed
  its archive file manager. Logs: `.codex-runs/04-amendment7-test-compile.log`
  and `.codex-runs/04-amendment7-test-compile-detail.log`.
- All five new Java files and their existing test-source dependencies
  compiled successfully with Java release 17 against the actual
  `target/classes` binaries and cached JUnit 5.11.4 API (eleven source files
  total). The existing compiler-probe approach reads class entries in memory
  to avoid the archive-close failure; it does not inspect implementation,
  copy repositories, change permissions, or install dependencies. Probe:
  `.codex-runs/Amendment7CompileProbe.java`; result:
  `.codex-runs/04-amendment7-probe-compile.log` (`compiled=true`). This verifies
  compilation of the added sources, but does not make Maven successful.
- **Question:** Can the calling environment rerun the requested Maven command
  with accessible cached dependencies? **Assumption:** This is an environment
  blocker; production, build configuration, dependencies, and existing tests
  remain unchanged. **Dependent tests:** Full Maven test compilation remains
  unverified. Runtime tests were not requested and were not run.

## Amendment 8: consent questions and fakeRefusal

New coverage is in `ConsentAmendment8Test`, `ProblemRatesAmendment8Test`,
`SimulationConfigAmendment8ParsingTest`, `SimulationConfigAmendment8IoTest`,
and `SimulationConfigAmendment8CopiesTest`, with
`SimulationConfigAmendment8Support` and `amendment-8-consent.json`.
Existing tests, fixtures, and questions are preserved. Expectations come only
from spec 04; no production source was opened, read, searched, or changed.

46. **How are consent names and choice codes compared, and what counts as
    forbidden whitespace?**
    - **Assumption:** Names and codes are compared as exact decoded strings,
      without trimming, case folding, sorting, or Unicode normalization.
      Consent field names follow the same stated nonempty/no-edge-whitespace
      restriction as earlier field and id names. Tests reject space, tab, CR,
      LF, and form feed at either edge, accept Unicode and internal spaces,
      and do not impose an identifier grammar or consult a form. Choice codes
      have only the stated nonempty/distinct constraints, so whitespace-only
      and edge-whitespace codes are accepted and preserved. A supplied code
      must also differ from the other code's default when that other member
      is absent. The precise Unicode whitespace boundary is left untested.
    - **Dependent tests:** `acceptedFields`, `invalidNames`, `invalidConsent`,
      their parse/load/constructor consumers, `exactCodes`,
      `choiceCodesAreExactNonemptyStringsWithoutFieldNameWhitespaceRestrictions`,
      and `constructorRejectsEqualChoiceCodes`.

47. **Which path must diagnose missing fields, equal codes, or duplicate
    field names?**
    - **Assumption:** A missing required member or a cross-member collision
      may identify `consent` or a more specific child. Invalid array entries
      identify their zero-based bracket index; a repeated field identifies
      the second occurrence, following the confirmed Amendment 7 convention.
      Single-value errors require their complete path. Duplicate object keys
      retain question 2's syntax-versus-schema flexibility. All errors require
      source attribution and an explanation; syntax errors also retain their
      cause and failing line. No exact wording or constructor message is
      imposed.
    - **Dependent tests:** `invalidConsent`, `duplicateKeys`, parse/load
      diagnostics, and syntax-error tests in the Amendment 8 classes.

48. **Do consent and fakeRefusal impose cross-setting or form prerequisites?**
    - **Assumption:** No new prerequisite is stated. An absent consent key
      stays absent even with fakeRefusal or a field override named consent;
      consent does not enable problems. Its rate is independent of other
      rates. Explicit overrides and special-value codes are retained without
      resolving generator precedence. FakeRefusal can be selected without
      consent and with all problem rates zero. Only the consent schema and
      accepted fault-name set change; arbitrary field, profile, and choice
      names remain valid. Actual consent simulation, refusal assignment,
      form order/existence checks, and statistical frequencies belong to
      specs 07 and 14 and are not tested here.
    - **Dependent tests:** `omissionDoesNotInferConsentFromFaultsOrAnOverride`,
      `requiredFieldsAloneSupplyOnlyTheDocumentedConsentDefaults`,
      `consentIsIndependentOfOtherProbabilitiesNamesAndOverrides`,
      `fakeRefusalDoesNotRequireConsentOrAnyPositiveProblemRate`, `unknownKeys`,
      and every ordered-subset test containing fakeRefusal.

49. **What numeric precision and immutability conventions apply to Consent?**
    - **Assumption:** The prior number-valued rate conventions apply, including
      inclusive endpoints, ordinary exponent forms, and numeric equality of
      either sign of zero. Constructor validation covers the Java double
      boundaries, NaN, and infinities. JSON values that round onto endpoints,
      underflow, or overflow remain untested as in questions 7, 19, and 39.
      The constructor takes a defensive snapshot of fields: retaining a
      caller-owned mutable list would violate the immutable configuration
      contract. Mutation attempts which change a list must throw
      UnsupportedOperationException, including iterators and views. No list
      or Consent identity requirement is inferred. Raw, heap-polluted Java
      lists are not tested; wrong JSON element types are tested.
    - **Dependent tests:** Consent rate and constructor tests, all consent
      list mutation/snapshot tests, `mutations`, and the copy tests.

50. **Does the new fault default change explicitly configured or legacy
    fault lists, and should older committed tests be rewritten?**
    - **Assumption:** Only the default fault list gains fakeRefusal. Explicit
      arrays preserve exactly their members and order, including empty lists.
      The seven-rate constructor still has no faults. Every existing copy API
      preserves consent, including absence, zero rate, unchanged assignments,
      and clearing problems. Older tests asserting the Amendment 7 three-fault
      default may conflict with Amendment 8 at runtime; they remain unchanged
      as explicitly requested for this run. The new tests assert four defaults.
    - **Dependent tests:** `defaultsAppendFakeRefusalAfterTheThreeExistingFaults`,
      `omittedFaultsInAnyPresentProblemsObjectUseAllFourDefaults`,
      `explicitlySelectedLegacyFaultsDoNotAutomaticallyAcquireFakeRefusal`,
      `sevenRateConstructorStillDisablesEveryFaultIncludingFakeRefusal`, and
      all Amendment 8 copy tests.

### Amendment 8 build verification (2026-10-24)

- Ran the requested `.\mvnw.cmd -o -q test-compile`, followed by an `-e`
  diagnostic retry. Both reached `maven-compiler-plugin:3.15.0:testCompile`
  and failed with `java.nio.file.AccessDeniedException` for
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. Logs:
  `.codex-runs/04-amendment8-test-compile.log` and
  `.codex-runs/04-amendment8-test-compile-detail.log`.
- All six new Java files and their existing test-source dependencies compiled
  successfully with Java release 17 against the actual `target/classes`
  binaries and cached JUnit 5.11.4 API (twelve source files total). The
  existing compiler-probe approach reads class entries in memory to avoid
  the archive-access failure. It does not inspect production implementation,
  copy repositories, alter permissions, or install dependencies. Probe:
  `.codex-runs/Amendment8CompileProbe.java`; result:
  `.codex-runs/04-amendment8-probe-compile.log` (`compiled=true`). This verifies
  compilation of the added sources, but does not make Maven successful.
- **Question:** Can the calling environment rerun the requested Maven command
  with accessible cached dependencies? **Assumption:** The access failure is
  an environment blocker; production, build configuration, dependencies,
  and existing tests remain unchanged. **Dependent tests:** Full Maven test
  compilation remains unverified. Runtime tests were not requested and were
  not run. No commit was created.
