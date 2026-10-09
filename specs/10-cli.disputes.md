# Disputes — spec 10 (command line)

## Dispute 1: the runaway fixture cannot fail within the test's 45 seconds

`CliFailureTest.walkNavigationLimitFailureReturnsOneWithoutAReport` runs
the CLI on `runaway.xml`, a counted repeat of 100 005 instances of one
read-only question, in a child JVM that must finish within 45 seconds.
The walker stops at the 100 000-step limit (spec 07) as required, at
`/data/row[50000]/note[1]`, but reaching it takes about 50 seconds on the
owner's machine: JavaRosa's navigation slows down with the size of a
repeat (each step costs more as instances accumulate), so the time grows
roughly with the square of the number of instances crossed. The result
(status 1, a message naming the submission, no report) is correct; only
the harness timeout is exceeded.

Requested change: keep the test's assertions but reach the step limit
with fewer repeat instances, for example several read-only questions per
instance (ten notes per row and a count of 10 001 reach 100 000 steps
around row 5 000), so that the run is well within the timeout.
