package ctosim;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment5Support.constraint;
import static org.junit.jupiter.api.Assertions.*;

/** Amendment 6: force all 50 proposals to fail, using constraints with no hint candidates. */
@Isolated
@Timeout(120)
class FormWalkerAmendment6SearchTest {
    static LoadedForm question(String type, String rule) throws Exception {
        return single(type, constraint(rule));
    }

    static SimulationConfig forced(String rejected, String extra) throws Exception {
        return config("\"seed\":606031,\"missingRate\":0,\"specialRate\":0,"
                + "\"fields\":{\"answer\":{\"value\":\"" + rejected + "\"}}"
                + (extra.isEmpty() ? "" : "," + extra));
    }

    static Submission searched(String type, String rule, String rejected, String extra) throws Exception {
        return new FormWalker(question(type, rule), forced(rejected, extra)).walk(1);
    }

    static void successful(Submission result) {
        assertEquals(1, result.answered(), "Search is one accepted navigation answer");
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
        assertElapsed(result, 1);
    }

    static String nonblank(Submission result, String path) throws Exception {
        String answer = value(result, path);
        assertFalse(answer.isEmpty(), () -> "Search must answer " + path + "; warnings: " + result.warnings());
        return answer;
    }

    @Test void firstNumericLevelWinsEvenWhenTheBroadLevelHasOtherValidAnswers() throws Exception {
        Submission result = searched("int", ". mod 7 = 3", "0",
                "\"integerRange\":{\"min\":9,\"max\":11}");
        assertEquals("10", value(result, "answer"));
        successful(result);
    }

    @ParameterizedTest @ValueSource(ints = {-10000, -9999, 0, 9999, 10000})
    void broadIntegerLevelIncludesBothEndpointsAndZero(int target) throws Exception {
        Submission result = searched("int", ". * 2 = " + (2 * target), "1",
                "\"integerRange\":{\"min\":1,\"max\":1}");
        assertEquals(Integer.toString(target), value(result, "answer"));
        successful(result);
    }

    @Test void firstLevelAlsoSearchesConfiguredValuesOutsideTheBroadRange() throws Exception {
        Submission result = searched("int", ". mod 20000 = 10003", "0",
                "\"integerRange\":{\"min\":10002,\"max\":10004}");
        assertEquals("10003", value(result, "answer"));
        successful(result);
    }

    @Test void aLargeDrawingRangeStillSearchesItsLastEndpoint() throws Exception {
        Submission result = searched("int", ". * 2 = 40000", "0",
                "\"integerRange\":{\"min\":0,\"max\":20000}");
        assertEquals("20000", value(result, "answer"));
        successful(result);
    }

    @ParameterizedTest @ValueSource(strings = {"-99.99", "-0.01", "0.01", "99.99"})
    void decimalBroadLevelCanFindCentValuesAbsentFromIntegerCandidates(String target) throws Exception {
        String doubled = new BigDecimal(target).multiply(BigDecimal.valueOf(2)).toPlainString();
        Submission result = searched("decimal", "round(. * 200) = "
                + new BigDecimal(target).multiply(BigDecimal.valueOf(200)).toPlainString()
                + " and . * 2 = " + doubled, "0",
                "\"decimalRange\":{\"min\":0,\"max\":0}");
        assertEquals(0, new BigDecimal(target).compareTo(new BigDecimal(nonblank(result, "answer"))));
        successful(result);
    }

    @Test void decimalFirstLevelIncludesNonIntegralRangeEndpoints() throws Exception {
        Submission result = searched("decimal", ". * 4 = 4005", "0",
                "\"decimalRange\":{\"min\":1001.25,\"max\":1001.75}");
        assertEquals(0, new BigDecimal("1001.25").compareTo(new BigDecimal(nonblank(result, "answer"))));
        successful(result);
    }

    @Test void dateSearchFallsBackOutsideTheDrawingYear() throws Exception {
        Submission result = searched("date", "format-date-time(., '%Y-%m-%d') = '2027-07-13'",
                "2026-01-01", "\"start\":\"2026-01-05T08:00:00\",\"fieldworkDays\":1");
        assertEquals("2027-07-13", value(result, "answer"));
        successful(result);
    }

