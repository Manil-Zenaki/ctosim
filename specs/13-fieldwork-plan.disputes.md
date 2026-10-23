# Spec 13 — disputes

## Dispute 1: tests of the quota that Amendment 1 replaced, and one search() pool

### Point 1: the quota derived from `fieldworkDays` (Amendment 1, assumption 14)

Amendment 1 sets the daily quota `q` to `config.interviewsPerDay()`
(4 by default), "whatever `N`, `E` and `D`"; assumption 14 of the
questions file already records that the earlier quota tests conflict
with it. Two tests still check the earlier quota `N / (E × D)` rounded
up:

- `FieldworkScheduleTest.roundRobinAssignmentsAndCeilingQuotaDetermineShortInterviewDays`,
  all three cases (`ceiling quota, counted separately per enumerator ==>
  expected: <1> but was: <0>`): with a quota of 4, short interviews stay
  on day 0 where the old quota moved them to day 1.
- `FieldworkScheduleTest.firstInterviewMinutesOnInitialAndLaterDaysAreUniformIncludingBothEndpoints`,
  case `100,61` (line 125): 6 100 submissions over 100 enumerators and 61
  days gave an old quota of 1, so every interview was the first of its
  day and started 0 to 60 minutes after the start of work. With a quota
  of 4 most interviews follow the previous one, more than 60 minutes
  after the start of work. Case `6100,1` passes.

Requested: base these cases on `interviewsPerDay` (for example set it in
the configuration, or set it to 1 where the test needs one interview a
day), keeping what they check (round-robin assignment, days counted per
enumerator, uniform first-interview minutes).

### Point 2: `startswith` on the label column

