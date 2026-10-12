package ctosim;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment7Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@Timeout(180)
class FormWalkerAmendment7ProfilesTest {
    private static final String FIELDS = "\"fields\":{\"profile_name\":{\"expr\":\"profile()\"},"
            + "\"branch\":{\"value\":\"y\",\"when\":[{\"if\":\"profile() = 'a'\",\"value\":\"x\"}]},"
            + "\"again\":{\"expr\":\"profile()\"}}";

    @Test void acceptanceProfilesAndConditionalAnswersAgreeForSubmissionsOneToOneHundred() throws Exception {
        FormWalker walker = new FormWalker(fixture7("profiles.xml"), settings7("\"profiles\":{\"a\":1,\"b\":3}," + FIELDS));
        int a = 0;
        for (int n = 1; n <= 100; n++) {
            Submission result = walker.walk(n);
            String profile = value(result, "profile_name");
            assertTrue(Set.of("a", "b").contains(profile), profile);
            assertEquals(profile, value(result, "again"));
            assertEquals(profile.equals("a") ? "x" : "y", value(result, "branch"));
            if (profile.equals("a")) a++;
            assertSameSubmission(result, walker.walk(n));
            assertEquals(result.submitted(), walker.walk(n).submitted());
            assertTrue(result.warnings().isEmpty(), result.warnings().toString());
        }
        assertTrue(a > 0 && a < 100, "Both profiles occur");
        assertUniformBin(a, 100, 0.25, "Acceptance profile a");
    }

    @Test void profileFrequenciesFollowUnnormalizedFractionalWeightsAndNeverChooseZeroWeight() throws Exception {
        FormWalker walker = new FormWalker(single("string", ""), settings7(
                "\"profiles\":{\"zero\":0,\"a\":0.25,\"b\":0.75}," + field7("\"expr\":\"profile()\"")));
        int a = 0;
        int draws = 1200;
        for (int n = 1; n <= draws; n++) {
            String profile = value(walker.walk(n), "answer");
            assertTrue(Set.of("a", "b").contains(profile), profile);
            if (profile.equals("a")) a++;
        }
        assertUniformBin(a, draws, 0.25, "Weighted profile a");
    }

    @ParameterizedTest @ValueSource(strings = {"single", "Élodie 東京", "O'Neil \"quoted\""})
    void aSinglePositiveProfilePreservesItsExactNameInExpressions(String name) throws Exception {
        FormWalker walker = new FormWalker(single("string", ""), settings7(
                "\"profiles\":{\"zero\":0," + quote(name) + ":7}," + field7("\"expr\":\"profile()\"")));
        for (int n = 1; n <= 10; n++) assertEquals(name, value(walker.walk(n), "answer"));
    }

    @Test void absentProfilesGiveAnEmptyExpressionAndAnEmptyStringInConditions() throws Exception {
        LoadedForm form = form("<answer>old</answer><branch/>", bind("answer", "string", "")
                + bind("branch", "string", ""), input("answer") + input("branch"));
        SimulationConfig cfg = settings7("\"fields\":{\"answer\":{\"expr\":\"profile()\"},"
                + "\"branch\":{\"value\":\"wrong\",\"when\":["
                + rule7("profile() = ''", "\"value\":\"empty-profile\"") + "]}}");
        Submission result = new FormWalker(form, cfg).walk(1);
        assertEquals("", value(result, "answer"));
        assertEquals("empty-profile", value(result, "branch"));
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test void profileReplayIsIndependentOfWalkOrderAndPreviousWalkers() throws Exception {
        String members = "\"profiles\":{\"a\":1,\"b\":3}," + FIELDS;
        FormWalker original = new FormWalker(fixture7("profiles.xml"), settings7(members));
        FormWalker reloaded = new FormWalker(fixture7("profiles.xml"), settings7(members));
        FormWalker unrelated = new FormWalker(fixture7("profiles.xml"), settings7("\"profiles\":{\"other\":1}," + FIELDS));
        for (int n : new int[] {19, 1, 100, 7, 19, 2}) {
            Submission expected = original.walk(n);
            unrelated.walk(n);
            reloaded.walk(n + 500);
            Submission actual = reloaded.walk(n);
            assertSameSubmission(expected, actual);
            assertEquals(expected.submitted(), actual.submitted());
        }
    }

    @Test void profileSequenceDependsOnTheSeedAndSubmissionNumber() throws Exception {
        SimulationConfig cfg = settings7("\"profiles\":{\"a\":1,\"b\":1}," + field7("\"expr\":\"profile()\""));
        FormWalker first = new FormWalker(single("string", ""), cfg);
        FormWalker second = new FormWalker(single("string", ""), cfg.withSeed(881912));
        Set<String> profiles = new HashSet<>();
        boolean difference = false;
        for (int n = 1; n <= 80; n++) {
            String profile = value(first.walk(n), "answer");
            profiles.add(profile);
            difference |= !profile.equals(value(second.walk(n), "answer"));
        }
        assertEquals(Set.of("a", "b"), profiles);
        assertTrue(difference, "Changing seed changes at least one profile over 80 independent submissions");
    }

    @Test void drawingAnUnusedProfileDoesNotChangeAnyOtherSubmissionDraw() throws Exception {
        String base = "\"repeats\":{\"min\":0,\"max\":4},\"speedViolationRate\":0.5";
        LoadedForm form = fixture7("randomness.xml");
        FormWalker none = new FormWalker(form, settings7(base));
        FormWalker weighted = new FormWalker(form, settings7(base + ",\"profiles\":{\"a\":1,\"b\":3}"));
        FormWalker single = new FormWalker(form, settings7(base + ",\"profiles\":{\"only\":1}"));
        for (int n = 1; n <= 40; n++) {
            Submission expected = none.walk(n);
            for (FormWalker walker : new FormWalker[] {weighted, single}) {
                Submission actual = walker.walk(n);
                assertSameSubmission(expected, actual);
                assertEquals(expected.submitted(), actual.submitted(), "Profile draw must not shift upload time");
            }
        }
    }

    @Test void unrelatedQuestionsAndRepeatCountsCannotChangeTheProfileOfASubmission() throws Exception {
        LoadedForm shortForm = single("string", "");
        LoadedForm longForm = form("<noise/><row jr:template=\"\"><item/></row><answer/>",
                bind("noise", "int", "") + bind("row/item", "string", "") + bind("answer", "string", ""),
                input("noise") + "<repeat nodeset=\"/data/row\">" + input("row/item") + "</repeat>" + input("answer"));
        SimulationConfig cfg = settings7("\"profiles\":{\"a\":1,\"b\":3},\"repeats\":{\"min\":2,\"max\":4},"
                + field7("\"expr\":\"profile()\""));
        FormWalker shortWalker = new FormWalker(shortForm, cfg);
        FormWalker longWalker = new FormWalker(longForm, cfg);
        for (int n = 1; n <= 40; n++) assertEquals(value(shortWalker.walk(n), "answer"), value(longWalker.walk(n), "answer"));
    }
}
