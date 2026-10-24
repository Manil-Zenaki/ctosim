package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment1OutlierTest {
    private static final String NORMAL = "\"fields\":{\"amount\":{\"min\":90,\"max\":110,"
            + "\"distribution\":{\"normal\":{\"mean\":100,\"sd\":10}}}}";

    @ParameterizedTest @CsvSource({"1,0,0", "1,0.000001,1", "10,0.25,3", "10,0.249,2", "10,1,10"})
    void outlierCountsRoundHalfUpAndPositiveRatesHaveAtLeastOneTarget(int n, double probability, int expected) throws Exception {
        var r = run(number("decimal", ""), settings(n, n, "").withProblems(rates(probability)));
        assertEquals(expected, records(r, "outlier").size()); assertEquals(expected, targets(r, "outlier").size());
        // E=N: every target is an enumerator's first interview, which remains eligible for outliers.
        newMetadata(r);
    }
    @Test void uniformDecimalUsesTheIqrFenceAndSamplesTheEntireUpperInterval() throws Exception {
        var r = run(number("decimal", ""), settings(600, 2, "").withProblems(rates(1)));
        // U[10,20]: Q1=12.5, Q3=17.5, mean=median=15, sd=10/sqrt(12).
        // max(25,23.660...) = 25, hence injection interval [25,35].
        assertIntervalAndUniformity(r, 25, 35, false);
        for (var p : records(r, "outlier")) assertEquals(value(r.clean(p.submission()), "amount"), p.cleanValue());
        newMetadata(r);
    }
    @ParameterizedTest @ValueSource(strings = {"int", "decimal"})
    void normalFencesIgnoreConfiguredDrawingBoundsAndUseMeanPlusThreeStandardDeviations(String type) throws Exception {
        var r = run(number(type, ""), settings(600, 2, NORMAL).withProblems(rates(1)));
        assertIntervalAndUniformity(r, 130, 160, type.equals("int"));
        for (var p : records(r, "outlier")) {
            double clean = Double.parseDouble(p.cleanValue()); assertTrue(clean >= 90 && clean <= 110);
        }
    }
    @Test void lognormalFencesAndMedianUseVariableMomentsRatherThanLogSpaceParameters() throws Exception {
        var c = settings(600, 2, "\"fields\":{\"amount\":{\"min\":1,\"max\":200,"
                + "\"distribution\":{\"lognormal\":{\"mean\":100,\"sd\":100}}}}");
        double sigma = Math.sqrt(Math.log(2)), mu = Math.log(100) - Math.log(2) / 2;
        double q1 = Math.exp(mu - .6744897501960817 * sigma), q3 = Math.exp(mu + .6744897501960817 * sigma);
        double fence = Math.max(q3 + 1.5 * (q3 - q1), 400), median = Math.exp(mu);
        var r = run(number("decimal", ""), c.withProblems(rates(1)));
        assertIntervalAndUniformity(r, fence, 2 * fence - median, false);
    }
    @Test void hintsClipTheUpperIntervalWithoutReplacingTheDrawingDistribution() throws Exception {
        var r = run(number("decimal", "constraint=\". &lt;= 140\""), settings(500, 2, NORMAL).withProblems(rates(1)));
        assertIntervalAndUniformity(r, 130, 140, false);
    }
    @Test void whenUpperIntervalIsEmptyTheLowerIntervalIsUsedAndClipped() throws Exception {
        var r = run(number("decimal", "constraint=\". &gt;= 50 and . &lt; 130\""),
                settings(500, 2, NORMAL).withProblems(rates(1)));
        assertIntervalAndUniformity(r, 50, 70, false);
    }
    @Test void upperIntervalHasPriorityWhenBothTailsAreAllowed() throws Exception {
        var r = run(number("int", "constraint=\". &gt;= 40 and . &lt;= 160\""), settings(80, 2, NORMAL).withProblems(rates(1)));
        assertEquals(80, records(r, "outlier").size());
        for (var p : records(r, "outlier")) assertTrue(Double.parseDouble(p.value()) >= 130);
    }
    @Test void integerLowerTailRoundsConstraintBoundsInward() throws Exception {
        var r = run(number("int", "constraint=\". &gt; 49.5 and . &lt;= 129.9\""),
                settings(400, 2, NORMAL).withProblems(rates(1)));
        assertIntervalAndUniformity(r, 50, 70, true);
    }
    @Test void anInclusiveFenceIsEligibleEvenWhenOnlyThatOnePointFits() throws Exception {
        var r = run(number("int", "constraint=\". &gt;= 71 and . &lt;= 130\""),
                settings(20, 2, NORMAL).withProblems(rates(1)));
        assertEquals(20, records(r, "outlier").size());
        for (var p : records(r, "outlier")) assertEquals("130", p.value());
    }
    @Test void excludingBothFencesLeavesNoEligibleQuestionAndWarnsForEveryTarget() throws Exception {
        var r = run(number("decimal", "constraint=\". &gt; 70 and . &lt; 130\""),
                settings(8, 2, NORMAL).withProblems(rates(1)));
        assertTrue(records(r, "outlier").isEmpty()); assertEquals(8, r.plan().warnings().size());
        for (int n = 1; n <= 8; n++) { assertWarning(r, "", n); ordinary(r, n); }
    }
    @Test void constraintHintsDetermineTheUsualUniformDrawingRangeBeforeComputingFences() throws Exception {
        // R=[10,20] and H=[100,200] do not intersect; spec 09 draws uniformly on H.
        // Neither tail beyond [50,250] is accepted by H, so this is not an eligible outlier field.
        var r = run(number("decimal", "constraint=\". &gt;= 100 and . &lt;= 200\""),
                settings(8, 2, "").withProblems(rates(1)));
        assertTrue(records(r, "outlier").isEmpty()); assertEquals(8, r.plan().warnings().size());
        for (var s : r.submissions()) { double x = Double.parseDouble(value(s, "amount")); assertTrue(x >= 100 && x <= 200); }
    }
    @Test void twentyRejectedOutlierDrawsFallBackToTheUsualAnswerAndNameTheQuestion() throws Exception {
        // Nonlinear arithmetic yields no hint, but every [130,160] draw fails the engine constraint.
        var r = run(number("int", "constraint=\". * 2 &lt; 240\""), settings(6, 2, NORMAL).withProblems(rates(1)));
        assertTrue(records(r, "outlier").isEmpty()); assertEquals(6, r.plan().warnings().size());
        for (int n = 1; n <= 6; n++) { assertWarning(r, "amount", n); ordinary(r, n); }
        // Exact internal retry count is not observable via the public API; the documented fallback is.
    }
    @Test void oneEligibleQuestionIsChosenUniformlyAndOnlyItsAnswerIsOutsideTheOrdinaryRange() throws Exception {
        var f = fixture("<z/><a/><m/>", bind("m", "decimal", "") + bind("a", "decimal", "") + bind("z", "decimal", ""),
                input("z") + input("a") + input("m"));
        var r = run(f, settings(600, 2, "").withProblems(rates(1))); assertEquals(600, records(r, "outlier").size());
        Map<String, Integer> frequencies = new HashMap<>();
        for (var p : records(r, "outlier")) {
            frequencies.merge(p.field(), 1, Integer::sum); var clean = r.clean(p.submission());
            for (String name : List.of("z", "a", "m")) {
                if (p.field().equals(name)) assertEquals(value(clean, name), p.cleanValue());
                else {
                    double x = Double.parseDouble(value(r.target(p), name));
                    assertTrue(x >= 10 && x <= 20, "only the selected question gets an outlier");
                }
            }
        }
        assertEquals(Set.of("z", "a", "m"), frequencies.keySet()); frequencies.values().forEach(n -> rate(n, 600, 1.0 / 3));
        newMetadata(r);
    }
    @Test void onlyTheFirstInstanceOfTheSelectedRepeatedQuestionIsAnOutlier() throws Exception {
        var f = fixture("<person jr:template=\"\"><amount/></person>", bind("person/amount", "decimal", ""),
                "<repeat nodeset=\"/data/person\">" + input("person/amount") + "</repeat>");
        var r = run(f, settings(40, 2, "\"repeats\":{\"min\":3,\"max\":3}").withProblems(rates(1)));
        assertEquals(40, records(r, "outlier").size());
        for (var p : records(r, "outlier")) {
            var s = r.target(p); assertEquals("amount", p.field()); assertEquals(3, count(s, "person"));
            assertEquals(value(s, "person[1]/amount"), p.value());
            assertEquals(value(r.clean(p.submission()), "person[1]/amount"), p.cleanValue());
            for (int i = 2; i <= 3; i++) {
                double x = Double.parseDouble(value(s, "person[" + i + "]/amount")); assertTrue(x >= 10 && x <= 20);
            }
        }
    }
    @Test void protectedAndIrrelevantNumbersAndRepeatCountsAreNotEligible() throws Exception {
        var f = fixture("<id/><readonly>15</readonly><calc/><hidden/><size/><person jr:template=\"\"><name/></person>",
                bind("id", "int", "") + bind("readonly", "int", "readonly=\"true()\"")
                + bind("calc", "int", "calculate=\"7\"") + bind("hidden", "int", "relevant=\"false()\"")
                + bind("size", "int", "") + bind("person/name", "string", ""),
                input("id") + input("readonly") + input("hidden") + input("size")
                + "<repeat nodeset=\"/data/person\" jr:count=\"/data/size\">" + input("person/name") + "</repeat>");
        var r = run(f, settings(6, 2, "\"id\":\"id\",\"repeats\":{\"min\":2,\"max\":2}").withProblems(rates(1)));
        assertTrue(r.plan().problems().isEmpty()); assertEquals(6, r.plan().warnings().size());
        for (var s : r.submissions()) { ordinary(r, s.number()); assertEquals(2, count(s, "person")); }
    }
    @Test void aFormWithNoNumericQuestionWarnsWithoutInventingAnOutlier() throws Exception {
        var r = run(singleTimed(0), settings(3, 2, "").withProblems(rates(1)));
        assertTrue(r.plan().problems().isEmpty()); assertEquals(3, r.plan().warnings().size());
        for (int n = 1; n <= 3; n++) { assertWarning(r, "", n); ordinary(r, n); }
    }
    @Test void secondWalkReevaluatesRelevanceAndCalculationsFromTheInjectedAnswer() throws Exception {
        var f = fixture("<amount/><twice/><followup/>", bind("amount", "decimal", "")
                + bind("twice", "decimal", "calculate=\"/data/amount * 2\"")
                + bind("followup", "string", "relevant=\"/data/amount &gt; 24\""), input("amount") + input("followup"));
        var r = run(f, settings(30, 2, "").withProblems(rates(1)));
        assertEquals(30, records(r, "outlier").size());
        for (var s : r.submissions()) {
            assertEquals(2 * Double.parseDouble(value(s, "amount")), Double.parseDouble(value(s, "twice")), 1e-8);
            assertFalse(value(s, "followup").isEmpty());
        }
    }
    @Test void outlierTargetsDependOnSeedAndNButNotAnswersAndAreUniformAcrossSeeds() throws Exception {
        var f = number("decimal", ""); var other = fixture("<text/><amount/>", bind("text", "string", "")
                + bind("amount", "decimal", ""), input("text") + input("amount"));
        int[] bins = new int[4];
        for (int seed = 1; seed <= 200; seed++) {
            var c = settings(4, 2, "").withSeed(seed).withProblems(rates(.25));
            var a = run(f, c); var b = run(other, c);
            assertEquals(targets(a, "outlier"), targets(b, "outlier"));
            assertEquals(1, targets(a, "outlier").size()); bins[targets(a, "outlier").iterator().next() - 1]++;
        }
        for (int n : bins) rate(n, 200, .25);
    }
    @Test void outlierRecordsAndAnswerValuesReplayExactly() throws Exception {
        var c = settings(30, 2, "").withProblems(rates(.5)); var r = run(number("decimal", ""), c);
        var expected = List.copyOf(r.plan().problems()); var next = r.plan().walkAll();
        assertEquals(expected, r.plan().problems());
        for (int i = 0; i < next.size(); i++) assertSameSubmission(r.submissions().get(i), next.get(i));
        var changed = run(number("decimal", ""), c.withSeed(991));
        assertNotEquals(expected, changed.plan().problems());
    }
    private static void assertIntervalAndUniformity(Run r, double low, double high, boolean integer) {
        var ps = records(r, "outlier"); assertEquals(r.config().submissions(), ps.size()); int[] bins = new int[3];
        for (var p : ps) {
            double x = Double.parseDouble(p.value());
            // Decimal serialization rounds to hundredths (spec 05); allow only that boundary rounding.
            assertTrue(x >= low - .005001 && x <= high + .005001, () -> "outlier " + x + " outside [" + low + "," + high + "]");
            if (integer) { assertEquals(Math.rint(x), x); assertTrue(p.value().matches("-?\\d+")); }
            // Use the discrete support width for whole-number uniform draws.
            double width = high - low + (integer ? 1 : 0);
            bins[Math.min(2, Math.max(0, (int) ((x - low) * 3 / width)))]++;
        }
        for (int n : bins) rate(n, ps.size(), 1.0 / 3);
    }
}
