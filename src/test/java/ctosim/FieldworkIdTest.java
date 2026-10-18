package ctosim;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("FormWalker temporarily changes the JVM time zone")
class FieldworkIdTest {
    @Test void withoutAnIdSettingAllVisitsHaveNullIdsAndThereIsNoIdWarning() throws Exception {
        var plan = new Fieldwork(question("int", ""), settings("\"submissions\":8"));
        plan.walkAll();
        assertTrue(plan.visits().stream().allMatch(v -> v.id() == null));
        assertTrue(plan.warnings().isEmpty());
        assertTrue(plan.configWarnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"string", "int"})
    void fixedOverrideGivesOnePossibleIdEvenWhenOtherSourcesHaveManyValues(String type) throws Exception {
        var config = settings("\"submissions\":4,\"id\":\"answer\",\"integerRange\":{\"min\":1,\"max\":90},"
                + "\"fields\":{\"answer\":{\"value\":17}}");
        assertIds(question(type, ""), config, Set.of("17"), true);
    }

    @Test void aFixedSelectOverrideTakesPrecedenceOverTheChoiceList() throws Exception {
        assertIds(choices(), settings("\"submissions\":4,\"id\":\"answer\","
                + "\"fields\":{\"answer\":{\"value\":\"b\"}}"), Set.of("b"), true);
    }

    @Test void aFixedSpecialChoiceRemainsAnExplicitlyAllowedId() throws Exception {
        assertIds(choices(), settings("\"submissions\":2,\"id\":\"answer\","
                + "\"fields\":{\"answer\":{\"value\":\"99\"}}"), Set.of("99"), true);
    }

    @Test void textPoolsSupplyUniqueUnicodeIdsAndCycleInTheSameOrder() throws Exception {
        assertIds(question("string", ""), settings("\"submissions\":8,\"id\":\"answer\","
                + "\"fields\":{\"answer\":{\"texts\":[\"家庭-一\",\"ménage-é\",\"بيت-٣\"]}}"),
                Set.of("家庭-一", "ménage-é", "بيت-٣"), true);
    }

    @Test void textsTakePrecedenceOverAnIntegerOverridesRange() throws Exception {
        assertIds(question("int", ""), settings("\"submissions\":6,\"id\":\"answer\","
                + "\"fields\":{\"answer\":{\"texts\":[\"7\",\"11\"],\"min\":20,\"max\":24}}"),
                Set.of("7", "11"), true);
    }

    @Test void textsTakePrecedenceOverASelectChoiceList() throws Exception {
        assertIds(choices(), settings("\"submissions\":5,\"id\":\"answer\","
                + "\"fields\":{\"answer\":{\"texts\":[\"a\",\"c\"]}}"), Set.of("a", "c"), true);
    }

    @ParameterizedTest @CsvSource({"-2.8,1.9,-2,-1,0,1", "-2,1,-2,-1,0,1"})
    void integerOverrideRangeIncludesExactlyTheWholeNumbersBetweenItsBounds(
            double min, double max, String a, String b, String c, String d) throws Exception {
        assertIds(question("int", ""), settings("\"submissions\":9,\"id\":\"answer\","
                + "\"integerRange\":{\"min\":100,\"max\":110},\"fields\":{\"answer\":{\"min\":"
                + min + ",\"max\":" + max + "}}"), Set.of(a, b, c, d), true);
    }

    @Test void integerWithoutAnOverrideUsesTheConfiguredGlobalRange() throws Exception {
        assertIds(question("int", ""), settings("\"submissions\":7,\"id\":\"answer\","
                + "\"integerRange\":{\"min\":5.1,\"max\":8.9}"), Set.of("6", "7", "8"), true);
    }

    @Test void defaultIntegerRangeSuppliesAllOneHundredAndOneWholeNumbers() throws Exception {
        Set<String> expected = java.util.stream.IntStream.rangeClosed(0, 100).mapToObj(Integer::toString)
                .collect(Collectors.toSet());
        assertIds(question("int", ""), settings("\"submissions\":101,\"id\":\"answer\""), expected, false);
    }

    @Test void selectIdsExcludeTheConfiguredSpecialValues() throws Exception {
        assertIds(choices(), settings("\"submissions\":8,\"id\":\"answer\","
                + "\"specialValues\":[\"b\",\"99\",\"-99\"]"), Set.of("a", "c"), true);
    }

    @Test void selectIdsIncludeFormerSpecialValuesWhenTheSpecialListIsEmpty() throws Exception {
        assertIds(choices(), settings("\"submissions\":5,\"id\":\"answer\",\"specialValues\":[]"),
                Set.of("a", "b", "c", "99", "-99"), false);
    }

    @Test void choiceWeightsDoNotRemoveAllowedChoicesFromTheIdPool() throws Exception {
        assertIds(choices(), settings("\"submissions\":3,\"id\":\"answer\","
                + "\"fields\":{\"answer\":{\"weights\":{\"a\":1,\"b\":0,\"c\":0}}}"),
                Set.of("a", "b", "c"), false);
    }

    @ParameterizedTest @CsvSource({"1,1,false", "3,3,false", "2,3,true", "3,2,false"})
    void exhaustionWarningAppearsExactlyWhenKIsLessThanN(int k, int n, boolean warn) throws Exception {
        var config = settings("\"submissions\":" + n + ",\"id\":\"answer\",\"integerRange\":{\"min\":1,\"max\":" + k + "}");
        var plan = new Fieldwork(question("int", ""), config);
        plan.walkAll();
        assertEquals(warn ? 1 : 0, plan.warnings().size());
        if (warn) warning(plan.warnings(), "answer", k, n);
        assertEquals(Math.min(k, n), new HashSet<>(ids(plan)).size());
    }

