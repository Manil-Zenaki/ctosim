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
