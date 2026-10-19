package ctosim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.bind;
import static ctosim.FormWalkerTestSupport.input;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 13 Amendment 1: enumerate the search choices offered at form opening. */
@Isolated("FormWalker and locale/time-zone replay tests use JVM defaults")
class FieldworkAmendment1SearchIdTest extends FieldworkAmendment1SearchSupport {
    private static final Set<String> LOADED = Set.of("A01", "B02", "É03", "0007", "8");

    @ParameterizedTest
    @ValueSource(strings = {"search('households')", "search('households.csv')", "minimal search('households') quick"})
    void attachedValuesAndStaticChoicesReplaceThePlaceholderAndCycleWithoutDuplicates(String appearance)
            throws Exception {
        assertPool(search(appearance, "7", "code", "8", "99", "-99"), idConfig(14, ""),
                Set.of("7", "A01", "B02", "É03", "0007", "8"), true);
    }

    @ParameterizedTest @ValueSource(ints = {1, 4, 5, 6, 12})
    void searchPoolExhaustionWarnsExactlyWhenDistinctKIsLessThanN(int n) throws Exception {
        assertPool(search("search('households')", "code"), idConfig(n, ""), LOADED, true);
    }

    @Test void configuredSpecialValuesAreRemovedFromBothLoadedAndStaticChoices() throws Exception {
        assertPool(search("search('households')", "7", "code", "8", "99"),
                idConfig(7, "\"specialValues\":[\"A01\",\"8\",\"99\",\"-99\",\"7\"]"),
                Set.of("B02", "É03", "0007"), true);
    }

    @Test void clearingSpecialValuesRestoresCodesFromTheFileAndStaticList() throws Exception {
        assertPool(search("search('households')", "7", "code", "99", "-99"),
                idConfig(8, "\"specialValues\":[]"),
                Set.of("A01", "B02", "É03", "0007", "8", "7", "99", "-99"), true);
    }

    @Test void loadedAndStaticDuplicatesCountAsOnePossibleId() throws Exception {
        assertPool(search("search('households')", "8", "code"), idConfig(6, ""), LOADED, true);
    }

    @Test void weightsAndMissingRateDoNotSuppressPossibleSearchIds() throws Exception {
        assertPool(search("search('households')", "code"),
                idConfig(5, "\"fields\":{\"answer\":{\"missingRate\":1,\"weights\":{"
                        + "\"A01\":1,\"B02\":0,\"É03\":0,\"0007\":0,\"8\":0}}}"), LOADED, true);
    }

    @Test void aFixedLoadedValueTakesPrecedenceOverSearchAndStaticChoices() throws Exception {
        assertPool(search("search('households')", "7", "code"),
                idConfig(3, "\"fields\":{\"answer\":{\"value\":\"B02\"}}"), Set.of("B02"), true);
    }

    @Test void explicitTextsTakePrecedenceOverTheSearchPoolAndRetainExplicitSpecialCodes() throws Exception {
        assertPool(search("search('households')", "7", "code"),
                idConfig(7, "\"fields\":{\"answer\":{\"texts\":[\"B02\",\"99\",\"B02\",\"\"]}}"),
                Set.of("B02", "99"), true);
    }

    @Test void fixedStaticValueStillWorksWhenTheAttachmentIsMissing() throws Exception {
        assertPool(search("search('missing')", "7", "code", "8"),
                idConfig(3, "\"fields\":{\"answer\":{\"value\":\"8\"}}"), Set.of("8"), true);
    }

    static Stream<Arguments> filteredPools() {
        return Stream.of(
                Arguments.of("'matches', 'region', 'north'", Set.of("A01", "É03", "0007", "7")),
                Arguments.of("'matches', 'region', 'south'", Set.of("B02", "8", "7")),
                Arguments.of("'startswith', 'label', 'Al'", Set.of("A01", "7")),
                Arguments.of("'endswith', 'label', 'ta'", Set.of("B02", "7")),
                Arguments.of("'contains', 'label', 'cho'", Set.of("É03", "7")),
                Arguments.of("'matches', 'region', 'north', 'active', 'yes'", Set.of("A01", "0007", "7")),
                Arguments.of("'matches', 'region', 'NORTH'", Set.of("7")));
    }

