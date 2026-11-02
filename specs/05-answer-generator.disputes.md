# Spec 05: disputed tests

## Dispute 1: configurations that are not valid JSON (2026-11-01)

**Tests:** in `AnswerGeneratorOtherChoiceTest`:
`emptyAndLabelSelectsStayBlankEvenWhenRequiredAndFixedToOther` (both
cases), `fixedValuesStillOverrideOtherRateInBothDirections` (both cases).

**Spec:** spec 04, "Errors": a configuration that is not valid JSON is
rejected with a `ConfigException`.

**What the tests do:** they build the configuration
`{"otherCodes":[...],"otherRate":1,"fields":{"answer":{"value":"other_z"}}}}`,
with one closing brace too many (lines 181 and 208: `"}}}"` after the
value, inside a string the helper already closes). Loading it fails with
`invalid JSON at line 1 (Unexpected close marker '}')` before any answer
is drawn, so the behaviour the tests are about is never reached.

**Requested change:** remove the extra closing brace, keeping the
assertions.
