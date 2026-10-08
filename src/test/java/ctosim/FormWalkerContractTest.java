package ctosim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class FormWalkerContractTest {
    @Test void nullFormIsRejected() {
        assertThrows(NullPointerException.class, () -> new FormWalker(null, SimulationConfig.defaults()));
    }

    @Test void nullConfigurationIsRejected() throws Exception {
        LoadedForm loaded = single("string", "");
        assertThrows(NullPointerException.class, () -> new FormWalker(loaded, null));
    }

    @ParameterizedTest @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void submissionNumbersBelowOneAreRejected(int number) throws Exception {
        FormWalker walker = new FormWalker(single("string", ""), completeConfig());
        assertThrows(IllegalArgumentException.class, () -> walker.walk(number));
    }

    @ParameterizedTest @ValueSource(ints = {1, 101, Integer.MAX_VALUE})
    void anyPositiveNumberIsReportedEvenBeyondConfiguredBatchSize(int number) throws Exception {
        Submission submission = new FormWalker(single("string", ""), completeConfig()).walk(number);
        assertEquals(number, submission.number());
    }

    @Test void emptyFormHasNoAnswersWarningsOrElapsedTime() throws Exception {
        Submission submission = new FormWalker(form("", "", ""), completeConfig()).walk(1);
        assertAll(() -> assertEquals(0, submission.answered()),
                () -> assertEquals(submission.start(), submission.end()),
                () -> assertTrue(submission.warnings().isEmpty()),
                () -> assertEquals("walker-contract", xml(submission).getDocumentElement().getAttribute("id")));
    }

    @Test void allPrimaryInstanceElementKindsAreRecognizedAsOverrideNames() throws Exception {
        LoadedForm loaded = form("<question/><note/><calc/><group><nested/></group>"
                        + "<row jr:template=\"\"><member/></row>" + METADATA,
                bind("question", "string", "") + bind("note", "string", "readonly=\"true()\"")
                        + bind("calc", "int", "calculate=\"2\"") + METADATA_BINDS,
                input("question") + input("note") + "<repeat nodeset=\"/data/row\">"
                        + input("row/member") + "</repeat>");
        String fields = String.join(",", List.of("data", "question", "note", "calc", "group", "nested",
                        "row", "member", "started", "finished", "meta", "instanceID").stream()
                .map(n -> "\"" + n + "\":{\"value\":\"x\"}").toList());
        FormWalker walker = new FormWalker(loaded, config("\"fields\":{" + fields + "}"));
        assertTrue(walker.configWarnings().isEmpty());
    }

    @Test void unknownOverridesAreReportedOnceInNaturalNameOrder() throws Exception {
        LoadedForm loaded = form("<answer/><g><answer/></g>", bind("answer", "string", ""), input("answer"), "",
                "<instance id=\"catalog\"><root><secondary_only/></root></instance>");
        FormWalker walker = new FormWalker(loaded, config("""
            "fields":{"z_missing":{"value":"x"},"answer":{"value":"ok"},
            "secondary_only":{"value":"x"},"A_missing":{"value":"x"},
            "é_missing":{"value":"x"},"Answer":{"value":"x"}}
            """));
        List<String> names = List.of("A_missing", "Answer", "secondary_only", "z_missing", "é_missing");
        List<String> warnings = walker.configWarnings();
        assertEquals(names.size(), warnings.size());
        for (int i = 0; i < names.size(); i++) assertTrue(warnings.get(i).contains(names.get(i)));
    }

    @Test void configurationWarningsCannotBeModified() throws Exception {
        for (String members : List.of("", "\"fields\":{\"absent\":{\"value\":1}}")) {
            List<String> warnings = new FormWalker(single("string", ""), config(members)).configWarnings();
            assertThrows(UnsupportedOperationException.class, () -> warnings.add("injected"));
            if (!warnings.isEmpty()) {
                assertThrows(UnsupportedOperationException.class, () -> warnings.set(0, "changed"));
                assertThrows(UnsupportedOperationException.class, () -> warnings.remove(0));
            }
        }
    }

    @Test void submissionWarningsAreUnmodifiableIncludingTheEmptyList() throws Exception {
        for (String attrs : List.of("", "constraint=\"false()\"")) {
            List<String> warnings = new FormWalker(single("int", attrs), completeConfig()).walk(1).warnings();
            assertThrows(UnsupportedOperationException.class, () -> warnings.add("injected"));
            if (!warnings.isEmpty()) {
                assertThrows(UnsupportedOperationException.class, () -> warnings.set(0, "changed"));
                assertThrows(UnsupportedOperationException.class, () -> warnings.remove(0));
            }
        }
    }

    private LoadedForm randomForm() throws Exception {
        return form(METADATA + "<answer/><draw/><uid/><row jr:template=\"\"><member/></row>",
                METADATA_BINDS + bind("answer", "int", "") + bind("draw", "decimal", "calculate=\"random()\"")
                        + bind("uid", "string", "calculate=\"uuid()\"") + bind("row/member", "string", ""),
                input("answer") + "<repeat nodeset=\"/data/row\">" + input("row/member") + "</repeat>");
    }

    @Test void repeatedAndOutOfOrderWalksAreIdenticalAcrossWalkersAndSeparateLoads() throws Exception {
        LoadedForm loaded = randomForm();
        FormWalker first = new FormWalker(loaded, completeConfig());
        FormWalker shared = new FormWalker(loaded, completeConfig());
        FormWalker reloaded = new FormWalker(randomForm(), completeConfig());
        Submission expected = first.walk(7);
        String originalXml = expected.instanceXml();
        List<String> originalWarnings = new ArrayList<>(expected.warnings());
        for (int number : new int[]{1, 9, 2, 7, 3, 1}) {
            shared.walk(number);
            reloaded.walk(11 - number);
            first.walk(number);
        }
        assertSameSubmission(expected, first.walk(7));
        assertSameSubmission(expected, shared.walk(7));
        assertSameSubmission(expected, reloaded.walk(7));
        assertEquals(originalXml, expected.instanceXml(), "Earlier results remain stable");
        assertEquals(originalWarnings, expected.warnings());
    }

    @Test void warningResultsAreAlsoReproducibleAndDoNotLeakBetweenSubmissions() throws Exception {
        LoadedForm loaded = single("int", "constraint=\"false()\" required=\"true()\"");
        FormWalker walker = new FormWalker(loaded, completeConfig());
        Submission first = walker.walk(17);
        List<String> warnings = List.copyOf(first.warnings());
        Submission second = walker.walk(18);
        assertSameSubmission(first, new FormWalker(loaded, completeConfig()).walk(17));
        assertEquals(warnings, first.warnings());
        assertFalse(warnings.isEmpty());
        assertTrue(warnings.stream().allMatch(w -> w.startsWith("submission 17:")));
        assertTrue(second.warnings().stream().allMatch(w -> w.startsWith("submission 18:")));
    }

    @Test void differentSubmissionNumbersHaveDifferentInstanceIdentifiers() throws Exception {
        FormWalker walker = new FormWalker(randomForm(), completeConfig());
        HashSet<String> identifiers = new HashSet<>();
        for (int number = 1; number <= 30; number++) {
            String id = value(walker.walk(number), "meta/instanceID");
            assertTrue(id.matches("uuid:[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
            assertTrue(identifiers.add(id), "Repeated instanceID for number " + number);
        }
    }

    @Test void configurationSeedControlsEngineRandomnessAsWellAsWalkerRandomness() throws Exception {
        LoadedForm loaded = randomForm();
        Submission one = new FormWalker(loaded, completeConfig().withSeed(1)).walk(4);
        Submission two = new FormWalker(loaded, completeConfig().withSeed(2)).walk(4);
        assertNotEquals(value(one, "meta/instanceID"), value(two, "meta/instanceID"));
        assertNotEquals(value(one, "draw"), value(two, "draw"));
        assertNotEquals(value(one, "uid"), value(two, "uid"));
    }

    @ParameterizedTest @ValueSource(strings = {"answers", "clock", "start", "repeats"})
    void changingSeedChangesEachWalkerRandomOutputAcrossABatch(String component) throws Exception {
        LoadedForm loaded = component.equals("repeats") ? repeat(false, false) : single("int", "");
        FormWalker first = new FormWalker(loaded, completeConfig().withSeed(11939));
        FormWalker second = new FormWalker(loaded, completeConfig().withSeed(79331));
        List<Object> firstValues = new ArrayList<>(), secondValues = new ArrayList<>();
        for (int n = 1; n <= 32; n++) {
            for (int seedIndex = 0; seedIndex < 2; seedIndex++) {
                Submission result = (seedIndex == 0 ? first : second).walk(n);
                Object observation = switch (component) {
                    case "answers" -> value(result, "answer");
                    case "clock" -> seconds(result);
                    case "start" -> result.start();
                    case "repeats" -> count(result, "row");
                    default -> throw new AssertionError(component);
                };
                (seedIndex == 0 ? firstValues : secondValues).add(observation);
            }
        }
        assertNotEquals(firstValues, secondValues, component + " must use config.seed()");
    }

    @Test void timestampPreloadsMatchReportedStartAndPostProcessedEnd() throws Exception {
        Submission submission = new FormWalker(form(METADATA + "<answer/>",
                METADATA_BINDS + bind("answer", "string", ""), input("answer")), completeConfig()).walk(1);
        // JavaRosa includes a zone offset; compare local wall time, as specified by spec 06.
        assertEquals(submission.start(), java.time.OffsetDateTime.parse(value(submission, "started")).toLocalDateTime());
        assertEquals(submission.end(), java.time.OffsetDateTime.parse(value(submission, "finished")).toLocalDateTime());
        assertElapsed(submission, 1);
    }

    @Test void secondPrecisionInConfiguredStartIsPreserved() throws Exception {
        SimulationConfig config = config("\"start\":\"2040-02-28T23:59:37\",\"fieldworkDays\":1");
        Submission submission = new FormWalker(form("", "", ""), config).walk(1);
        assertStart(submission, config);
        assertEquals(37, submission.start().getSecond());
    }
}