    @ParameterizedTest @MethodSource("filteredPools")
    void onlyRowsOfferedByTheOpeningSearchEnterThePool(String arguments, Set<String> expected) throws Exception {
        assertPool(search("search('households', " + arguments + ")", "code", "7"),
                idConfig(expected.size() * 2 + 1, ""), expected, true);
    }

    @ParameterizedTest @ValueSource(strings = {"", "initial"})
    void idPoolUsesBlankOrDefaultValuesBeforeAnEarlierQuestionIsAnswered(String initial) throws Exception {
        var form = attached("<region>" + initial + "</region><answer/>",
                bind("region", "string", "") + bind("answer", "string", ""),
                input("region") + control("search('opening', 'matches', 'region', /data/region)", "code"));
        var config = idConfig(5, "\"fields\":{\"region\":{\"value\":\"later\"}}");
        // The initial IDs need not remain accepted after region changes. Allocation alone is the oracle here.
        Set<String> expected = initial.isEmpty() ? Set.of("BLANK1", "BLANK2") : Set.of("INITIAL1", "INITIAL2");
        var plan = assertPool(form, config, expected, false);
        var firstIds = new ArrayList<>(ids(plan));
        plan.walkAll();
        assertEquals(firstIds, ids(plan), "previous submissions must not leak their later region into the opening pool");
    }

    @Test void sourceFileExpressionIsAlsoEvaluatedBeforeEarlierAnswers() throws Exception {
        var form = attached("<source>households</source><answer/>",
                bind("source", "string", "") + bind("answer", "string", ""),
                input("source") + control("search(/data/source)", "code", "7"));
        assertPool(form, idConfig(13, "\"fields\":{\"source\":{\"value\":\"missing\"}}"),
                Set.of("A01", "B02", "É03", "0007", "8", "7"), false);
    }

