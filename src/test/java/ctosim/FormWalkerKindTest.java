package ctosim;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class FormWalkerKindTest {
    @ParameterizedTest
    @CsvSource({"int,-7,-7", "long,42,42", "decimal,-2.75,-2.75", "date,2040-02-29,2040-02-29"})
    void ordinaryAnswersUseEngineTypeConversion(String type, String answer, String serialized) throws Exception {
        Submission result = fixed(single(type, ""), answer);
        assertEquals(serialized, value(result, "answer"));
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"int", "long"})
    void integerAndLongTypesUseIntegerGeneratorRange(String type) throws Exception {
        Submission result = walk(single(type, ""), "\"missingRate\":0,\"integerRange\":{\"min\":17,\"max\":17}");
        assertEquals("17", value(result, "answer"));
        assertEquals(1, result.answered());
    }

    @Test void decimalTypeUsesDecimalGeneratorRange() throws Exception {
        Submission result = walk(single("decimal", ""), "\"missingRate\":0,\"decimalRange\":{\"min\":2.75,\"max\":2.75}");
        assertEquals(2.75, Double.parseDouble(value(result, "answer")));
        assertEquals(1, result.answered());
    }

    @ParameterizedTest @ValueSource(strings = {"int", "decimal"})
    void rangeControlsAreGeneratedUsingTheirDataType(String type) throws Exception {
        LoadedForm loaded = form("<answer/>", bind("answer", type, ""),
                "<range ref=\"/data/answer\" start=\"0\" end=\"100\" step=\"1\"><label>Range</label></range>");
        Submission result = walk(loaded, "\"missingRate\":0,\"integerRange\":{\"min\":17,\"max\":17},\"decimalRange\":{\"min\":2.75,\"max\":2.75}");
        assertEquals(type.equals("int") ? 17 : 2.75, Double.parseDouble(value(result, "answer")));
        assertEquals(1, result.answered());
    }

    @ParameterizedTest @CsvSource({"select1,select1", "select1,int", "select,select", "select,int"})
    void selectControlTakesPrecedenceOverItsDataType(String control, String type) throws Exception {
        LoadedForm loaded = select(control, type, "");
        FormWalker walker = new FormWalker(loaded, config("\"missingRate\":0,\"specialRate\":0,\"integerRange\":{\"min\":777,\"max\":777}"));
        for (int n = 1; n <= 20; n++) {
            Submission result = walker.walk(n);
            List<String> answers = Arrays.asList(value(result, "answer").split(" "));
            assertFalse(answers.isEmpty());
            assertTrue(Set.of("20", "10", "30").containsAll(answers));
            if (control.equals("select1")) assertEquals(1, answers.size());
            else assertTrue(answers.size() >= 1 && answers.size() <= 3);
            assertEquals(answers.size(), Set.copyOf(answers).size());
            assertEquals(1, result.answered());
            assertTrue(result.warnings().isEmpty());
        }
    }

    @Test void fixedSelectOneIsMatchedByValueRatherThanLabelOrIndex() throws Exception {
        Submission result = fixed(select("select1", "select1", ""), "10");
        assertEquals("10", value(result, "answer"));
        assertEquals(1, result.answered());
    }

    @Test void selectMultipleConvertsEverySpaceSeparatedValue() throws Exception {
        Submission result = fixed(select("select", "select", ""), "20 30");
        assertEquals(Set.of("20", "30"), Set.copyOf(Arrays.asList(value(result, "answer").split(" "))));
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    @ParameterizedTest @CsvSource({"select1,select1,999", "select1,select1,Ten", "select,select,20 999"})
    void anyUnofferedSelectValueRejectsTheWholeAnswer(String control, String type, String answer) throws Exception {
        Submission result = fixed(select(control, type, ""), answer);
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertWarning(result, "answer", answer);
        assertEquals(1, result.warnings().size());
        assertElapsed(result, 1);
    }

    @Test void rejectedRequiredSelectAlsoWarnsThatItIsRequired() throws Exception {
        Submission result = fixed(select("select1", "select1", "required=\"true()\""), "missing");
        assertWarning(result, "answer", "missing");
        assertWarning(result, "answer", "required");
    }

    @Test void filteredSelectsUseCurrentChoicesAndPreserveDisplayOrder() throws Exception {
        LoadedForm loaded = loadXml(resource("filtered-selects.xml"));
        FormWalker walker = new FormWalker(loaded, config("""
            "missingRate":0,"specialRate":0,"fields":{"region":{"value":"west"},
            "one":{"weights":{"a":1000,"m":1}}}
            """));
        List<String> order = List.of("z", "m", "b");
        for (int n = 1; n <= 80; n++) {
            Submission result = walker.walk(n);
            assertEquals("m", value(result, "one"), "Filtered-out weights must not create a choice");
            List<String> many = Arrays.asList(value(result, "many").split(" "));
            assertTrue(order.containsAll(many), many.toString());
            assertEquals(many, order.stream().filter(many::contains).toList(), "Display order, not lexical order");
            assertEquals(3, result.answered());
            assertTrue(result.warnings().isEmpty());
        }
    }

    @Test void fixedChoiceExcludedByFilterIsUnconvertible() throws Exception {
        Submission result = walk(loadXml(resource("filtered-selects.xml")), """
            "missingRate":0,"fields":{"region":{"value":"west"},"one":{"value":"a"}}
            """);
        assertEquals("", value(result, "one"));
        assertWarning(result, "one", "a");
        assertEquals(2, result.answered());
    }

    @Test void selectsWithNoCurrentlyOfferedChoicesAreLeftBlankAndClockStillMoves() throws Exception {
        Submission result = walk(loadXml(resource("filtered-selects.xml")), """
            "missingRate":0,"fields":{"region":{"value":"nowhere"},"one":{"value":"a"}}
            """);
        assertEquals("", value(result, "one"));
        assertEquals("", value(result, "many"));
        assertEquals(1, result.answered());
        assertElapsed(result, 3);
        assertTrue(result.warnings().isEmpty());
    }

    @Test void triggerUsesAcknowledgeGenerator() throws Exception {
        LoadedForm loaded = form("<answer/>", bind("answer", "string", ""),
                "<trigger ref=\"/data/answer\"><label>Acknowledge</label></trigger>");
        Submission result = new FormWalker(loaded, completeConfig()).walk(1);
        assertEquals("OK", value(result, "answer"));
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    @ParameterizedTest @CsvSource({"image/*,jpg", "audio/*,m4a", "video/*,mp4", "application/octet-stream,bin"})
    void captureControlsUseTheMatchingMediaKind(String mediaType, String extension) throws Exception {
        LoadedForm loaded = form("<answer/>", bind("answer", "binary", ""),
                "<upload ref=\"/data/answer\" mediatype=\"" + mediaType + "\"><label>Capture</label></upload>");
        Submission result = new FormWalker(loaded, completeConfig()).walk(1);
        assertTrue(value(result, "answer").matches("answer_[1-9][0-9]{0,5}\\." + extension));
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    @Test void barcodeDataTypeUsesTenDigitGenerator() throws Exception {
        Submission result = new FormWalker(single("barcode", ""), completeConfig()).walk(1);
        assertTrue(value(result, "answer").matches("[0-9]{10}"));
        assertEquals(1, result.answered());
    }

    @ParameterizedTest @ValueSource(strings = {"date", "dateTime"})
    void generatedDatesUseSimulatedInterviewDate(String type) throws Exception {
        FormWalker walker = new FormWalker(single(type, ""), config("\"start\":\"2040-02-29T08:00:00\",\"fieldworkDays\":3,\"missingRate\":0"));
        for (int n = 1; n <= 40; n++) {
            Submission result = walker.walk(n);
            String answer = value(result, "answer");
            LocalDate date = LocalDate.parse(answer.substring(0, 10));
            assertFalse(date.isBefore(result.start().toLocalDate().minusDays(364)));
            assertFalse(date.isAfter(result.start().toLocalDate()));
            if (type.equals("dateTime")) {
                java.time.LocalTime time = java.time.OffsetDateTime.parse(answer).toLocalTime();
                assertFalse(time.isBefore(java.time.LocalTime.of(6, 0)));
                assertFalse(time.isAfter(java.time.LocalTime.of(20, 0)));
                assertEquals(0, time.getSecond());
            }
            assertEquals(1, result.answered());
            assertTrue(result.warnings().isEmpty());
        }
    }

    @Test void generatedTimeUsesMinutePrecisionAndSpecifiedHours() throws Exception {
        FormWalker walker = new FormWalker(single("time", ""), completeConfig());
        for (int n = 1; n <= 40; n++) {
            Submission result = walker.walk(n);
            java.time.LocalTime time = java.time.LocalTime.parse(value(result, "answer").substring(0, 8));
            assertFalse(time.isBefore(java.time.LocalTime.of(6, 0)));
            assertFalse(time.isAfter(java.time.LocalTime.of(20, 0)));
            assertEquals(0, time.getSecond());
            assertEquals(1, result.answered());
        }
    }

    @ParameterizedTest @CsvSource({"geopoint,1", "geotrace,4", "geoshape,5"})
    void geographicKindsAreConvertedAndSerializedAsPointSequences(String type, int points) throws Exception {
        Submission result = new FormWalker(single(type, ""), completeConfig()).walk(1);
        String[] actual = value(result, "answer").split(";\\s*");
        assertEquals(points, actual.length);
        for (String point : actual) {
            String[] coordinates = point.trim().split("\\s+");
            assertEquals(4, coordinates.length);
            assertTrue(Math.abs(Double.parseDouble(coordinates[0])) <= 1.003);
            assertTrue(Math.abs(Double.parseDouble(coordinates[1])) <= 1.003);
            assertEquals(0, Double.parseDouble(coordinates[2]));
            assertEquals(5, Double.parseDouble(coordinates[3]));
        }
        if (points == 5) assertEquals(actual[0].trim(), actual[4].trim());
        assertEquals(1, result.answered());
        assertTrue(result.warnings().isEmpty());
    }

    static Stream<Arguments> unsupportedControls() {
        return Stream.of(
                Arguments.of("boolean", input("answer")),
                Arguments.of("string", "<rank ref=\"/data/answer\"><label>Rank</label>"
                        + "<item><label>A</label><value>a</value></item><item><label>B</label><value>b</value></item></rank>"),
                Arguments.of("binary", "<upload ref=\"/data/answer\" mediatype=\"osm/*\"><label>Map</label></upload>"));
    }

    @ParameterizedTest @MethodSource("unsupportedControls")
    void unsupportedControlOrTypeIsSkippedWithWarningAndWithoutClockAdvance(String type, String body) throws Exception {
        Submission result = new FormWalker(form("<answer/>", bind("answer", type, ""), body), completeConfig()).walk(1);
        assertEquals("", value(result, "answer"));
        assertEquals(0, result.answered());
        assertEquals(1, result.warnings().size());
        assertWarning(result, "answer");
        assertElapsed(result, 0);
    }

    @Test void readOnlyTakesPrecedenceOverUnsupportedType() throws Exception {
        Submission result = new FormWalker(single("boolean", "readonly=\"true()\""), completeConfig()).walk(1);
        assertEquals(0, result.answered());
        assertTrue(result.warnings().isEmpty());
        assertElapsed(result, 0);
    }

    @Test void existingValueTakesPrecedenceOverUnsupportedControl() throws Exception {
        LoadedForm loaded = form("<answer>a b</answer>", bind("answer", "string", ""),
                "<rank ref=\"/data/answer\"><label>Rank</label>"
                        + "<item><label>A</label><value>a</value></item><item><label>B</label><value>b</value></item></rank>");
        Submission result = new FormWalker(loaded, completeConfig()).walk(1);
        assertEquals("a b", value(result, "answer"));
        assertEquals(0, result.answered());
        assertTrue(result.warnings().isEmpty());
        assertElapsed(result, 0);
    }
}