    @Test void dateFirstLevelTakesPriorityOverMatchingDatesInTheBroadLevel() throws Exception {
        FormWalker walker = new FormWalker(question("date", "format-date-time(., '%d') = '13'"),
                forced("2000-01-01", ""));
        for (int n = 1; n <= 20; n++) {
            Submission result = walker.walk(n);
            LocalDate answer = LocalDate.parse(nonblank(result, "answer"));
            LocalDate today = result.start().toLocalDate();
            assertEquals(13, answer.getDayOfMonth());
            assertFalse(answer.isBefore(today.minusDays(364)));
            assertFalse(answer.isAfter(today));
            successful(result);
        }
    }

    @Test void datetimeSearchConvertsTheNoonCandidate() throws Exception {
        Submission result = searched("dateTime", "format-date-time(., '%Y-%m-%d %H:%M:%S') = '2027-07-13 12:00:00'",
                "2026-01-01T06:00:00", "\"start\":\"2026-01-05T08:00:00\",\"fieldworkDays\":1");
        assertTrue(value(result, "answer").startsWith("2027-07-13T12:00:00"));
        successful(result);
    }

    @ParameterizedTest @ValueSource(strings = {"00:00:00", "23:59:00", "00:00:01", "23:59:59"})
    void timeSearchCoversWholeDayMinutesAndFallsBackToSeconds(String target) throws Exception {
        Submission result = searched("time", "format-date-time(., '%H:%M:%S') = '" + target + "'",
                "06:00:00", "");
        assertTrue(value(result, "answer").startsWith(target), result.instanceXml());
        successful(result);
    }

    @ParameterizedTest @ValueSource(strings = {"string", "barcode"})
    void constraintLiteralsPrecedeGeneratorLevelsForTextAndBarcode(String type) throws Exception {
        // Both '@' and the broad-level string '11' pass; the literal-only level must win.
        Submission result = searched(type, "contains(., '@') or number(.) * 2 = 22", "bad", "");
        assertEquals("@", value(result, "answer"));
        successful(result);
    }

    @Test void literalsAreParsedWithBothQuoteStylesAndPreserveUnicode() throws Exception {
        Submission result = searched("string", "contains(., \"l'été 東京\")", "bad", "");
        assertEquals("l'été 東京", value(result, "answer"));
        successful(result);
    }

    @Test void rejectedLiteralLevelDoesNotPreventSearchingGeneratorLevels() throws Exception {
        Submission result = searched("string", "contains(., 'aa') and string-length(.) * 2 = 6", "bad", "");
        assertEquals("aaa", value(result, "answer"));
        successful(result);
    }

    @Test void searchPassesPrefixHintsToTheGeneratorAfterTheLiteralItselfFails() throws Exception {
        Submission result = searched("string", "starts-with(., 'AB') and string-length(.) * 2 = 10", "bad", "");
        assertEquals("AB000", value(result, "answer"));
        successful(result);
    }

    @Test void unconvertibleHintCandidateDoesNotPreventSearchingTheLaterLevels() throws Exception {
        Submission result = searched("int", ". = 'not-number' or . * 2 = 14", "0", "");
        assertEquals("7", value(result, "answer"));
        successful(result);
    }

    @ParameterizedTest @ValueSource(strings = {"string", "barcode"})
    void broadTextLevelSearchesHundredCharacterStrings(String type) throws Exception {
        Submission result = searched(type, "string-length(.) * 2 = 200", "bad", "");
        assertTrue(Set.of("a".repeat(100), "0".repeat(100), "1".repeat(100)).contains(value(result, "answer")));
        successful(result);
    }

    static String choices(boolean multiple, String... values) {
        String tag = multiple ? "select" : "select1";
        StringBuilder body = new StringBuilder("<" + tag + " ref=\"/data/answer\"><label>Answer</label>");
        for (String v : values) body.append("<item><label>").append(v).append("</label><value>")
                .append(v).append("</value></item>");
        return body.append("</").append(tag).append(">").toString();
    }

    @Test void selectOneSearchUsesOnlyOfferedChoicesEvenWithZeroDrawingWeight() throws Exception {
        LoadedForm form = form("<answer/>", bind("answer", "select1", constraint("contains(., 'z')")),
                choices(false, "bad", "zürich"));
        SimulationConfig cfg = config("\"seed\":606031,\"missingRate\":0,\"specialRate\":0,"
                + "\"fields\":{\"answer\":{\"weights\":{\"bad\":1,\"zürich\":0}}}");
        Submission result = new FormWalker(form, cfg).walk(1);
        assertEquals("zürich", value(result, "answer"));
        successful(result);
    }

