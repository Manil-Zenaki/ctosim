# Simulated sessions: questions and test assumptions

Expectations are derived from `06-simulated-sessions.md` and the explicitly
referenced session contract in `01-form-loader.md`. No production source was
read or modified. Tests use the specified API and JavaRosa's public session,
answer, navigation, instance, and post-processing APIs. They do not install
function handlers, preload handlers, or manually initialize returned sessions.

1. **Question:** Does nanosecond precision apply to JavaRosa date answers as well
   as to `SimulatedClock`? What rounding should conversion use?
   **Assumption:** The explicit nanosecond requirement applies to the clock and
   to measuring elapsed duration. Engine date/time assertions use whole-second
   clock values and compare their instants exactly in the JVM default zone.
   They do not prescribe subsecond engine date serialization or rounding.
   Fractional opening times and advances are tested through numeric `duration()`
   without requiring nanoseconds to survive a Java `Date` answer.
   **Tests:** `SimulatedClockTest`, `durationFloorsElapsedNanosecondsToWholeSeconds`,
   `durationAccumulatesSmallAdvancesBeforeRounding`, and tests using `assertTime`.

2. **Question:** What happens when a valid nonnegative duration overflows
   `LocalDateTime`, or when callers change the default zone during a session?
   **Assumption:** These cases are unspecified and are not tested. Large
   advances remain within `LocalDateTime`'s range. Time-zone tests set the zone
   before loading/opening and restore it in `finally`; they cover UTC, positive
   fractional offsets, large positive offsets, and negative winter/summer
   offsets. The tests do not modify the operating system clock or zone.
   **Tests:** `largeValidDurationsDoNotOverflowAnIntSecondCounter`,
   `durationSupportsMoreThanAnIntOfElapsedSeconds`,
   `functionsActionsAndPreloadsUseTheJvmDefaultZone`.

3. **Question:** Does elapsed duration mean elapsed local clock arithmetic or
   elapsed time between zone-converted instants across a DST transition?
   **Assumption:** "Whole seconds elapsed on the clock" refers to the
   `LocalDateTime` clock advanced by `Duration`, independently of the default
   zone's DST offset changes. A three-hour advance yields 10,800 seconds.
   Conversion tests avoid nonexistent and ambiguous local start/end times;
   how such times should be resolved is left open.
   **Tests:** `durationUsesClockArithmeticAcrossDaylightSavingTransitions`.

4. **Question:** Must changing the clock automatically update existing calculated
   answers? Which operations trigger evaluation?
   **Assumption:** The spec promises the current clock value when the engine
   evaluates an expression, not a background recalculation on `advance`.
   Fixtures explicitly depend on an answered `tick` field to trigger native
   dependency recalculation. Clock-sensitive constraints are tested when
   submitting an answer. Reading an already stored answer is not treated as
   another evaluation. `end` must read the clock at post-processing even when
   no answer/recalculation occurred after the last clock advance.
   **Tests:** Time recalculation tests, `clockDependentBindPropertiesRecalculateWhenTheirDependencyChanges`,
   `constraintReadsTheAdvancedClockWhenAnAnswerIsValidated`,
   `endReadsTheClockAtPostProcessingEvenWithoutRecalculation`.

5. **Question:** Which setvalue lifecycle events and XPath evaluation contexts
   are included, and must unrelated direct expression evaluation be overridden?
   **Assumption:** First-load setvalue actions are part of the initialized
   session (the confirmed spec 01 convention). The suite checks each named
   function when referenced only in a first-load action, as well as in a form
   containing calculations and metadata. It also uses JavaRosa's native
   `xforms-value-changed` setvalue action inside an input to evaluate functions
   after clock advances and answers, including nonzero action `duration()`.
   This event/fixture convention was checked with an ordinary JavaRosa session
   through public APIs. The suite does not require support for new action event
   types or direct XPath evaluation outside the form. All assertions of
   simulated functions observe actual form fields/properties.
   **Tests:** `functionReferencedOnlyBySetvalueUsesTheSimulatedSession`,
   `valueChangedSetvalueActionsUseLiveTimeAndReproducibleRandomness`,
   `firstLoadSetvalueActionsReadTheSimulatedClock`,
   `simulatedFunctionsAreAvailableInEveryBooleanBindAttribute`.

