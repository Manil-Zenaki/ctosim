package ctosim;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendmentSupport.integer;
import static ctosim.FormWalkerAmendment2Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("walk temporarily changes the JVM default time zone")
class FormWalkerAmendment2SpeedViolationTest {
    @ParameterizedTest @ValueSource(strings = {"absent", "", "bad", "-1", "0"})
    void absentInvalidAndNonpositiveMinimaNeverQualifyForViolations(String minimum) throws Exception {
        String attribute = minimum.equals("absent") ? "" : "ct:minimum-seconds=\"" + minimum + "\"";
        var walker = new FormWalker(timed(attribute), settings(1));
        for (int n = 1; n <= 10; n++) {
            Submission result = walker.walk(n);
            assertEquals("", value(result, "violations"));
            assertEquals("", value(result, "violated"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"0.001", "5", "30", "100"})
    void anyPositiveMinimumQualifiesEvenWhenTheClockDrawAlreadyExceedsIt(String minimum) throws Exception {
        var walker = new FormWalker(timed("ct:minimum-seconds=\"" + minimum + "\""), settings(1));
        for (int n = 1; n <= 10; n++) {
            Submission result = walker.walk(n);
            assertEquals(1, integer(result, "violations"));
            assertEquals("answer", value(result, "violated"));
        }
    }

    @Test void zeroRateClearsExistingReportValuesInsteadOfWritingZero() throws Exception {
        LoadedForm form = amended2("<answer/><violations>99</violations><violated>stale</violated>",
                bind("answer", "string", "ct:minimum-seconds=\"90\"") + REPORT_BINDS, input("answer"));
        Submission result = new FormWalker(form, settings(0)).walk(1);
        assertEquals(1, count(result, "violations"));
        assertEquals(1, count(result, "violated"));
        assertEquals("", value(result, "violations"));
        assertEquals("", value(result, "violated"));
    }

    @Test void emptyInterviewHasNoViolationValuesAndNoTimeAdvance() throws Exception {
        Submission result = new FormWalker(amended2(REPORTS, REPORT_BINDS, ""), settings(1)).walk(1);
        assertEquals("", value(result, "violations"));
        assertEquals("", value(result, "violated"));
        assertEquals(0, seconds(result));
        assertEquals(0, result.answered());
    }

    @Test void violationDoesNotAlterFixedAnswersOrTheRequiredClockAdvance() throws Exception {
        LoadedForm form = timed("ct:minimum-seconds=\"100\"");
        String fixed = ",\"seed\":71,\"fields\":{\"answer\":{\"value\":\"unchanged\"}}";
        Submission no = walk(form, "\"speedViolationRate\":0" + fixed);
        Submission yes = walk(form, "\"speedViolationRate\":1" + fixed);
        assertEquals("unchanged", value(yes, "answer"));
        assertEquals(value(no, "answer"), value(yes, "answer"));
        assertEquals(100, seconds(no));
        assertEquals(100, seconds(yes));
        assertEquals(1, yes.answered());
    }

    @Test void listUsesNavigationOrderNotInstanceOrBindOrderAndPreservesUnicode() throws Exception {
        LoadedForm form = amended2("<last/><réponse/><first/>" + REPORTS,
                bind("last", "string", "ct:minimum-seconds=\"1\"")
                + bind("réponse", "string", "ct:minimum-seconds=\"1\"")
                + bind("first", "string", "ct:minimum-seconds=\"1\"") + REPORT_BINDS,
                input("first") + input("réponse") + input("last"));
        Submission result = new FormWalker(form, settings(1)).walk(1);
        assertEquals(3, integer(result, "violations"));
        assertEquals("first,réponse,last", value(result, "violated"));
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void nestedCountedAndUncountedRepeatsUseActualSiblingPositionsAndGlobalTotals(int children) throws Exception {
        LoadedForm form = amended2("<parents>2</parents><main><resp_name/><parent jr:template=\"\">"
                + "<member jr:template=\"\"><member_name/><local_count/><local_list/></member>"
                + "</parent></main>" + REPORTS,
                bind("parents", "int", "")
                + bind("main/resp_name", "string", "ct:minimum-seconds=\"70\"")
                + bind("main/parent/member/member_name", "string", "ct:minimum-seconds=\"70\"")
                + bind("main/parent/member/local_count", "int", "ct:speed-violations=\"count\"")
                + bind("main/parent/member/local_list", "string", "ct:speed-violations=\"list\"") + REPORT_BINDS,
                "<group ref=\"/data/main\">" + input("main/resp_name")
                + "<repeat nodeset=\"/data/main/parent\" jr:count=\"/data/parents\">"
                + "<repeat nodeset=\"/data/main/parent/member\">" + input("main/parent/member/member_name")
                + "</repeat></repeat></group>");
        Submission result = walk(form, "\"missingRate\":0,\"speedViolationRate\":1,\"repeats\":{\"min\":"
                + children + ",\"max\":" + children + "}");
        List<String> paths = new ArrayList<>();
        paths.add("main[1]/resp_name");
        for (int p = 1; p <= 2; p++) {
            assertEquals(children, count(result, "main/parent[" + p + "]/member"));
            for (int c = 1; c <= children; c++)
                paths.add("main[1]/parent[" + p + "]/member[" + c + "]/member_name");
        }
        String expected = String.join(",", paths);
        assertEquals(paths.size(), integer(result, "violations"));
        assertEquals(expected, value(result, "violated"));
        assertEquals(70L * paths.size(), seconds(result));
        for (int p = 1; p <= 2; p++) for (int c = 1; c <= children; c++) {
            String row = "main/parent[" + p + "]/member[" + c + "]/";
            assertEquals(paths.size(), integer(result, row + "local_count"));
            assertEquals(expected, value(result, row + "local_list"));
        }
    }

    @Test void everyRelevantReportIsSetIncludingReadonlyFieldsAndFieldsWithoutControls() throws Exception {
        LoadedForm form = amended2("<answer/>" + REPORTS + "<copy_count/><copy_list/><hidden_count/><hidden_list/>",
                bind("answer", "string", "ct:minimum-seconds=\"70\"") + REPORT_BINDS
                + bind("copy_count", "int", "alias:speed-violations=\"count\" readonly=\"true()\"")
                + bind("copy_list", "string", "alias:speed-violations=\"list\" readonly=\"true()\"")
                + bind("hidden_count", "int", "ct:speed-violations=\"count\" relevant=\"false()\"")
                + bind("hidden_list", "string", "ct:speed-violations=\"list\" relevant=\"false()\""),
                input("copy_count") + input("copy_list") + input("answer"));
        Submission result = new FormWalker(form, settings(1)).walk(1);
        assertEquals(1, integer(result, "violations"));
        assertEquals(1, integer(result, "copy_count"));
        assertEquals("answer", value(result, "violated"));
        assertEquals("answer", value(result, "copy_list"));
        assertEquals(0, count(result, "hidden_count"));
        assertEquals(0, count(result, "hidden_list"));
        assertEquals(1, result.answered());
        assertEquals(70, seconds(result));
    }

    @ParameterizedTest @ValueSource(strings = {"speed-violations", "other:speed-violations"})
    void reportAttributesInOtherNamespacesDoNotOverwriteDefaults(String attribute) throws Exception {
        LoadedForm form = amended2("<answer/><n>42</n><s>kept</s>",
                bind("answer", "string", "ct:minimum-seconds=\"1\"")
                + bind("n", "int", attribute + "=\"count\"")
                + bind("s", "string", attribute + "=\"list\""), input("answer"));
        Submission result = new FormWalker(form, settings(1)).walk(1);
        assertEquals(42, integer(result, "n"));
        assertEquals("kept", value(result, "s"));
    }

    @Test void reportUpdatesPropagateToCalculationsAndRelevance() throws Exception {
        LoadedForm form = amended2("<answer/>" + REPORTS + "<double/><message/><visible>kept</visible>",
                bind("answer", "string", "ct:minimum-seconds=\"70\"") + REPORT_BINDS
                + bind("double", "int", "calculate=\"/data/violations * 2\"")
                + bind("message", "string", "calculate=\"concat(/data/violated, ':', /data/double)\"")
                + bind("visible", "string", "relevant=\"/data/violations &gt; 0\""), input("answer"));
        Submission result = new FormWalker(form, settings(1)).walk(1);
        assertEquals(2, integer(result, "double"));
        assertEquals("answer:2", value(result, "message"));
        assertEquals("kept", value(result, "visible"));
    }

    @Test void reportsArePopulatedBeforeTheFinalDurationRecalculation() throws Exception {
        LoadedForm form = amended2("<marker/><answer/>" + REPORTS,
                bind("marker", "string", "calculate=\"if(duration() &gt; 0, once(if(/data/violations = 1, 'ready', 'too-early')), '')\"")
                + bind("answer", "string", "ct:minimum-seconds=\"70\"") + REPORT_BINDS, input("answer"));
        Submission result = new FormWalker(form, settings(1)).walk(1);
        assertEquals("ready", value(result, "marker"));
    }

    @Test void clearingAStaleCountAlsoRefreshesItsDependents() throws Exception {
        LoadedForm form = amended2("<violations>9</violations><violated>old</violated><message/>",
                REPORT_BINDS + bind("message", "string", "calculate=\"concat('count=', /data/violations, ';list=', /data/violated)\""), "");
        Submission result = new FormWalker(form, settings(0)).walk(1);
        assertEquals("count=;list=", value(result, "message"));
    }

    @Test void reportRelevanceIsEvaluatedAtTheEndAfterAnswersHaveChangedIt() throws Exception {
        LoadedForm form = amended2("<gate/>" + REPORTS,
                bind("gate", "int", "ct:minimum-seconds=\"70\"")
                + bind("violations", "int", "ct:speed-violations=\"count\" relevant=\"/data/gate = 1\"")
                + bind("violated", "string", "ct:speed-violations=\"list\" relevant=\"/data/gate = 1\""), input("gate"));
        Submission result = walk(form, "\"speedViolationRate\":1,\"fields\":{\"gate\":{\"value\":1}}");
        assertEquals(1, integer(result, "violations"));
        assertEquals("gate", value(result, "violated"));
    }

    @Test void skippedRepeatInstancesDoNotRenumberThePathsOfLaterViolations() throws Exception {
        LoadedForm form = amended2("<size>3</size><member jr:template=\"\"><name/></member>" + REPORTS,
                bind("size", "int", "")
                + bind("member/name", "string", "ct:minimum-seconds=\"70\" relevant=\"position(..) != 2\"")
                + REPORT_BINDS,
                "<repeat nodeset=\"/data/member\" jr:count=\"/data/size\">" + input("member/name") + "</repeat>");
        Submission result = new FormWalker(form, settings(1)).walk(1);
        assertEquals(2, integer(result, "violations"));
        assertEquals("member[1]/name,member[3]/name", value(result, "violated"));
        assertEquals(140, seconds(result));
    }

    @Test void partialRateReportsExactlyTheSelectedPathsInOrderWithoutDuplicates() throws Exception {
        StringBuilder instance = new StringBuilder(REPORTS);
        StringBuilder binds = new StringBuilder(REPORT_BINDS);
        StringBuilder body = new StringBuilder();
        List<String> ordered = List.of("a", "b", "c", "d", "e", "f");
        for (String name : ordered) {
            instance.append('<').append(name).append("/>");
            binds.append(bind(name, "string", "ct:minimum-seconds=\"1\""));
            body.append(input(name));
        }
        var walker = new FormWalker(amended2(instance.toString(), binds.toString(), body.toString()), settings(0.37));
        int[] frequencies = new int[ordered.size()];
        int draws = 800;
        for (int n = 1; n <= draws; n++) {
            Submission result = walker.walk(n);
            String list = value(result, "violated");
            if (list.isEmpty()) {
                assertEquals("", value(result, "violations"));
                continue;
            }
            String[] paths = list.split(",", -1);
            assertEquals(paths.length, integer(result, "violations"));
            int previous = -1;
            for (String path : paths) {
                int index = ordered.indexOf(path);
                assertTrue(index > previous, "Paths are known, unique, and in navigation order: " + list);
                frequencies[index]++;
                previous = index;
            }
        }
        for (int i = 0; i < frequencies.length; i++)
            assertUniformBin(frequencies[i], draws, 0.37, "question " + ordered.get(i));
    }

    @ParameterizedTest @ValueSource(doubles = {0.1, 0.37, 0.8})
    void configuredRateControlsTheFrequencyOfViolations(double rate) throws Exception {
        var walker = new FormWalker(timed("ct:minimum-seconds=\"1\""), settings(rate));
        int violations = 0;
        int draws = 2500;
        for (int n = 1; n <= draws; n++) {
            Submission result = walker.walk(n);
            if (value(result, "violations").isEmpty()) assertEquals("", value(result, "violated"));
            else {
                assertEquals(1, integer(result, "violations"));
                assertEquals("answer", value(result, "violated"));
                violations++;
            }
        }
        assertUniformBin(violations, draws, rate, "violation probability");
    }

    @Test void violationStateIsResetAndReplayedAcrossWalkOrderAndSeparateLoads() throws Exception {
        String attrs = "ct:minimum-seconds=\"70\"";
        var first = new FormWalker(timed(attrs), settings(0.37));
        var second = new FormWalker(timed(attrs), settings(0.37));
        List<Submission> expected = new ArrayList<>();
        for (int n = 1; n <= 60; n++) expected.add(first.walk(n));
        for (int n = 60; n >= 1; n--) {
            assertSameSubmission(expected.get(n - 1), first.walk(n));
            assertSameSubmission(expected.get(n - 1), second.walk(n));
            assertEquals(expected.get(n - 1).submitted(), second.walk(n).submitted());
        }
        assertTrue(expected.stream().anyMatch(s -> {
            try { return value(s, "violations").isEmpty(); } catch (Exception e) { throw new AssertionError(e); }
        }), "The sample must include submissions without violations");
        assertTrue(expected.stream().anyMatch(s -> {
            try { return !value(s, "violations").isEmpty(); } catch (Exception e) { throw new AssertionError(e); }
        }), "The sample must also include violations");
    }

    @Test void changingSeedChangesTheViolationSequenceOverASample() throws Exception {
        LoadedForm form = timed("ct:minimum-seconds=\"1\"");
        var first = new FormWalker(form, settings(0.5).withSeed(1));
        var second = new FormWalker(form, settings(0.5).withSeed(2));
        boolean different = false;
        for (int n = 1; n <= 80; n++)
            different |= !value(first.walk(n), "violations").equals(value(second.walk(n), "violations"));
        assertTrue(different, "No particular seed-mixing algorithm is prescribed");
    }
}
