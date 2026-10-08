# FormLoader test questions and assumptions

Expectations come from `01-form-loader.md`. Only the three named public API
stub signatures were inspected; no production implementation was read.

1. **Question:** Does “loading does not depend on ... the current date” also
   require freezing native `today()` results?
   **Assumption:** No. The explicit requirement to preserve native `today()`
   behavior means its result follows the current local date. Metadata and
   deterministic calculations must not depend on ambient locale/time zone.
   No clock-injection API is specified, so the suite does not manipulate the
   machine clock or claim to prove invariance across different calendar dates.
   **Tests:** `loadingAndDeterministicCalculationsIgnoreDefaultLocaleAndTimeZone`
   and `todayRemainsANativeDateAndIsNotReplacedWithAnEmptyString` (accepts either
   date bracketing evaluation to tolerate midnight).

2. **Question:** Which whitespace must title trimming remove?
   **Assumption:** Test ordinary spaces, tabs, CR and LF only; preserve internal
   double spaces and non-ASCII text. NBSP and other Unicode space characters
   are not specified, so their trimming is not tested.
   **Tests:** `titleIsDecodedAndTrimmedWithoutCollapsingInternalWhitespace`,
   `emptyAndWhitespaceOnlyTitlesAreEmpty`.

3. **Question:** Are empty source names and empty metadata attributes legal?
   **Assumption:** A non-null source name is returned verbatim, including the
   empty string and surrounding spaces. A present version is a string even
   when empty; only an absent version is null. An empty `id` is left untested
   because the spec only explicitly rejects a missing `id` attribute.
   **Tests:** `streamSourceNameIsPreservedExactly`,
   `aPresentButEmptyVersionIsPreservedAsAnEmptyString`, `absentVersionIsNull`.

4. **Question:** Does `h:html` mean the literal prefix `h` or the XHTML expanded
   name, and how should other namespace errors be treated?
   **Assumption:** XML prefixes are aliases: another prefix bound to XHTML is
   valid, while an `html` element in an unrelated namespace is not. The suite
   does not impose additional namespace-repair rules.
   **Tests:** `namespacePrefixesDoNotChangeMetadata`,
   `anHtmlLocalNameInTheWrongNamespaceIsNotAnXform`.

5. **Question:** What diagnostic wording and cause depth are required?
   **Assumption:** Do not prescribe punctuation, wording, locale, or an exact
   wrapper depth. Messages must contain the supplied source and explanatory
   text beyond that source. The automated check is structural; it cannot fully
   assess whether prose explains the specific failure well. Unreadable paths
   must appear as supplied; for errors in readable files, the basename is
   sufficient, and a full path also satisfies that assertion. Injected
   `IOException` objects must survive by identity somewhere in the cause chain.
   XML/XPath validation may detect errors directly, so those tests do not
   require an underlying cause where none necessarily existed.
   **Tests:** All error tests using `assertSourceAndExplanation`, especially
   `ioFailuresAreWrappedAndTheOriginalFailureIsRetained`.

6. **Question:** Does caller ownership of an input stream also apply on failure,
   and must every failed input be consumed to EOF?
   **Assumption:** A failed load must not close the caller's stream. Complete
   consumption is asserted only for successful loads; an unreadable or invalid
   stream may fail early. Short reads and zero `available()` are valid stream
   behavior and must not truncate a successful read.
   **Tests:** `streamIsConsumedThroughTrailingWhitespaceWithoutBeingClosed`,
   `oneByteReadsDoNotCorruptMultibyteUtf8Characters`,
   `invalidStreamIsRejectedAtLoadTimeWithItsSourceAndReason`,
   `ioFailuresAreWrappedAndTheOriginalFailureIsRetained`.

7. **Question:** May a loaded form require the original source when opening a
   subsequent session?
   **Assumption:** “Loaded form” and “reads the stream to its end” imply the
   definition is available after the caller closes the stream or removes the
   source file. Session creation should not reopen that source.
   **Tests:** `sessionsCanBeOpenedAfterTheCallerClosesTheSourceStream`,
   `sessionsUseTheLoadedDefinitionEvenAfterItsFileIsRemoved`.

8. **Question:** Does “any number of sessions” require concurrent/thread-safe
   session creation or a particular capacity/performance limit?
   **Assumption:** Neither is specified. Test 64 simultaneously retained,
   independently answered sessions created sequentially; impose no timing
   threshold, concurrency requirement, or arbitrary maximum form size.
   **Tests:** `manySessionsRemainUsableAndRetainTheirOwnAnswers` and the focused
   session isolation tests.