6. **Question:** Is a particular PRNG, sequence ordering, uniform distribution,
   or number of draws per UUID mandated?
   **Assumption:** No algorithm, golden outputs, exact draw counts, cross-form
   sequence equivalence, statistical uniformity, or UUID bit-frequency target
   is prescribed. Fixed-seed batches test the explicit half-open range,
   finiteness, UUID format, variation, replay, and isolation. They do not impose
   distribution thresholds absent from this spec. Clock changes must not
   affect random values for the same form with the same expression-evaluation
   sequence because the sequence is determined by the seed alone.
   **Tests:** `SimulatedSessionRandomTest`, especially
   `seedAloneDeterminesRandomnessRegardlessOfClockDateOrAdvances`.

7. **Question:** Are all `long` seeds valid, and can differences in high bits be
   ignored? How strictly should accidental random collisions be checked?
   **Assumption:** With no documented seed restrictions, zero, negative,
   minimum, maximum, and high-bit-differing seeds are legal. The explicit
   different-seed rule applies to these seeds, including `0` versus `2^32`
   and `Long.MIN_VALUE`. Different-seed comparisons require the random
   calculation, UUID calculation, first-load random/UUID actions, once-random
   value, and instance ID to differ. An accidental collision is possible but
   overwhelmingly unlikely, as the spec acknowledges. Within a single sequence,
   variation is required across a batch; every pair is not required to differ.
   **Tests:** `instanceIdIsUuidPrefixedAndFormattedForEverySeed`,
   `sameSeedReproducesAnEntireSequenceOfRandomNumbersAndUuids`,
   `differentSeedsChangeEverySpecifiedRandomSource`, random/UUID variation tests.

8. **Question:** Does `once(random())` specify which draw is retained, or what
   happens when a caller clears or directly edits the calculated node?
   **Assumption:** Only retention of the first initialized value through normal
   answers, dependency recalculation, clock advances, and post-processing is
   asserted. No particular first draw or clearing/editing behavior is assumed.
   **Tests:** `onceRandomKeepsItsInitialValueThroughAnswersAdvancesAndPostProcessing`
   and whole-instance reproducibility tests.

9. **Question:** What does isolation mean if the caller deliberately supplies
   the very same mutable clock object to two sessions? Is thread safety required?
   **Assumption:** Intentional clock aliasing and concurrency are unspecified.
   Isolation tests supply separate clocks and operate sequentially, including
   interleaving two sessions' operations, opening additional sessions, and
   retaining 32 answered sessions. No capacity or performance limit is imposed.
   **Tests:** `SimulatedSessionIsolationTest`,
   `identicallyDrivenSessionsHaveIdenticalValuesAtEveryNode`.

10. **Question:** Does "identical instances" require byte-identical XML output,
    attribute ordering, or equality of engine object identities?
    **Assumption:** The spec defines equality as every node having the same
    value. Snapshots traverse every primary-instance node, including nested
    `meta/instanceID`, and compare paths, multiplicities, nulls, and typed
    values. Dates are copied when taking snapshots. Controllers, instances,
    and nodes are nevertheless distinct objects. XML serialization formatting
    and namespace-prefix choices are not tested. Replay comparisons cover
    initialization, each driven step, and post-processing, across both shared
    and separate loads, including the path and stream overloads.
    **Tests:** `identicallyDrivenSessionsHaveIdenticalValuesAtEveryNode`,
    `pathAndStreamLoadsOfTheSameFormProduceIdenticalSimulatedInstances`,
    `simulatedSessionsHaveTheirOwnControllersInstancesAndNodes`, interference tests.

