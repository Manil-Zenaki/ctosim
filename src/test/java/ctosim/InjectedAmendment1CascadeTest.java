package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static ctosim.FormWalkerTestSupport.value;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the amendment's protection of survey IDs and all cascade ancestors. */
@Isolated("Walks change the JVM time zone")
class InjectedAmendment1CascadeTest extends FieldworkAmendment3Support {
    @Override LoadedForm attached(String instance, String binds, String body) throws Exception {
        return super.attached(instance + "<username/><meta><instanceID/></meta>", binds
                + "<bind nodeset=\"/data/username\" type=\"string\" jr:preload=\"property\" jr:preloadParams=\"username\"/>"
                + "<bind nodeset=\"/data/meta/instanceID\" type=\"string\" jr:preload=\"uid\"/>", body);
    }
    private static String specials() {
        return "\"enumerators\":2,\"specialValues\":[\"R1\",\"R2\",\"R0\",\"D1\",\"D2\",\"D3\",\"D0\","
                + "\"A\",\"B\",\"C\",\"E\",\"X\",\"Z\",\"0007\",\"É02\",\"B1\",\"B2\",\"B3\",\"SHARED\",\"C1\",\"E1\"]";
    }
    @Test void dontKnowUsesOnlyCurrentlyOfferedSearchChoicesNotFilteredRowsOrPlaceholders() throws Exception {
        var f = attached("<district>D1</district><answer/>",
                FormWalkerTestSupport.bind("district", "string", "readonly=\"true()\"")
                + FormWalkerTestSupport.bind("answer", "string", ""),
                control("search('a3-villages', 'matches', 'district', /data/district)", "code"));
        var r = run(f, InjectedAmendment1Support.settings(600, 2,
                "\"specialValues\":[\"A\",\"C\",\"code\"],\"fields\":{\"answer\":{\"value\":\"B\"}}")
                .withProblems(rates(0, "dontKnow")));
        rate(records(r, "dontKnow").size(), 300, .25);
        for (var p : records(r, "dontKnow")) {
            assertEquals("answer", p.field()); assertEquals("B", p.cleanValue()); assertEquals("A", p.value());
        }
    }
    @Test void dontKnowNeverReplacesTheIdOrAnyOfItsCascadeAncestors() throws Exception {
        var f = deep(false);
        for (int seed = 1; seed <= 12; seed++) {
            var c = idConfig(6, specials()).withSeed(seed);
            var actual = run(f, c.withProblems(rates(0, "dontKnow"))); var clean = run(f, c.withProblems(null));
            assertTrue(records(actual, "dontKnow").isEmpty());
            for (int i = 0; i < 6; i++) for (String field : List.of("region", "district", "village", "answer"))
                assertEquals(value(clean.submissions().get(i), field), value(actual.submissions().get(i), field));
        }
    }
    @Test void faultsDoNotOverwriteTheCascadeAnswersCopiedByReinterview() throws Exception {
        var c = idConfig(6, specials()).withProblems(new ProblemRates(0, 1.0 / 3, 0, 0, 0, 0, 0, 0,
                List.of("tooFast", "skipModules", "dontKnow")));
        var r = run(deep(false), c); assertEquals(2, records(r, "reinterview").size());
        for (var p : records(r, "reinterview")) {
            for (String field : List.of("region", "district", "village", "answer"))
                assertEquals(value(r.related(p), field), value(r.target(p), field));
        }
        assertTrue(records(r, "dontKnow").isEmpty()); assertTrue(records(r, "skipModules").isEmpty());
    }
}
