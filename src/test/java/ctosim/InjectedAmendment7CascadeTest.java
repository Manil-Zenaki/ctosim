package ctosim;

import java.util.List;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.rates;
import static ctosim.InjectedAmendment7Support.teamCodes;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment7CascadeTest extends FieldworkAmendment3Support {
    @Override LoadedForm attached(String instance, String binds, String body) throws Exception {
        return super.attached(instance + "<username/><meta><instanceID/></meta>", binds
                + bind("username", "string", "jr:preload=\"property\" jr:preloadParams=\"username\"")
                + bind("meta/instanceID", "string", "jr:preload=\"uid\""), body);
    }

    private static String codes(String fault) {
        return "\"enumerators\":2," + teamCodes(fault, "R1", "R2", "R0", "D1", "D2", "D3", "D0",
                "A", "B", "C", "E", "X", "Z", "0007", "É02", "B1", "B2", "B3", "SHARED", "C1", "E1");
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void everyUpperCascadeQuestionRemainsProtectedWhateverItsStringCodes(String fault) throws Exception {
        var f = deep(false);
        for (int seed = 1; seed <= 16; seed++) {
            var c = idConfig(6, codes(fault)).withSeed(seed);
            var clean = run(f, c.withProblems(null));
            var actual = run(f, c.withProblems(rates(0, fault)));
            assertTrue(records(actual, fault).isEmpty());
            for (int i = 0; i < 6; i++) for (String field : List.of("region", "district", "village", "answer"))
                assertEquals(value(clean.submissions().get(i), field), value(actual.submissions().get(i), field));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void cascadeAnswersCopiedByReinterviewsCannotBeChanged(String fault) throws Exception {
        int copies = 0;
        var f = deep(false);
        for (int seed = 1; seed <= 16; seed++) {
            var c = idConfig(6, codes(fault)).withSeed(seed)
                    .withProblems(new ProblemRates(0, 1.0 / 3, 0, 0, 0, 0, 0, 0, List.of(fault)));
            var r = run(f, c);
            assertTrue(records(r, fault).isEmpty());
            for (var p : records(r, "reinterview")) {
                copies++;
                for (String field : List.of("region", "district", "village", "answer"))
                    assertEquals(value(r.related(p), field), value(r.target(p), field));
            }
        }
        assertTrue(copies > 0);
    }
}
