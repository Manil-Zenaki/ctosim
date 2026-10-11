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

## Adjudication of Dispute 3

**Decision: upheld for all thirteen points; fix the evaluation sites,
keep the expected results.** Amendment 4's "Where these rules apply"
explicitly limits per-instance conditions and the new decimal-time
behavior to the form's own expressions. It separately specifies eager,
once-per-call argument evaluation for externally parsed calls, and native
decimal-time behavior there. This scope rule is sufficient to decide the
dispute; no claim about implementation internals is needed or was checked.

The changed conditional tests read bracketed calculate results, following
the existing fixtures. Brackets preserve strings and avoid confusing raw
XPath Double results with whole-number calculate storage (Amendment 1).
The expected numeric values, ordering, filtering and sentinels are unchanged.

1. **filtersEachInstanceInsteadOfEvaluatingTheConditionOnce:** read the
   existing `/data/result` calculation. Keep all six functions and their
   expected count 2, sum 40, minimum 10, maximum 30, join `10|30`, and rank 2.
   The spec requires those results for the form's per-instance condition.
2. **excludedNonnumericNodesDoNotContaminateAggregation:** put the
   conditional call in the calculation. Keep the independent native
   aggregate over `[kind = 'odd']` as the oracle, with identical bracket
   formatting for comparison. Amendment 4 explicitly delegates aggregate
   semantics to the native function on included nodes only.
3. **predicateCanCombineInstanceFieldsAndAnExternalThreshold:** calculate
   the original condition in the form and retain count 1. The repeat
   fields vary by candidate while `/data/threshold` keeps its call-context
   meaning, as the amendment requires.
4. **predicateCanReferToTheRepeatNodeItself:** calculate the original
   `boolean(/data/row[kind = 'odd'])` condition and retain count 2. A
   reference to the repeat itself must designate the candidate instance
   during the form condition's evaluation.
5. **numericPredicateValuesAreConvertedSeparatelyForEachInstance:**
   calculate the condition in the form and retain count 2 for negative,
   zero, fractional and nonnumeric score inputs. Per-instance XPath
   boolean conversion includes the two nonzero numeric values.
6. **stringPredicateValuesAreConvertedSeparatelyForEachInstance:**
   calculate the condition in the form and retain count 2. Both nonempty
   strings, including `false`, are true under XPath boolean conversion;
   the empty string is false.
7. **relativeReferencesResolveFromTheCallContextAndThenSelectEachInstance:**
   bind the original relative expression to `/data/result` and retain sum
   40. This keeps the same reference anchor and tests the amendment's
   explicit relative-reference rule within its required scope.
8. **joinUsesTheSuppliedSeparatorAndDocumentOrderWithDuplicateUnicodeValues:**
   calculate the original join in the form. Keep all four separators,
   Unicode text, duplicate included values, excluded row and document
   order. These follow the included-node native join rule.
9. **separatorCanBeAnExpression:** calculate the original call in the
   form and retain `10::30`. The separator remains `concat(':', ':')`;
   the common argument rule permits expressions.
10. **nestedRepeatFilteringRetainsTheCallingParentAndGlobalReferences:**
    keep the existing total assertions unchanged. Add `includedCount` to
    each family and its template in `amendment4-nested.xml`, with the
    original relative count expression in its calculate. Read that field
    and retain counts 2 and 1. It is a sibling of `total`, so the relative
    references retain the same parent and global context.
11. **engineCreatedRepeatCanBecomeIncludedAfterItsAnswersAreEntered:**
    parameterize the engine-creation and answer sequence over sum and
    count calculations. Retain sum 47 and count 3 as separate cases,
    each observing its stored result after entering the new instance's
    score and kind. Neither case evaluates an external conditional call.
12. **ranksOnlyIncludedNumericScoresButPositionsCountEveryInstance:**
    create a calculation for each original position and read its result.
    Keep every input row and expected rank, including excluded positions,
    nonnumeric values, singleton repeats, numeric ordering and ties.
    Amendment 4 retains original positions and the rank-index rules.
13. **decimalTimeReturnsNaNForValuesThatAreNeitherTimesNorDateTimes:**
    use `calculation(...)` and assert the stored result is Double NaN,
    retaining all seven invalid inputs. The amendment and confirmed
    assumption 33 require NaN for these inputs in form expressions;
    native external evaluation is not the required observation site.