11. **Question:** Must post-processing run automatically upon navigation, update
    `end` repeatedly, or resolve conflicts with metadata defaults/calculations?
    **Assumption:** The specified `FormDef.postProcessInstance()` call is used
    explicitly once per session. `end` is initially unset; `start` and
    `instanceID` remain stable through normal answers and finalization. Fixtures
    use initially empty metadata fields of appropriate types and the stated
    preload parameters. Repeated post-processing, conflicting defaults,
    calculate/preload precedence, and metadata in newly created repeats are
    left open. The `uid` bind omits `jr:preloadParams`, as no parameter is given.
    **Tests:** Metadata lifecycle tests in `SimulatedSessionTimeTest`,
    `SimulatedSessionPreloadTest` (each preload without any function references),
    and `postProcessingOneSessionLeavesOtherSessionsEndUnset`.

12. **Question:** What exact guarantees apply to ordinary sessions' native
    timestamps and identifiers after simulated sessions have been used?
    **Assumption:** Preserve spec 01: current timestamps are bracketed around
    engine operations with a one-second precision tolerance; dates accept
    either bracketing local date across midnight. Ordinary duration remains
    zero. Native uid strings are nonblank and vary across a batch, without
    imposing the simulated-session UUID prefix/version contract. Repeatedly
    opening an identical simulation must not reset native randomness. These
    checks assume the real clock does not jump during a single operation.
    **Tests:** `ordinarySessionsOpenedBeforeAndAfterSimulationKeepNativeTimeAndDuration`,
    `openingSimulationsDoesNotResetNativeRandomnessToTheirSeed`,
    `durationRemainsListedAfterOpeningAndUsingSimulatedSessions`.

13. **Question:** Are time/random function names recognized as XPath syntax or
    replaced wherever their text occurs? Are extra arguments supported?
    **Assumption:** Literal strings containing `today()`, `now()`, `duration()`,
    `random()` or `uuid()` retain their contents, including Unicode; ordinary
    XPath nesting/composition remains supported. Only the zero-argument forms
    listed in spec 06 are tested for the simulated overrides. Handling of
    unsupported argument counts and types is left open.
   **Tests:** `nativeFunctionNamesInsideStringLiteralsAreNotRewritten`,
   `simulatedFunctionsComposeWithNativeXPathOperations`.

## Validation

- `mvnw.cmd -o -q test-compile` passed for the complete test source tree.
- The 137 new test cases were also executed: 2 assertion failures and 135
  errors, all due to the expected `UnsupportedOperationException` stubs. The
  two assertion failures expect `NullPointerException` at the specified null
  entry points. No test was skipped.
- A temporary, untracked probe exercised the fixtures through ordinary public
  session APIs and verified initialization, dependent recalculation,
  `once(random())` retention, post-processing, and the native value-changed
  setvalue event. No production implementation was inspected.
- The Windows environment required mapping the workspace to `S:` with `subst`
  and running the wrapper there. `MAVEN_USER_HOME` used the existing
  `.tools/preload-maven-home-link`; `MAVEN_OPTS` pointed `maven.repo.local` at
  the offline cache copy `.tools/simulated-sessions-maven-repository` using
  its `S:` path. This avoids the wrapper's null-Target error and Java's
  `AccessDeniedException` when canonicalizing the original long JAR paths.
  No wrapper, POM, production source, or dependency versions were changed.

## Dispute adjudication validation (2026-10-08)

- **Question:** Can the wrapper's existing Maven distribution and cached
  dependency JARs be made accessible to the build account?
- **Assumption:** This is an environment blocker, not evidence about the
  tests or implementation. `mvnw.cmd -o -q test` failed before Maven started
  because the wrapper attempted a distribution download and could not
  connect. The existing `.tools/apache-maven-3.9.16/bin/mvn.cmd` was then
  invoked directly with `-o -q test`; compilation failed on the cached
  `hamcrest-all-1.3.jar`. Diagnostic retries with `-e` reached `testCompile`
  but reported `java.nio.file.AccessDeniedException` for the cached
  `apiguardian-api-1.1.2.jar`. No repository copies, drive mappings, or
  dependency installations were attempted. The earlier validation above
  does not establish compilation of this adjudication's change.
