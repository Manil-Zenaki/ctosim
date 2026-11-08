package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.choices;
import static ctosim.InjectedAmendment1Support.rates;
import static ctosim.InjectedAmendment10Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form engine shares JVM state")
class InjectedAmendment10ProtectionTest {
    @ParameterizedTest @CsvSource({"dontKnow,int", "dontKnow,string", "refuse,int", "refuse,string"})
    void teamCodeFaultsLeaveAvailabilityAndVisitNumberAloneWhileChangingOtherAnswers(String fault, String type) throws Exception {
        var r = run(protectedCodes(type, false), settings10(240, 2, 4, 0, 0, 2,
                POINT + ",\"dontKnowCodes\":[\"DK\",\"999\"],\"refuseCodes\":[\"RF\",\"998\"],"
                        + "\"fields\":{\"answer\":{\"value\":\"ordinary\"}}")
                .withProblems(rates(0, fault)));
        assertFalse(records(r, fault).isEmpty(), "the configured fault is active on an unprotected answer");
        noTrackingRecords(r); trackingAnswers(r);
        assertTrue(records(r, fault).stream().allMatch(p -> p.field().equals("answer")));
    }

    @Test void cumulatedDontKnowAndRefuseStillCannotReplaceEitherTrackingAnswer() throws Exception {
        var r = run(protectedCodes("int", false), settings10(320, 2, 4, 0, 0, 2,
                POINT + ",\"dontKnowCodes\":[\"DK\",\"999\"],\"refuseCodes\":[\"RF\",\"998\"],"
                        + "\"fields\":{\"answer\":{\"value\":\"ordinary\"}}")
                .withProblems(rates(0, "dontKnow", "refuse")));
        assertFalse(records(r, "dontKnow").isEmpty()); assertFalse(records(r, "refuse").isEmpty());
        noTrackingRecords(r); trackingAnswers(r);
    }

