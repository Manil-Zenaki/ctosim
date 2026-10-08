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
