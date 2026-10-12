package ctosim;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.ConstraintHintsAmendment2Support.*;
import static ctosim.ConstraintHintsTestSupport.*;
import static ctosim.FormWalkerTestSupport.assertUniformBin;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintHintsAmendment2AlternativesTest {
    private static ConstraintHints mixed() {
        return hints().alternatives(hints().range(10, 11).build(), hints().range(20, 29).build(),
                hints().range(888, 888).build(), hints().range(-999, -999).build()).build();
    }

    @Test void specialRateChoosesGroupsAndEachAlternativeUniformlyRegardlessOfWidthOrSpecialValues() throws Exception {
        var generator = generator("\"specialRate\":0.2,\"specialValues\":[]");
        var q = question(QuestionKind.INTEGER, mixed());
        var random = new Random(SEED);
        int n = 30000; int[] bins = new int[4];
        for (int i = 0; i < n; i++) {
            int value = Integer.parseInt(generator.generate(q, TODAY, random));
            if (value >= 10 && value <= 11) bins[0]++;
            else if (value >= 20 && value <= 29) bins[1]++;
            else if (value == 888) bins[2]++;
            else if (value == -999) bins[3]++;
            else fail("Answer outside all alternatives: " + value);
        }
        for (int i = 0; i < bins.length; i++) assertUniformBin(bins[i], n, i < 2 ? .4 : .1, "alternative " + i);
    }

    @ParameterizedTest @ValueSource(doubles = {0, 1})
    void specialRateEndpointsChooseOnlyTheRequestedGroup(double rate) throws Exception {
        var generator = generator("\"specialRate\":" + rate);
        var q = question(QuestionKind.INTEGER, mixed());
        var random = new Random(SEED);
        for (int i = 0; i < 500; i++) {
            int value = Integer.parseInt(generator.generate(q, TODAY, random));
            if (rate == 1) assertTrue(value == 888 || value == -999);
            else assertTrue(value == 10 || value == 11 || value >= 20 && value <= 29);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void missingGroupFallsBackToTheOtherAndIsUniform(boolean allSpecial) throws Exception {
        var h = allSpecial ? hints().alternatives(hints().range(7, 7).build(), hints().range(9, 9).build()).build()
                : hints().alternatives(hints().range(10, 11).build(), hints().range(20, 21).build()).build();
        var generator = generator("\"specialRate\":" + (allSpecial ? 0 : 1));
        var random = new Random(SEED); int first = 0; int n = 12000;
        for (int i = 0; i < n; i++) {
            int value = Integer.parseInt(generator.generate(question(QuestionKind.INTEGER, h), TODAY, random));
            if (allSpecial) { assertTrue(value == 7 || value == 9); if (value == 7) first++; }
            else { assertTrue(value == 10 || value == 11 || value == 20 || value == 21); if (value < 20) first++; }
        }
        assertUniformBin(first, n, .5, "available group");
    }

    static Stream<Arguments> impossibleAlternatives() {
        LocalDate date = LocalDate.of(2040, 1, 1);
        var exclusive = hints().range(5, 5); exclusive.lower = new Bound(5, false);
        var exclusiveDate = hints().dates(date, date); exclusiveDate.earliest = new DateBound(date, false);
        return Stream.of(Arguments.of("numeric inverted", QuestionKind.INTEGER, hints().range(9, 2).build()),
                Arguments.of("numeric equal exclusive", QuestionKind.DECIMAL, exclusive.build()),
                Arguments.of("date inverted", QuestionKind.DATE, hints().dates(date.plusDays(1), date).build()),
                Arguments.of("date equal exclusive", QuestionKind.DATETIME, exclusiveDate.build()),
                Arguments.of("length inverted", QuestionKind.TEXT, hints().length(8, 2).build()),
                Arguments.of("count inverted", QuestionKind.SELECT_MULTIPLE, hints().count(3, 2).build()),
                Arguments.of("included and excluded", QuestionKind.SELECT_MULTIPLE, hints().include("a").exclude("a").build()),
                Arguments.of("more included than maximum", QuestionKind.SELECT_MULTIPLE, hints().include("a", "b").count(1, 1).build()),
                Arguments.of("two select one inclusions", QuestionKind.SELECT_ONE, hints().include("a", "b").build()));
    }

    @ParameterizedTest(name = "{0}") @MethodSource("impossibleAlternatives")
    void unsatisfiableAlternativesAreNeverChosen(String label, QuestionKind kind, ConstraintHints impossible) throws Exception {
        ConstraintHints good = switch (kind) {
            case INTEGER, DECIMAL -> hints().range(777, 777).build();
            case DATE, DATETIME -> hints().dates(TODAY.plusDays(1000), TODAY.plusDays(1000)).build();
            case TEXT -> hints().prefix("ok").length(2, 2).build();
            default -> hints().include("c").count(1, 1).build();
        };
        var generator = generator("\"specialRate\":0");
        var q = question(kind, hints().alternatives(impossible, good).build());
        var random = new Random(SEED);
        for (int i = 0; i < 200; i++) {
            String answer = generator.generate(q, TODAY, random);
            switch (kind) {
                case INTEGER, DECIMAL -> assertEquals("777", answer, label);
                case DATE -> assertEquals(TODAY.plusDays(1000).toString(), answer);
                case DATETIME -> assertTrue(answer.startsWith(TODAY.plusDays(1000) + "T"));
                case TEXT -> assertEquals("ok", answer);
                default -> assertEquals("c", answer, label);
            }
        }
    }

    @ParameterizedTest(name = "{0}") @MethodSource("impossibleAlternatives")
    void allUnsatisfiableAlternativesUseTheOriginalRandomStream(String label, QuestionKind kind, ConstraintHints impossible) throws Exception {
        var generator = generator("\"specialRate\":0");
        var q = question(kind, hints().alternatives(impossible, impossible).build());
        var plain = question(kind, ConstraintHints.none());
        var actual = new Random(SEED); var expected = new Random(SEED);
        for (int i = 0; i < 100; i++) assertEquals(generator.generate(plain, TODAY, expected),
                generator.generate(q, TODAY, actual), label);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void exactIncludedSelectionIsSpecialForBothSelectKinds(boolean multiple) throws Exception {
        var special = multiple ? hints().include("a", "b").count(2, 2).build()
                : hints().include("a").count(1, 1).build();
        var ordinary = hints().exclude("a", "b").count(1, 1).build();
        var h = hints().alternatives(special, ordinary).build();
        var q = select(multiple ? QuestionKind.SELECT_MULTIPLE : QuestionKind.SELECT_ONE, h, "b", "c", "a", "d");
        var generator = generator("\"specialRate\":0.3,\"specialValues\":[]");
        var random = new Random(SEED); int count = 0; int n = 16000;
        for (int i = 0; i < n; i++) {
            String answer = generator.generate(q, TODAY, random);
            if (answer.equals(multiple ? "b a" : "a")) count++;
            else assertTrue(answer.equals("c") || answer.equals("d"), answer);
        }
        assertUniformBin(count, n, .3, "exact included selection");
    }

    @Test void chosenAlternativeRetainsItsExclusionAndPrefixRules() throws Exception {
        var a = hints().prefix("ab").length(3, 3).exclude("aba").build();
        var b = hints().prefix("09").length(4, 4).exclude("0900").build();
        var q = question(QuestionKind.TEXT, hints().alternatives(a, b).build());
        var generator = generator(""); var random = new Random(SEED);
        int letters = 0, digits = 0;
        for (int i = 0; i < 2000; i++) {
            String answer = generator.generate(q, TODAY, random);
            assertFalse(List.of("aba", "0900").contains(answer));
            if (answer.matches("ab[a-z]")) letters++;
            else if (answer.matches("09[0-9]{2}")) digits++;
            else fail("Alternative rules were lost: " + answer);
        }
        assertTrue(letters > 0 && digits > 0);
    }

    @Test void equalSeedsReplayAlternativesAndFreshDrawsAndDifferentSeedsVary() throws Exception {
        var q = question(QuestionKind.INTEGER, mixed());
        var a = generator("\"specialRate\":0.3"); var b = generator("\"specialRate\":0.3");
        var ra = new Random(SEED); var rb = new Random(SEED); var rc = new Random(SEED + 1);
        var first = new ArrayList<String>(); var second = new ArrayList<String>();
        for (int i = 0; i < 200; i++) {
            String value = a.generate(q, TODAY, ra);
            assertEquals(value, b.generate(q, TODAY, rb));
            first.add(value); second.add(b.generate(q, TODAY, rc));
        }
        assertTrue(first.stream().distinct().count() > 1);
        assertNotEquals(first, second);
    }

    @Test void blankAndFixedRulesStillPrecedeAlternativeChoice() throws Exception {
        var h = mixed();
        var optional = question(QuestionKind.INTEGER, "", false, h);
        assertNull(generator("\"missingRate\":1,\"fields\":{\"answer\":{\"value\":42}}")
                .generate(optional, TODAY, new Random(SEED)));
        assertEquals("42", generator("\"fields\":{\"answer\":{\"value\":42}}")
                .generate(question(QuestionKind.INTEGER, h), TODAY, new Random(SEED)));
    }
}
