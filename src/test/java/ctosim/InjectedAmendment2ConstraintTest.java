package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment2Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment2ConstraintTest {
    // N(100,10): fences 70 and 130; preferred outlier interval [130,160].
    private static final String NORMAL = "\"min\":90,\"max\":110,\"distribution\":{\"normal\":{\"mean\":100,\"sd\":10}}";
    private static SimulationConfig config2(int n, String cap) throws Exception {
        return settings(n, 2, "\"fields\":{\"cap\":{\"value\":" + cap + "},\"amount\":{" + NORMAL + "}}")
                .withProblems(rates(1));
    }
    private static LoadedForm constrained(String type, String expression) throws Exception {
        return fixture("<cap/><amount/><twice/>", bind("cap", "decimal", "")
                + bind("amount", type, "constraint=\"" + expression + "\"")
                + bind("twice", "decimal", "calculate=\"/data/amount * 2\""), input("cap") + input("amount"));
    }
    private static void accepted(Run r, double low, double high) throws Exception {
        assertEquals(r.config().submissions(), records(r, "outlier").size());
        assertTrue(r.plan().warnings().isEmpty(), "successful constraint-aware injection needs no refusal warning");
        for (var p : records(r, "outlier")) {
            assertEquals("amount", p.field()); double x = Double.parseDouble(p.value());
            assertTrue(x >= low && x <= high, "outlier in accepted portion of interval: " + x);
            assertEquals(p.value(), value(r.target(p), "amount"));
            assertEquals(value(r.clean(p.submission()), "amount"), p.cleanValue());
            assertTrue(r.target(p).unsatisfiedConstraints().isEmpty()); assertTrue(r.target(p).formErrors().isEmpty());
            assertEquals(x * 2, Double.parseDouble(value(r.target(p), "twice")), 1e-8);
        }
        metadata2(r);
    }

    @ParameterizedTest @ValueSource(strings = {"int", "decimal"})
    void anUpperBoundFromAnEarlierAnswerClipsTheActualOutlierValues(String type) throws Exception {
        var r = run(constrained(type, ". &gt;= 90 and . &lt;= /data/cap"), config2(80, "135"));
        accepted(r, 130, 135);
        if (type.equals("int")) for (var p : records(r, "outlier")) assertEquals(Math.rint(Double.parseDouble(p.value())), Double.parseDouble(p.value()));
    }

    @ParameterizedTest @ValueSource(strings = {"int", "decimal"})
    void aSingleAcceptedPointAtTheFenceIsEnoughForEligibility(String type) throws Exception {
        accepted(run(constrained(type, ". &gt;= 90 and . &lt;= /data/cap"), config2(30, "130")), 130, 130);
    }

    @Test void aNarrowDecimalIntervalIsFoundRatherThanLostToTwentyRandomRejections() throws Exception {
        accepted(run(constrained("decimal", ". &gt;= 90 and . &lt;= /data/cap"), config2(60, "130.01")), 130, 130.01);
    }

    @Test void aNonlinearConstraintCanLeaveOnlyOneAcceptedIntegerInTheInterval() throws Exception {
        // Usual [90,110] answers pass, but the only allowed outlier is exactly cap=137.
        var r = run(constrained("int", ". &lt; 120 or (. - /data/cap) * (. - /data/cap) = 0"), config2(40, "137"));
        accepted(r, 137, 137);
    }

    @Test void disjointAcceptedIntegerValuesNeedNotFillAContinuousSubinterval() throws Exception {
        var r = run(constrained("int", ". &lt; 120 or . = /data/cap or . = /data/cap + 7"), config2(60, "137"));
        accepted(r, 130, 160);
        for (var p : records(r, "outlier")) assertTrue(Set.of("137", "144").contains(p.value()));
        // Amendment 2 promises an accepted value, not uniformity over a disconnected set.
    }

    @Test void aCalculatedBoundIsEvaluatedWithTheCurrentEarlierAnswer() throws Exception {
        var f = fixture("<base/><cap/><amount/><twice/>", bind("base", "int", "")
                + bind("cap", "int", "calculate=\"/data/base + 30\"")
                + bind("amount", "int", "constraint=\". &gt;= 90 and . &lt;= /data/cap\"")
                + bind("twice", "int", "calculate=\"2 * /data/amount\""), input("base") + input("amount"));
        var c = settings(40, 2, "\"fields\":{\"base\":{\"value\":100},\"amount\":{" + NORMAL + "}}")
                .withProblems(rates(1));
        accepted(run(f, c), 130, 130);
    }

    @ParameterizedTest @ValueSource(strings = {". &gt;= 90 and . &lt; /data/cap", ". &gt;= 90 and . * 2 &lt; /data/cap * 2"})
    void exclusiveRealFenceWithNoAcceptedOutlierProducesOnlyTheFinalTargetWarning(String expression) throws Exception {
        var r = run(constrained("int", expression), config2(1, "130"));
        assertTrue(records(r, "outlier").isEmpty()); ordinary(r, 1);
        assertEquals(1, r.plan().warnings().size()); assertWarning(r, "", 1);
        assertFalse(r.plan().warnings().get(0).matches("(?s).*\\b20\\b.*"), "obsolete twenty-refusal diagnostic is removed");
    }

    @Test void rejectedOutlierQuestionsDoNotPreventAnotherQuestionFromReceivingTheOutlier() throws Exception {
        var f = fixture("<cap/><blocked/><amount/><twice/>", bind("cap", "int", "")
                + bind("blocked", "int", "constraint=\". &gt;= 90 and . &lt; /data/cap\"")
                + bind("amount", "int", "constraint=\". &gt;= 90 and . &lt;= /data/cap\"")
                + bind("twice", "int", "calculate=\"2 * /data/amount\""), input("cap") + input("blocked") + input("amount"));
        var c = settings(60, 2, "\"fields\":{\"cap\":{\"value\":130},\"blocked\":{" + NORMAL + "},\"amount\":{" + NORMAL + "}}")
                .withProblems(rates(1));
        var r = run(f, c); accepted(r, 130, 130);
        for (var s : r.submissions()) assertTrue(Double.parseDouble(value(s, "blocked")) < 130);
    }

    @Test void orderIsUniformAmongQuestionsThatPassTheRealConstraint() throws Exception {
        String nodes = "<cap/>", binds = bind("cap", "int", ""), body = input("cap");
        String fields = "\"cap\":{\"value\":130}";
        for (String name : List.of("blocked", "z", "a", "m")) {
            nodes += "<" + name + "/>";
            binds += bind(name, "int", "constraint=\". &gt;= 90 and . " + (name.equals("blocked") ? "&lt;" : "&lt;=") + " /data/cap\"");
            body += input(name); fields += ",\"" + name + "\":{" + NORMAL + "}";
        }
        var r = run(fixture(nodes, binds, body), settings(600, 2, "\"fields\":{" + fields + "}").withProblems(rates(1)));
        assertEquals(600, records(r, "outlier").size()); Map<String, Integer> counts = new HashMap<>();
        for (var p : records(r, "outlier")) { assertEquals("130", p.value()); counts.merge(p.field(), 1, Integer::sum); }
        assertEquals(Set.of("z", "a", "m"), counts.keySet());
        for (int count : counts.values()) rate(count, 600, 1.0 / 3);
    }

    @Test void aRealLowerBoundRestrictsTheLowerIntervalWhenHintsAlreadyExcludeTheUpperTail() throws Exception {
        var r = run(constrained("int", ". &lt;= 120 and . &gt;= /data/cap"), config2(40, "70"));
        accepted(r, 70, 70);
    }

    @Test void anAcceptedValueOutsideTheOutlierIntervalDoesNotMakeAQuestionEligible() throws Exception {
        var r = run(constrained("int", ". &gt;= 90 and (. &lt; 120 or . = /data/cap)"), config2(1, "200"));
        assertTrue(r.plan().problems().isEmpty()); assertEquals(1, r.plan().warnings().size()); ordinary(r, 1);
    }

    @Test void realConstraintResultsReplayAcrossCallsLocalesAndTimeZones() throws Exception {
        var r = run(constrained("int", ". &lt; 120 or . = /data/cap"), config2(30, "137"));
        accepted(r, 137, 137);
        Locale locale = Locale.getDefault(); TimeZone zone = TimeZone.getDefault();
        try { Locale.setDefault(Locale.FRANCE); TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu")); replay2(r); }
        finally { Locale.setDefault(locale); TimeZone.setDefault(zone); }
    }
}
