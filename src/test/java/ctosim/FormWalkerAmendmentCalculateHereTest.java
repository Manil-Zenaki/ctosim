package ctosim;

import java.time.OffsetDateTime;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendmentSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("walk temporarily changes the JVM default time zone")
class FormWalkerAmendmentCalculateHereTest {
    @ParameterizedTest @ValueSource(strings = {"", "readonly=\"true()\""})
    void calculationPrecedesExistingValueReadOnlyAndConfiguredOverride(String readonly) throws Exception {
        LoadedForm form = amendedForm("<answer>old</answer>", here("answer", "string", "'calculated'", readonly), input("answer"));
        Submission result = walk(form, "\"missingRate\":1,\"fields\":{\"answer\":{\"value\":\"override\"}}");
        assertEquals("calculated", value(result, "answer"));
        assertEquals(0, result.answered());
        assertElapsed(result, 0);
        assertTrue(result.warnings().isEmpty());
    }

    @Test void calculationPrecedesUnsupportedControlHandling() throws Exception {
        LoadedForm form = amendedForm("<answer/>", here("answer", "string", "'computed'", ""),
                "<upload ref=\"/data/answer\" mediatype=\"osm/*\"><label>OSM</label></upload>");
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        assertEquals("computed", value(result, "answer"));
        assertEquals(0, result.answered());
        assertElapsed(result, 0);
        assertTrue(result.warnings().isEmpty());
    }

    @Test void calculationRunsAtItsBodyPositionUsingTheCurrentClock() throws Exception {
        LoadedForm form = amendedForm("<early/><first/><elapsed/><at_here/><second/>",
                here("early", "int", "duration()", "readonly=\"true()\"")
                + bind("first", "string", "") + here("elapsed", "int", "duration()", "readonly=\"true()\"")
                + here("at_here", "dateTime", "now()", "readonly=\"true()\"") + bind("second", "string", ""),
                input("early") + input("first") + input("elapsed") + input("at_here") + input("second"));
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        assertEquals(0, integer(result, "early"));
        long elapsed = integer(result, "elapsed");
        assertTrue(elapsed >= 5 && elapsed <= 60);
        assertEquals(result.start().plusSeconds(elapsed), OffsetDateTime.parse(value(result, "at_here")).toLocalDateTime());
        assertTrue(seconds(result) - elapsed >= 5 && seconds(result) - elapsed <= 60);
        assertEquals(2, result.answered());
    }

