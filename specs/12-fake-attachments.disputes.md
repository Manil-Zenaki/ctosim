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
