# Spec 12 — disputes

## Dispute 1: forms the engine cannot load

`FakeAttachmentsDiscoveryTest.unreadablePulldataCallsDoNotThrowOrLoseOtherReadableCalls`
(all four cases) fails before `FakeAttachments.of` is called: the test
support loads a form whose calculate is `pulldata()`, `pulldata('good')`,
`pulldata('good','bad_value','bad_key')` or a five-argument call, and
`FormLoader.load` stops with "the form engine could not initialise the
form (... XPath evaluation: type mismatch for function 'pulldata')".
`pulldata()` takes exactly four arguments in the engine (spec 03), so
such a form cannot be loaded at all and no `LoadedForm` can reach this
module. The case the spec covers ("a call it cannot read adds nothing")
is a call the engine accepts but this module cannot read, for example a
non-literal file or column name (`pulldata(/data/f, 'age', 'id', ...)`),
or a call in an expression that is not evaluated when the form
initialises (a `relevant` or `constraint` of a question). The test
should use such a call, or expect the load to fail.

## Adjudication

### Dispute 1: upheld; fix the fixture, retain the expectations

Spec 12 accepts an already loaded `LoadedForm`. Its Errors section requires
unreadable calls to add nothing without throwing; it does not require
`FormLoader` to accept forms whose calculations cannot initialise. The four
disputed cases therefore used the wrong expression site: a failure while
loading their malformed calculations cannot establish a violation by
`FakeAttachments.of`.

Updated all four cases of
`FakeAttachmentsDiscoveryTest.unreadablePulldataCallsDoNotThrowOrLoseOtherReadableCalls`
to place the malformed call in the `constraint` of an input question instead
of its `calculate`. Constraints are among the bind expressions spec 12 scans,
and this fixture does not answer or validate the question. The valid
four-argument calculation remains in the same form. Kept the assertions that
`FakeAttachments.of` does not throw and that the generated `good` file has
exactly `age,id` as its columns: these enforce the Errors section and column
discovery rules without requiring the loader to evaluate an invalid call.
Expecting a load failure instead would test the loader rather than spec 12.

Validation: ran `.\mvnw.cmd -o -q test`, then repeated with `-e` to diagnose
the compiler failure. Both stopped at `testCompile`; the diagnostic reports
`java.nio.file.AccessDeniedException` on
`.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`. No tests ran, so
compilation and execution of the revised fixtures remain unverified in this
sandbox. Logs are `.codex-runs/fake-attachments-adjudication-test.log` and
`.codex-runs/fake-attachments-adjudication-diagnostic.log`. No production
source was read or modified.

## Dispute 2: an id level compared with the file's rows (Amendment 1)

`FakeAttachmentsAmendment1ContractTest.countsAtOrBelowOrdinaryLeafCountPreserveBytesEvenWhenIdCountIsSmaller`,
seven cases (line 64), expects a file whose id level has fewer codes than
the submissions to stay unchanged as long as the submissions do not
exceed the file's rows (for an id at the first level of a three-level
file, up to 27). The Clarification of Amendment 1 corrects assumption 17:
the comparison is with the number of codes of the id level, so such a
level is expanded (3 codes, 20 submissions: 20 codes).

Requested: expect expansion when the submissions exceed the id level's
own codes, and keep the byte-for-byte check for counts at or below them.