- **Tests:** The four corrected `now()` cases of
  `simulatedFunctionsAreAvailableInEveryBooleanBindAttribute`; the full
  test suite could not run, and test compilation remains unverified.

## Amendment 2: device properties (2026-10-09)

The new `SimulatedSessionDevice*Test` classes cover Amendment 2 only. Existing
tests and fixtures are retained unchanged. Expectations come from Amendment 2,
the unchanged clock/random/session rules it explicitly incorporates, and spec
01's ordinary-session property values. No production source was read or changed.

14. **Question:** Does "every parameter" include application-specific, Unicode,
    and differently cased property names? Are values normalized?
    **Assumption:** The specified `properties.get(p)` lookup uses exact keys
    and values. Tests include the five named properties, an application-specific
    key, a Unicode key, and `USERNAME` distinct from `username`. Nonempty values
    retain leading zeros, spaces, punctuation, Unicode, and function-like text.
    Unused keys (including an empty key) are accepted, but the tests do not
    prescribe how an empty or omitted XML preload parameter is parsed.
    **Tests:** `SimulatedSessionDevicePropertyTest`, particularly
    `eachPropertyWorksWithoutAnyUsernameFunctionReference`,
    `propertyStringsArePreservedWithoutTrimmingParsingOrExpressionEvaluation`,
    `uppercaseUsernameIsADistinctPropertyAndDoesNotSupplyUsernameFunction`, and
    `unusedPropertiesAreAcceptedAndCannotSupplyMissingPreloads`.

15. **Question:** Must an explicitly supplied empty property string be stored
    as a non-null empty answer object, or can JavaRosa normalize it to null?
    **Assumption:** The answer-object representation of explicit empty strings
    is not specified. Empty property preloads accept null or an empty string,
    while missing keys strictly require a null answer object. For `username()`
    the tests observe `concat('[', username(), ']')`, which must yield `[]`
    for missing or explicitly empty usernames. This checks the XPath result
    without imposing storage details or allowing a default username fallback.
    **Tests:** `explicitEmptyPropertyValuesAreAcceptedWithoutInventingAFallback`,
    `explicitlyEmptyUsernameReturnsTheEmptyStringInCalculationsAndActions`,
    `eachMissingPropertyHasNoAnswerEvenWhenOtherPropertiesArePresent`, and
    `emptyMapDoesNotAcquireTheTwoArgumentOverloadsDefaultUsername`.

16. **Question:** Is the new username value required in all form expression
    contexts, including a form with no property preload at all?
    **Assumption:** The existing scope for bind attributes and setvalue actions
    applies to the new `username()` behavior. Tests cover calculation, relevant,
    required, readonly, constraint, first-load actions, and value-changed actions,
    including forms referencing `username()` only in one such location.
    Direct XPath evaluation outside the form and unsupported arities are not
    prescribed. Recalculation uses an explicitly referenced `tick` input.
    **Tests:** `SimulatedSessionDeviceExpressionTest` and the username assertions
    in `SimulatedSessionDeviceIsolationTest`.

17. **Question:** How broadly does copying the map at session opening protect
    against subsequent caller mutations?
    **Assumption:** Replacing, removing, clearing, or adding entries after opening
    cannot affect stored preloads or later username evaluations. A null inserted
    into the original map after opening must likewise not affect that session:
    null rejection applies to the map being supplied at opening. Reusing a mutable
    map for a later session takes a new snapshot. Concurrent mutation during
    opening, custom maps with nonstandard lookup semantics, and thread safety
    remain unspecified and are not tested.
    **Tests:** `callerMapChangesCannotAlterPreloadsOrSubsequentUsernameEvaluations`,
    `addingUsernameToAnOriginallyEmptyMapDoesNotChangeTheOpenedSession`,
    `eachSessionCopiesTheSameMutableMapAtItsOwnOpeningTime`, and
    `openingASessionDoesNotModifyTheCallersMap`.

