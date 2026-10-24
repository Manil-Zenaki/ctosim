package ctosim;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment7Support.*;
import static ctosim.SimulationConfigProblemsCopiesTest.*;
import static ctosim.SimulationConfigTestSupport.parse;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment7CopiesTest {
    static Stream<Arguments> transitions() {
        ProblemRates[] settings = {null, ProblemRates.defaults(), rates(.625, List.of("dontKnow", "tooFast")),
                rates(0, List.of()), rates(1, List.of("skipModules")),
                new ProblemRates(.11, .22, .33, .44, .55, .66, .77)};
        return Stream.of(settings).flatMap(before -> Stream.of(settings).map(after -> Arguments.of(before, after)));
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void withProblemsPreservesNewComponentsAcrossAddReplaceClearAndUnchangedAssignments(
            ProblemRates before, ProblemRates after) throws Exception {
        SimulationConfig baseline = baseline();
        SimulationConfig original = baseline.withProblems(before);
        SimulationConfig copy = original.withProblems(after);
        assertNotSame(original, copy);
        assertEquals(Optional.ofNullable(after), copy.problems());
        assertEquals(Optional.ofNullable(before), original.problems());
        assertOnlyProblemsMayDiffer(baseline, copy);
        assertOnlyProblemsMayDiffer(baseline, original);
        assertTrue(baseline.problems().isEmpty());
        if (after != null) {
            assertEquals(after.outliers(), copy.problems().orElseThrow().outliers());
            assertEquals(after.faults(), copy.problems().orElseThrow().faults());
            assertThrows(UnsupportedOperationException.class, () -> copy.problems().orElseThrow().faults().add("tooFast"));
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, 0, 83, Long.MAX_VALUE})
    void seedCopyPreservesNewComponentsAndEveryOtherSetting(long seed) throws Exception {
        SimulationConfig baseline = baseline();
        ProblemRates settings = rates(.625, List.of("dontKnow", "tooFast"));
        SimulationConfig original = baseline.withProblems(settings);
        SimulationConfig copy = original.withSeed(seed);
        assertNotSame(original, copy);
        assertEquals(seed, copy.seed());
        assertEquals(original.submissions(), copy.submissions());
        assertOtherSettings(baseline, copy);
        assertEquals(settings, copy.problems().orElseThrow());
        assertEquals(settings, original.problems().orElseThrow());
        assertOnlyProblemsMayDiffer(baseline, original);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 19, 1_000_000})
    void submissionCopyPreservesNewComponentsAndEveryOtherSetting(int count) throws Exception {
        SimulationConfig baseline = baseline();
        ProblemRates settings = rates(.625, List.of("dontKnow", "tooFast"));
        SimulationConfig original = baseline.withProblems(settings);
        SimulationConfig copy = original.withSubmissions(count);
        assertNotSame(original, copy);
        assertEquals(count, copy.submissions());
        assertEquals(original.seed(), copy.seed());
        assertOtherSettings(baseline, copy);
        assertEquals(settings, copy.problems().orElseThrow());
        assertEquals(settings, original.problems().orElseThrow());
        assertOnlyProblemsMayDiffer(baseline, original);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment7Support#faultLists")
    void unchangedSeedAndCountCopiesAndCopyChainsRetainParsedFaultOrder(List<String> faults) throws Exception {
        SimulationConfig original = parse("{\"problems\":{\"outliers\":0.875,\"faults\":" + array(faults) + "}}");
        SimulationConfig seedCopy = original.withSeed(original.seed());
        SimulationConfig countCopy = original.withSubmissions(original.submissions());
        assertNotSame(original, seedCopy);
        assertNotSame(original, countCopy);
        for (SimulationConfig copy : List.of(seedCopy, countCopy,
                original.withSeed(Long.MAX_VALUE).withSubmissions(1).withSeed(Long.MIN_VALUE).withSubmissions(1_000_000))) {
            assertEquals(.875, copy.problems().orElseThrow().outliers());
            assertEquals(faults, copy.problems().orElseThrow().faults());
            assertThrows(UnsupportedOperationException.class, () -> copy.problems().orElseThrow().faults().add("tooFast"));
        }
    }

    @Test
    void mutatingCallerListCannotChangeConfigurationOrCopies() throws Exception {
        List<String> callerList = new ArrayList<>(List.of("skipModules", "dontKnow"));
        ProblemRates settings = rates(.875, callerList);
        SimulationConfig original = baseline().withProblems(settings);
        SimulationConfig seedCopy = original.withSeed(9);
        SimulationConfig countCopy = original.withSubmissions(3);
        callerList.clear();
        callerList.add("tooFast");
        for (SimulationConfig config : List.of(original, seedCopy, countCopy)) {
            assertEquals(.875, config.problems().orElseThrow().outliers());
            assertEquals(List.of("skipModules", "dontKnow"), config.problems().orElseThrow().faults());
        }
    }

    @Test
    void parsingNewComponentsAlongsideExistingSettingsPreservesEveryOtherSetting() throws Exception {
        String json = SimulationConfigRealisticSupport.fixture();
        SimulationConfig result = parse("{\"id\":\"household\",\"interviewsPerDay\":37,"
                + "\"problems\":{\"outliers\":0.625,\"faults\":[\"dontKnow\",\"tooFast\"]},"
                + json.substring(json.indexOf('{') + 1));
        assertOnlyProblemsMayDiffer(baseline(), result);
        assertEquals(.625, result.problems().orElseThrow().outliers());
        assertEquals(List.of("dontKnow", "tooFast"), result.problems().orElseThrow().faults());
        SimulationConfigProblemsSupport.assertRates(SimulationConfigProblemsSupport.DEFAULTS, result.problems().orElseThrow());
    }

    @Test
    void rejectedSubmissionCopyLeavesBothNewComponentsUnchanged() throws Exception {
        SimulationConfig original = baseline().withProblems(rates(.625, List.of("dontKnow", "tooFast")));
        assertThrows(IllegalArgumentException.class, () -> original.withSubmissions(0));
        assertEquals(.625, original.problems().orElseThrow().outliers());
        assertEquals(List.of("dontKnow", "tooFast"), original.problems().orElseThrow().faults());
        assertOnlyProblemsMayDiffer(baseline(), original);
    }
}