9. **Question:** Which representations count as the specified stub return types,
   and are functions absent from the form required to be callable too?
   **Assumption:** Query the session's public JavaRosa evaluation context and
   require a Java string for XPath strings and a `Number` with value zero for
   numeric stubs, without fixing the numeric wrapper class. Every directly
   evaluated stub is also referenced by its fixture. Unreferenced-function
   availability is left open. “Any type” covers strings, numbers, booleans,
   nonempty/empty node sets and dates; argument counts include 0, 1, mixed 6,
   and 32. No statistical or seed tests apply to these fixed return values.
   **Tests:** `stubsAcceptZeroOneManyAndMixedTypeArguments`,
   `nestedStubsAreDiscoveredAndEvaluated`.

10. **Question:** What counts as a function reference, and do body predicates
    need to work if JavaRosa evaluates them during parsing?
    **Assumption:** Syntactic XPath calls count, even in an unchosen branch;
    quoted strings, comments, labels, titles and ordinary instance text do not.
    The explicitly listed bind attributes, setvalue values, body `ref`, body
    `nodeset` and choice-filter predicates must all be scanned. A valid body
    predicate using a stub must be loadable even if JavaRosa checks it early.
    The tests do not specify extension attributes outside the listed XPath
    sites or malformed/undeclared namespace-qualified function names.
    **Tests:** `everySpecifiedExpressionLocationContributesToTheStubInventory`,
    `functionsInUnchosenBranchesAreStillReferences`,
    `functionNamesInsideStringsLabelsDataAndCommentsAreNotReferences`,
    `whitespaceBeforeAnOpeningParenthesisDoesNotHideAFunction`.

11. **Question:** What exception signals an attempted set mutation, including
    mutation through sorted views?
    **Assumption:** Standard Java collection behavior: a mutating operation
    that would change the returned set throws `UnsupportedOperationException`.
    Tests do not require no-op mutations (such as removing an absent element
    or clearing an already-empty set) to throw. No identity, live-view, or
    comparator-object requirement is imposed; actual iteration order is tested.
    **Tests:** `stubbedFunctionSetAndItsViewsCannotBeModified`,
    `anEmptyStubbedFunctionSetCannotBePopulated`,
    `onlyReferencedStubsAreReportedOnceInNaturalOrder`.

12. **Question:** How can an unreadable-file failure be reproduced portably?
    **Assumption:** A directory supplied as a form-file path is unreadable as
    form content. Test that and a missing path, plus injected stream I/O errors.
    Do not rely on OS-specific ACL changes or read-permission flags that can
    be ignored by privileged users.
    **Tests:** `directoryCannotBeReadAsAFormFile`,
    `missingPathIsReportedWithItsFullPath`,
    `ioFailuresAreWrappedAndTheOriginalFailureIsRetained`.

13. **Question:** Does an initialized session include first-load setvalue actions
    and subsequent native dependency recalculation?
    **Assumption:** Yes, as part of the specified initialized JavaRosa session.
    Tests use `odk-instance-first-load` and the public `answerQuestion` API.
    They do not manually initialize the production session or register handlers,
    since doing so could conceal loader failures.
    **Tests:** `setvalueActionsCanCallStubsInEachInitializedSession`,
    `recalculationInOneSessionDoesNotChangeAnotherInstance`,
    `stubbedConstraintRunsWhenAnAnswerIsSubmitted`.

14. **Question:** Are `FormLoadException` constructors expected to follow standard
    Java exception-constructor semantics?
    **Assumption:** The message-only constructor preserves its message and has
    no cause; the message/cause constructor preserves both arguments.
    **Tests:** `exceptionConstructorsPreserveMessageAndCause`.

## Amendment 2: metadata preload questions and assumptions

These additions concern only Amendment 2. No production source was inspected
for this run. Existing tests and the earlier questions above are preserved.
The tests do not install property managers, register preload handlers, or
manually initialize a returned session.

15. **Question:** Does preserving native timestamp behavior require `end` to
    contain a timestamp immediately upon session creation?
    **Assumption:** No. "Keep the engine's native behaviour" includes its
    lifecycle: JavaRosa 6.0.0 initializes `start` on creation, leaves `end`
    unset, and fills `end` during native instance post-processing. Tests call
    the returned session's public `FormDef.postProcessInstance()` to check
    this lifecycle; they do not inject or configure a preloader. This native
    lifecycle was checked in the cached JavaRosa dependency, not repository
    production code. No automatic finalization at session creation is assumed.
    **Tests:** `endTimestampIsUnsetUntilNativePostProcessing`,
    `endTimestampUsesTheCurrentTimeDuringNativePostProcessing`,
    `nativePreloadsAlsoWorkThroughThePathOverload`,
    `allPreloadsCoexistInAnInitializedSessionWithoutCallerConfiguration`,
    `postProcessingMixedMetadataPopulatesEndWithoutChangingStartOrUid`,
    `postProcessingOneSessionDoesNotPopulateEndInOtherSessions`.

