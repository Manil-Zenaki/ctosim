# Disputes — spec 03 (SurveyCTO functions)

## Dispute 1: type of the real form's instancenumber

**Tests:** `SurveyCtoRealFormsTest.realHouseholdRosterCalculatesStringInstanceNumbers`
(all three cases).

**Failure:** `XPath result type ==> expected: <java.lang.String> but was: <java.lang.Integer>`.

**Argument:** the test follows the spec's original acceptance paragraph,
which said the value is the string `"k"`. The spec was wrong about the
engine. A calculate's result is stored through JavaRosa 6.0.0's
`IAnswerData.wrapData(Object, int)`, whose bytecode tests
`instanceof Double` and, when the value is within 1e-9 of a whole number
in the int range, constructs `new IntegerData(int)`, before considering
the declared data type (only BOOLEAN is handled first). So a whole-number
result is stored as `IntegerData` even in a string field, for any function
returning a number, native ones included.

The spec has been amended ("Amendment 1: how the engine stores numeric
results"): the k-th `instancenumber` holds `IntegerData` k.

**Requested change:** expect the `Integer` k (via the value of the node)
instead of the `String` "k". Keep the rest of the test (instance counts,
creation through the engine, every k from 1 to N).

## Adjudication

### Dispute 1: upheld; correct the test

Amendment 1 explicitly requires the k-th `instancenumber` to hold
`IntegerData` whose `getValue()` is the `Integer` k, even though the field
is declared as a string. The acceptance paragraph now requires the number
k and refers to that amendment for storage. The test's `String` expectation
therefore contradicts the current spec. The general rule that number
function results are `Double` does not override the amendment's explicit
rule for stored calculate results.

Changed the expected node value to `Integer.valueOf(k + 1)` and renamed
the test to `realHouseholdRosterCalculatesIntegerInstanceNumbers`.
The existing `assertValue` helper checks both the exact runtime class and
the value. Kept all three instance counts (1, 3, 25), creation through the
engine, and verification of every instance from 1 to N unchanged.
This decision follows the amended spec; no implementation code was read.

Validation attempted: `mvnw.cmd -o -q test` failed during wrapper startup
with a null-array indexing error. Direct offline runs using the cached
Maven 3.9.16 installation and each existing local repository also failed:
the preload repository failed during compilation on `hamcrest-all-1.3.jar`,
and the dispute repository reached test compilation but failed with
`java.nio.file.AccessDeniedException` on `junit-platform-engine-1.11.4.jar`.
Test compilation and execution could not be confirmed in this environment.

## Dispute 2: tests that still treat `pulldata` as a fixed stub (Amendment 3)

Amendment 3 makes `pulldata` a real function: it takes exactly four
arguments ("A call with a number of arguments other than four fails to
evaluate"), reads `<source>.csv` from the media directory, and "is no
longer listed by `stubbedFunctions()`". Spec 01's table now marks its stub
row as superseded. These tests, written before the amendment, still
expect the stub behaviour; each fails because a form calling `pulldata`
with another arity no longer initialises, or because `pulldata` is
expected in `stubbedFunctions()`:

- `FormLoaderFunctionTest`: `stubsAcceptZeroOneManyAndMixedTypeArguments`
  (the `pulldata` cases), `nestedStubsAreDiscoveredAndEvaluated`,
  `onlyReferencedStubsAreReportedOnceInNaturalOrder`,
  `stubbedFunctionSetAndItsViewsCannotBeModified`,
  `nativeAndStubbedFunctionsWorkTogetherInACalculate`;
- `SimulatedSessionContractTest.surveyCtoStubsComposeWithSimulatedFunctionsWithoutCallerConfiguration`;
- `SurveyCtoFunctionContractTest.originalFixedStubsAndUnimplementedConditionalFunctionsKeepTheirValuesAndInventory`.

Requested change: remove `pulldata` from the stub expectations of these
tests (the other stubs and their checks stay as they are). Where a test
needs a SurveyCTO stub, use another one of the fixed stubs; where it
calls `pulldata`, call it with four arguments and expect `""` (a form
loaded from a stream has no media directory).

## Adjudication of the current disputes

### Dispute 1: keep the already corrected integer expectation

Amendment 1 explicitly says the stored value is `IntegerData` and its
`getValue()` returns the `Integer` k. The current test is already named
`realHouseholdRosterCalculatesIntegerInstanceNumbers` and checks
`Integer.valueOf(k + 1)` through an exact-type-and-value assertion. Keep
it unchanged, including counts 1, 3 and 25, engine-created instances, and
the check of every instance. The original string expectation would be
wrong, but it is no longer present.

### Dispute 2: upheld; correct the obsolete pulldata stub expectations

Amendment 3 requires exactly four arguments, an empty string when there
is no media directory, and exclusion from `stubbedFunctions()`. Its final
clarification explicitly supersedes the earlier fixed-stub rule. The
test helpers used here load forms from streams, so the no-media result
is specified rather than an assumption about local attachment files.

- `FormLoaderFunctionTest.stubsAcceptZeroOneManyAndMixedTypeArguments`:
  remove `pulldata` from the parameter source. Arbitrary arities remain
  tested for every other existing fixed or catch-all stub.
- `FormLoaderFunctionTest.nestedStubsAreDiscoveredAndEvaluated`: give
  the nested `pulldata` call four arguments and remove it from the
  expected stub inventory. Keep the nested stub calls and result check.
- `FormLoaderFunctionTest.onlyReferencedStubsAreReportedOnceInNaturalOrder`:
  replace `pulldata()` with `plug-in-metadata()`, updating its position
  in the expected sorted inventory. Preserve deduplication and ordering.
- `FormLoaderFunctionTest.stubbedFunctionSetAndItsViewsCannotBeModified`:
  use `plug-in-metadata()` as the second stub and update the subset's
  lower bound accordingly. All eight mutation checks remain, including
  nonempty head, tail and subset views, and the unchanged-content check.
- `FormLoaderFunctionTest.nativeAndStubbedFunctionsWorkTogetherInACalculate`:
  give `pulldata` four arguments and exclude it from the inventory. Keep
  the expected `simulated:2:3:` result; its final component is the specified
  empty lookup result.
- `SimulatedSessionContractTest.surveyCtoStubsComposeWithSimulatedFunctionsWithoutCallerConfiguration`:
  give `pulldata` four arguments. Keep the expected `simulated::0:::`
  result and all the other composed functions. Amendment 3 explicitly
  applies to simulated sessions too.
- `SurveyCtoFunctionContractTest.originalFixedStubsAndUnimplementedConditionalFunctionsKeepTheirValuesAndInventory`:
  remove only the `pulldata` parameter case. Its four-argument call was
  already valid, but expecting it in the stub inventory was wrong. Keep
  all remaining fixed, conditional and unknown-function cases.

The existing dedicated `SurveyCtoPulldataLookupTest` suite covers invalid
arity and exclusion from the stub inventory. Literal text containing
`pulldata()` in the lexical-decoy test is not a function call and is
unchanged. No production source was read or changed.

### Validation of this adjudication

- Ran `.\mvnw.cmd -o -q test`. Maven reached `testCompile` but failed
  with `java.nio.file.AccessDeniedException` on the cached
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. A retry with
  `-e` confirmed the cause. Logs: `.codex-runs/03-dispute-adjudication-test.log`
  and `.codex-runs/03-dispute-adjudication-test-detail.log`.
- Using the existing diagnostic compiler approach, compiled the four
  disputed test classes, their three test-support classes and a JUnit
  runner with `--release 17` against existing compiled production classes
  and cached dependencies. Compilation succeeded. This approach reads
  dependency class bytes in memory; no repository was copied, drive
  mapped, dependency installed or build configuration changed. Log:
  `.codex-runs/03-disputed-memory-compile.log`.
- Executed all four disputed test classes with JUnit: **230 tests run,
  230 successful, zero failed or skipped**. Log:
  `.codex-runs/03-disputed-junit.log`. This validates the disputed classes
  against the existing compiled implementation; it is not a successful
  full Maven build or full-suite run.

## Dispute 3: Amendment 4 calls evaluated outside the form's expressions

Thirteen tests evaluate a conditional function whose condition depends on
the repeat instance, or `decimal-time()`, by parsing an XPath expression
that is not part of the form (`SurveyCtoTestSupport.evaluate` /
`evaluateAt`, i.e. `XPathParseTool.parseXPath(...).eval(...)`). No
implementation can satisfy them with JavaRosa 6.0.0:

- `XPathFuncExpr.eval` evaluates **every argument before** it dispatches
  to a custom `IFunctionHandler` (only `if`, `coalesce` and
  `indexed-repeat` are evaluated lazily, inside the engine). The
  condition `/data/row/kind = 'odd'`, evaluated once in the call's
  context, designates three nodes and fails with "This field is
  repeated" before any handler runs; a condition such as
  `boolean(/data/row[kind = 'odd'])` evaluates once to `true`. The
  parse tree is built by `XPathParseTool` with no extension point, so a
  call parsed by the test cannot be given per-instance semantics.
- The engine's **built-in functions are matched before custom
  handlers**: a directly parsed `decimal-time(...)` or
  `format-date-time(...)` always runs the engine's own code
  (`decimal-time('06:00:00')` throws "can't be converted to a date").

Per-instance conditions, and the new `decimal-time` / `format-date-time`
behaviour, can only be given to the form's **own** expressions, which
the loader rewrites before the engine parses them (as spec 03 already
does for select functions in Amendment 2, whose last paragraph excludes
"calls typed directly into an XPath expression that is not part of the
form"). The spec now says the same for Amendment 4 (Clarification
(Amendment 4)).

Disputed tests (each assertion that evaluates such a call directly):

1. `SurveyCtoAmendment4ConditionalTest.filtersEachInstanceInsteadOfEvaluatingTheConditionOnce`
2. `SurveyCtoAmendment4ConditionalTest.excludedNonnumericNodesDoNotContaminateAggregation`
3. `SurveyCtoAmendment4ConditionalTest.predicateCanCombineInstanceFieldsAndAnExternalThreshold`
4. `SurveyCtoAmendment4ConditionalTest.predicateCanReferToTheRepeatNodeItself`
5. `SurveyCtoAmendment4ConditionalTest.numericPredicateValuesAreConvertedSeparatelyForEachInstance`
6. `SurveyCtoAmendment4ConditionalTest.stringPredicateValuesAreConvertedSeparatelyForEachInstance`
7. `SurveyCtoAmendment4ConditionalTest.relativeReferencesResolveFromTheCallContextAndThenSelectEachInstance`
8. `SurveyCtoAmendment4ConditionalTest.joinUsesTheSuppliedSeparatorAndDocumentOrderWithDuplicateUnicodeValues`
9. `SurveyCtoAmendment4ConditionalTest.separatorCanBeAnExpression`
10. `SurveyCtoAmendment4ContextTest.nestedRepeatFilteringRetainsTheCallingParentAndGlobalReferences`
    (the `evaluateAt` assertion; the stored `total` assertion is fine)
11. `SurveyCtoAmendment4ContextTest.engineCreatedRepeatCanBecomeIncludedAfterItsAnswersAreEntered`
    (the final direct `count-if` assertion)
12. `SurveyCtoAmendment4RankTest.ranksOnlyIncludedNumericScoresButPositionsCountEveryInstance`
13. `SurveyCtoAmendment4DateTimeTest.decimalTimeReturnsNaNForValuesThatAreNeitherTimesNorDateTimes`
    (the direct `evaluate`; the form is built from the same call)

Requested change: keep every expectation, but observe it through a form
calculation, as the other tests in these classes already do (the
`repeats(expression, rows)`, `calculation(expression)` and
`expressionForm(expression)` helpers put the call in a bind's
`calculate`, and the result is read from the field). For test 7, the
call can be the calculation of `/data/result`, whose relative references
then start from that node. Tests that evaluate directly a call whose
condition is the same for every instance (`true()`, `false()`, numbers,
strings) are not disputed: in that case the condition evaluated once
gives the same inclusion for every instance.
