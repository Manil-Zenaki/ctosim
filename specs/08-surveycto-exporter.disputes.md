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