    @Test void itemsetSearchIncludesStaticChoicesAndLoadedRowsDespiteTheChoiceFilter() throws Exception {
        String secondary = "<instance id=\"choices\"><root>"
                + "<item><name>7</name><label>Static</label><keep>no</keep></item>"
                + "<item><name>code</name><label>Placeholder</label><keep>no</keep></item>"
                + "<item><name>99</name><label>Special</label><keep>yes</keep></item>"
                + "</root></instance>";
        String body = FormWalkerAmendment3SearchSupport.selectControl("select1", "answer", "search('households')",
                "<itemset nodeset=\"instance('choices')/root/item[keep = 'yes']\">"
                        + "<value ref=\"name\"/><label ref=\"label\"/></itemset>");
        var form = attached("<answer/>", secondary + bind("answer", "string", ""), body);
        assertPool(form, idConfig(13, ""), Set.of("A01", "B02", "É03", "0007", "8", "7"), true);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "wrong-column"})
    void anUnreadableSearchFallsBackToStaticChoicesOnBothSidesOfThePlaceholder(String source) throws Exception {
        assertPool(search("search('" + source + "')", "7", "code", "8", "99"),
                idConfig(5, ""), Set.of("7", "8"), true);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "wrong-column", "header-only"})
    void noUsableLoadedOrStaticChoicesLeavesNullVisitIdsAndOnePlanWarning(String source) throws Exception {
        assertEmptyPool(search("search('" + source + "')", "code", "99", "-99"));
    }

    @Test void readableHeaderOnlyFileLeavesStaticChoicesAsTheIdPool() throws Exception {
        assertPool(search("search('header-only')", "7", "code", "8"), idConfig(5, ""), Set.of("7", "8"), true);
    }

    @Test void noMatchingRowsWithoutStaticChoicesGivesAnEmptyPool() throws Exception {
        assertEmptyPool(search("search('households', 'matches', 'region', 'absent')", "code"));
    }

    @Test void excludingEveryLoadedValueAsSpecialLeavesNoIds() throws Exception {
        var form = search("search('households')", "code");
        var config = idConfig(3, "\"specialValues\":[\"A01\",\"B02\",\"É03\",\"0007\",\"8\",\"99\",\"-99\"]");
        var plan = new Fieldwork(form, config);
        plan.walkAll();
        assertTrue(plan.visits().stream().allMatch(v -> v.id() == null));
        assertEquals(1, plan.warnings().size());
        warning(plan.warnings(), "answer");
    }

    @Test void aSearchWithoutAPlaceholderUsesItsOrdinaryStaticList() throws Exception {
        assertPool(search("search('missing')", "-2", "0", "2.5", "99"),
                idConfig(7, ""), Set.of("-2", "0", "2.5"), true);
    }

    @Test void aNonnumericChoiceWithoutSearchRemainsAnOrdinaryId() throws Exception {
        assertPool(search("minimal", "7", "code", "8"), idConfig(7, ""), Set.of("7", "code", "8"), true);
    }

    @Test void extendingNPreservesTheSeededSearchIdPrefix() throws Exception {
        var form = search("search('households')", "7", "code");
        var config = idConfig(3, "");
        var shortPlan = new Fieldwork(form, config);
        var longPlan = new Fieldwork(form, config.withSubmissions(14));
        shortPlan.walkAll();
        longPlan.walkAll();
        assertEquals(ids(shortPlan), ids(longPlan).subList(0, 3));
    }

    @Test void differentSeedsCanChangeTheSearchIdPermutation() throws Exception {
        var form = search("search('households')", "code");
        var config = idConfig(5, "");
        var orders = new HashSet<List<String>>();
        for (int seed = 1; seed <= 12; seed++) {
            var plan = assertPool(form, config.withSeed(seed), LOADED, true);
            orders.add(ids(plan));
        }
        assertTrue(orders.size() > 1, "the search pool must be shuffled from the seed");
    }

    @Test void freshLoadsAndRepeatCallsReproduceTheSearchPlanAcrossLocaleAndTimeZoneChanges() throws Exception {
        var form = search("search('households')", "code");
        var config = idConfig(12, "\"interviewsPerDay\":2");
        var plan = new Fieldwork(form, config);
        var first = List.copyOf(plan.walkAll());
        var visits = List.copyOf(plan.visits());
        var warnings = List.copyOf(plan.warnings());
        sameRun(first, plan.walkAll());
        assertEquals(visits, plan.visits());
        assertEquals(warnings, plan.warnings());
        Locale oldLocale = Locale.getDefault();
        Locale oldDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        Locale oldFormat = Locale.getDefault(Locale.Category.FORMAT);
        TimeZone oldZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            var reloaded = new Fieldwork(FormLoader.load(xform, media), config);
            sameRun(first, reloaded.walkAll());
            assertEquals(visits, reloaded.visits());
            assertEquals(warnings, reloaded.warnings());
        } finally {
            TimeZone.setDefault(oldZone);
            Locale.setDefault(oldLocale);
            Locale.setDefault(Locale.Category.DISPLAY, oldDisplay);
            Locale.setDefault(Locale.Category.FORMAT, oldFormat);
        }
    }

    @Test void searchIdsStoredByThePlanMatchWalkingTheSameVisitsDirectly() throws Exception {
        var form = search("search('households')", "7", "code");
        var config = idConfig(8, "");
        var plan = new Fieldwork(form, config);
        var submissions = plan.walkAll();
        var walker = new FormWalker(form, config);
        for (int i = 0; i < submissions.size(); i++) same(submissions.get(i), walker.walk(i + 1, plan.visits().get(i)));
    }

    @Test void quotaOverflowWarningPrecedesTheExhaustedSearchPoolWarning() throws Exception {
        var config = idConfig(7, "\"enumerators\":1,\"fieldworkDays\":1,\"interviewsPerDay\":2");
        var plan = new Fieldwork(search("search('households')", "code"), config);
        plan.walkAll();
        assertEquals(2, plan.warnings().size());
        warning(plan.warnings().subList(0, 1), null, 4, 1);
        warning(plan.warnings().subList(1, 2), "answer", 5, 7);
    }
}