    @Test void selectMultipleSearchFindsFourChoiceCombinationBeyondNormalDrawSize() throws Exception {
        LoadedForm form = form("<answer/>", bind("answer", "select", constraint("count-selected(.) * 2 = 8")),
                choices(true, "d", "b", "a", "c"));
        Submission result = new FormWalker(form, forced("d", "")).walk(1);
        assertEquals("d b a c", value(result, "answer"));
        successful(result);
    }

    @Test void hintCandidatesAreTriedBeforeConstraintLiteralSearch() throws Exception {
        Submission result = searched("string", "contains(., '@') or . = 'candidate'", "bad", "");
        assertEquals("candidate", value(result, "answer"));
        successful(result);
    }

    @Test void anAcceptedProposalDoesNotGetReplacedBySearch() throws Exception {
        Submission result = searched("string", "contains(., '@')", "person@example.org", "");
        assertEquals("person@example.org", value(result, "answer"));
        successful(result);
    }

    @Test void intentionallyMissingOptionalAnswerDoesNotTriggerSearch() throws Exception {
        Submission result = new FormWalker(question("string", "contains(., '@')"),
                config("\"seed\":606031,\"missingRate\":1")).walk(1);
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertTrue(result.warnings().isEmpty());
        assertElapsed(result, 1);
    }

    @Test void initialConversionFailureDoesNotTriggerConstraintSearch() throws Exception {
        Submission result = searched("int", ". mod 7 = 3", "not-an-integer", "");
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertWarning(result, "answer", "not-an-integer");
        assertElapsed(result, 1);
    }

    static Stream<Arguments> uniformLevels() {
        return Stream.of(
                Arguments.of("int", ". mod 7 = 3", "0", "\"integerRange\":{\"min\":3,\"max\":24}",
                        List.of("3", "10", "17", "24")),
                Arguments.of("string", "contains(., '@') or contains(., '#') or contains(., '@')", "bad", "",
                        List.of("@", "#")));
    }

    @ParameterizedTest @MethodSource("uniformLevels") @Timeout(180)
    void searchChoosesUniformlyFromAllAcceptedValuesAndDeduplicatesLiterals(
            String type, String rule, String rejected, String extra, List<String> accepted) throws Exception {
        FormWalker walker = new FormWalker(question(type, rule), forced(rejected, extra));
        Map<String, Integer> counts = new HashMap<>();
        int draws = 800;
        for (int n = 1; n <= draws; n++) {
            Submission result = walker.walk(n);
            String answer = value(result, "answer");
            assertTrue(accepted.contains(answer), result.instanceXml());
            counts.merge(answer, 1, Integer::sum);
        }
        for (String answer : accepted) assertUniformBin(counts.getOrDefault(answer, 0), draws,
                1.0 / accepted.size(), answer);
    }

    @Test void searchedAnswersReplayAcrossLoadsAndWalkOrderAndDependOnTheSeed() throws Exception {
        String rule = ". mod 7 = 3";
        SimulationConfig cfg = forced("0", "\"integerRange\":{\"min\":3,\"max\":24}");
        FormWalker first = new FormWalker(question("int", rule), cfg);
        FormWalker reloaded = new FormWalker(question("int", rule), cfg);
        FormWalker changedSeed = new FormWalker(question("int", rule), cfg.withSeed(606032));
        Set<String> firstAnswers = new HashSet<>();
        boolean seedChangesAnswer = false;
        for (int n = 1; n <= 30; n++) {
            Submission expected = first.walk(n);
            assertFalse(value(expected, "answer").isEmpty());
            reloaded.walk(100 - n);
            assertSameSubmission(expected, reloaded.walk(n));
            assertEquals(expected.submitted(), reloaded.walk(n).submitted());
            firstAnswers.add(value(expected, "answer"));
            seedChangesAnswer |= !value(expected, "answer").equals(value(changedSeed.walk(n), "answer"));
        }
        assertTrue(firstAnswers.size() > 1, "Uniform search must not always take its first accepted value");
        assertTrue(seedChangesAnswer, "The answers' random sequence is seeded by the configuration");
    }
}
