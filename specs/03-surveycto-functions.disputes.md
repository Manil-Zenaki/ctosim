# Disputes — spec 03 (SurveyCTO functions)

## Dispute 1: type of the real form's instancenumber

**Tests:** `SurveyCtoRealFormsTest.realHouseholdRosterCalculatesStringInstanceNumbers`
(all three cases).

**Failure:** `XPath result type ==> expected: <java.lang.String> but was: <java.lang.Integer>`.

**Argument:** the test follows the spec's original acceptance paragraph,
which said the value is the string `"k"`. The spec was wrong about the
engine. A calculate's result is stored through JavaRosa 6.0.0's
`IAnswerData.wrapData(Object, int)`, whose bytecode tests
`instanceof Double` and, when the value is within 1e-9 of a whole number
in the int range, constructs `new IntegerData(int)`, before considering
the declared data type (only BOOLEAN is handled first). So a whole-number
result is stored as `IntegerData` even in a string field, for any function
returning a number, native ones included.

The spec has been amended ("Amendment 1: how the engine stores numeric
results"): the k-th `instancenumber` holds `IntegerData` k.

**Requested change:** expect the `Integer` k (via the value of the node)
instead of the `String` "k". Keep the rest of the test (instance counts,
creation through the engine, every k from 1 to N).

## Adjudication

### Dispute 1: upheld; correct the test

Amendment 1 explicitly requires the k-th `instancenumber` to hold
`IntegerData` whose `getValue()` is the `Integer` k, even though the field
is declared as a string. The acceptance paragraph now requires the number
k and refers to that amendment for storage. The test's `String` expectation
therefore contradicts the current spec. The general rule that number
function results are `Double` does not override the amendment's explicit
rule for stored calculate results.

Changed the expected node value to `Integer.valueOf(k + 1)` and renamed
the test to `realHouseholdRosterCalculatesIntegerInstanceNumbers`.
The existing `assertValue` helper checks both the exact runtime class and
the value. Kept all three instance counts (1, 3, 25), creation through the
engine, and verification of every instance from 1 to N unchanged.
This decision follows the amended spec; no implementation code was read.

Validation attempted: `mvnw.cmd -o -q test` failed during wrapper startup
with a null-array indexing error. Direct offline runs using the cached
Maven 3.9.16 installation and each existing local repository also failed:
the preload repository failed during compilation on `hamcrest-all-1.3.jar`,
and the dispute repository reached test compilation but failed with
`java.nio.file.AccessDeniedException` on `junit-platform-engine-1.11.4.jar`.
Test compilation and execution could not be confirmed in this environment.
