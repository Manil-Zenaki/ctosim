# Disputes — spec 07 (FormWalker)

## Dispute 1: retry-budget test now satisfied by constraint hints

**Test:** `FormWalkerDistributionTest.retriesUseFreshDrawsAndApproximateTheFiftyAttemptBudget`.

**Failure since spec 09 was implemented:**
`Exhausted retry budget ==> expected: <192.36...> but was: <0.0>`.

**Argument:** the test measures how often 50 proposals drawn from 1–20 all
fail the constraint `. = 1`. Spec 09 (which amends spec 07: "Using hints
in the walker") makes the walker derive hints from the constraint, and an
equality term gives both bounds, so the generator proposes 1 directly and
the budget is never exhausted. The test's premise ("each proposal
succeeds with p=1/20") no longer holds for this constraint. Spec 09's
"Amendment 1: effect on spec 07's retry behaviour" records this and names
constraint shapes that give no hints.

**Requested change:** keep the test and its statistics, but use a
constraint that is equivalent for validation and gives no hint under
spec 09, for example `. = 1 or . = 1` or `not(. != 1)`, so that each
proposal still succeeds with probability 1/20.
