package ctosim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FieldworkTestSupport.*;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Tests locale independence; FormWalker temporarily changes the JVM time zone")
class FieldworkContractTest {
    @Test void visitsAndRunWarningsAreEmptyBeforeTheFirstWalk() throws Exception {
        var plan = new Fieldwork(timed(9 * 3600), settings("\"submissions\":4,\"fieldworkDays\":1,"
                + "\"enumerators\":1,\"id\":\"answer\""));
        assertTrue(plan.visits().isEmpty());
        assertTrue(plan.warnings().isEmpty());
    }

    @Test void allReturnedListsRejectModificationBeforeAndAfterWalking() throws Exception {
        var plan = new Fieldwork(question("string", ""), settings("\"submissions\":3,\"id\":\"answer\","
                + "\"fields\":{\"unknown\":{\"value\":\"x\"}}"));
        assertThrows(UnsupportedOperationException.class, () -> plan.visits().add(visit(1)));
        assertThrows(UnsupportedOperationException.class, () -> plan.warnings().add("injected"));
        assertImmutable(plan.configWarnings(), "injected");
        var submissions = plan.walkAll();
        assertImmutable(submissions, submissions.get(0));
        assertImmutable(plan.visits(), visit(1));
        assertImmutable(plan.warnings(), "injected");
        assertEquals(3, plan.visits().size());
        assertEquals(1, plan.warnings().size());
    }

    @Test void anEmptyConfigurationWarningListIsAlsoUnmodifiable() throws Exception {
        var plan = new Fieldwork(metadata(), settings("\"submissions\":1"));
        assertTrue(plan.configWarnings().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> plan.configWarnings().add("injected"));
    }

    @Test void repeatedCallsReproduceResultsAndReplaceRatherThanAccumulateLastRunState() throws Exception {
        var config = settings("\"submissions\":5,\"enumerators\":1,\"fieldworkDays\":1,\"id\":\"answer\","
                + "\"fields\":{\"answer\":{\"texts\":[\"x\",\"y\"]}}");
        var plan = new Fieldwork(timed(9 * 3600), config);
        var first = new ArrayList<>(plan.walkAll());
        var visits = new ArrayList<>(plan.visits());
        var warnings = new ArrayList<>(plan.warnings());
        var configurationWarnings = new ArrayList<>(plan.configWarnings());
        for (int i = 0; i < 3; i++) {
            sameRun(first, plan.walkAll());
            assertEquals(visits, plan.visits());
            assertEquals(warnings, plan.warnings());
            assertEquals(configurationWarnings, plan.configWarnings());
        }
        assertEquals(5, plan.visits().size());
        assertEquals(2, plan.warnings().size());
    }

    @Test void independentlyLoadedIdenticalFormBytesGiveIdenticalPlansAndSubmissions() throws Exception {
        var config = settings("\"submissions\":15,\"enumerators\":3,\"fieldworkDays\":2,\"id\":\"answer\"");
        var first = new Fieldwork(metadata(), config);
        var second = new Fieldwork(metadata(), settings("\"submissions\":15,\"enumerators\":3,\"fieldworkDays\":2,\"id\":\"answer\""));
        sameRun(first.walkAll(), second.walkAll());
        assertEquals(first.visits(), second.visits());
        assertEquals(first.warnings(), second.warnings());
        assertEquals(first.configWarnings(), second.configWarnings());
    }

    @ParameterizedTest @ValueSource(strings = {"metadata", "locations", "rejectedIds"})
    void eachPlannedSubmissionEqualsAnIndependentWalkOfItsAssignedVisit(String kind) throws Exception {
        LoadedForm form = switch (kind) {
            case "locations" -> locations();
            case "rejectedIds" -> question("int", "constraint=\". = 7\"");
            default -> metadata();
        };
        String extra = kind.equals("locations") ? "" : ",\"id\":\"answer\"";
        if (kind.equals("rejectedIds")) extra += ",\"fields\":{\"answer\":{\"texts\":[\"7\",\"8\"]}}";
        var config = settings("\"submissions\":12,\"enumerators\":3,\"fieldworkDays\":2" + extra);
        var plan = new Fieldwork(form, config);
        var submissions = plan.walkAll();
        var walker = new FormWalker(form, config);
        for (int i = submissions.size() - 1; i >= 0; i--) {
            same(submissions.get(i), walker.walk(i + 1, plan.visits().get(i)));
        }
    }

