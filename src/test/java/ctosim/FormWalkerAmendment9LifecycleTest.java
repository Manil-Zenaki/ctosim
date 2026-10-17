package ctosim;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerAmendment9Support.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(120)
class FormWalkerAmendment9LifecycleTest {
    @Test void defaultCountOutsideSmallRangeIsKeptWithoutAnsweringOrAdvancingTime() throws Exception {
        Submission result = run9(direct9("int", "7", "required=\"true()\"", input("answer")), 2, 2, "");
        assertCount9(result, 7);
        assertEquals(0, result.answered());
        assertEquals(result.start(), result.end());
    }

    @Test void explicitOverrideStillDisablesKeepingAnExistingCount() throws Exception {
        Submission result = run9(direct9("int", "7", "", input("answer")), 2, 2, field9("\"missingRate\":0"));
        assertCount9(result, 2);
        assertEquals(1, result.answered());
    }

    @Test void invalidDefaultIsRegeneratedFromTheSmallRange() throws Exception {
        Submission result = run9(direct9("int", "7", "constraint=\". &lt;= 3\"", input("answer")), 2, 2, "");
        assertCount9(result, 2);
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
    }

    @Test void readOnlyCountIsNotOverriddenBySyntheticBoundsOrConfiguredValues() throws Exception {
        Submission result = run9(direct9("int", "7", "readonly=\"true()\"", input("answer")),
                2, 2, field9("\"value\":4"));
        assertCount9(result, 7);
        assertEquals(0, result.answered());
        assertEquals(result.start(), result.end());
    }

    @Test void irrelevantCountQuestionIsNotAnsweredJustBecauseItControlsARepeat() throws Exception {
        LoadedForm form = form("<answer/>" + row9("row"),
                bind("answer", "int", "relevant=\"false()\" required=\"true()\"")
                        + bind("row", "string", "relevant=\"false()\"") + rowBind9("row"),
                input("answer") + repeat9("row", "/data/answer"));
        Submission result = run9(form, 2, 2, "");
        assertEquals(0, count(result, "answer"));
        assertEquals(0, count(result, "row"));
        assertEquals(0, result.answered());
        assertEquals(result.start(), result.end());
    }

    @Test void constraintHintsMayRequireACountOutsideTheSmallRange() throws Exception {
        LoadedForm form = direct9("int", "", "required=\"true()\" constraint=\". &gt;= 6 and . &lt;= 8\"", input("answer"));
        FormWalker walker = new FormWalker(form, settings9(2, 2, ""));
        for (int n = 1; n <= 20; n++) {
            Submission result = walker.walk(n);
            int answer = Integer.parseInt(value(result, "answer"));
            assertTrue(answer >= 6 && answer <= 8);
            assertCount9(result, answer);
        }
    }

    @Test void constraintHintsNarrowTheAddedRangeInsteadOfReplacingItWithGlobalIntegerBounds() throws Exception {
        LoadedForm form = direct9("int", "", "required=\"true()\" constraint=\". &gt;= 2\"", input("answer"));
        FormWalker walker = new FormWalker(form, settings9(1, 3, ""));
        for (int n = 1; n <= 30; n++) {
            Submission result = walker.walk(n);
            int answer = Integer.parseInt(value(result, "answer"));
            assertTrue(answer == 2 || answer == 3, "Hints must intersect 1..3, got " + answer);
            assertCount9(result, answer);
        }
    }

    @Test void retriesStillRejectCountsWithinTheRangeThatViolateAConstraint() throws Exception {
        LoadedForm form = direct9("int", "", "required=\"true()\" constraint=\". mod 2 = 0\"", input("answer"));
        FormWalker walker = new FormWalker(form, settings9(1, 3, ""));
        for (int n = 1; n <= 20; n++) assertCount9(walker.walk(n), 2);
    }

