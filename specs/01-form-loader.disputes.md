# Disputes — spec 01 (FormLoader)

## Dispute 1: body control `ref` predicate calling a stub

**Test:** `FormLoaderFunctionTest.everySpecifiedExpressionLocationContributesToTheStubInventory`,
case `body ref` (`<input ref="/data/answer[probe-ref() = '']">`).

**Test expectation:** the form loads, `stubbedFunctions()` is `{probe-ref}`
and `probe-ref()` evaluates to `""`.

**Argument:** this cannot be satisfied with the JavaRosa engine. During
parsing, `FormInstanceParser.verifyControlBindings` checks every body
control's `ref` by calling `new EvaluationContext(instance).expandReference(...)`,
an evaluation context created inside the parser with no custom function
handlers. The predicate is evaluated there and the parser throws
`XPathUnhandledException: cannot handle function 'probe-ref'`. The parser
offers no hook into that context. Observed stack trace (abridged):

```
XPathUnhandledException: XPath evaluation: cannot handle function 'probe-ref'
  at XPathFuncExpr.eval
  at EvaluationContext.expandReference
  at FormInstanceParser.verifyControlBindings
  at FormInstanceParser.parseInstance
  at XFormParser.parse
```

The spec has been amended (section "Amendment: engine limit on body `ref`
predicates") to state that such a form is rejected with
`FormLoadException`. The other expression sites in this parameterised
test, including the itemset `nodeset` choice filter and the itemset label
`ref`, are unaffected and pass.

**Requested change:** for the `body ref` case, expect `FormLoadException`
(naming the source, with an explanation) instead of a successful load.
Keep all the other cases.

## Adjudication

### Dispute 1: accepted; correct the test

The spec's "Amendment: engine limit on body `ref` predicates" explicitly
withdraws the earlier confirmation of assumption 10 for body control `ref`
predicates that call stubbed functions. It requires `load` to throw
`FormLoadException` for precisely the disputed `<input>` expression. The
previous successful-load expectation therefore contradicts the amended
spec. This decision follows the spec, without inspecting implementation
code or relying on the dispute's engine internals as an independent rule.

Removed only the `body ref` case from
`everySpecifiedExpressionLocationContributesToTheStubInventory` and added
`bodyControlRefPredicateCallingAStubIsRejectedAtLoadTimeWithSourceAndReason`.
The replacement requires rejection during loading with `FormLoadException`,
a message naming `locations.xml`, and explanatory text beyond the source
name, as required by the Errors section. It does not prescribe exact
diagnostic wording or an engine-specific exception class.

All other expression-location cases remain unchanged. The amendment
explicitly preserves support for bind attributes, setvalue values, itemset
`nodeset` choice filters, and itemset label/value `ref`; it does not justify
weakening those successful-load expectations.

### Validation

Attempted `mvnw.cmd -o -q test`; the wrapper failed before starting Maven
with a PowerShell null-array indexing error. Retried offline with the
bundled Maven distribution and the populated local dependency cache, then
with a workspace copy of that cache and with a forked compiler. Compilation
could not be verified: the Java compiler encountered
`java.nio.file.AccessDeniedException` on dependency JAR paths. The forked
attempt also reported unresolved FormLoader API symbols. No test results
are claimed from these unsuccessful build attempts. `git diff --check`
passed. No production source, build configuration, or spec requirements
were changed.
