package ctosim;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FieldworkTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Form walks share JVM state")
class InjectedAmendment4ReinterviewTest extends InjectedAmendment4Support {
    void twoDistantVillages() throws Exception {
        villageTables();
        Files.writeString(media.resolve("a4-villages.csv"), "code,district,lat_col,lon_col\nA,D1,0,0\nC,D2,0,4\n");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aLaterInterviewPrefersAnEligibleOriginalFromItsOwnVillageOfTheDay(boolean located) throws Exception {
        twoDistantVillages();
        var f = villageForm(false);
        var c = cfg(8, 2, 4, located ? LOCATED : "").withProblems(only("reinterview", .125));
        int checked = 0, mixedPools = 0;
        for (int seed = 100; seed < 180; seed++) {
            var r = run(f, c.withSeed(seed));
            assertEquals(1, records(r, "reinterview").size());
            var p = records(r, "reinterview").get(0);
            int previous = p.submission() - 2;
            if (day(r, previous) != day(r, p.submission())) continue;
            String dayVillage = value(r.submissions().get(previous - 1), "village");
            boolean otherVillage = false;
            for (int n = 1; n < p.submission(); n++)
                otherVillage |= !value(r.submissions().get(n - 1), "village").equals(dayVillage);
            if (otherVillage) mixedPools++;
            assertEquals(dayVillage, value(r.related(p), "village"),
                    "the previous ordinary interview is an eligible same-village original");
            assertEquals(dayVillage, value(r.target(p), "village"));
            checked++;
        }
        assertTrue(checked >= 40);
        assertTrue(mixedPools >= 10, "preference must be exercised against an eligible other-village alternative");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aFirstInterviewReinterviewEstablishesTheOriginalsWholePlaceForTheDay(boolean located) throws Exception {
        twoDistantVillages();
        var r = find(villageForm(true), cfg(4, 1, 2, located ? LOCATED : "")
                        .withProblems(only("reinterview", .25)),
                candidate -> targets(candidate, "reinterview").equals(Set.of(3)));
        var p = records(r, "reinterview").get(0);
        firstOfNextDay(r, 2, 3);
        assertEquals(day(r, 3), day(r, 4));
        for (String field : List.of("district", "village")) {
            assertEquals(value(r.related(p), field), value(r.target(p), field));
            assertEquals(value(r.related(p), field), value(r.submissions().get(3), field),
                    "subsequent ordinary interview must inherit the reinterview's place");
        }
        assertEquals(value(r.related(p), "answer"), value(r.target(p), "answer"));
        if (located) { nearVillage(r, 3); nearVillage(r, 4); }
    }

    @Test void firstInterviewDoesNotRetainTheOrdinaryRunsDifferentNewDayPlace() throws Exception {
        twoDistantVillages();
        var form = villageForm(false);
        var base = cfg(4, 1, 2, LOCATED);
        var r = find(form, base.withProblems(only("reinterview", .25)), candidate -> {
            if (!targets(candidate, "reinterview").equals(Set.of(3))) return false;
            var clean = run(form, candidate.config().withProblems(null));
            return !value(clean.submissions().get(2), "village")
                    .equals(value(candidate.submissions().get(0), "village"));
        });
        var p = records(r, "reinterview").get(0);
        assertEquals(value(r.related(p), "village"), value(r.submissions().get(3), "village"));
        assertEquals(value(r.related(p), "district"), value(r.submissions().get(3), "district"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void withoutASameVillageOriginalTheReinterviewDoesNotChangeTheDaysPlaceOrChargeAMove(boolean located) throws Exception {
        twoDistantVillages();
        // Submission 4 is the first of day two and is reserved for short duration.
        // With target 5 reserved for reinterview, its only eligible originals are 1..3.
        var rates = new ProblemRates(0, 1.0 / 6, 1.0 / 6, 0, 0, 0, 0);
        var r = find(villageForm(false), cfg(6, 1, 3, located ? LOCATED : "").withProblems(rates), candidate ->
                targets(candidate, "reinterview").equals(Set.of(5))
                        && targets(candidate, "short").equals(Set.of(4))
                        && !value(candidate.submissions().get(0), "village")
                            .equals(value(candidate.submissions().get(3), "village")));
        var p = records(r, "reinterview").get(0);
        assertTrue(r.related(p).number() <= 3, "a table target cannot become an original");
        for (String field : List.of("district", "village")) {
            assertEquals(value(r.related(p), field), value(r.target(p), field));
            assertEquals(value(r.submissions().get(3), field), value(r.submissions().get(5), field),
                    "a different-place reinterview is temporary and does not replace the day's place");
        }
        assertEquals(day(r, 4), day(r, 5));
        assertEquals(day(r, 4), day(r, 6));
        for (int n : List.of(5, 6)) {
            long minutes = gap(r.submissions().get(n - 2), r.submissions().get(n - 1));
            assertTrue(minutes >= 10 && minutes <= 40, "neither the reinterview nor return to the day village is a move");
        }
        assertEquals(1, records(r, "reinterview").size());
        assertTrue(records(r, "travel").isEmpty(), "the duplicate is not also reported as travel");
        if (located) { nearVillage(r, 5); nearVillage(r, 6); }
    }

    @Test void villagePreferenceStillExcludesAllTargetsAndPreviouslyUsedOriginals() throws Exception {
        twoDistantVillages();
        var r = run(villageForm(false), cfg(12, 2, 4, LOCATED)
                .withProblems(only("reinterview", .25)));
        var ps = records(r, "reinterview");
        assertFalse(ps.isEmpty());
        Set<Integer> targetNumbers = targets(r, "reinterview");
        Set<String> originals = new HashSet<>();
        for (var p : ps) {
            assertTrue(originals.add(p.relatedKey()), "each original is used at most once");
            assertFalse(targetNumbers.contains(r.related(p).number()));
            assertTrue(r.related(p).number() < p.submission());
            assertEquals(value(r.related(p), "answer"), p.value());
            assertEquals(value(r.related(p), "answer"), value(r.target(p), "answer"));
            assertEquals(value(r.related(p), "village"), value(r.target(p), "village"));
            assertEquals(value(r.related(p), "district"), value(r.target(p), "district"));
            assertEquals("", p.cleanValue());
        }
        assertMetadata(r);
    }
}
