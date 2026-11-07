package ctosim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static ctosim.AnswerGeneratorTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 05 Amendment 4: classification, precedence, boundaries and repeatability. */
class AnswerGeneratorOtherChoiceTest {
    private static final List<String> OTHERS = List.of("other_z", "other_a", "autre");
    private static final String CODES = "\"otherCodes\":[\"other_z\",\"other_a\",\"autre\"]";

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void rateZeroExcludesOtherWhenAnOrdinaryChoiceExists(QuestionKind kind) throws Exception {
        AnswerGenerator g = generator(CODES + ",\"otherRate\":0,\"specialRate\":0");
        for (String answer : draws(g, select(kind, List.of("other_z", "a", "other_a")), 500)) {
            assertEquals("a", answer);
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void rateOneSelectsOrAddsExactlyOneOtherInDisplayOrder(QuestionKind kind) throws Exception {
        List<String> choices = List.of("other_z", "a", "other_a");
        List<String> expected = kind == QuestionKind.SELECT_ONE
                ? List.of("other_z", "other_a") : List.of("other_z a", "a other_a");
        assertUniform(draws(generator(CODES + ",\"otherRate\":1"), select(kind, choices), DRAWS), expected);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void onlyOtherChoicesFallBackToOneUniformOtherAtEveryRate(QuestionKind kind) throws Exception {
        for (double rate : new double[] {0, 0.37, 1}) {
            assertUniform(draws(generator(CODES + ",\"otherRate\":" + rate + ",\"specialRate\":1"),
                    select(kind, OTHERS), DRAWS), OTHERS);
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void aSingleOtherIsReturnedEvenAtRateZero(QuestionKind kind) throws Exception {
        for (String answer : draws(generator(CODES + ",\"otherRate\":0"),
                select(kind, List.of("autre")), 100)) assertEquals("autre", answer);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void otherFallbackWinsWhenNoOrdinaryExistsAndSpecialBranchIsDisabled(QuestionKind kind) throws Exception {
        assertUniform(draws(generator(CODES + ",\"otherRate\":0,\"specialRate\":0,"
                        + "\"specialValues\":[\"s\"]"),
                select(kind, List.of("other_a", "s", "other_z")), DRAWS), List.of("other_a", "other_z"));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void absentOtherCodesLeaveOnlySpecialFallbackUniform(QuestionKind kind) throws Exception {
        assertUniform(draws(generator(CODES + ",\"otherRate\":1,\"specialRate\":0,"
                        + "\"specialValues\":[\"s2\",\"s1\"]"),
                select(kind, List.of("s1", "s2")), DRAWS), List.of("s1", "s2"));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void membershipInOtherCodesTakesPrecedenceOverSpecialValues(QuestionKind kind) throws Exception {
        for (String answer : draws(generator(CODES + ",\"otherRate\":0,\"specialRate\":1,"
                        + "\"specialValues\":[\"other_z\"]"),
                select(kind, List.of("other_z", "ordinary")), 1000)) assertEquals("ordinary", answer);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void specialBranchReturnsOnlyOneSpecialWithoutAddingOther(QuestionKind kind) throws Exception {
        assertUniform(draws(generator(CODES + ",\"otherRate\":1,\"specialRate\":1,"
                        + "\"specialValues\":[\"s2\",\"s1\",\"other_z\"]"),
                select(kind, List.of("other_z", "s1", "a", "s2", "other_a")), DRAWS), List.of("s1", "s2"));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void codesMatchExactlyIncludingCaseUnicodeAndNumericSpelling(QuestionKind kind) throws Exception {
        for (String code : List.of("96", "Autr\u00e9", "\u5176\u4ed6", "-666")) {
            List<String> choices = List.of("096", "96.0", "autr\u00e9", "Autre", "Autr\u00e9_x", "\u5176\u4ed6_x", "-0666", code);
            List<String> answers = draws(generator("\"otherCodes\":[\"" + code + "\"],\"otherRate\":1,\"specialValues\":[]"),
                    select(kind, choices), 1000);
            for (String answer : answers) {
                if (kind == QuestionKind.SELECT_ONE) assertEquals(code, answer);
                else {
                    List<String> selected = orderedValues(answer, choices);
                    assertTrue(selected.contains(code), answer);
                    assertTrue(selected.size() >= 2 && selected.size() <= 4, answer);
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void filteredOutOtherCodesAreNeverReturned(QuestionKind kind) throws Exception {
        assertUniform(draws(generator(CODES + ",\"otherRate\":1"),
                select(kind, List.of("other_a")), DRAWS), List.of("other_a"));
        for (String answer : draws(generator(CODES + ",\"otherRate\":1"),
                select(kind, List.of("a")), 100)) assertEquals("a", answer);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void positiveWeightMakesOtherAnOrdinaryChoiceEvenIfAlsoSpecial(QuestionKind kind) throws Exception {
        for (String answer : draws(generator(CODES + ",\"otherRate\":0,\"specialRate\":0,"
                        + "\"specialValues\":[\"other_z\"],\"fields\":{\"answer\":{\"weights\":{\"other_z\":7,\"a\":0}}}"),
                select(kind, List.of("other_z", "a")), 1000)) {
            List<String> selected = orderedValues(answer, List.of("other_z", "a"));
            assertTrue(selected.contains("other_z"), answer);
            if (kind == QuestionKind.SELECT_ONE) assertEquals("other_z", answer);
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void namingOtherWithZeroWeightStillRemovesItFromOtherAndSpecialCategories(QuestionKind kind) throws Exception {
        String members = CODES + ",\"otherRate\":1,\"specialRate\":1,\"specialValues\":[\"other_z\"],"
                + "\"fields\":{\"answer\":{\"weights\":{\"other_z\":0,\"a\":1,\"b\":1,\"c\":1}}}";
        List<String> answers = draws(generator(members), select(kind, List.of("other_z", "a", "b", "c")), DRAWS);
        for (String answer : answers) {
            assertFalse(orderedValues(answer, List.of("other_z", "a", "b", "c")).contains("other_z"), answer);
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void aWeightForAnotherFieldDoesNotReclassifyThisQuestionsOther(QuestionKind kind) throws Exception {
        String members = CODES + ",\"otherRate\":0,\"fields\":{\"different_field\":{\"weights\":{\"other_z\":1}}}";
        for (String answer : draws(generator(members), select(kind, List.of("a", "other_z")), 100)) assertEquals("a", answer);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void explicitOverrideControlsReclassificationAndNullClearsConfiguredWeights(QuestionKind kind) throws Exception {
        SimulationConfig c = config(CODES + ",\"otherRate\":0,\"fields\":{\"answer\":{\"weights\":{\"other_z\":1}}}");
        AnswerGenerator g = new AnswerGenerator(c);
        Question q = select(kind, List.of("other_z", "a"));
        FieldOverride explicit = config("\"fields\":{\"answer\":{\"weights\":{\"a\":1,\"other_z\":0}}}")
                .field("answer").orElseThrow();
        Random random = new Random(SEED);
        for (int i = 0; i < 500; i++) {
            assertEquals("a", g.generate(q, TODAY, random, null));
            String answer = g.generate(q, TODAY, random, explicit);
            assertTrue(orderedValues(answer, q.choices()).contains("a"), answer);
            if (kind == QuestionKind.SELECT_ONE) assertEquals("a", answer);
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void explicitWeightsCanPromoteOtherWhenConfigurationHasNoFieldWeights(QuestionKind kind) throws Exception {
        AnswerGenerator g = generator(CODES + ",\"otherRate\":0,\"specialRate\":0");
        Question q = select(kind, List.of("other_z", "a"));
        FieldOverride explicit = config("\"fields\":{\"answer\":{\"weights\":{\"other_z\":1}}}")
                .field("answer").orElseThrow();
        Random random = new Random(SEED);
        for (int i = 0; i < 500; i++) {
            String answer = g.generate(q, TODAY, random, explicit);
            assertTrue(orderedValues(answer, q.choices()).contains("other_z"), answer);
            if (kind == QuestionKind.SELECT_ONE) assertEquals("other_z", answer);
            assertEquals("a", g.generate(q, TODAY, random));
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void fixedValuesStillOverrideOtherRateInBothDirections(QuestionKind kind) throws Exception {
        for (String fixed : List.of("other_z", "a", "unoffered")) {
            for (int rate : new int[] {0, 1}) {
                AnswerGenerator g = generator(CODES + ",\"otherRate\":" + rate
                        + ",\"fields\":{\"answer\":{\"value\":\"" + fixed + "\"}}");
                for (String answer : draws(g, select(kind, List.of("a", "other_z")), 50)) assertEquals(fixed, answer);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void optionalMissingRuleStillPrecedesOtherSelectionAndFixedValues(QuestionKind kind) throws Exception {
        Question q = new Question("answer", kind, List.of("other_z"), "", false);
        for (String suffix : List.of("", ",\"fields\":{\"answer\":{\"value\":\"other_z\"}}")) {
            for (String answer : draws(generator(CODES + ",\"otherRate\":1,\"missingRate\":1" + suffix), q, 100)) {
                assertNull(answer);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void requiredOtherOnlyQuestionIgnoresMissingRateOne(QuestionKind kind) throws Exception {
        for (String answer : draws(generator(CODES + ",\"otherRate\":1,\"missingRate\":1"),
                select(kind, List.of("other_z")), 100)) assertEquals("other_z", answer);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void emptyAndLabelSelectsStayBlankEvenWhenRequiredAndFixedToOther(QuestionKind kind) throws Exception {
        AnswerGenerator g = generator(CODES + ",\"otherRate\":1,\"fields\":{\"answer\":{\"value\":\"other_z\"}}");
        for (Question q : List.of(select(kind, List.of()),
                new Question("answer", kind, List.of("other_z", "a"), "list label", true))) {
            for (String answer : draws(g, q, 100)) assertNull(answer);
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void otherDrawsAreRepeatableFreshAndIndependentOfEarlierCalls(QuestionKind kind) throws Exception {
        String members = CODES + ",\"otherRate\":0.43,\"specialRate\":0.23,\"specialValues\":[\"s\"],"
                + "\"fields\":{\"answer\":{\"weights\":{\"a\":1,\"b\":4}}}";
        Question q = select(kind, List.of("other_a", "b", "s", "a", "other_z"));
        AnswerGenerator reused = generator(members);
        draws(reused, q, 137);
        List<String> expected = draws(generator(members), q, 1000);
        assertEquals(expected, draws(reused, q, 1000));
        assertTrue(new HashSet<>(expected).size() > 1, "Retries must use fresh draws");
        assertNotEquals(expected, draws(generator(members), q, TODAY, new Random(SEED + 1), 1000));
    }

    static List<String> orderedValues(String answer, List<String> offered) {
        assertNotNull(answer);
        List<String> selected = List.of(answer.split(" ", -1));
        assertFalse(selected.isEmpty());
        assertEquals(selected.size(), new HashSet<>(selected).size(), "No repeated choices: " + answer);
        assertTrue(offered.containsAll(selected), "Only offered values: " + answer);
        List<String> ordered = new ArrayList<>();
        for (String value : offered) if (selected.contains(value)) ordered.add(value);
        assertEquals(String.join(" ", ordered), answer, "Single spaces and offered display order");
        return selected;
    }
}