    @ParameterizedTest @ValueSource(strings = {"plainText", "emptyIntegerRange", "allChoicesSpecial", "decimal"})
    void noPossibleValuesLeavesVisitIdsNullAndProducesOneFieldWarning(String kind) throws Exception {
        LoadedForm form = switch (kind) {
            case "emptyIntegerRange" -> question("int", "");
            case "allChoicesSpecial" -> choices();
            case "decimal" -> question("decimal", "");
            default -> question("string", "");
        };
        String extra = switch (kind) {
            case "emptyIntegerRange" -> ",\"fields\":{\"answer\":{\"min\":0.2,\"max\":0.8}}";
            case "allChoicesSpecial" -> ",\"specialValues\":[\"a\",\"b\",\"c\",\"99\",\"-99\"]";
            default -> "";
        };
        var plan = new Fieldwork(form, settings("\"submissions\":4,\"id\":\"answer\"" + extra));
        assertTrue(plan.warnings().isEmpty());
        assertTrue(plan.configWarnings().isEmpty());
        plan.walkAll();
        assertTrue(plan.visits().stream().allMatch(v -> v.id() == null));
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), "answer");
    }

    @Test void absentFieldWarnsAtConfigurationTimeAfterTheWalkersWarnings() throws Exception {
        LoadedForm form = question("int", "");
        var config = settings("\"submissions\":3,\"id\":\"missing_id\","
                + "\"fields\":{\"z_unknown\":{\"value\":1},\"a_unknown\":{\"value\":2}}");
        List<String> walkerWarnings = new FormWalker(form, config).configWarnings();
        var plan = new Fieldwork(form, config);
        assertEquals(walkerWarnings.size() + 1, plan.configWarnings().size());
        assertEquals(walkerWarnings, plan.configWarnings().subList(0, walkerWarnings.size()));
        assertTrue(plan.configWarnings().get(walkerWarnings.size()).contains("missing_id"));
        List<String> before = List.copyOf(plan.configWarnings());
        plan.walkAll();
        assertEquals(before, plan.configWarnings());
        assertTrue(plan.visits().stream().allMatch(v -> v.id() == null));
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void idFieldLookupIsCaseSensitive() throws Exception {
        var plan = new Fieldwork(question("int", ""), settings("\"submissions\":1,\"id\":\"ANSWER\""));
        warning(plan.configWarnings(), "ANSWER");
        plan.walkAll();
        assertNull(plan.visits().get(0).id());
    }

    @Test void anExistingNonQuestionElementIsNotReportedAsAnAbsentElement() throws Exception {
        var form = fixture("<group><answer/></group>", bind("group/answer", "string", ""),
                "<group ref=\"/data/group\"><label>Group</label>" + input("group/answer") + "</group>");
        var plan = new Fieldwork(form, settings("\"id\":\"group\",\"submissions\":1"));
        assertTrue(plan.configWarnings().isEmpty(), "the absent-element rule tests all primary-instance elements");
    }

    @Test void theIdPermutationIsSeededReproducibleAndNotAlwaysSourceOrder() throws Exception {
        LoadedForm form = question("int", "");
        var config = settings("\"submissions\":12,\"id\":\"answer\",\"integerRange\":{\"min\":1,\"max\":12}");
        Set<List<String>> orders = new HashSet<>();
        for (int seed = 1; seed <= 12; seed++) {
            var a = new Fieldwork(form, config.withSeed(seed));
            var b = new Fieldwork(question("int", ""), config.withSeed(seed));
            a.walkAll();
            b.walkAll();
            assertEquals(ids(a), ids(b));
            assertEquals(12, new HashSet<>(ids(a)).size());
            orders.add(ids(a));
        }
        assertTrue(orders.size() > 1, "meaningfully different seeds must affect the permutation");
    }

    @Test void longerPlansKeepThePrefixOfTheSameSeededIdPermutation() throws Exception {
        LoadedForm form = question("int", "");
        var config = settings("\"submissions\":4,\"id\":\"answer\",\"integerRange\":{\"min\":1,\"max\":9}");
        var shortPlan = new Fieldwork(form, config);
        var longPlan = new Fieldwork(form, config.withSubmissions(20));
        shortPlan.walkAll();
        longPlan.walkAll();
        assertEquals(ids(shortPlan), ids(longPlan).subList(0, 4));
    }

    static List<String> ids(Fieldwork plan) {
        return plan.visits().stream().map(Visit::id).toList();
    }

    private static void assertIds(LoadedForm form, SimulationConfig config, Set<String> expected, boolean exhausted) throws Exception {
        var plan = new Fieldwork(form, config);
        var submissions = plan.walkAll();
        assertEquals(expected, new HashSet<>(ids(plan)), "every allowed value appears before any repeats");
        for (int i = 0; i < submissions.size(); i++) {
            String id = plan.visits().get(i).id();
            assertEquals(plan.visits().get(i % expected.size()).id(), id, "cycle by n modulo K");
            assertEquals(id, value(submissions.get(i), "answer"));
        }
        assertEquals(exhausted ? 1 : 0, plan.warnings().size());
        if (exhausted) warning(plan.warnings(), "answer", expected.size(), config.submissions());
    }
}