    @Test void otherCannotReplaceTrackingSelectsEvenWhenBothHaveSpecifyQuestions() throws Exception {
        var form = fixture("<id/><status/><attempt/><status_detail/><attempt_detail/><choice/><choice_detail/>",
                bind("id", "int", "") + bind("status", "select1", "") + bind("attempt", "select1", "")
                        + bind("status_detail", "string", "relevant=\"/data/status = 'O'\"")
                        + bind("attempt_detail", "string", "relevant=\"/data/attempt = 'O'\"")
                        + bind("choice", "select1", "") + bind("choice_detail", "string", "relevant=\"/data/choice = 'O'\""),
                input("id") + choices("status", false, "yes", "absent", "O")
                        + choices("attempt", false, "1", "O") + input("status_detail") + input("attempt_detail")
                        + choices("choice", false, "real", "O") + input("choice_detail"));
        var r = run(form, settings10(240, 2, 4, 0, 0, 2, POINT
                + ",\"otherCodes\":[\"O\"],\"fields\":{\"choice\":{\"value\":\"real\"}}")
                .withProblems(rates(0, "other")));
        assertFalse(records(r, "other").isEmpty());
        trackingAnswers(r); noTrackingRecords(r);
        assertTrue(records(r, "other").stream().allMatch(p -> p.field().equals("choice")));
        for (var s : r.submissions()) {
            assertEquals("", value(s, "status_detail")); assertEquals("", value(s, "attempt_detail"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"int", "decimal"})
    void outliersMustChooseAnUnprotectedNumberInsteadOfEitherTrackingQuestion(String statusType) throws Exception {
        var form = numericTracking(statusType, true);
        var c = numericSettings(24, "12").withProblems(problem("outlier", 1));
        var r = run(form, c);
        assertEquals(24, records(r, "outlier").size(), "ordinary numeric companion allows every requested outlier");
        assertTrue(records(r, "outlier").stream().allMatch(p -> p.field().equals("amount")));
        for (var s : r.submissions()) {
            assertEquals(12, Double.parseDouble(value(s, "status")));
            assertEquals("1", value(s, "attempt"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"int", "decimal"})
    void aFormWithOnlyProtectedNumbersHasNoEligibleOutlierAndWarns(String statusType) throws Exception {
        var r = run(numericTracking(statusType, false), numericSettings(8, "12").withProblems(problem("outlier", 1)));
        assertTrue(records(r, "outlier").isEmpty());
        assertTrue(r.plan().warnings().stream().anyMatch(w -> w.contains("outlier")));
        for (var s : r.submissions()) {
            assertEquals(12, Double.parseDouble(value(s, "status"))); assertEquals("1", value(s, "attempt"));
        }
    }

    private static LoadedForm numericTracking(String statusType, boolean companion) throws Exception {
        return fixture("<id/><status/><attempt/>" + (companion ? "<amount/>" : ""),
                bind("id", "int", "") + bind("status", statusType, "") + bind("attempt", "int", "")
                        + (companion ? bind("amount", "int", "") : ""),
                input("id") + input("status") + input("attempt") + (companion ? input("amount") : ""));
    }

    private static SimulationConfig numericSettings(int n, String available) throws Exception {
        return config(n, 2, "\"id\":\"id\"," + POINT + ","
                + visits("status", available, "attempt", 0, 0, 2));
    }

    @Test void skipModulesCannotUseEitherTrackingSelectToHideAModule() throws Exception {
        String nodes = "<id/><status/><attempt/><gate/>", binds = bind("id", "int", ""),
                body = input("id");
        for (String gate : List.of("status", "attempt", "gate")) {
            String yes = gate.equals("attempt") ? "1" : "yes";
            binds += bind(gate, "select1", ""); body += choices(gate, false, yes, "no");
        }
        for (String gate : List.of("status", "attempt", "gate")) {
            String group = "module_" + gate, yes = gate.equals("attempt") ? "1" : "yes";
            nodes += "<" + group + "><a/><b/><c/></" + group + ">";
            binds += "<bind nodeset=\"/data/" + group + "\" relevant=\"/data/" + gate + " = '" + yes + "'\"/>";
            body += "<group ref=\"/data/" + group + "\">";
            for (String q : List.of("a", "b", "c")) {
                binds += bind(group + "/" + q, "string", ""); body += input(group + "/" + q);
            }
            body += "</group>";
        }
        var r = run(fixture(nodes, binds, body), settings10(240, 2, 4, 0, 0, 2,
                POINT + ",\"fields\":{\"gate\":{\"value\":\"yes\"}}")
                .withProblems(rates(0, "skipModules")));
        assertFalse(records(r, "skipModules").isEmpty(), "unprotected gate remains a skip opportunity");
        assertTrue(records(r, "skipModules").stream().allMatch(p -> p.field().equals("gate")));
        trackingAnswers(r); noTrackingRecords(r);
        for (var s : r.submissions()) for (String gate : List.of("status", "attempt"))
            for (String q : List.of("a", "b", "c")) assertFalse(value(s, "module_" + gate + "/" + q).isEmpty());
    }

    @Test void anAvailabilityAnswerThatCountsARosterCannotBeShortened() throws Exception {
        var form = fixture("<id/><status/><attempt/><people jr:template=\"\"><name/></people><size/><control jr:template=\"\"><name/></control>",
                bind("id", "int", "") + bind("status", "int", "") + bind("attempt", "int", "")
                        + bind("people/name", "string", "") + bind("size", "int", "") + bind("control/name", "string", ""),
                input("id") + input("status") + input("attempt")
                        + "<repeat nodeset=\"/data/people\" jr:count=\"/data/status\">" + input("people/name") + "</repeat>"
                        + input("size") + "<repeat nodeset=\"/data/control\" jr:count=\"/data/size\">" + input("control/name") + "</repeat>");
        var c = config(240, 2, "\"id\":\"id\"," + POINT + ","
                + visits("status", "4", "attempt", 0, 0, 2) + ",\"fields\":{\"size\":{\"value\":4}}")
                .withProblems(rates(0, "skipModules"));
        var r = run(form, c);
        assertFalse(records(r, "skipModules").isEmpty(), "companion roster is shortened");
        noTrackingRecords(r);
        for (var s : r.submissions()) { assertEquals("4", value(s, "status")); assertEquals(4, count(s, "people")); }
        assertTrue(records(r, "skipModules").stream().allMatch(p -> p.field().equals("size")));
    }

    @ParameterizedTest @ValueSource(strings = {"status", "attempt"})
    void aTrackingAnswerInsideAnAddAnotherRosterCannotBeUsedToEndItEarly(String protectedField) throws Exception {
        String positive = protectedField.equals("status") ? "yes" : "1";
        String root = protectedField.equals("status") ? "attempt" : "status";
        var form = fixture("<id/><" + root + "/><total/><people jr:template=\"\"><" + protectedField + "/><flag/></people>",
                bind("id", "int", "") + bind(root, root.equals("status") ? "select1" : "int", "")
                        + bind("total", "int", "calculate=\"min(sum(/data/people/flag) + 1, 3)\"")
                        + bind("people/" + protectedField, "select1", "")
                        + bind("people/flag", "int", "calculate=\"if(../" + protectedField + " = '" + positive + "', 1, 0)\""),
                input("id") + (root.equals("status") ? choices(root, false, "yes", "no") : input(root))
                        + "<repeat nodeset=\"/data/people\" jr:count=\"/data/total\">"
                        + choices("people/" + protectedField, false, positive, "no") + "</repeat>");
        var r = run(form, settings10(160, 2, 4, 0, 0, 2, POINT).withProblems(rates(0, "skipModules")));
        assertTrue(records(r, "skipModules").isEmpty(), "no record on the roster either");
        for (var s : r.submissions()) {
            assertEquals(3, count(s, "people"));
            for (int i = 1; i <= 3; i++) assertEquals(positive, value(s, "people[" + i + "]/" + protectedField));
        }
    }

    @Test void aRejectedVisitNumberRetainsItsOrdinaryFallbackEvenWhenItCanShortenARoster() throws Exception {
        var form = fixture("<id/><status/><attempt/><people jr:template=\"\"><name/></people>",
                bind("id", "int", "") + bind("status", "select1", "")
                        + bind("attempt", "int", "constraint=\". &gt;= 2 and . &lt;= 4\"") + bind("people/name", "string", ""),
                input("id") + choices("status", false, "yes", "absent") + input("attempt")
                        + "<repeat nodeset=\"/data/people\" jr:count=\"/data/attempt\">" + input("people/name") + "</repeat>");
        var r = run(form, settings10(160, 2, 4, 0, 0, 2, POINT + ",\"fields\":{\"attempt\":{\"value\":4}}")
                .withProblems(rates(0, "skipModules")));
        assertTrue(records(r, "skipModules").isEmpty());
        for (var s : r.submissions()) {
            assertEquals("4", value(s, "attempt"), "protection applies to the fallback as well as accepted visit answers");
            assertEquals(4, count(s, "people"));
            assertTrue(s.warnings().stream().anyMatch(w -> w.contains("attempt")));
        }
    }

    @Test void tooFastChangesDurationsWithoutChangingTrackingAnswers() throws Exception {
        var r = run(tracking(), settings10(40, 2, 2, 0, 0, 2, POINT).withProblems(rates(0, "tooFast")));
        assertEquals(20, records(r, "tooFast").size());
        trackingAnswers(r); noTrackingRecords(r);
    }

    @Test void protectionFollowsConfiguredNamesAndDoesNotDependOnHavingANumberSetting() throws Exception {
        var form = fixture("<availability/><ordinary/>", bind("availability", "string", "") + bind("ordinary", "string", ""),
                input("availability") + input("ordinary"));
        var c = config(240, 2, POINT + "," + visits("availability", "présent", null, 0, 0, 2)
                + ",\"dontKnowCodes\":[\"DK\"],\"fields\":{\"ordinary\":{\"value\":\"ordinary\"}}")
                .withProblems(rates(0, "dontKnow"));
        var r = run(form, c);
        assertFalse(records(r, "dontKnow").isEmpty());
        for (var s : r.submissions()) assertEquals("présent", value(s, "availability"));
        assertTrue(records(r, "dontKnow").stream().allMatch(p -> p.field().equals("ordinary")));
    }
}
