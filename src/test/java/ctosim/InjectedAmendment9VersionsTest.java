package ctosim;

import java.time.LocalDate;
import java.util.stream.Stream;
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
class InjectedAmendment9VersionsTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void updateAppliesWithoutProblemsAndWithAnExplicitEmptyFaultList(boolean empty) throws Exception {
        var c = settings(12, 2, "\"interviewsPerDay\":1," + update(UPDATE.toString(), null));
        if (empty) c = c.withProblems(rates(0));
        var r = run(simple(NEW), c);
        normalVersions(r, UPDATE, OLD, NEW);
        assertTrue(r.submissions().stream().anyMatch(s -> s.start().toLocalDate().equals(UPDATE.minusDays(1))));
        assertTrue(r.submissions().stream().anyMatch(s -> s.start().toLocalDate().equals(UPDATE)));
        assertTrue(r.submissions().stream().anyMatch(s -> s.start().toLocalDate().equals(UPDATE.plusDays(1))));
        assertTrue(r.plan().problems().isEmpty()); assertTrue(r.plan().warnings().isEmpty());
    }

    static Stream<Arguments> inferred() {
        return Stream.of(Arguments.of("1", "0"), Arguments.of("2", "1"), Arguments.of(NEW, OLD),
                Arguments.of("9223372036854775809", "9223372036854775808"));
    }

    @ParameterizedTest @MethodSource("inferred")
    void positiveDigitVersionsAreDecrementedExactlyWithoutIntegerOverflow(String current, String previous) throws Exception {
        var r = run(simple(current), settings(8, 2, update(UPDATE.toString(), null)));
        normalVersions(r, UPDATE, previous, current); assertTrue(r.plan().warnings().isEmpty());
    }

    @ParameterizedTest @CsvSource({"2020-01-01,2601051230", "2030-01-01,2601051229"})
    void updateOutsideTheFieldworkPeriodStillSelectsTheCorrectVersion(String date, String expected) throws Exception {
        var r = run(simple(NEW), settings(20, 2, update(date, null)));
        for (var s : r.submissions()) assertEquals(expected, version(s));
        assertTrue(r.plan().warnings().isEmpty()); assertTrue(r.plan().problems().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "v2", "1.0", "1e3", "+2", " 2", "2 "})
    void nonPositiveOrNonDigitVersionWithoutAnExplicitPreviousVersionDoesNotSimulateAnUpdate(String current) throws Exception {
        var f = simple(current); var clean = run(f, settings(20, 2, ""));
        var r = run(f, settings(20, 2, update(UPDATE.toString(), null)));
        FieldworkTestSupport.sameRun(clean.submissions(), r.submissions());
        assertEquals(clean.plan().visits(), r.plan().visits());
        assertTrue(r.plan().problems().isEmpty());
        assertEquals(1, r.plan().warnings().size(), "one explanation for the impossible update");
        assertFalse(r.plan().warnings().get(0).isBlank());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aFormWithoutAVersionCannotSimulateAnUpdateEvenWithAnExplicitPreviousVersion(boolean explicit) throws Exception {
        var f = simple(null); var clean = run(f, settings(20, 2, ""));
        var r = run(f, settings(20, 2, update(UPDATE.toString(), explicit ? "old" : null)));
        FieldworkTestSupport.sameRun(clean.submissions(), r.submissions());
        assertTrue(r.plan().problems().isEmpty()); assertEquals(1, r.plan().warnings().size());
    }

    @ParameterizedTest @ValueSource(strings = {"v2", "0", "-1", "2601051230"})
    void explicitPreviousVersionOverridesInferenceAndSupportsNonnumericFormVersions(String current) throws Exception {
        String previous = "ancienne, \"été\" & <beta>";
        var r = run(simple(current), settings(12, 2, update(UPDATE.toString(), previous)));
        normalVersions(r, UPDATE, previous, current); assertTrue(r.plan().warnings().isEmpty());
    }

    @Test void equalPreviousAndCurrentVersionsDisableTheUpdateWithOneWarning() throws Exception {
        var f = simple(NEW); var clean = run(f, settings(20, 2, ""));
        var r = run(f, settings(20, 2, update(UPDATE.toString(), NEW)));
        FieldworkTestSupport.sameRun(clean.submissions(), r.submissions());
        assertTrue(r.plan().problems().isEmpty()); assertEquals(1, r.plan().warnings().size());
        assertFalse(r.plan().warnings().get(0).isBlank());
    }

    @Test void noUpdateKeepsTheOriginalVersionAndDoesNotAddWarnings() throws Exception {
        var r = run(simple(NEW), settings(20, 2, ""));
        for (var s : r.submissions()) assertEquals(NEW, version(s));
        assertTrue(r.plan().warnings().isEmpty()); assertTrue(r.plan().problems().isEmpty());
    }

    @Test void anInterviewEndingAndUploadingAfterTheUpdateUsesItsEarlierStartDate() throws Exception {
        var c = SimulationConfig.parse("{\"seed\":819731,\"submissions\":4,\"enumerators\":2,"
                + "\"start\":\"2026-03-02T22:30:00\",\"fieldworkDays\":1000,\"missingRate\":0,"
                + update("2026-03-03", null) + "}", "overnight-version.json").withProblems(rates(0, FAULT));
        var r = run(timed(7200), c);
        for (int i = 0; i < 2; i++) {
            var s = r.submissions().get(i);
            assertEquals(LocalDate.of(2026, 3, 2), s.start().toLocalDate());
            assertFalse(s.end().toLocalDate().isBefore(LocalDate.of(2026, 3, 3)));
            assertFalse(s.submitted().toLocalDate().isBefore(LocalDate.of(2026, 3, 3)));
            assertEquals(OLD, version(s));
        }
        assertTrue(outdated(r).stream().noneMatch(p -> p.submission() <= 2));
        assertEquals(1, outdated(r).size(), "only the faulty enumerator's next-day interview is outdated");
    }

    @Test void schedulingProblemsUseTheActualInterviewStartDateForVersionSelection() throws Exception {
        var c = settings(60, 2, update("2026-03-03", null))
                .withProblems(only("offhours", .35));
        var r = run(simple(NEW), c);
        assertFalse(records(r, "offhours").isEmpty());
        normalVersions(r, LocalDate.of(2026, 3, 3), OLD, NEW);
        assertTrue(outdated(r).isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"+14:00", "-11:00"})
    void updateDateIsTheLocalInterviewDateRatherThanTheUtcCalendarDate(String zone) throws Exception {
        var c = SimulationConfig.parse("{\"seed\":819731,\"submissions\":20,\"enumerators\":2,"
                + "\"start\":\"2026-03-04T00:00:00\",\"timeZone\":\"" + zone + "\","
                + "\"fieldworkDays\":1000,\"missingRate\":0," + update("2026-03-04", null) + "}", "local-version.json");
        var r = run(simple(NEW), c);
        for (var s : r.submissions()) assertEquals(NEW, version(s));
        assertTrue(r.plan().warnings().isEmpty());
    }

    @Test void aValidUpdateChangesOnlyVersionAttributesAndDoesNotMutateTheLoadedForm() throws Exception {
        var f = simple(NEW); var c = settings(20, 2, ""); var before = run(f, c);
        var updated = run(f, settings(20, 2, update(UPDATE.toString(), null)));
        normalVersions(updated, UPDATE, OLD, NEW); onlyVersionMayDiffer(before, updated);
        assertEquals(NEW, f.version());
        FieldworkTestSupport.sameRun(before.submissions(), run(f, c).submissions());
    }
}