    @Test void hintCandidatesCanRepairAFixedCountOutsideTheAddedRange() throws Exception {
        LoadedForm form = direct9("int", "", "required=\"true()\" constraint=\". = 7\"", input("answer"));
        Submission result = run9(form, 2, 2, field9("\"value\":1"));
        assertCount9(result, 7);
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void searchCanLeaveTheSmallRangeAfterAllDrawsFail(boolean explicitRange) throws Exception {
        LoadedForm form = direct9("int", "", "required=\"true()\" constraint=\". * 2 = 14\"", input("answer"));
        Submission result = run9(form, 2, 2, explicitRange ? field9("\"min\":1,\"max\":1") : "");
        assertCount9(result, 7);
        assertEquals(1, result.answered());
        assertElapsed(result, 1);
    }

    @Test void impossibleCountConstraintStillLeavesBlankAndReportsFailure() throws Exception {
        LoadedForm form = direct9("int", "", "required=\"true()\" constraint=\"false()\"", input("answer"));
        Submission result = run9(form, 2, 2, "");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertWarning(result, "answer", "constraint");
        assertWarning(result, "answer", "required");
        assertElapsed(result, 1);
    }

    @Test void endOfFormRepairStillSearchesBeyondSmallCountsWithoutExtraAccounting() throws Exception {
        LoadedForm form = form("<answer/><gate>0</gate>" + row9("row"),
                bind("answer", "int", "required=\"true()\" constraint=\"/data/gate = 0 or . * 2 = 14\"")
                        + bind("gate", "int", "") + rowBind9("row"),
                input("answer") + repeat9("row", "/data/answer") + input("gate"));
        Submission result = run9(form, 2, 2, "\"fields\":{\"gate\":{\"value\":1}}");
        assertEquals("7", value(result, "answer"));
        assertEquals(2, result.answered(), "Final repair does not increment navigation answer count");
        assertElapsed(result, 2);
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
        // Dynamic creation/removal of repeat instances after final repair is not specified here.
    }

    @Test void nestedCountQuestionsUseSmallRangesInEveryParentInstance() throws Exception {
        LoadedForm form = loadXml(resource("amendment9/nested-counts.xml"));
        Submission result = run9(form, 2, 2, "");
        assertEquals("2", value(result, "households"));
        assertEquals(2, count(result, "household"));
        for (int parent = 1; parent <= 2; parent++) {
            String path = "household[" + parent + "]";
            assertEquals("2", value(result, path + "/members"));
            assertEquals(2, count(result, path + "/member"));
        }
        assertEquals(3, result.answered());
        assertElapsed(result, 3);
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test void nestedConditionalRangesAreReevaluatedInEachParent() throws Exception {
        LoadedForm form = loadXml(resource("amendment9/nested-counts.xml"));
        String fields = "\"fields\":{\"households\":{\"value\":2},\"members\":{\"when\":["
                + "{\"if\":\"position(..) = 1\",\"min\":4,\"max\":4}]}}";
        Submission result = run9(form, 2, 2, fields);
        assertEquals(2, count(result, "household"));
        assertEquals("4", value(result, "household[1]/members"));
        assertEquals(4, count(result, "household[1]/member"));
        assertEquals("2", value(result, "household[2]/members"));
        assertEquals(2, count(result, "household[2]/member"));
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test void unconfiguredCountsAreUniformAcrossBothInclusiveEndpoints() throws Exception {
        int draws = 700;
        int[] bins = new int[5];
        FormWalker walker = new FormWalker(direct9(), settings9(0, 4, ""));
        for (int n = 1; n <= draws; n++) {
            Submission result = walker.walk(n);
            int answer = Integer.parseInt(value(result, "answer"));
            assertTrue(answer >= 0 && answer <= 4, "Count outside 0..4: " + answer);
            bins[answer]++;
            assertCount9(result, answer);
        }
        for (int i = 0; i < bins.length; i++) assertUniformBin(bins[i], draws, 0.2, "count " + i);
    }

    @Test void smallCountsReplayAcrossLoadsAndInterleavedWalkOrderAndRespondToSeed() throws Exception {
        String fixture = resource("amendment9/nested-counts.xml");
        SimulationConfig cfg = settings9(0, 3, "");
        FormWalker original = new FormWalker(loadXml(fixture), cfg);
        FormWalker reloaded = new FormWalker(loadXml(fixture), settings9(0, 3, ""));
        FormWalker otherSeed = new FormWalker(loadXml(fixture), cfg.withSeed(907732));
        List<String> firstCounts = new ArrayList<>();
        List<String> changedCounts = new ArrayList<>();
        for (int n = 1; n <= 30; n++) {
            Submission expected = original.walk(n);
            original.walk(n + 100);
            reloaded.walk(80 - n);
            Submission replay = reloaded.walk(n);
            assertSameSubmission(expected, original.walk(n));
            assertSameSubmission(expected, replay);
            assertEquals(expected.submitted(), replay.submitted());
            firstCounts.add(value(expected, "households"));
            changedCounts.add(value(otherSeed.walk(n), "households"));
        }
        assertNotEquals(firstCounts, changedCounts, "Seed sensitivity is checked across a sequence, not one draw");
    }

    @Test void sameLoadedFormMayUseDifferentRepeatBoundsWithoutCrossWalkerLeakage() throws Exception {
        LoadedForm form = direct9();
        FormWalker two = new FormWalker(form, settings9(2, 2, ""));
        FormWalker four = new FormWalker(form, settings9(4, 4, ""));
        assertCount9(two.walk(1), 2);
        assertCount9(four.walk(1), 4);
        assertCount9(two.walk(1), 2);
    }
}