    @Test void calculationUsesEarlierAnswersAndImmediatelyUpdatesCalculateChainsAndRelevance() throws Exception {
        LoadedForm form = amendedForm("<source/><computed/><double/><chain/><shown/><hidden/>",
                bind("source", "int", "") + here("computed", "int", "../source + 1", "readonly=\"true()\"")
                + bind("double", "int", "calculate=\"/data/computed * 2\"")
                + bind("chain", "int", "calculate=\"/data/double + 1\"")
                + bind("shown", "string", "relevant=\"/data/chain = 17\"")
                + bind("hidden", "string", "relevant=\"/data/chain != 17\""),
                input("source") + input("computed") + input("shown") + input("hidden"));
        Submission result = walk(form, "\"missingRate\":0,\"fields\":{\"source\":{\"value\":7},\"shown\":{\"value\":\"visible\"}}");
        assertEquals(8, integer(result, "computed"));
        assertEquals(16, integer(result, "double"));
        assertEquals(17, integer(result, "chain"));
        assertEquals("visible", value(result, "shown"));
        assertEquals(0, count(result, "hidden"));
        assertEquals(2, result.answered());
        assertElapsed(result, 2);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void conditionalCalculationIsHandledOnlyWhenRelevantAtNavigation(int gate) throws Exception {
        LoadedForm form = amendedForm("<gate/><marker/><bad/>", bind("gate", "int", "")
                + here("marker", "int", "42", "readonly=\"true()\" relevant=\"/data/gate = 1\"")
                + here("bad", "string", "pulldata('wrong-arity')", "readonly=\"true()\" relevant=\"false()\""),
                input("gate") + input("marker") + input("bad"));
        Submission result = walk(form, "\"missingRate\":0,\"fields\":{\"gate\":{\"value\":" + gate + "}}");
        assertEquals(gate, count(result, "marker"));
        if (gate == 1) assertEquals(42, integer(result, "marker"));
        assertEquals(0, count(result, "bad"));
        assertTrue(result.warnings().isEmpty(), "An irrelevant failing expression must not be evaluated");
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
    }

    @Test void calculationsInsideCountedNestedRepeatsUseTheirOwnQuestionNode() throws Exception {
        LoadedForm form = amendedForm("<parents>2</parents><children>3</children><parent jr:template=\"\"><index/><child jr:template=\"\"><index/><result/></child></parent>",
                bind("parents", "int", "") + bind("children", "int", "")
                + bind("parent/index", "int", "calculate=\"position(..)\"")
                + bind("parent/child/index", "int", "calculate=\"position(..)\"")
                + here("parent/child/result", "int", "../../index * 100 + ../index", "readonly=\"true()\""),
                "<repeat nodeset=\"/data/parent\" jr:count=\"/data/parents\"><repeat nodeset=\"/data/parent/child\" jr:count=\"/data/children\">"
                + input("parent/child/result") + "</repeat></repeat>");
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        assertEquals(2, count(result, "parent"));
        for (int p = 1; p <= 2; p++) {
            assertEquals(3, count(result, "parent[" + p + "]/child"));
            for (int c = 1; c <= 3; c++) assertEquals(p * 100 + c,
                    integer(result, "parent[" + p + "]/child[" + c + "]/result"));
        }
        assertEquals(0, result.answered());
        assertElapsed(result, 0);
    }

    @ParameterizedTest @ValueSource(strings = {"", "original"})
    void evaluationFailureKeepsTheValueWarnsAndContinuesWithoutAClockAdvance(String initial) throws Exception {
        LoadedForm form = amendedForm("<bad>" + initial + "</bad><after/>",
                here("bad", "string", "pulldata('wrong-arity')", "readonly=\"true()\"") + bind("after", "string", ""),
                input("bad") + input("after"));
        Submission result = new FormWalker(form, completeConfig()).walk(23);
        assertEquals(initial, value(result, "bad"));
        assertWarning(result, "bad");
        assertTrue(result.warnings().stream().allMatch(w -> w.startsWith("submission 23:")));
        assertFalse(value(result, "after").isBlank());
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
    }

    @Test void failedCalculationsWarnInTheOrderQuestionsAreReached() throws Exception {
        LoadedForm form = amendedForm("<alpha/><zeta/>",
                here("alpha", "string", "pulldata('wrong-arity')", "readonly=\"true()\"")
                + here("zeta", "string", "pulldata('wrong-arity')", "readonly=\"true()\""),
                input("zeta") + input("alpha"));
        Submission result = new FormWalker(form, completeConfig()).walk(3);
        assertEquals(2, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("zeta"));
        assertTrue(result.warnings().get(1).contains("alpha"));
        assertElapsed(result, 0);
    }

    @ParameterizedTest @ValueSource(strings = {"once(99)", "once(random())", "once(duration())"})
    void onceKeepsAnExistingValueWhenTheQuestionIsReached(String expression) throws Exception {
        LoadedForm form = amendedForm("<answer>17</answer>",
                here("answer", "decimal", expression, "readonly=\"true()\""), input("answer"));
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        assertEquals(17, integer(result, "answer"));
        assertElapsed(result, 0);
        assertEquals(0, result.answered());
    }

    @Test void onceWithAnEmptyValueCapturesTimeAtTheQuestionAndSurvivesLaterAnswers() throws Exception {
        LoadedForm form = amendedForm("<first/><snapshot/><second/>", bind("first", "string", "")
                + here("snapshot", "int", "once(duration())", "readonly=\"true()\"") + bind("second", "string", ""),
                input("first") + input("snapshot") + input("second"));
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        long snapshot = integer(result, "snapshot");
        assertTrue(snapshot >= 5 && snapshot <= 60);
        assertTrue(seconds(result) - snapshot >= 5 && seconds(result) - snapshot <= 60);
    }

    @Test void randomAndUuidCalculationsAreSeededAndReproducible() throws Exception {
        LoadedForm form = amendedForm("<draw/><id/>", here("draw", "decimal", "random()", "readonly=\"true()\"")
                + here("id", "string", "uuid()", "readonly=\"true()\""), input("draw") + input("id"));
        var walker = new FormWalker(form, completeConfig());
        Submission first = walker.walk(21);
        walker.walk(5);
        assertSameSubmission(first, walker.walk(21));
        assertSameSubmission(first, new FormWalker(form, completeConfig()).walk(21));
        var ids = new HashSet<String>();
        var draws = new HashSet<String>();
        for (int n = 1; n <= 20; n++) {
            Submission result = walker.walk(n);
            double draw = Double.parseDouble(value(result, "draw"));
            assertTrue(draw >= 0 && draw < 1);
            String id = value(result, "id");
            assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"), id);
            ids.add(id);
            draws.add(value(result, "draw"));
        }
        assertEquals(20, ids.size());
        assertTrue(draws.size() > 1);
        Submission otherSeed = new FormWalker(form, completeConfig().withSeed(919191)).walk(21);
        assertNotEquals(value(first, "id"), value(otherSeed, "id"));
    }

    @Test void namespaceUriIsRecognizedRegardlessOfPrefix() throws Exception {
        String fixture = resource("amendment1/template.xml").replace("xmlns:ct=", "xmlns:other=")
                .replace("__INSTANCE__", "<answer/>")
                .replace("__BINDS__", bind("answer", "int", "other:calculate-here=\"42\" readonly=\"true()\""))
                .replace("__BODY__", input("answer"));
        Submission result = new FormWalker(loadXml(fixture), completeConfig()).walk(1);
        assertEquals(42, integer(result, "answer"));
    }

    @Test void aCalculateHereBindWithoutABodyQuestionIsNeverReached() throws Exception {
        LoadedForm form = amendedForm("<hidden>kept</hidden><answer/>",
                here("hidden", "string", "'not reached'", "") + bind("answer", "string", ""), input("answer"));
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        assertEquals("kept", value(result, "hidden"));
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
    }

    @Test void todayUsesTheSimulatedDateAtTheCalculateHereQuestion() throws Exception {
        LoadedForm form = amendedForm("<today_here/>", here("today_here", "date", "today()", "readonly=\"true()\""), input("today_here"));
        Submission result = walk(form, "\"timeZone\":\"+14:00\",\"start\":\"2040-02-28T23:59:58\",\"fieldworkDays\":1");
        assertEquals(result.start().toLocalDate().toString(), value(result, "today_here"));
        assertEquals(0, result.answered());
        assertElapsed(result, 0);
    }

    @ParameterizedTest @ValueSource(strings = {"calculate-here=\"99\"", "xmlns:wrong=\"urn:other\" wrong:calculate-here=\"99\""})
    void unqualifiedAndWrongNamespaceAttributesAreNotCalculateHere(String attr) throws Exception {
        LoadedForm form = amendedForm("<answer>17</answer>", bind("answer", "int", attr + " readonly=\"true()\""), input("answer"));
        Submission result = new FormWalker(form, completeConfig()).walk(1);
        assertEquals(17, integer(result, "answer"));
        assertTrue(result.warnings().isEmpty());
        assertElapsed(result, 0);
    }
}
