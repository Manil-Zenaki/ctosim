package ctosim;

import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkAmendment5Support.*;
import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form engine shares JVM state")
class FieldworkAmendment5WalkTest {
    @Test void legacyVisitConstructorMeansFirstAttemptAndFound() {
        var visit = new Visit(2, START, -1, 179, "0007");
        assertEquals(new Visit(2, START, -1, 179, "0007", 1, true), visit);
        assertEquals(1, visit.attempt());
        assertTrue(visit.found());
    }

    @ParameterizedTest @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void nonpositiveAttemptIsRejectedByWalkEvenWithoutVisitConfiguration(int attempt) throws Exception {
        var walker = new FormWalker(simple(), settings(""));
        assertThrows(IllegalArgumentException.class, () -> walker.walk(1, supplied(attempt, true)));
    }

    @ParameterizedTest @ValueSource(ints = {1, 2, 10, 11, Integer.MAX_VALUE})
    void everyPositiveAttemptIsWrittenAsAWholeNumber(int attempt) throws Exception {
        var row = new FormWalker(simple(), settings(visitSettings(0, 0, 2)))
                .walk(1, supplied(attempt, true));
        assertEquals(Integer.toString(attempt), value(row, "attempt"));
    }

    @Test void attemptAndFoundHaveNoEffectWithoutVisitsSetting() throws Exception {
        var walker = new FormWalker(simple(), settings(""));
        same(walker.walk(7, supplied(1, true)), walker.walk(7, supplied(9, false)));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void availabilityAndAttemptReplaceFixedOverridesAndPerFieldMissingRates(boolean found) throws Exception {
        var config = settings(visitSettings(0, 0, 3) + ",\"fields\":{"
                + "\"status\":{\"value\":\"" + (found ? "absent" : "yes") + "\",\"missingRate\":1},"
                + "\"attempt\":{\"value\":99,\"missingRate\":1}}");
        var row = new FormWalker(simple(), config).walk(12, supplied(2, found));
        assertEquals("2", value(row, "attempt"));
        if (found) assertEquals("yes", value(row, "status"));
        else assertTrue(List.of("absent", "busy").contains(value(row, "status")));
        assertTrue(row.warnings().isEmpty(), row.warnings().toString());
    }

    @ParameterizedTest @ValueSource(strings = {"expression", "rule"})
    void visitAnswersReplaceExpressionsAndApplicableConditionalOverrides(String kind) throws Exception {
        String status = kind.equals("expression") ? "\"expr\":\"'absent'\""
                : "\"when\":[{\"if\":\"true()\",\"value\":\"absent\"}]";
        String number = kind.equals("expression") ? "\"expr\":\"99\""
                : "\"when\":[{\"if\":\"true()\",\"value\":99}]";
        var config = settings(visitSettings(0, 0, 3) + ",\"fields\":{\"status\":{" + status
                + "},\"attempt\":{" + number + "}}");
        var row = new FormWalker(simple(), config).walk(1, supplied(2, true));
        assertEquals("yes", value(row, "status"));
        assertEquals("2", value(row, "attempt"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void optionalVisitAnswersBypassGlobalMissingRate(boolean found) throws Exception {
        var config = SimulationConfig.parse("{\"missingRate\":1," + visitSettings(0, 0, 3) + "}", "visits.json");
        var row = new FormWalker(simple(), config).walk(1, supplied(3, found));
        assertFalse(value(row, "status").isEmpty());
        assertEquals("3", value(row, "attempt"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void formRelevanceFollowsTheInjectedAvailability(boolean found) throws Exception {
        var row = new FormWalker(simple(), settings(visitSettings(0, 0, 3)))
                .walk(1, supplied(1, found));
        assertEquals(!found, value(row, "details").isEmpty());
        assertEquals(found ? 4 : 3, row.answered());
        assertEquals(found ? 240 : 180, seconds(row));
    }

    @Test void omittedNumberSettingLeavesTheNumberQuestionAlone() throws Exception {
        var config = settings("\"visits\":{\"field\":\"status\",\"available\":\"yes\"},"
                + "\"fields\":{\"attempt\":{\"value\":47}}");
        var row = new FormWalker(simple(), config).walk(1, supplied(3, false));
        assertEquals("47", value(row, "attempt"));
        assertNotEquals("yes", value(row, "status"));
    }

    @Test void failedChoicesExcludeAllFourSpecialCodeFamiliesEvenAtSpecialRateOne() throws Exception {
        var form = fixture("<status/>", bind("status", "string", ""),
                choices("status", "yes", "absent", "S", "D", "R", "O"));
        var config = SimulationConfig.parse("{\"seed\":731991,\"specialRate\":1,\"otherRate\":1,"
                + "\"specialValues\":[\"S\"],\"dontKnowCodes\":[\"D\"],"
                + "\"refuseCodes\":[\"R\"],\"otherCodes\":[\"O\"],"
                + "\"visits\":{\"field\":\"status\",\"available\":\"yes\"}}", "codes.json");
        var walker = new FormWalker(form, config);
        for (int n = 1; n <= 80; n++) assertEquals("absent", value(walker.walk(n, supplied(1, false)), "status"));
    }

    @Test void availableValueIsInjectedEvenWhenItIsASpecialCode() throws Exception {
        var config = settings(visitSettings(0, 0, 3) + ",\"dontKnowCodes\":[\"yes\"]");
        assertEquals("yes", value(new FormWalker(simple(), config).walk(1, supplied(1, true)), "status"));
    }

    @Test void failedValuesAreUniformDespiteWeightsAndRepeatForTheSameSeedAndNumber() throws Exception {
        var form = fixture("<status/>", bind("status", "string", ""), choices("status", "yes", "a", "b", "c"));
        var config = settings("\"visits\":{\"field\":\"status\",\"available\":\"yes\"},"
                + "\"fields\":{\"status\":{\"weights\":{\"yes\":100,\"a\":1,\"b\":0,\"c\":0}}}");
        var walker = new FormWalker(form, config);
        var counts = new HashMap<String, Integer>();
        for (int n = 1; n <= 900; n++) {
            String answer = value(walker.walk(n, supplied(1, false)), "status");
            assertTrue(List.of("a", "b", "c").contains(answer));
            counts.merge(answer, 1, Integer::sum);
            if (n <= 12) assertEquals(answer, value(walker.walk(n, supplied(7, false)), "status"));
        }
        for (String answer : List.of("a", "b", "c")) assertUniformBin(counts.getOrDefault(answer, 0), 900, 1.0 / 3, answer);
    }

    @ParameterizedTest @ValueSource(strings = {"yes", "0007", "trouvé"})
    void configuredAvailableCodeIsAnExactString(String code) throws Exception {
        var form = fixture("<status/>", bind("status", "string", ""), choices("status", code, "absent"));
        var config = settings("\"visits\":{\"field\":\"status\",\"available\":\"" + code + "\"}");
        assertEquals(code, value(new FormWalker(form, config).walk(1, supplied(1, true)), "status"));
    }

    @ParameterizedTest @ValueSource(strings = {"choice", "constraint", "conversion"})
    void rejectedAvailableAnswerUsesOrdinaryFallbackAndWarnsWithFieldAndValue(String reason) throws Exception {
        String type = reason.equals("conversion") ? "int" : "string";
        String fallback = reason.equals("conversion") ? "7" : "absent";
        var form = fixture("<status/>", bind("status", type,
                        reason.equals("constraint") ? "constraint=\". = 'absent'\"" : ""),
                reason.equals("conversion") ? input("status") : choices("status",
                        reason.equals("choice") ? "other" : "yes", "absent"));
        var config = settings("\"visits\":{\"field\":\"status\",\"available\":\"yes\"},"
                + "\"fields\":{\"status\":{\"value\":\"" + fallback + "\"}}");
        var row = new FormWalker(form, config).walk(1, supplied(1, true));
        assertEquals(fallback, value(row, "status"));
        assertWarning(row, "status", "yes");
    }

    @Test void rejectedFailureChoiceFallsBackAndWarnsAboutThatChoice() throws Exception {
        var form = fixture("<status/>", bind("status", "string", "constraint=\". = 'yes'\""),
                choices("status", "yes", "absent"));
        var config = settings("\"visits\":{\"field\":\"status\",\"available\":\"yes\"},"
                + "\"fields\":{\"status\":{\"value\":\"yes\"}}");
        var row = new FormWalker(form, config).walk(1, supplied(1, false));
        assertEquals("yes", value(row, "status"));
        assertWarning(row, "status", "absent");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void noEligibleFailureChoiceFallsBackAndWarns(boolean onlySpecialAlternatives) throws Exception {
        var form = fixture("<status/>", bind("status", "string", ""), onlySpecialAlternatives
                ? choices("status", "yes", "99", "D", "R", "O") : choices("status", "yes"));
        var config = settings("\"visits\":{\"field\":\"status\",\"available\":\"yes\"},"
                + "\"dontKnowCodes\":[\"D\"],\"refuseCodes\":[\"R\"],\"otherCodes\":[\"O\"],"
                + "\"fields\":{\"status\":{\"value\":\"yes\"}}");
        var row = new FormWalker(form, config).walk(1, supplied(1, false));
        assertEquals("yes", value(row, "status"));
        assertWarning(row, "status");
    }

    @Test void aNonSelectAvailabilityWithNoOfferedFailureValuesUsesItsUsualAnswerAndWarns() throws Exception {
        var form = fixture("<status/>", bind("status", "string", ""), input("status"));
        var config = settings("\"visits\":{\"field\":\"status\",\"available\":\"yes\"},"
                + "\"fields\":{\"status\":{\"value\":\"ordinary\"}}");
        var row = new FormWalker(form, config).walk(1, supplied(1, false));
        assertEquals("ordinary", value(row, "status"));
        assertWarning(row, "status");
    }

    @Test void questionsWithTheSameNameInSeparateGroupsAllReceiveVisitAnswers() throws Exception {
        String instance = "", binds = "", body = "";
        for (String group : List.of("first", "second")) {
            instance += "<" + group + "><status/><attempt/></" + group + ">";
            binds += bind(group + "/status", "string", "") + bind(group + "/attempt", "string", "");
            body += "<group ref=\"/data/" + group + "\"><label>Group</label>"
                    + choices(group + "/status", "yes", "absent") + input(group + "/attempt") + "</group>";
        }
        var row = new FormWalker(fixture(instance, binds, body), settings(visitSettings(0, 0, 3)))
                .walk(1, supplied(2, false));
        for (String group : List.of("first", "second")) {
            assertEquals("absent", value(row, group + "/status"));
            assertEquals("2", value(row, group + "/attempt"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"constraint", "choice", "conversion"})
    void rejectedAttemptFallsBackAndWarnsWithWholeNumber(String reason) throws Exception {
        var form = fixture("<status/><attempt/>", bind("status", "string", "")
                + bind("attempt", reason.equals("conversion") ? "date" : "int",
                        reason.equals("constraint") ? "constraint=\". = 7\"" : ""),
                choices("status", "yes", "absent") + (reason.equals("choice")
                        ? choices("attempt", "7") : input("attempt")));
        String fallback = reason.equals("conversion") ? "2026-03-02" : "7";
        var config = settings(visitSettings(0, 0, 3) + ",\"fields\":{\"attempt\":{\"value\":\"" + fallback + "\"}}");
        var row = new FormWalker(form, config).walk(1, supplied(2, true));
        assertEquals(fallback, value(row, "attempt"));
        assertWarning(row, "attempt", "2");
    }

    @Test void injectionTargetsEveryAnsweredRepeatInstance() throws Exception {
        var form = fixture("<size>3</size><row jr:template=\"\"><status/><attempt/></row>",
                bind("size", "int", "") + bind("row/status", "string", "") + bind("row/attempt", "int", ""),
                "<repeat nodeset=\"/data/row\" jr:count=\"/data/size\">"
                        + choices("row/status", "yes", "absent") + input("row/attempt") + "</repeat>");
        var row = new FormWalker(form, settings(visitSettings(0, 0, 3))).walk(1, supplied(3, false));
        assertEquals(3, count(row, "row"));
        for (int i = 1; i <= 3; i++) {
            assertEquals("absent", value(row, "row[" + i + "]/status"));
            assertEquals("3", value(row, "row[" + i + "]/attempt"));
        }
    }

    @Test void irrelevantAndReadOnlyQuestionsAreNotForcedIntoAnswers() throws Exception {
        var form = fixture("<status/><attempt/>", bind("status", "string", "relevant=\"false()\"")
                        + bind("attempt", "int", "readonly=\"true()\""),
                choices("status", "yes", "absent") + input("attempt"));
        var row = new FormWalker(form, settings(visitSettings(0, 0, 3))).walk(1, supplied(2, false));
        assertEquals("", value(row, "status"));
        assertEquals("", value(row, "attempt"));
        assertEquals(0, row.answered());
    }
}