Direct calls with constant conditions, native comparison calls, and
unrelated tests remain unchanged. The previously adjudicated integer
storage expectation (Dispute 1) and pulldata corrections (Dispute 2)
are already present and remain supported by Amendments 1 and 3.
No production source was read or changed. Build configuration and
dependencies remain unchanged; compiled classes were used for validation.

### Validation

- `.\mvnw.cmd -o -q test` failed at `testCompile`. A diagnostic retry
  with `-e` confirmed `java.nio.file.AccessDeniedException` for
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. Logs:
  `.codex-runs/03-dispute3-maven-test.log` and
  `.codex-runs/03-dispute3-maven-detail.log`.
- The existing diagnostic compiler successfully compiled all seven
  Amendment 4 Java files, three unchanged test support/dependency files
  and the JUnit runner with `--release 17`, using cached dependency class
  bytes and existing compiled production classes. No Maven repository
  was copied or changed. Log: `.codex-runs/03-dispute3-compile.log`.
- The JUnit run discovered and executed **266 cases: 20 passed and 246
  failed**, with no skipped or aborted tests and no failed containers.
  The extra case is the separate engine-created-repeat count case.
  Failures against the existing compiled implementation include repeated
  condition evaluation, native decimal-time conversion errors, locale
  formatting and other Amendment 4 behavior. These failures do not
  justify weakening the amended spec's expectations. Log:
  `.codex-runs/03-dispute3-junit.log`.
- Compilation and test discovery/execution are confirmed independently;
  a successful full Maven build and passing runtime suite are not.

## Dispute 4: tests superseded by Amendment 4, and its two engine limits

After the implementation of Amendment 4, these tests fail; each
contradicts the amended spec.

1. `SurveyCtoScalarFunctionsTest.hashIsLowercaseSha256OfUtf8ValuesSeparatedByUnitSeparator`
   and `SurveyCtoScalarFunctionsTest.hashAcceptsManyArgumentsAndIncludesEveryArgument`
   expect SHA-256 over values joined with U+001F. Amendment 4 replaces
   that rule: `hash` is MD5 of the values concatenated with no
   separator (`hash('abc')` = `900150983cd24fb0d6963f7d28e17f72`, the
   value SurveyCTO exported). `SurveyCtoAmendment4HashTest` covers the
   new rule. Requested: remove them, or change their expectations to the
   MD5 rule.
2. `SurveyCtoFunctionContractTest.originalFixedStubsAndUnimplementedConditionalFunctionsKeepTheirValuesAndInventory`
   expects `count-if`, `sum-if`, `join-if`, `min-if`, `max-if` and
   `rank-index-if` to stay catch-all stubs returning `""` and listed by
   `stubbedFunctions()`. Amendment 4 implements them and removes them
   from `stubbedFunctions()` (`implementedConditionalFunctionsAreNotReportedAsStubs`
   checks this). Their call there, `name('data', 1, true(), /data/text)`,
   now has a wrong number of arguments and fails to evaluate, as
   Amendment 4 requires. Requested: drop the six conditional functions
   from that parameter list; the other stubs keep their cases.
3. `SurveyCtoAmendment4ConditionalTest.predicateCanReferToTheRepeatNodeItself`
   uses the condition `boolean(/data/row[kind = 'odd'])`. The engine
   binds a step of a path to the instance being checked only when that
   step has no predicate of its own, so `/data/row[kind = 'odd']`
   designates rows 1 and 3 whatever the instance, and `boolean()` of two
   nodes fails in the engine ("This field is repeated:
   /data/row[1];/data/row[3]"). The spec now states this limit
   (Amendment 4, "Two further limits"). Requested: express the same
   intent with a predicate below the repeat's step, which the engine
   binds per instance, e.g. `count(/data/row/kind[. = 'odd']) > 0`
   (expected count 2), or remove the test.
4. `SurveyCtoAmendment4DateTimeTest.decimalTimeReturnsNaNForValuesThatAreNeitherTimesNorDateTimes`
   (as adjudicated in Dispute 3) asserts that the field calculated as
   `decimal-time('garbage')` stores `Double.NaN`. The engine stores no
   value for a `NaN` result, and so does SurveyCTO: in
   `samples/coverage3/wide/ctosim coverage test 3_WIDE.csv`,
   `f_not_a_number` (`number('abc')`) is empty in all four rows. The
   spec now says so (Amendment 4, "Two further limits"). Requested:
   assert that the field has no value, and observe `NaN` through the
   expression itself (for example a second calculation
   `string(decimal-time('garbage'))`, which stores `NaN`).
