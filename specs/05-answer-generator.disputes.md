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

## Adjudication (2026-11-07)

**Dispute 1 upheld: correct both JSON fixtures; retain all assertions.**
`AnswerGeneratorTestSupport.config` wraps the supplied members in an outer
JSON object. Each disputed fixture opens only `fields` and `answer` within
those members, so it must close two objects, not three. Spec 04's Errors
section requires invalid JSON to be rejected with `ConfigException`; these
tests must supply valid JSON to reach the behavior specified by spec 05.

- `fixedValuesStillOverrideOtherRateInBothDirections`: removed the extra
  closing brace for both parameterized select kinds. Kept every fixed value
  and both rate cases. Spec 05's Fixed value rule returns the override value
  on every nonblank call, including a value not among the offered choices
  (confirmed assumption 12). Amendment 4 leaves this precedence unchanged.
- `emptyAndLabelSelectsStayBlankEvenWhenRequiredAndFixedToOther`: removed the
  extra closing brace for both parameterized select kinds. Kept both empty
  and label scenarios and their null assertions. Spec 05's Blank answers
  rule and clarification 12 make empty selects blank even when required or
  fixed; Amendment 1 explicitly imposes the same result for label selects.
  Amendment 4 preserves these rules.

**Validation:** Ran `mvnw.cmd -o -q test`, followed by an offline `-e`
diagnostic run. Both stopped in `testCompile`; the diagnostic identifies
`java.nio.file.AccessDeniedException` for the cached
`.tools/m2/com/fasterxml/jackson/core/jackson-databind/2.13.2.1/jackson-databind-2.13.2.1.jar`.
Compilation and runtime results remain unverified. Logs are under
`.codex-runs/answer-generator-dispute/`; the build limitation is also recorded
in the questions file. No production source was read or modified.
