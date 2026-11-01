package ctosim;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment2Support.*;
import static ctosim.InjectedAmendment5Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment5DisabledTest {
    @ParameterizedTest @ValueSource(ints = {1, 2, 8})
    void omittedCodesDisableTheFaultAndWarnOnceWithoutReservingAnEnumerator(int enumerators) throws Exception {
        var form = selection(false, "ok", "97");
        var config = settings(enumerators * 20, enumerators,
                "\"specialValues\":[\"97\"]," + fixed("answer", "ok"));
        assertTrue(config.dontKnowCodes().isEmpty(), "omission represents the empty accessor list");
        var clean = run(form, config);
        var disabled = run(form, config.withProblems(rates(0, "dontKnow")));
        sameResults(clean, disabled);
        assertEquals(1, disabled.plan().warnings().size(), "one configuration warning, not per interview or enumerator");
        assertTrue(disabled.plan().warnings().get(0).contains("dontKnow"));
    }

    @ParameterizedTest @ValueSource(ints = {4, 40})
    void aDisabledDontKnowDoesNotCauseCumulationOrExtraSmallSampleWarnings(int submissions) throws Exception {
        var form = singleTimed(0);
        var config = settings(submissions, 2, "\"specialValues\":[\"97\"]");
        var control = run(form, config.withProblems(rates(0, "tooFast")));
        var disabled = run(form, config.withProblems(rates(0, "dontKnow", "tooFast")));
        sameResults(control, disabled);
        var warnings = disabled.plan().warnings();
        assertEquals(1, warnings.stream().filter(w -> w.contains("dontKnow")).count());
        assertEquals(control.plan().warnings(), warnings.stream().filter(w -> !w.contains("dontKnow")).toList());
    }

    @ParameterizedTest @ValueSource(ints = {4, 8})
    void laterFaultsAreAssignedAsIfTheInactiveDontKnowWereAbsent(int enumerators) throws Exception {
        var form = resource2("consent");
        var config = settings(enumerators * 100, enumerators, consent(.5)
                + ",\"fields\":{\"gate\":{\"value\":\"yes\"},\"code\":{\"value\":\"ok\"}}");
        var control = run(form, config.withProblems(rates(0, "tooFast", "skipModules", "fakeRefusal")));
        var disabled = run(form, config.withProblems(rates(0, "fakeRefusal", "dontKnow", "skipModules", "tooFast")));
        for (String fault : List.of("tooFast", "skipModules", "fakeRefusal")) {
            assertFalse(records(control, fault).isEmpty(), "exercise active fault " + fault);
        }
        sameResults(control, disabled);
        assertEquals(1, disabled.plan().warnings().stream().filter(w -> w.contains("dontKnow")).count());
        assertEquals(control.plan().warnings(), disabled.plan().warnings().stream()
                .filter(w -> !w.contains("dontKnow")).toList());
    }

    @Test void twoInactiveFaultsProduceOnlyTheirOwnConfigurationWarnings() throws Exception {
        var form = singleTimed(0);
        var config = settings(2, 1, "");
        var control = run(form, config);
        var disabled = run(form, config.withProblems(rates(0, "dontKnow", "fakeRefusal")));
        sameResults(control, disabled);
        assertEquals(2, disabled.plan().warnings().size());
        assertEquals(1, disabled.plan().warnings().stream().filter(w -> w.contains("dontKnow")).count());
        assertEquals(1, disabled.plan().warnings().stream().filter(w -> w.contains("fakeRefusal")).count());
    }

    @Test void theMissingCodesWarningAppearsAfterTableWarningsAndBeforeOutlierWarnings() throws Exception {
        // No eligible reinterview targets above E; text cannot receive an outlier.
        var run = run(singleTimed(0), settings(2, 2, "").withProblems(
                new ProblemRates(0, .5, 0, 0, 0, 0, 0, .5, List.of("dontKnow"))));
        var warnings = run.plan().warnings();
        int faultIndex = -1, lastOriginal = -1;
        for (int i = 0; i < warnings.size(); i++) {
            String warning = warnings.get(i);
            if (warning.contains("reinterview")) lastOriginal = i;
            if (warning.contains("dontKnow")) { assertEquals(-1, faultIndex); faultIndex = i; }
        }
        assertTrue(lastOriginal >= 0, "exercise an original-table warning");
        assertTrue(faultIndex > lastOriginal);
        // A failed outlier must name its submission, but need not use the literal word "outlier".
        assertTrue(faultIndex < warnings.size() - 1, "failed-outlier warning follows the fault warning");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void noMissingCodesWarningIsGivenWhenDontKnowIsNotRequested(boolean problemsPresent) throws Exception {
        var form = selection(false, "ok", "97");
        var config = settings(40, 2, "\"specialValues\":[\"97\"]," + fixed("answer", "ok"));
        if (problemsPresent) config = config.withProblems(rates(0));
        var run = run(form, config);
        assertTrue(run.plan().problems().isEmpty());
        assertTrue(run.plan().warnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void supplyingCodesDoesNotOptIntoFaultInjection(boolean problemsPresent) throws Exception {
        var form = selection(false, "ok", "DK");
        var config = settings(100, 2, codes("DK") + "," + fixed("answer", "ok"));
        if (problemsPresent) config = config.withProblems(rates(0));
        var run = run(form, config);
        unchanged(run);
        assertTrue(run.plan().warnings().isEmpty());
    }

    @Test void aNonemptyButUnusableCodeListStillReservesItsFaultyEnumerator() throws Exception {
        // The disabling rule is about the empty configuration list, not form eligibility.
        var run = run(number("int", ""), settings(40, 2, codes("not-a-number"))
                .withProblems(rates(0, "tooFast", "dontKnow")));
        assertTrue(records(run, "dontKnow").isEmpty());
        assertFalse(records(run, "tooFast").isEmpty());
        assertEquals(1, run.plan().warnings().size(), "two active faults cumulate on one enumerator");
        assertWarning(run, "", 4);
    }
}
