# Disputes — spec 08 (SurveyCTO exporter)

## Dispute 1: audio audits are no longer written unchanged (Amendment 1)

Amendment 1 says "`audio audit` fields are written like `text audit`
fields: when the field's element is present in the XML, the attachment
URL of `AA_<u>_AFTER_0S.m4a`". The `"audio audit"` and `"AuDiO audit"`
cases of `SurveyCtoExporterValueTest.otherTypesAreStoredVerbatimIncludingSpecialMultiwordTypes`
still expect the stored value unchanged (the test author noted this in
question 11).

Requested change: remove those two cases from that parameterised test
(the new `SurveyCtoExporterAudioAuditAmendmentTest` covers audio audits,
including the case-insensitive type).

## Adjudication

### Dispute 1: upheld; obsolete verbatim expectations removed

Amendment 1 explicitly changes `audio audit` fields to emit the attachment
URL of `AA_<u>_AFTER_0S.m4a` when the field element is present, and an empty
cell when it is absent. This supersedes the original unchanged-value rule.
The confirmed case-insensitive type comparison applies to `AuDiO audit`
as well. Both disputed cases therefore contradict the current spec.

Removed only `"audio audit"` and `"AuDiO audit"` from
`SurveyCtoExporterValueTest.otherTypesAreStoredVerbatimIncludingSpecialMultiwordTypes`.
All other cases remain. Kept `SurveyCtoExporterAudioAuditAmendmentTest`
unchanged: its present-element, absent-element, synthesized-filename and
case-insensitive assertions follow Amendment 1 and the unchanged attachment
encoding rules. In particular,
`presentAudioAuditGeneratesAnAaFilenameRegardlessOfStoredValue` and
`audioAuditRecognitionRetainsCaseInsensitiveTypeComparison` retain coverage
of the behavior that replaces the two removed expectations.

Question 11 records an earlier instruction to preserve existing tests;
the current adjudication request explicitly authorizes fixing incorrect
tests. No implementation source was read or modified.

### Validation

- `mvnw.cmd -o -q test` stopped in `testCompile` with a fatal compiler
  error naming `.tools/m2/commons-io/commons-io/2.5/commons-io-2.5.jar`.
  Log: `.codex-runs/exporter-dispute-test.log`.
- The existing independent compiler probe successfully compiled all 11
  selected exporter/catalog/factory test and support sources with
  `--release 17`, using existing compiled classes and cached dependencies.
  Log: `.codex-runs/exporter-dispute-compile.log`.
- A diagnostic JUnit run of `SurveyCtoExporterValueTest` and
  `SurveyCtoExporterAudioAuditAmendmentTest` passed all 145 tests, with no
  skipped/aborted tests or failed containers.
  Log: `.codex-runs/exporter-dispute-junit.log`.

This verifies compilation and execution of the affected tests; it does
not establish a successful full Maven suite run. No dependencies, build
configuration, production sources, or published fixtures were changed.
