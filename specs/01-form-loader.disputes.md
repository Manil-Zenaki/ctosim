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
