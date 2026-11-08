package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment9Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment9FaultTest {
    @Test void everyPostUpdateInterviewOfTheFaultyEnumeratorAndOnlyThoseInterviewsIsOutdated() throws Exception {
        var r = run(simple(NEW), settings(80, 2, "\"interviewsPerDay\":1," + update(UPDATE.toString(), null))
                .withProblems(rates(0, FAULT)));
        String faulty = soleEnumerator(r, FAULT);
        var recorded = targets(r, FAULT);
        assertEquals(38, outdated(r).size(), "40 interviews per enumerator, two before the update");
        for (var s : r.submissions()) {
            boolean before = s.start().toLocalDate().isBefore(UPDATE);
            boolean isFaulty = value(s, "username").equals(faulty);
            assertEquals(!before && isFaulty, recorded.contains(s.number()), "exactly the outdated interviews are recorded");
            assertEquals(before || isFaulty ? OLD : NEW, version(s));
        }
        metadata(r, OLD, NEW); assertTrue(r.plan().warnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(ints = {2, 3, 8})
    void anUpdateOnTheFirstDayIncludesTheFaultyEnumeratorsFirstInterview(int e) throws Exception {
        var r = run(simple(NEW), settings(e * 10, e, update("2026-03-02", null)).withProblems(rates(0, FAULT)));
        assertEquals(10, outdated(r).size());
        assertEquals(1, enumerators(r, FAULT).size());
        assertTrue(outdated(r).get(0).submission() <= e, "faults have no first-interview exclusion");
        metadata(r, OLD, NEW);
    }

    @Test void aSingleSubmissionHasOnlyTheAssignedEnumeratorsVersion() throws Exception {
        var f = simple(NEW); var c = settings(20, 2, update("2026-03-02", null)).withProblems(rates(0, FAULT));
        String faulty = soleEnumerator(run(f, c), FAULT);
        var single = run(f, settings(1, 2, update("2026-03-02", null)).withProblems(rates(0, FAULT)));
        var s = single.submissions().get(0); boolean assigned = value(s, "username").equals(faulty);
        assertEquals(assigned ? 1 : 0, outdated(single).size());
        assertEquals(assigned ? OLD : NEW, version(s));
    }

    @Test void versionFaultLeavesAnswersClocksMetadataLocationsAndWarningsUnchanged() throws Exception {
        var f = versioned(NEW, "<answer/><text/><gps/><twice/>",
                bind("answer", "int", "") + bind("text", "string", "") + bind("gps", "geopoint", "")
                        + bind("twice", "int", "calculate=\"/data/answer * 2\""),
                input("answer") + input("text") + input("gps"));
        var c = settings(40, 2, update(UPDATE.toString(), null));
        var clean = run(f, c); var faulty = run(f, c.withProblems(rates(0, FAULT)));
        assertFalse(outdated(faulty).isEmpty()); onlyVersionMayDiffer(clean, faulty);
        assertEquals(clean.plan().warnings(), faulty.plan().warnings());
    }

    @Test void explicitPreviousVersionIsTheExactStoredAndRecordedValue() throws Exception {
        String previous = "ancienne, \"été\" & <beta>";
        var r = run(simple("release-new"), settings(24, 2, update(UPDATE.toString(), previous)).withProblems(rates(0, FAULT)));
        assertFalse(outdated(r).isEmpty()); metadata(r, previous, "release-new");
        for (var p : outdated(r)) assertEquals(previous, version(r.target(p)));
    }

    @Test void inferredZeroIsAUsablePreviousVersionForTheFault() throws Exception {
        var r = run(simple("1"), settings(20, 2, update("2026-03-02", null)).withProblems(rates(0, FAULT)));
        assertEquals(10, outdated(r).size()); metadata(r, "0", "1");
        assertTrue(r.plan().warnings().isEmpty());
    }

    @Test void anExplicitPreviousVersionDoesNotEnableTheFaultWhenTheFormHasNoVersion() throws Exception {
        var f = simple(null); var c = settings(20, 2, update("2026-03-02", "old"));
        var r = run(f, c.withProblems(rates(0, FAULT)));
        assertTrue(outdated(r).isEmpty()); assertEquals(1, r.plan().warnings().size()); assertWarning(r, FAULT);
        FieldworkTestSupport.sameRun(run(f, settings(20, 2, "")).submissions(), r.submissions());
    }

    @Test void aFutureValidUpdateProducesNoOutdatedRecordsAndDoesNotDisableTheFaultSlot() throws Exception {
        var f = sevenFaultForm();
        var r = run(f, settings(12 * 100, 12, InjectedAmendment8Support.sixSettings(true)
                + "," + update("2040-01-01", null)).withProblems(rates(0, SEVEN.toArray(String[]::new))));
        assertTrue(outdated(r).isEmpty());
        for (var s : r.submissions()) assertEquals(OLD, version(s));
        for (String earlier : SEVEN.subList(0, 6)) assertFalse(records(r, earlier).isEmpty());
        assertEquals(1, r.plan().warnings().size()); assertWarning(r, "", 14);
    }

    @Test void missingUpdateDisablesTheFaultAndProducesOneNamedWarning() throws Exception {
        var f = simple(NEW); var c = settings(40, 2, "");
        var clean = run(f, c); var disabled = run(f, c.withProblems(rates(0, FAULT)));
        FieldworkTestSupport.sameRun(clean.submissions(), disabled.submissions());
        assertEquals(clean.plan().visits(), disabled.plan().visits());
        assertTrue(outdated(disabled).isEmpty()); assertEquals(1, disabled.plan().warnings().size());
        assertWarning(disabled, FAULT);
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"0", "-7", "v2", "2601051230"})
    void impossibleOrEqualVersionsDisableTheFaultWithOneExplanation(String current) throws Exception {
        var f = simple(current);
        String previous = NEW.equals(current) ? NEW : null;
        var r = run(f, settings(40, 2, update(UPDATE.toString(), previous)).withProblems(rates(0, FAULT)));
        var clean = run(f, settings(40, 2, ""));
        FieldworkTestSupport.sameRun(clean.submissions(), r.submissions());
        assertTrue(outdated(r).isEmpty()); assertEquals(1, r.plan().warnings().size()); assertWarning(r, FAULT);
    }

    @Test void disablingTheFaultRemovesItBeforeEarlierFaultsAreAssigned() throws Exception {
        var f = sevenFaultForm(); var c = settings(12 * 100, 12, InjectedAmendment8Support.sixSettings(true));
        var earlier = run(f, c.withProblems(rates(0, SEVEN.subList(0, 6).toArray(String[]::new))));
        var disabled = run(f, c.withProblems(rates(0, SEVEN.toArray(String[]::new))));
        for (String fault : SEVEN.subList(0, 6)) assertFalse(records(earlier, fault).isEmpty());
        InjectedAmendment6Support.sameResults(earlier, disabled);
        assertTrue(earlier.plan().warnings().isEmpty());
        assertEquals(1, disabled.plan().warnings().size()); assertWarning(disabled, FAULT);
    }

    @Test void invalidUpdateRemovesItsFaultBeforeAssignmentToo() throws Exception {
        var f = sevenFaultForm(); var c = settings(1200, 12, InjectedAmendment8Support.sixSettings(true)
                + "," + update(UPDATE.toString(), NEW));
        var earlier = run(f, c.withProblems(rates(0, SEVEN.subList(0, 6).toArray(String[]::new))));
        var disabled = run(f, c.withProblems(rates(0, SEVEN.toArray(String[]::new))));
        InjectedAmendment6Support.sameResults(earlier, disabled);
        assertTrue(outdated(disabled).isEmpty()); assertEquals(1, disabled.plan().warnings().size());
    }

    @Test void disabledFaultWarningIsBetweenOriginalProblemWarningsAndOutlierWarnings() throws Exception {
        var f = versioned(NEW, "<answer/>", bind("answer", "string", ""), input("answer"));
        var r = run(f, settings(40, 2, "").withProblems(new ProblemRates(0, 0, 0, 0, 0, 0, .1, .1, List.of(FAULT))));
        var warnings = r.plan().warnings();
        assertEquals(6, warnings.size(), "one travel, one disabled fault, four ineligible outliers");
        assertTrue(warnings.get(0).contains("travel")); assertTrue(warnings.get(1).contains(FAULT));
        for (int i = 2; i < warnings.size(); i++) assertTrue(warnings.get(i).contains("outlier"));
    }
}
