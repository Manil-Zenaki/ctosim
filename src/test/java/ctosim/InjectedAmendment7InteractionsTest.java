package ctosim;

import java.util.List;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment7Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment7InteractionsTest {
    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void resentCopiesKeepInjectedTextAndCarryOnlyTheResentRecord(String fault) throws Exception {
        var r = run(textQuestion(""), settings(600, 2, teamCodes(fault, "-999") + ","
                + fixedText("answer", "ordinary"))
                .withProblems(new ProblemRates(.25, 0, 0, 0, 0, 0, 0, 0, List.of(fault))));
        int copiedCodes = 0;
        for (var p : records(r, "resent")) {
            var original = r.related(p);
            assertEquals(value(original, "answer"), value(r.target(p), "answer"));
            assertEquals(1, r.plan().problems().stream().filter(q -> q.submission() == p.submission()).count());
            if (value(original, "answer").equals("-999")) copiedCodes++;
        }
        assertTrue(copiedCodes > 0, "exercise a copy of an actual text fault");
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void answersChangedBySkipModulesCannotReceiveTeamCodes(String fault) throws Exception {
        var f = module(3, false, false, "/data/gate = 'yes'", "yes", "no", "TEAM");
        var r = run(f, settings(1600, 2, teamCodes(fault, "TEAM")
                + ",\"specialValues\":[\"TEAM\"]," + fixedText("gate", "yes"))
                .withProblems(rates(0, "skipModules", fault)));
        var skipped = targets(r, "skipModules");
        assertFalse(skipped.isEmpty());
        assertTrue(records(r, fault).stream().anyMatch(p -> p.field().equals("gate")), "exercise unprotected gate answers too");
        for (var p : records(r, fault)) {
            if (p.field().equals("gate")) {
                assertFalse(skipped.contains(p.submission()));
                assertEquals("yes", p.cleanValue());
                assertEquals("TEAM", p.value());
            }
        }
        for (var p : records(r, "skipModules")) assertEquals("no", value(r.target(p), "gate"));
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void anOutlierCannotBeOverwrittenByEitherTeamCodeFault(String fault) throws Exception {
        var r = run(number("int", ""), settings(400, 2, teamCodes(fault, "-999"))
                .withProblems(rates(1, fault)));
        assertEquals(400, records(r, "outlier").size());
        assertTrue(records(r, fault).isEmpty());
        for (var p : records(r, "outlier")) assertEquals(p.value(), value(r.target(p), "amount"));
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void addAnotherAnswersThatControlRepeatCountsRemainProtected(String fault) throws Exception {
        var f = InjectedAmendment2Support.extending(true, false, InjectedAmendment2Support.items("yes", "TEAM"));
        var r = textRun(fault, f, 240, teamCodes(fault, "TEAM") + "," + fixedText("more", "yes"));
        for (var s : r.submissions()) {
            assertEquals(2, count(s, "person"));
            assertEquals("yes", value(s, "person[1]/more"));
            assertEquals("yes", value(s, "person[2]/more"));
        }
        assertTrue(records(r, fault).stream().noneMatch(p -> p.field().contains("more")));
    }
}
