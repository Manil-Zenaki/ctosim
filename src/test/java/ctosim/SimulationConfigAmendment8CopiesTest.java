package ctosim;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment8Support.*;
import static ctosim.SimulationConfigProblemsCopiesTest.*;
import static ctosim.SimulationConfigTestSupport.parse;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment8CopiesTest {
    private static SimulationConfig configured() throws Exception {
        String existing = SimulationConfigRealisticSupport.fixture();
        return parse("{\"id\":\"household\",\"interviewsPerDay\":37,"
                + "\"consent\":{\"fields\":[\"z\",\"a\"],\"rate\":0.625,\"yes\":\"Y\",\"no\":\"N\"},"
                + "\"problems\":{\"faults\":[\"fakeRefusal\",\"dontKnow\"],\"outliers\":0.875},"
                + existing.substring(existing.indexOf('{') + 1));
    }

    @Test
    void addingConsentAndFakeRefusalPreservesAllExistingConfigurationSettings() throws Exception {
        SimulationConfig config = configured();
        assertOnlyProblemsMayDiffer(baseline(), config);
        assertEquals(new Consent(List.of("z", "a"), .625, "Y", "N"), config.consent().orElseThrow());
        assertEquals(List.of("fakeRefusal", "dontKnow"), config.problems().orElseThrow().faults());
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, 0, 83, Long.MAX_VALUE})
    void seedCopyPreservesConsentAndFakeRefusalAndChangesOnlySeed(long seed) throws Exception {
        SimulationConfig original = configured();
        SimulationConfig copy = original.withSeed(seed);
        assertNotSame(original, copy);
        assertEquals(seed, copy.seed());
        assertEquals(original.submissions(), copy.submissions());
        assertOtherSettings(original, copy);
        assertEquals(original.consent(), copy.consent());
        assertEquals(original.problems(), copy.problems());
        assertUnchanged(original);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 19, 1_000_000})
    void submissionCopyPreservesConsentAndFakeRefusalAndChangesOnlyCount(int count) throws Exception {
        SimulationConfig original = configured();
        SimulationConfig copy = original.withSubmissions(count);
        assertNotSame(original, copy);
        assertEquals(count, copy.submissions());
        assertEquals(original.seed(), copy.seed());
        assertOtherSettings(original, copy);
        assertEquals(original.consent(), copy.consent());
        assertEquals(original.problems(), copy.problems());
        assertUnchanged(original);
    }

    static Stream<String> consentConfigurations() {
        return Stream.of("{}", consentJson("\"fields\":[\"c\"]"),
                consentJson("\"fields\":[\"z\",\"a\"],\"rate\":0,\"yes\":\"Y\",\"no\":\"N\""),
                consentJson("\"fields\":[\"c\"],\"rate\":1"));
    }

    @ParameterizedTest
    @MethodSource("consentConfigurations")
    void everyCopyKeepsAbsentDefaultCustomAndEndpointConsentIncludingUnchangedCopies(String json) throws Exception {
        SimulationConfig original = parse(json);
        List<SimulationConfig> copies = List.of(original.withSeed(original.seed()),
                original.withSubmissions(original.submissions()), original.withProblems(null),
                original.withSeed(Long.MAX_VALUE).withSubmissions(1).withProblems(ProblemRates.defaults())
                        .withSeed(Long.MIN_VALUE).withSubmissions(1_000_000).withProblems(null));
        for (SimulationConfig copy : copies) {
            assertNotSame(original, copy);
            assertEquals(original.consent(), copy.consent());
            assertTrue(copy.problems().isEmpty());
            copy.consent().ifPresent(consent ->
                    assertThrows(UnsupportedOperationException.class, () -> consent.fields().add("new")));
        }
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment8CopiesTest#consentConfigurations")
    void problemsAddReplaceClearAndUnchangedCopiesPreserveConsent(String json) throws Exception {
        SimulationConfig original = parse(json);
        ProblemRates fake = rates(List.of("fakeRefusal", "tooFast"));
        ProblemRates legacy = new ProblemRates(0, 0, 0, 0, 0, 0, 0);
        for (ProblemRates before : new ProblemRates[]{null, fake, legacy, ProblemRates.defaults()}) {
            SimulationConfig receiver = original.withProblems(before);
            for (ProblemRates after : new ProblemRates[]{null, fake, legacy, ProblemRates.defaults(), before}) {
                SimulationConfig copy = receiver.withProblems(after);
                assertNotSame(receiver, copy);
                assertEquals(original.consent(), copy.consent());
                assertEquals(original.consent(), receiver.consent());
                assertEquals(Optional.ofNullable(after), copy.problems());
                assertEquals(Optional.ofNullable(before), receiver.problems());
                assertOnlyProblemsMayDiffer(original, copy);
                copy.consent().ifPresent(consent ->
                        assertThrows(UnsupportedOperationException.class, () -> consent.fields().clear()));
            }
        }
    }

    @Test
    void problemsCopiesPreserveConsentAndEveryNondefaultExistingSetting() throws Exception {
        SimulationConfig original = configured();
        for (ProblemRates replacement : new ProblemRates[]{null, ProblemRates.defaults(),
                rates(List.of("fakeRefusal")), original.problems().orElseThrow()}) {
            SimulationConfig copy = original.withProblems(replacement);
            assertNotSame(original, copy);
            assertOnlyProblemsMayDiffer(original, copy);
            assertEquals(original.consent(), copy.consent());
            assertEquals(Optional.ofNullable(replacement), copy.problems());
        }
        assertUnchanged(original);
    }

    @Test
    void callerFaultListCannotChangeConsentOrFaultsInConfigurationsOrCopies() throws Exception {
        List<String> caller = new ArrayList<>(List.of("fakeRefusal", "tooFast"));
        SimulationConfig original = configured().withProblems(rates(caller));
        List<SimulationConfig> copies = List.of(original, original.withSeed(0), original.withSubmissions(1));
        caller.clear();
        for (SimulationConfig config : copies) {
            assertEquals(List.of("fakeRefusal", "tooFast"), config.problems().orElseThrow().faults());
            assertEquals(new Consent(List.of("z", "a"), .625, "Y", "N"), config.consent().orElseThrow());
            assertThrows(UnsupportedOperationException.class, () -> config.consent().orElseThrow().fields().set(0, "x"));
            assertThrows(UnsupportedOperationException.class, () -> config.problems().orElseThrow().faults().clear());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1_000_001})
    void rejectedSubmissionCopiesLeaveConsentAndFakeRefusalUnchanged(int count) throws Exception {
        SimulationConfig original = configured();
        assertThrows(IllegalArgumentException.class, () -> original.withSubmissions(count));
        assertUnchanged(original);
    }

    private static void assertUnchanged(SimulationConfig original) throws Exception {
        assertOnlyProblemsMayDiffer(baseline(), original);
        assertEquals(new Consent(List.of("z", "a"), .625, "Y", "N"), original.consent().orElseThrow());
        assertEquals(.875, original.problems().orElseThrow().outliers());
        assertEquals(List.of("fakeRefusal", "dontKnow"), original.problems().orElseThrow().faults());
    }
}
