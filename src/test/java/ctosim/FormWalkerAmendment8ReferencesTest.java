package ctosim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment5Support.*;
import static ctosim.FormWalkerAmendment8Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment8ReferencesTest {
    private Submission recorded(String instance, String rule) throws Exception {
        return walk8(form(instance + "<answer/>", bind("answer", "int", constraint(rule)), input("answer")));
    }

    @Test void converterPaddingIsRemovedExactlyAsInTheAmendmentExample() throws Exception {
        UnsatisfiedConstraint failure = onlyRecord(recorded("<age>90</age>", "(.>=  /data/age ) and .<= 60"));
        assertEquals("(.>= ${age}) and .<= 60", failure.constraint());
        assertEquals(Map.of("age", "90"), failure.references());
    }

    @Test void unpaddedReferencesAreReplacedWithoutReserializingTheRestOfTheExpression() throws Exception {
        UnsatisfiedConstraint failure = onlyRecord(recorded("<age>90</age>", "(.>=/data/age)  and (.<=60)"));
        assertEquals("(.>=${age})  and (.<=60)", failure.constraint());
    }

    @Test void nestedPathsUseLeafNamesAndDoNotConfuseNamesWithSharedPrefixes() throws Exception {
        UnsatisfiedConstraint failure = onlyRecord(recorded("<group><age>90</age><age_limit>60</age_limit></group>",
                "(.>=/data/group/age) and (.<=/data/group/age_limit)"));
        assertEquals("(.>=${age}) and (.<=${age_limit})", failure.constraint());
        assertEquals(Map.of("age", "90", "age_limit", "60"), failure.references());
        assertEquals(List.of("age", "age_limit"), new ArrayList<>(failure.references().keySet()));
    }

    @Test void referencesAreDeduplicatedAndIterateInExpressionOrderRatherThanInstanceOrAlphabeticalOrder() throws Exception {
        UnsatisfiedConstraint failure = onlyRecord(recorded("<alpha>A</alpha><middle>M</middle><zeta>Z</zeta>",
                "string-length(concat(/data/zeta,/data/alpha,/data/zeta,/data/middle)) < 0"));
        assertEquals(List.of("zeta", "alpha", "middle"), new ArrayList<>(failure.references().keySet()));
        assertEquals(List.of("Z", "A", "M"), new ArrayList<>(failure.references().values()));
        assertEquals("string-length(concat(${zeta},${alpha},${zeta},${middle})) < 0", failure.constraint());
    }

    @Test void referencesInAShortCircuitedBranchAreStillRecorded() throws Exception {
        UnsatisfiedConstraint failure = onlyRecord(recorded("<age>90</age>", "false() and (/data/age>0)"));
        assertEquals("false() and (${age}>0)", failure.constraint());
        assertEquals(Map.of("age", "90"), failure.references());
    }

    @Test void primaryLookingPathsInsideStringLiteralsAreNeitherRewrittenNorReferenced() throws Exception {
        String rule = "concat('/data/age', \"/data/other\", /data/real) = 'impossible'";
        UnsatisfiedConstraint failure = onlyRecord(recorded("<age>90</age><other>60</other><real>R</real>", rule));
        // No converter padding around the reference in this fixture: avoid imposing a rule for ambiguous spaces.
        assertTrue(failure.constraint().contains("'/data/age'"));
        assertTrue(failure.constraint().contains("\"/data/other\""));
        assertTrue(failure.constraint().contains("${real}"));
        assertEquals(Map.of("real", "R"), failure.references());
    }

    @Test void constraintsWithoutReferencesKeepTheirExactDecodedText() throws Exception {
        String rule = "(. < 0)  and  (. >= 0)";
        UnsatisfiedConstraint failure = onlyRecord(recorded("", rule));
        assertEquals(rule, failure.constraint());
        assertTrue(failure.references().isEmpty());
    }

    @Test void selfReferencesAreRewrittenButNotIncludedInTheReferencesMap() throws Exception {
        String rule = "(.=/data/answer) and (.>=/data/age) and (.<=60)";
        UnsatisfiedConstraint failure = onlyRecord(recorded("<age>90</age>", rule));
        assertEquals("(.=${answer}) and (.>=${age}) and (.<=60)", failure.constraint());
        assertEquals(Map.of("age", "90"), failure.references());
    }

    @Test void aSelfOnlyConstraintHasAnEmptyReferenceMap() throws Exception {
        UnsatisfiedConstraint failure = onlyRecord(recorded("", "not(.=/data/answer) and false()"));
        assertEquals("not(.=${answer}) and false()", failure.constraint());
        assertTrue(failure.references().isEmpty());
    }

    @Test void relativeReferencesAreCapturedButOnlyAbsolutePathsAreRewritten() throws Exception {
        UnsatisfiedConstraint failure = onlyRecord(recorded("<age>90</age>", "(.>=../age) and (.<=60)"));
        assertEquals("(.>=../age) and (.<=60)", failure.constraint());
        assertEquals(Map.of("age", "90"), failure.references());
    }

    @Test void secondaryInstanceReferencesAreExcludedEvenWhenTheirLeafNameExistsInThePrimaryInstance() throws Exception {
        String rule = "(instance('lookup')/root/age=90) and (.>=/data/limit) and (.<=60)";
        Submission result = walk8(form("<age>4</age><limit>90</limit><answer/>", bind("answer", "int", constraint(rule)),
                input("answer"), "", "<instance id=\"lookup\"><root><age>90</age></root></instance>"));
        UnsatisfiedConstraint failure = onlyRecord(result);
        assertEquals("(instance('lookup')/root/age=90) and (.>=${limit}) and (.<=60)", failure.constraint());
        assertEquals(Map.of("limit", "90"), failure.references());
    }

    @Test void missingValuesAreEmptyStringsAndUnicodeTextIsPreserved() throws Exception {
        UnsatisfiedConstraint failure = onlyRecord(recorded("<empty/><zero>0</zero><nom>Zoë &amp; 李</nom>",
                "string-length(concat(/data/empty,/data/zero,/data/nom)) < 0"));
        assertEquals(Map.of("empty", "", "zero", "0", "nom", "Zoë & 李"), failure.references());
        assertEquals(List.of("empty", "zero", "nom"), new ArrayList<>(failure.references().keySet()));
    }

    @Test void unicodeFieldNamesAreUsedInBothThePlaceholderAndTheReferenceMap() throws Exception {
        UnsatisfiedConstraint failure = onlyRecord(recorded("<âge>90</âge>", "(.>=/data/âge) and (.<=60)"));
        assertEquals("(.>=${âge}) and (.<=60)", failure.constraint());
        assertEquals(Map.of("âge", "90"), failure.references());
    }

    @ParameterizedTest @CsvSource({"int,0007", "decimal,2.7500", "date,2026-01-05", "string,hello"})
    void referenceValuesUseStoredTextRatherThanProposalsOrNumericCoercions(String type, String proposed) throws Exception {
        LoadedForm form = form("<source/><answer/>", bind("source", type, "")
                + bind("answer", "int", constraint("string-length(string(/data/source)) < 0")), input("source") + input("answer"));
        Submission result = run(form, "\"fields\":{\"source\":{\"value\":\"" + proposed + "\"}}");
        assertEquals(Map.of("source", value(result, "source")), onlyRecord(result).references());
        assertFalse(value(result, "source").isEmpty());
    }

    @Test void navigationReferenceValuesAreSnapshotsBeforeLaterAnswersChangeThem() throws Exception {
        LoadedForm form = form("<age>90</age><answer/>", bind("age", "int", "")
                + bind("answer", "int", constraint("(.>=/data/age) and (.<=60)")), input("answer") + input("age"));
        Submission result = run(form, "\"fields\":{\"age\":{\"value\":25}}");
        assertEquals("25", value(result, "age"));
        assertEquals(Map.of("age", "90"), onlyRecord(result).references());
    }

    @Test void finalReferenceValuesAreSnapshotsBeforeLaterFinalClearings() throws Exception {
        LoadedForm form = form("<first>4</first><second>90</second><gate>0</gate>",
                bind("first", "int", constraint("/data/gate=0 or ((.>=/data/second) and (.<=60))"))
                + bind("second", "int", constraint("/data/gate=0")) + bind("gate", "int", ""),
                input("first") + input("second") + input("gate"));
        Submission result = run(form, gateAnd(""));
        assertRecordsMatchWarnings(result);
        assertEquals(2, result.unsatisfiedConstraints().size());
        assertEquals("first", result.unsatisfiedConstraints().get(0).field());
        assertEquals(Map.of("gate", "1", "second", "90"), result.unsatisfiedConstraints().get(0).references());
        assertEquals("", value(result, "second"));
    }

    @Test void referenceMapsRejectMutationThroughEntriesKeysValuesAndIterators() throws Exception {
        Map<String, String> refs = onlyRecord(recorded("<age>90</age>", "(.>=/data/age) and (.<=60)")).references();
        assertThrows(UnsupportedOperationException.class, () -> refs.put("new", "value"));
        assertThrows(UnsupportedOperationException.class, () -> refs.remove("age"));
        assertThrows(UnsupportedOperationException.class, refs::clear);
        assertThrows(UnsupportedOperationException.class, () -> refs.entrySet().iterator().next().setValue("changed"));
        assertThrows(UnsupportedOperationException.class, () -> refs.keySet().remove("age"));
        assertThrows(UnsupportedOperationException.class, () -> refs.values().remove("90"));
        var iterator = refs.entrySet().iterator();
        iterator.next();
        assertThrows(UnsupportedOperationException.class, iterator::remove);
        assertEquals(Map.of("age", "90"), refs);
    }

    @Test void emptyReferenceMapsAreAlsoUnmodifiable() throws Exception {
        Map<String, String> refs = onlyRecord(recorded("", "false()")).references();
        assertThrows(UnsupportedOperationException.class, () -> refs.put("new", "value"));
    }
}
