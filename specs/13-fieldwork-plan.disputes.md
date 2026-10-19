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