    @Test void planWarningsExcludeWarningsBelongingToIndividualSubmissions() throws Exception {
        var plan = new Fieldwork(question("int", "constraint=\"false()\""), settings("\"submissions\":3"));
        var submissions = plan.walkAll();
        assertTrue(submissions.stream().allMatch(s -> !s.warnings().isEmpty()), "fixture must produce submission warnings");
        assertTrue(plan.warnings().isEmpty());
        assertTrue(plan.configWarnings().isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void overflowWarningComesBeforeTheIdPoolWarning(boolean exhaustedPool) throws Exception {
        String fields = exhaustedPool ? ",\"fields\":{\"answer\":{\"texts\":[\"one\",\"two\"]}}" : "";
        var plan = new Fieldwork(timed(9 * 3600), settings("\"submissions\":5,\"enumerators\":1,"
                + "\"fieldworkDays\":2,\"id\":\"answer\"" + fields));
        plan.walkAll();
        assertEquals(2, plan.warnings().size());
        warning(List.of(plan.warnings().get(0)), null, 5, 2);
        if (exhaustedPool) warning(List.of(plan.warnings().get(1)), "answer", 2, 5);
        else warning(List.of(plan.warnings().get(1)), "answer");
    }

    @Test void differentSeedsMeaningfullyChangeThePlan() throws Exception {
        var config = settings("\"submissions\":20,\"enumerators\":4,\"fieldworkDays\":2");
        var first = new Fieldwork(metadata(), config);
        var second = new Fieldwork(metadata(), config.withSeed(SEED + 1));
        first.walkAll();
        second.walkAll();
        assertNotEquals(first.visits(), second.visits());
        assertNotEquals(first.visits().stream().map(Visit::start).toList(), second.visits().stream().map(Visit::start).toList());
        assertNotEquals(first.visits().stream().map(Visit::latitude).toList(), second.visits().stream().map(Visit::latitude).toList());
    }

    @Test void localeAndMachineTimeZoneDoNotChangeAnyResultAndTimeZoneIsRestored() throws Exception {
        Locale original = Locale.getDefault();
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        TimeZone zone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            var config = settings("\"submissions\":8,\"enumerators\":2,\"fieldworkDays\":2,\"timeZone\":\"Africa/Addis_Ababa\"");
            var first = new Fieldwork(locations(), config);
            var expected = first.walkAll();
            assertEquals("Pacific/Honolulu", TimeZone.getDefault().getID());
            for (Locale locale : List.of(Locale.FRANCE, Locale.forLanguageTag("ar-EG"), Locale.forLanguageTag("tr-TR"))) {
                Locale.setDefault(locale);
                TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
                var other = new Fieldwork(locations(), config);
                sameRun(expected, other.walkAll());
                assertEquals(first.visits(), other.visits());
                assertEquals(first.warnings(), other.warnings());
                assertEquals("Asia/Tokyo", TimeZone.getDefault().getID());
            }
        } finally {
            Locale.setDefault(original);
            Locale.setDefault(Locale.Category.FORMAT, format);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            TimeZone.setDefault(zone);
        }
    }

    private static <T> void assertImmutable(List<T> list, T replacement) {
        assertFalse(list.isEmpty(), "use a nonempty list to exercise all mutation methods");
        assertThrows(UnsupportedOperationException.class, () -> list.add(replacement));
        assertThrows(UnsupportedOperationException.class, () -> list.set(0, replacement));
        assertThrows(UnsupportedOperationException.class, () -> list.remove(0));
        assertThrows(UnsupportedOperationException.class, list::clear);
    }
}