`FieldworkAmendment1SearchIdTest.onlyRowsOfferedByTheOpeningSearchEnterThePool`,
case `'startswith', 'label', 'Al'` (`unexpected IDs: [A01, 7, 8, A01,
7]`), expects the pool `{A01, 7}`. The fixture `households.csv` has the
row `8,Also static,south,yes`, whose label `Also static` starts with
`Al`, so spec 07 Amendment 3 (`startswith`, exact and case-sensitive)
offers `8` too: the pool is `{A01, 8, 7}`, which is what the walker
offers at the question as well (the test's stored-id check agrees). The
six other cases of this test pass.

Requested: add `8` to the expected pool of that case (or change the
fixture row's label so that it does not start with `Al`).

## Adjudication (2026-10-23)

### Point 1: upheld; correct the superseded quota expectations

Amendment 1 explicitly replaces `ceil(N / (E * D))` with
`config.interviewsPerDay()`, defaulting to 4 independently of N, E and D.
The clarification also confirms that the earlier quota expectations are
superseded. The tests are wrong wherever they retain the old formula.

Renamed the round-robin test to
`roundRobinAssignmentsAndConfiguredQuotaDetermineShortInterviewDays` and
made its expected day use `config.interviewsPerDay()`. Its six input cases,
submission ordering, per-enumerator counting and schedule checks are retained.
The shared `assertSchedule` helper now uses the configured quota too, so its
daily-cap and quota-exhaustion checks follow the same amended contract.

Both cases of
`firstInterviewMinutesOnInitialAndLaterDaysAreUniformIncludingBothEndpoints`
now explicitly set `interviewsPerDay` to 1. This guarantees that all 6,100
observations are first interviews of a day, preserving the initial-day and
later-day coverage, fixed seed, uniformity checks and endpoint checks without
depending on the obsolete quota formula.

### Point 2: upheld; include ID `8` in the expected search pool

Spec 13 Amendment 1 includes the loaded and static choices offered at opening
for this literal-argument search. The incorporated spec 07 Amendment 3 defines
`startswith` as an exact, case-sensitive comparison. Both `Alpha` (ID `A01`)
and `Also static` (ID `8`) start with `Al`; the static ID `7` is also offered.
The correct pool is therefore `{A01, 8, 7}`.

Added `8` to this case's expected set, leaving the fixture and the other six
cases unchanged. The existing helper still checks pool membership,
distinctness before exhaustion, cycling, stored IDs and the warning's K/N
counts, now with K = 3 and N = 7 for this case.

### Verification

Ran `.\mvnw.cmd -o -q test`; it exited 1 at `testCompile` with a fatal
compiler error naming the cached `commons-io-2.5.jar`. The full Maven suite
could not run. This limitation is also recorded in the questions file.

As supplementary verification, adapted the existing diagnostic compiler and
JUnit launcher under `.codex-runs/fieldwork-dispute/`. Compilation of both
changed test classes, the Amendment 1 quota class and their test-support
dependencies succeeded against the existing compiled APIs with `--release 17`.
JUnit ran all 85 cases in `FieldworkScheduleTest`,
`FieldworkAmendment1QuotaTest` and `FieldworkAmendment1SearchIdTest`:
85 passed, none failed, skipped or aborted. Logs are `memory-compile.log`
and `junit.log` in that directory; the Maven log is `maven-test.log`.
This supplements, but does not establish, a successful Maven build.

No production source was read or changed, no build configuration or fixtures
were changed, and no commit was made.

## Dispute 2: an Amendment 1 test of an answer-dependent filter (Amendment 2)

`FieldworkAmendment1SearchIdTest.idPoolUsesBlankOrDefaultValuesBeforeAnEarlierQuestionIsAnswered`,
both cases (`no repeats before exhaustion ==> expected: <2> but was:
<1>`, line 103), allocates visit ids at opening for
`search('opening', 'matches', 'region', /data/region)`. That filter's
`text` argument is a field reference, so Amendment 2 now gives the id at
the question and the visit's `id()` is null; assumption 20 of the
questions file records the conflict. The Amendment 2 tests of the same
situation pass.

Requested: drop this case, or turn it into a case with a literal filter,
which keeps Amendment 1's rule.

## Adjudication of Dispute 2 (2026-10-23)

### Answer-dependent opening-time pool: upheld; replace the superseded test

The original test is wrong under Amendment 2. In
`search('opening', 'matches', 'region', /data/region)`, the `text`
argument is not a string literal. With no `value` or `texts` override on
the id question, Amendment 2 requires a null visit id and allocation from
the choices offered when the walker reaches that question. The earlier
expectation of an opening-time pool based on blank/default region values
is explicitly superseded by Amendment 2's clarification of assumption 20.

Renamed the test to
`literalFilterKeepsItsVisitIdPoolWhenAnEarlierAnswerChanges` and replaced
the reference with a quoted literal in both cases (`''` and `'initial'`).
Amendment 2 explicitly preserves Amendment 1 for all-literal searches.
These cases therefore retain their two-value pools, cycling, K/N warning
checks and deterministic replay. The test now also checks that the ids
are stored as answers and that replay stores those same ids despite the
earlier region question being answered `later`. No fixture was changed.

Kept the existing Amendment 2 tests, including
`idsComeFromTheAnsweredVillageRatherThanTheOpeningValue` and
`eitherNonliteralFilterArgumentActivatesAllocationAtTheQuestion`, which
cover the superseding dynamic-filter behavior. No new interpretation of
the spec is needed. The already-adjudicated Dispute 1 corrections are
present and remain unchanged.

### Verification

Ran `.\mvnw.cmd -o -q test`; it exited 1 at `testCompile`. A diagnostic
`-e test-compile` run confirmed `java.nio.file.AccessDeniedException` for
the cached `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`.
The full Maven suite could not run; this is also recorded in the questions
file.

Adapted the existing offline diagnostic compiler and JUnit launcher under
`.codex-runs/fieldwork-dispute2/`. All 11 selected test/support sources
compiled successfully with `--release 17` against the existing compiled
project APIs. JUnit ran 137 cases across `FieldworkScheduleTest`,
`FieldworkAmendment1QuotaTest`, `FieldworkAmendment1SearchIdTest`,
`FieldworkAmendment2CascadeTest`, `FieldworkAmendment2ContractTest` and
`FieldworkAmendment2RandomnessTest`: all passed, none skipped or aborted,
and no failed containers. This supplements, but does not establish, a
successful Maven build. Logs in that directory are `maven-test.log`,
`maven-detail.log`, `memory-compile.log` and `junit.log`.

No production source was read or changed, no dependency or build
configuration was changed, and no commit was made.

## Dispute 3: a reuse warning that depends on an ordinary draw (Amendment 3)

`FieldworkAmendment3FallbackTest.aHigherFixedAnswerIsRespectedWhileLowerUpperQuestionsStillSteer`
(line 89, `expected: <1> but was: <0>`) runs 8 submissions of the `deep`
fixture with `region` fixed to `R1`, under which 7 ids are reachable,
and expects the 8th submission to reuse an id (one warning). Every
assertion before line 89 passes: the first 7 submissions are steered and
receive the 7 reachable ids.

At the 8th submission no district and no village is viable, so the
amendment says they are "answered as before": an ordinary draw. The draw
gives district `D2`, whose villages are `C` and `X`, then village `X`,
the fixture's deliberately empty branch: the id question offers no
choice, so no id is received and none is reused (Clarification of
Amendment 2, point 21), and there is no warning. With village `C` the
id would have been reused. The amendment does not make the ordinary
draw avoid empty branches, so the warning count at line 89 depends on
that draw, not on the specified behaviour.

Requested: drop the assertion on the 8th submission's warning (or
accept 0 or 1 warning with the matching reuse count), or use 7
submissions, keeping the steering assertions.