18. **Question:** Must null entries be rejected even if the form never uses
    their keys? What is the precedence when both clock and properties are null?
    **Assumption:** The explicit rejection of a map containing any null key or
    value includes unused entries. Each invalid input is tested separately;
    exception precedence, messages, and atomicity after a failed opening are
    not specified and are not asserted.
    **Tests:** `nullPropertiesAreRejected`, `nullKeyIsRejectedEvenAlongsideAValidUsername`,
    `nullValueIsRejectedEvenForAnUnusedProperty`, and
    `threeArgumentOverloadStillRejectsANullClock`.

19. **Question:** Can a property map's contents or iteration order change the
    seeded random sequence?
    **Assumption:** The unchanged seed-alone rule still applies when form
    evaluations are driven identically. Equal maps with different insertion
    orders must replay identically. Changing property values cannot alter random
    draws in the existing `session.xml` fixture, whose random expressions have
    no property-dependent branches. No cross-form draw order or PRNG algorithm
    is imposed. Different-seed assertions retain the spec's overwhelmingly
    probable distinction, without imposing statistical distributions.
    **Tests:** `equalPropertyMapsProduceIdenticalInstancesAcrossLoadsAndMapIterationOrders`,
    `changingOnlyPropertiesCannotChangeTheSeededRandomSequence`,
    `differentSeedsStillChangeRandomSourcesWhenPropertiesAreEqual`, and
    `interleavedPropertySessionsCannotChangeAnotherSessionsAnswersClockOrRandomness`.

20. **Question:** Does default-overload equivalence cover just properties or
    the entire simulated session?
    **Assumption:** "Behaves as" requires equality of every instance node under
    identical driving, including timestamp and random fields. Both the new
    device fixture and the existing time/random fixture are checked from
    initialization through answers, clock advances, and post-processing.
    Explicit empty maps intentionally do not receive the default username.
    **Tests:** `defaultOverloadEqualsExplicitDefaultPropertiesForTheWholeSession`,
    `explicitPropertiesDoNotLeakToEarlierOrLaterTwoArgumentSessions`, and
    `emptyMapDoesNotAcquireTheTwoArgumentOverloadsDefaultUsername`.

21. **Question:** What should ordinary sessions do with arbitrary property
    parameters beyond the five listed in spec 01?
    **Assumption:** Spec 01 leaves those situations unspecified. The ordinary
    isolation test removes only the three extra property binds from the new
    fixture before loading it; it asserts `simulated` for username and null for
    the four named device properties. It tests sessions opened before and after
    explicit-property sessions, on shared and separate form loads. Custom-property
    expectations are asserted only for simulated sessions.
    **Tests:** `ordinarySessionsBeforeAndAfterCustomSessionsKeepSpec01Properties`.

### Amendment 2 validation

- `mvnw.cmd -o -q test-compile` was attempted before additions and failed during
  test compilation on the existing cached `commons-io-2.5.jar`.
- After adding the tests, `mvnw.cmd -o -q -e test-compile` confirmed
  `java.nio.file.AccessDeniedException` for
  `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`, in the JDK ZIP filesystem's
  `toRealPath` call. The log is `.codex-runs/device-properties-test-compile.log`.
- A diagnostic retry with `-Dmaven.compiler.fork=true` also failed on the same
  dependency-access exception and reported unresolved production class symbols
  in existing tests. Its log is `.codex-runs/device-properties-fork-compile.log`.
  Compilation of the new tests therefore remains unverified, and no test-run
  result is claimed. This environment blocker must be resolved before the
  requested compile check can pass.
- No repository copies, drive mappings, dependency installations, production
  edits, POM edits, or changes to existing tests were made. Temporary logs are
  confined to `.codex-runs/`.
