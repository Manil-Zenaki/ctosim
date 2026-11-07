package ctosim;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment9Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Tests change the JVM locale and time zone")
class InjectedAmendment9RecordsTest {
    @Test void answerKeyUsesVersionMetadataAndDeterministicKindWithExactCsvEscaping() throws Exception {
        String previous = "ancienne, \"été\" & <beta>";
        var r = run(simple(NEW), settings(30, 2, update(UPDATE.toString(), previous)).withProblems(rates(0, FAULT)));
        assertFalse(outdated(r).isEmpty()); metadata(r, previous, NEW);
        var rows = SurveyCtoExporterTestSupport.csv(AnswerKey.csv(r.plan().problems()));
        assertEquals(List.of("KEY", "enumerator", "family", "problem", "check", "field", "value", "clean_value", "related_KEY", "kind"), rows.get(0));
        assertEquals(outdated(r).size() + 1, rows.size());
        for (int i = 0; i < outdated(r).size(); i++) {
            var p = outdated(r).get(i);
            assertEquals(List.of(p.key(), p.enumerator(), "enumerator", FAULT, "ipacheckversions", "formdef_version",
                    previous, NEW, "", "deterministic"), rows.get(i + 1));
        }
    }

    @Test void simulatedVersionsReachTheWideAndLongExportsPerSubmission() throws Exception {
        var f = simple(NEW);
        var r = run(f, settings(30, 2, update(UPDATE.toString(), null)).withProblems(rates(0, FAULT)));
        assertFalse(outdated(r).isEmpty());
        var resource = getClass().getResource("/ctosim/injected-problems/amendment9/catalog.json"); assertNotNull(resource);
        var exporter = new SurveyCtoExporter(FieldCatalog.load(Path.of(resource.toURI())), f, "example");
        List<String> files = new ArrayList<>(); files.add(exporter.wide(r.submissions()));
        var longFiles = exporter.longFiles(r.submissions()); assertEquals(1, longFiles.size()); files.addAll(longFiles.values());
        Map<String, String> expected = new HashMap<>();
        for (var s : r.submissions()) expected.put(key(s), version(s));
        assertEquals(Set.of(OLD, NEW), new HashSet<>(expected.values()));
        for (String file : files) {
            var rows = SurveyCtoExporterTestSupport.csv(file);
            int keyColumn = rows.get(0).indexOf("KEY"), versionColumn = rows.get(0).indexOf("formdef_version");
            assertTrue(keyColumn >= 0); assertTrue(versionColumn >= 0);
            assertEquals(expected.size() + 1, rows.size()); Set<String> seen = new HashSet<>();
            for (var row : rows.subList(1, rows.size())) {
                String key = row.get(keyColumn); assertTrue(expected.containsKey(key)); assertTrue(seen.add(key));
                assertEquals(expected.get(key), row.get(versionColumn));
            }
        }
    }

    @Test void repeatedAndFreshWalksReproduceVersionsRecordsWarningsAndVisits() throws Exception {
        var f = simple(NEW); var c = settings(40, 2, update(UPDATE.toString(), null)).withProblems(rates(0, FAULT));
        var plan = new Fieldwork(f, c); assertTrue(plan.problems().isEmpty());
        var first = plan.walkAll(); var records = List.copyOf(plan.problems());
        var warnings = List.copyOf(plan.warnings()); var visits = List.copyOf(plan.visits());
        assertFalse(records.isEmpty());
        FieldworkTestSupport.sameRun(first, plan.walkAll());
        assertEquals(records, plan.problems()); assertEquals(warnings, plan.warnings()); assertEquals(visits, plan.visits());
        var fresh = run(simple(NEW), c); FieldworkTestSupport.sameRun(first, fresh.submissions());
        assertEquals(records, fresh.plan().problems()); assertEquals(warnings, fresh.plan().warnings()); assertEquals(visits, fresh.plan().visits());
        assertEquals(AnswerKey.csv(records), AnswerKey.csv(fresh.plan().problems()));
        assertThrows(UnsupportedOperationException.class, () -> plan.problems().add(records.get(0)));
        assertThrows(UnsupportedOperationException.class, () -> plan.problems().remove(0));
        assertThrows(UnsupportedOperationException.class, () -> plan.visits().clear());
    }

    @Test void aSharedLoadedFormDoesNotLeakVersionFaultsIntoSubsequentCleanRuns() throws Exception {
        var f = simple(NEW); var c = settings(30, 2, update(UPDATE.toString(), null));
        var clean = run(f, c); var faulty = run(f, c.withProblems(rates(0, FAULT)));
        assertFalse(outdated(faulty).isEmpty());
        InjectedAmendment6Support.sameResults(clean, run(f, c));
        normalVersions(clean, UPDATE, OLD, NEW); assertEquals(NEW, f.version());
    }

    @Test void versionsAndFaultAssignmentIgnoreMachineLocaleAndTimeZone() throws Exception {
        Locale locale = Locale.getDefault(), display = Locale.getDefault(Locale.Category.DISPLAY), format = Locale.getDefault(Locale.Category.FORMAT);
        TimeZone zone = TimeZone.getDefault();
        try {
            var c = settings(40, 2, update(UPDATE.toString(), null)).withProblems(rates(0, FAULT));
            Locale.setDefault(Locale.US); TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            var first = run(simple(NEW), c); assertFalse(outdated(first).isEmpty());
            Locale.setDefault(Locale.forLanguageTag("ar-EG")); TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            var second = run(simple(NEW), c);
            InjectedAmendment6Support.sameResults(first, second);
            assertEquals(first.plan().warnings(), second.plan().warnings());
            assertEquals(AnswerKey.csv(first.plan().problems()), AnswerKey.csv(second.plan().problems()));
        } finally {
            Locale.setDefault(locale); Locale.setDefault(Locale.Category.DISPLAY, display); Locale.setDefault(Locale.Category.FORMAT, format);
            TimeZone.setDefault(zone);
        }
    }
}
