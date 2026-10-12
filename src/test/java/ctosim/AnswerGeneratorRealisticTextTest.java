package ctosim;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.AnswerGeneratorRealisticSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AnswerGeneratorRealisticTextTest {
    @ParameterizedTest
    @ValueSource(strings = {"phone", "tel", "telephone", "mobile", "cell", "contact_phone",
            "contact-phone", "contact2phone3", "contactPhone", "PHONE", "CELL_2"})
    void phoneWordsProduceZeroAndNineAsciiDigits(String name) throws Exception {
        for (String value : draws(config(POOLS), text(name), null, 100)) {
            assertTrue(value.matches("0[0-9]{9}"), name + " -> " + value);
        }
    }

    @Test
    void phoneDigitsVaryAcrossAllNinePositions() throws Exception {
        int[][] counts = new int[9][10];
        for (String value : draws(config(""), text("phone"), null, DRAWS)) {
            assertTrue(value.matches("0[0-9]{9}"));
            for (int i = 0; i < 9; i++) counts[i][value.charAt(i + 1) - '0']++;
        }
        for (int[] position : counts) {
            assertTrue(java.util.Arrays.stream(position).filter(count -> count > 0).count() > 1,
                    "Each of the nine suffix positions contains random digits");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "mail", "contact_email", "contact-mail", "contact2mail",
            "contactEmail", "EMAIL"})
    void emailWordsUseNamesAndOneToNinetyNineAtExampleOrg(String name) throws Exception {
        Set<Integer> suffixes = new HashSet<>();
        for (String value : draws(config(POOLS), text(name), null, 2000)) {
            assertTrue(value.matches("ada\\.mensah[1-9][0-9]?@example\\.org"), value);
            suffixes.add(Integer.parseInt(value.substring("ada.mensah".length(), value.indexOf('@'))));
        }
        assertTrue(suffixes.size() > 1, "The numeric email suffix is a fresh random draw");
    }

    @Test
    void emailRemovesAccentsAndDropsPunctuationSpacesDigitsAndNonAsciiLetters() throws Exception {
        SimulationConfig config = config("\"textPools\":{\"firstName\":[\"\u00c9l-o die7\u674e\"],"
                + "\"lastName\":[\"D'\u00c1vila_2!\"]}");
        for (String value : draws(config, text("email"), null, 100)) {
            assertTrue(value.matches("elodie\\.davila[1-9][0-9]?@example\\.org"), value);
        }
    }

    static Stream<Arguments> poolNames() {
        return Stream.concat(Stream.concat(
                Stream.of("village", "town", "city", "locality", "district", "commune", "place", "location")
                        .map(name -> Arguments.of(name, "Kumasi")),
                Stream.of("name", "nom", "fullname").map(name -> Arguments.of(name, "Ada Mensah"))),
                Stream.concat(Stream.concat(
                        Stream.of("prenom", "firstname").map(name -> Arguments.of(name, "Ada")),
                        Stream.of("surname", "lastname").map(name -> Arguments.of(name, "Mensah"))),
                        Stream.of("comment", "comments", "remark", "remarks", "note", "notes", "specify",
                                        "other", "why", "reason", "describe", "description", "explain")
                                .map(name -> Arguments.of(name, "All recorded."))));
    }

    @ParameterizedTest
    @MethodSource("poolNames")
    void everyPoolKeywordSelectsItsSpecifiedPool(String name, String expected) throws Exception {
        for (String value : draws(config(POOLS), text(name), null, 8)) assertEquals(expected, value);
    }

    static Stream<Arguments> splitNames() {
        return Stream.of(Arguments.of("respondent_name", "Ada Mensah"),
                Arguments.of("respondent-name", "Ada Mensah"), Arguments.of("respondent9name2", "Ada Mensah"),
                Arguments.of("respondentName", "Ada Mensah"), Arguments.of("RESPONDENT_NAME", "Ada Mensah"),
                Arguments.of("__name--12", "Ada Mensah"), Arguments.of("homeCity", "Kumasi"),
                Arguments.of("extra2COMMENT", "All recorded."), Arguments.of("respondent_firstname", "Ada"),
                Arguments.of("respondentSurname", "Mensah"));
    }

    @ParameterizedTest
    @MethodSource("splitNames")
    void nameTokensSplitAtTheSpecifiedBoundariesAndCompareInLowerCase(String name, String expected)
            throws Exception {
        assertEquals(expected, draws(config(POOLS), text(name), null, 1).get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cellar", "telephonebook", "mailbox", "cityscape", "rename", "noteworthy",
            "otherness", "commentary", "villager", "answer", ""})
    void substringsAndUnrecognizedNamesRetainTheOriginalTextFallback(String name) throws Exception {
        for (String value : draws(config(POOLS), text(name), null, 30)) {
            assertTrue(value.startsWith(name + " "));
            assertTrue(value.substring(name.length() + 1).matches("[1-9][0-9]{0,3}"), value);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"village", "town", "city", "locality", "district", "commune", "place", "location"})
    void absentPlacePoolFallsThroughToTheOriginalTextRule(String name) throws Exception {
        for (String value : draws(config(""), text(name), null, 30)) {
            assertTrue(value.matches(name + " [1-9][0-9]{0,3}"), value);
        }
    }

    static Stream<Arguments> precedenceNames() {
        return Stream.of(Arguments.of("name_city", "Kumasi"),
                Arguments.of("reason_city_name", "Kumasi"), Arguments.of("comment_name", "Ada Mensah"),
                Arguments.of("city_comment", "Kumasi"));
    }

    @ParameterizedTest
    @MethodSource("precedenceNames")
    void firstMatchingTableLineWinsRegardlessOfWordOrder(String name, String expected) throws Exception {
        assertEquals(expected, draws(config(POOLS), text(name), null, 1).get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name_city_email_phone", "phone_mail", "mail_mobile", "cell_name"})
    void phoneLinePrecedesAllOtherMatchingLines(String name) throws Exception {
        assertTrue(draws(config(POOLS), text(name), null, 1).get(0).matches("0[0-9]{9}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name_email", "comment_city_mail", "city_name_email"})
    void emailLinePrecedesPlaceNameAndComment(String name) throws Exception {
        assertTrue(draws(config(POOLS), text(name), null, 1).get(0)
                .matches("ada\\.mensah[1-9][0-9]?@example\\.org"));
    }

    @Test
    void absentPlacePoolDoesNotBlockLaterMatchingLines() throws Exception {
        SimulationConfig config = config("\"textPools\":{\"firstName\":[\"Ada\"],\"lastName\":[\"Mensah\"],"
                + "\"comment\":[\"Recorded\"]}");
        assertEquals("Ada Mensah", draws(config, text("village_name"), null, 1).get(0));
        assertEquals("Recorded", draws(config, text("city_comment"), null, 1).get(0));
    }

    static Stream<Arguments> customPools() {
        return Stream.of(Arguments.of("firstname", "firstName"), Arguments.of("surname", "lastName"),
                Arguments.of("city", "place"), Arguments.of("comment", "comment"));
    }

    @ParameterizedTest
    @MethodSource("customPools")
    void customPoolsReplaceDefaultsAreUniformAndPreserveUnicodeVerbatim(String name, String pool)
            throws Exception {
        List<String> expected = List.of("\u00c9lodie", "\u674e", "two words", "  spaced  ");
        SimulationConfig config = config("\"textPools\":{\"" + pool
                + "\":[\"\u00c9lodie\",\"\u674e\",\"two words\",\"  spaced  \"]}");
        List<String> values = draws(config, text(name), null, DRAWS);
        for (String value : values) assertTrue(expected.contains(value), "Replacement must not mix in defaults");
        for (String value : expected) rate(values.stream().filter(value::equals).count(), DRAWS, 0.25);
    }

    @Test
    void fullNamesDrawUniformlyFromBothReplacementPools() throws Exception {
        SimulationConfig config = config("\"textPools\":{\"firstName\":[\"Ada\",\"Lin\"],"
                + "\"lastName\":[\"Mensah\",\"Silva\",\"Roy\"]}");
        List<String> values = draws(config, text("name"), null, DRAWS);
        for (String value : values) assertTrue(value.matches("(Ada|Lin) (Mensah|Silva|Roy)"), value);
        for (String first : List.of("Ada", "Lin")) {
            rate(values.stream().filter(s -> s.startsWith(first + " ")).count(), DRAWS, 0.5);
        }
        for (String last : List.of("Mensah", "Silva", "Roy")) {
            rate(values.stream().filter(s -> s.endsWith(" " + last)).count(), DRAWS, 1.0 / 3);
        }
    }

    @Test
    void emailDrawsNamesFromBothReplacementPools() throws Exception {
        SimulationConfig config = config("\"textPools\":{\"firstName\":[\"Ada\",\"Lin\"],"
                + "\"lastName\":[\"Mensah\",\"Silva\"]}");
        List<String> values = draws(config, text("mail"), null, DRAWS);
        for (String value : values) assertTrue(value.matches("(ada|lin)\\.(mensah|silva)[1-9][0-9]?@example\\.org"));
        rate(values.stream().filter(s -> s.startsWith("ada.")).count(), DRAWS, 0.5);
        rate(values.stream().filter(s -> s.contains(".mensah")).count(), DRAWS, 0.5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"firstname", "surname", "comment"})
    void builtInPoolsSupplyNonemptyVariedAnswersWithoutPrescribingTheirContent(String name) throws Exception {
        List<String> values = draws(config(""), text(name), null, 2000);
        assertTrue(values.stream().allMatch(v -> v != null && !v.isEmpty()));
        assertTrue(new HashSet<>(values).size() > 1, "Several languages/regions cannot be one repeated string");
    }

    @Test
    void replacingOnePoolLeavesTheOtherBuiltInPoolsAvailable() throws Exception {
        SimulationConfig config = config("\"textPools\":{\"firstName\":[\"OnlyFirst\"]}");
        List<String> names = draws(config, text("name"), null, 1000);
        assertTrue(names.stream().allMatch(v -> v.startsWith("OnlyFirst ") && v.length() > "OnlyFirst ".length()));
        assertTrue(new HashSet<>(names).size() > 1);
        List<String> comments = draws(config, text("comment"), null, 1000);
        assertTrue(comments.stream().allMatch(v -> v != null && !v.isEmpty()));
        assertTrue(new HashSet<>(comments).size() > 1);
    }

    @Test
    void replacementPoolsAreNotTruncatedToAFixedInternalSize() throws Exception {
        List<String> entries = java.util.stream.IntStream.range(0, 45).mapToObj(i -> "Entry" + i).toList();
        String jsonEntries = entries.stream().map(s -> "\"" + s + "\"").collect(java.util.stream.Collectors.joining(","));
        List<String> values = draws(config("\"textPools\":{\"firstName\":[" + jsonEntries + "]}"),
                text("firstname"), null, DRAWS);
        assertEquals(new HashSet<>(entries), new HashSet<>(values));
        for (String entry : entries) rate(values.stream().filter(entry::equals).count(), DRAWS, 1.0 / 45);
    }

    @ParameterizedTest
    @ValueSource(strings = {"firstname", "name", "comment", "phone", "email", "city"})
    void textsOverridePrecedesPlausibleAnswersAndHints(String name) throws Exception {
        Question question = hints(text(name), new ConstraintHints(null, null, null, null, 2, 2, "^ZZ$"));
        List<String> values = draws(config(POOLS), question, field("\"texts\":[\"\",\"\u674e\"]"), 1000);
        assertEquals(Set.of("", "\u674e"), new HashSet<>(values));
        rate(values.stream().filter(String::isEmpty).count(), values.size(), 0.5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"phone", "email", "city", "name", "comment"})
    void supportedPatternPrecedesEveryPlausibleTextLine(String name) throws Exception {
        Question question = hints(text(name), new ConstraintHints(null, null, null, null, null, null, "^Z[0-9]{3}$"));
        for (String value : draws(config(POOLS), question, null, 50)) assertTrue(value.matches("Z[0-9]{3}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"phone", "email", "city", "name", "comment"})
    void prefixPrecedesEveryPlausibleTextLine(String name) throws Exception {
        ConstraintHints hints = new ConstraintHints(null, null, null, null, null, null, null,
                "77", Set.of(), Set.of(), null, null, List.of(), List.of());
        for (String value : draws(config(POOLS), hints(text(name), hints), null, 50)) {
            assertTrue(value.matches("77[0-9]{8}"), value);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"phone", "email", "city", "name", "comment"})
    void lengthHintsPrecedeEveryPlausibleTextLine(String name) throws Exception {
        for (int length : new int[]{0, 3}) {
            ConstraintHints hints = new ConstraintHints(null, null, null, null, length, length, null);
            for (String value : draws(config(POOLS), hints(text(name), hints), null, 30)) {
                assertTrue(value.matches("[a-z]{" + length + "}"), value);
            }
        }
    }

    @Test
    void contradictoryLengthHintsFallThroughToPlausibleText() throws Exception {
        Question question = hints(text("firstname"), new ConstraintHints(null, null, null, null, 5, 2, null));
        assertEquals("Ada", draws(config(POOLS), question, null, 1).get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"numbers", "numbers_phone", "compact numbers wide"})
    void plausibleTextPrecedesTheEightDigitAppearanceRule(String appearance) throws Exception {
        for (String name : List.of("phone", "email", "city", "firstname", "comment")) {
            Question question = new Question(name, QuestionKind.TEXT, List.of(), appearance, true);
            String value = draws(config(POOLS), question, null, 1).get(0);
            switch (name) {
                case "phone" -> assertTrue(value.matches("0[0-9]{9}"));
                case "email" -> assertTrue(value.matches("ada\\.mensah[1-9][0-9]?@example\\.org"));
                case "city" -> assertEquals("Kumasi", value);
                case "firstname" -> assertEquals("Ada", value);
                case "comment" -> assertEquals("All recorded.", value);
                default -> fail("Uncovered fixture");
            }
        }
    }

    @Test
    void unrecognizedNameAndUnavailablePlacePoolStillUseNumbersAppearance() throws Exception {
        for (String name : List.of("answer", "village")) {
            Question question = new Question(name, QuestionKind.TEXT, List.of(), "numbers", true);
            for (String value : draws(config(""), question, null, 100)) assertTrue(value.matches("[0-9]{8}"));
        }
    }
}