16. **Question:** What timestamp precision, serialization, and clock tolerance
    are required?
    **Assumption:** A `dateTime` field has a Java date/time answer whose instant
    is near the operation that populates it. Tests bracket that operation with
    the system clock and allow one second for precision loss. They impose no
    timezone, textual format, exact instant, or inequality between rapidly
    opened sessions. As with other wall-clock tests, the machine clock is
    assumed not to jump during a single operation. No clock or seed is set.
    **Tests:** All tests using `FormLoaderPreloadTestSupport.assertCurrentTimestamp`,
    especially `startTimestampUsesTheCurrentTimeWhenEachSessionIsInitialized`.

17. **Question:** Must a uid have a particular UUID prefix/version/format, be
    reproducible, or satisfy a statistical distribution?
    **Assumption:** Only a nonblank string identifier and per-session native
    generation are required. Tests impose no prefix, length, UUID version,
    fixed seed, distribution, or pairwise uniqueness requirement. A batch of
    16 sessions must contain more than one identifier, detecting a fixed value
    or an identifier generated once at load time; accidental equality across
    the entire batch of native random identifiers is negligible. Subsequent
    session creation must preserve identifiers in already-open sessions.
    **Tests:** `uidWithoutPreloadParamsHasANonemptyStringValueImmediately`,
    `uidIsGeneratedPerSessionInsteadOfCopyingOneIdentifierFromTheLoadedForm`,
    `nestedUidBindIsInitializedAlongsideTheTopLevelUid`, and combined/path uid
    checks using `FormLoaderPreloadTestSupport.identifier`.

18. **Question:** What if a preloaded node already has a nonempty default,
    has its own calculate expression, or uses an unlisted preload parameter?
    **Assumption:** The amendment does not specify precedence or error handling
    for these cases, so no expectation is added. Fixtures use initially empty
    metadata nodes, string types for properties/uids and dateTime for timestamps.
    The uid fixture omits `jr:preloadParams`, as the amendment specifies none.
    No cases for unknown properties, missing property parameters, or unknown
    preload types are tested.
    **Tests:** All three new preload test classes use these fixture conventions.

19. **Question:** Do preload semantics depend on instance field names, nesting,
    body controls, or the literal `jr` prefix?
    **Assumption:** The specified binds select the fields and parameters select
    the properties. Therefore the same semantics apply to arbitrarily named
    fields, nested fields, multiple binds for the same property, and metadata
    without visible controls. An equivalent XML namespace prefix is valid,
    consistent with confirmed assumption 4. Repeat-creation timing is not
    specified by Amendment 2 and is left untested.
    **Tests:** `preloadAttributesAreRecognizedByNamespaceRatherThanLiteralPrefix`,
    `repeatedPropertyBindsAlsoPopulateNestedInstanceFields`,
    `nestedUidBindIsInitializedAlongsideTheTopLevelUid`, and the isolated and
    combined preload tests (which deliberately use non-property field names).

20. **Question:** How do preload binds interact with calculations, stub inventory,
    and session isolation?
    **Assumption:** Existing initialization/isolation rules continue to apply:
    calculations can read preloaded values before navigation, an answer in one
    session is private to it, and normal answers do not regenerate metadata.
    Preload attribute values are not XPath function calls, so they do not add
    entries to `stubbedFunctions()`. An explicit `username()` calculation is
    still listed and returns the same value as the username property preload.
    **Tests:** `calculationsCanReadThePreloadedUsernameImmediately`,
    `metadataPreloadsDoNotPreventOrdinaryDefaultsAndCalculations`,
    `answeringAMetadataFieldCannotChangeOtherSessionsPreloadedValues`,
    `answeringAnOrdinaryQuestionDoesNotRegenerateMetadata`,
    `preloadNamesAreNotReportedAsStubbedXPathFunctions`,
    `preloadedUsernameAgreesWithTheUsernameFunctionInEachSession`.

21. **Question:** Must preloads overwrite a property manager installed by other
    application code, and must tests prove operation in a fresh process?
    **Assumption:** The explicit requirement is that callers need not configure
    the engine. These tests perform no configuration and can run on their own
    in a fresh Maven test fork. Behavior with an externally configured property
    manager is not specified and is not tested; no global manager is reset or
    changed by the tests.
    **Tests:** `eachPropertyAloneLoadsAndOpensAnInitializedSession`,
    `eachNativePreloadAloneLoadsAndOpensAtTheBeginning`, and all other preload
    tests.
