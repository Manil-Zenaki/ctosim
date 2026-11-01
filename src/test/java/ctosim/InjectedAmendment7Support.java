package ctosim;

import java.util.*;
import java.util.stream.Collectors;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 14 Amendment 7: fixtures and assertions against public results only. */
final class InjectedAmendment7Support {
    private InjectedAmendment7Support() { }

    static String teamCodes(String fault, String... codes) {
        return SimulationConfigTestSupport.quote(fault.equals("dontKnow") ? "dontKnowCodes" : "refuseCodes")
                + ":" + Arrays.stream(codes).map(SimulationConfigTestSupport::quote)
                .collect(Collectors.joining(",", "[", "]"));
    }

    static String fixedText(String field, String answer) {
        return "\"fields\":{" + SimulationConfigTestSupport.quote(field) + ":{\"value\":"
                + SimulationConfigTestSupport.quote(answer) + "}}";
    }

    static LoadedForm textQuestion(String attributes) throws Exception {
        return fixture("<answer/>", bind("answer", "string", attributes), input("answer"));
    }

    static Run textRun(String fault, LoadedForm form, int n, String extra) throws Exception {
        return run(form, settings(n, 2, extra).withProblems(rates(0, fault)));
    }

    static void noChanges(Run run, String fault) {
        assertTrue(records(run, fault).isEmpty(), "no replacement implies no record");
        for (var s : run.submissions()) ordinary(run, s.number());
    }

    static void textRecords(Run run, String fault, String field, String clean, List<String> accepted)
            throws Exception {
        var ps = records(run, fault);
        assertFalse(ps.isEmpty(), "must observe replacements, not just a successful walk");
        var changed = targets(run, fault);
        assertEquals(changed.size(), ps.size(), "one record per changed answer");
        String enumerator = soleEnumerator(run, fault);
        for (var p : ps) {
            assertEquals(field, p.field());
            assertEquals(clean, p.cleanValue());
            assertTrue(accepted.contains(p.value()), p.toString());
        }
        for (var s : run.submissions()) {
            if (changed.contains(s.number())) {
                assertEquals(enumerator, value(s, "username"));
                assertTrue(accepted.contains(value(s, field)));
            } else assertEquals(clean, value(s, field), "no unrecorded or clean-enumerator replacement");
        }
        metadata(run);
    }

    static void metadata(Run run) throws Exception {
        int previous = 0;
        Set<String> changed = new HashSet<>();
        for (var p : run.plan().problems()) {
            assertTrue(p.submission() >= previous);
            previous = p.submission();
            if (!List.of("dontKnow", "refuse").contains(p.problem())) continue;
            assertEquals("enumerator", p.family());
            assertEquals("ipacheckenumdb", p.check());
            assertFalse(p.deterministic());
            assertEquals("", p.relatedKey());
            assertEquals(key(run.target(p)), p.key());
            assertEquals(value(run.target(p), "username"), p.enumerator());
            assertEquals(value(run.target(p), p.field()), p.value());
            assertTrue(changed.add(p.submission() + ":" + p.field()), "no field changed by both faults");
        }
    }
}
