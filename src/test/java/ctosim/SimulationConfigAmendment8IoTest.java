package ctosim;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment8Support.*;
import static ctosim.SimulationConfigTestSupport.SOURCE;
import static ctosim.SimulationConfigTestSupport.assertDiagnostic;
import static ctosim.SimulationConfigTestSupport.parse;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment8IoTest {
    @TempDir(factory = SimulationConfigProblemsIoTest.WorkspaceTempDirectory.class)
    Path temp;

    private Path write(String json) throws Exception {
        return Files.writeString(temp.resolve("consent étude.json"), json, StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void parseAndLoadAcceptUtf8ConsentAndFakeRefusalWithOptionalBom(boolean bom) throws Exception {
        String json;
        try (InputStream input = getClass().getResourceAsStream("/ctosim/simulation-config/amendment-8-consent.json")) {
            assertNotNull(input);
            json = (bom ? "\uFEFF" : "") + new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (SimulationConfig config : List.of(parse(json), SimulationConfig.load(write(json)))) {
            Consent result = config.consent().orElseThrow();
            assertEquals(List.of("z_consent", "同意", "accord_é"), result.fields());
            assertEquals(.625, result.rate());
            assertEquals("はい", result.yes());
            assertEquals("non", result.no());
            assertEquals(List.of("fakeRefusal", "dontKnow", "tooFast"), config.problems().orElseThrow().faults());
            assertThrows(UnsupportedOperationException.class, () -> result.fields().set(0, "changed"));
        }
    }

    @Test
    void loadDistinguishesAbsentConsentFromRequiredFieldsWithDefaults() throws Exception {
        SimulationConfig absent = SimulationConfig.load(write("{}"));
        assertTrue(absent.consent().isEmpty());
        assertTrue(absent.problems().isEmpty());
        SimulationConfig configured = SimulationConfig.load(write(consentJson("\"fields\":[\"c\"]")));
        assertEquals(new Consent(List.of("c"), .9, "1", "0"), configured.consent().orElseThrow());
        assertTrue(configured.problems().isEmpty());
        assertEquals(DEFAULT_FAULTS, SimulationConfig.load(write("{\"problems\":{}}"))
                .problems().orElseThrow().faults());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment8Support#acceptedRates")
    void loadKeepsConsentRateBoundariesAndNumericForms(String token) throws Exception {
        Consent result = SimulationConfig.load(write(consentJson("\"fields\":[\"c\"],\"rate\":" + token)))
                .consent().orElseThrow();
        assertEquals(Double.parseDouble(token), result.rate(), 0.0);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment8Support#acceptedFields")
    void loadPreservesExactNamesAndFieldOrder(List<String> fields) throws Exception {
        assertEquals(fields, SimulationConfig.load(write(consentJson("\"fields\":" + array(fields))))
                .consent().orElseThrow().fields());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment8Support#ordersIncludingFakeRefusal")
    void loadPreservesEveryOrderedSubsetContainingFakeRefusal(List<String> faults) throws Exception {
        assertEquals(faults, SimulationConfig.load(write("{\"problems\":{\"faults\":" + array(faults) + "}}"))
                .problems().orElseThrow().faults());
    }

    @ParameterizedTest
    @MethodSource({"ctosim.SimulationConfigAmendment8Support#invalidConsent",
            "ctosim.SimulationConfigAmendment8Support#unknownKeys",
            "ctosim.SimulationConfigAmendment8Support#invalidFakeFaults"})
    void loadRejectsInvalidNewSettingsWithFilePathLocationAndReason(String json, String location) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), location);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment8Support#duplicateKeys")
    void loadRejectsDuplicateDecodedConsentKeys(String json) throws Exception {
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), null);
    }

    static Stream<String> syntaxErrors() {
        return Stream.of("\n", "\r\n").flatMap(newline -> Stream.of(
                "\"fields\":[\"c\",@]", "\"fields\":[\"c\"],\"rate\":@",
                "\"fields\":[\"c\"],\"rate\":NaN", "\"fields\":[\"c\"],\"rate\":Infinity",
                "\"fields\":[\"c\"],\"rate\":-Infinity", "\"fields\":[\"c\"],\"yes\":@")
                .map(member -> "{" + newline + "\"consent\":{" + newline + member + newline + "}}"));
    }

    private static void assertSyntax(ConfigException error, String source) {
        assertDiagnostic(error, source, null);
        assertNotNull(error.getCause());
        assertTrue(error.getMessage().replace(source, "").matches("(?s).*\\b3\\b.*"),
                () -> "Expected failure on line 3: " + error.getMessage());
    }

    @ParameterizedTest
    @MethodSource("syntaxErrors")
    void parsePreservesCauseAndLineForSyntaxErrorsInsideConsent(String json) {
        assertSyntax(assertThrows(ConfigException.class, () -> parse(json)), SOURCE);
    }

    @ParameterizedTest
    @MethodSource("syntaxErrors")
    void loadPreservesCauseAndLineForSyntaxErrorsInsideConsent(String json) throws Exception {
        Path path = write(json);
        assertSyntax(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString());
    }

    static Stream<String> invalidRootOrTrailingContent() {
        String valid = consentJson("\"fields\":[\"c\"]");
        return Stream.of("[" + valid + "]", valid + " {}", valid + " null", valid + " trailing");
    }

    @ParameterizedTest
    @MethodSource("invalidRootOrTrailingContent")
    void consentDoesNotRelaxTheSingleRootObjectRule(String json) throws Exception {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, null);
        Path path = write(json);
        assertDiagnostic(assertThrows(ConfigException.class, () -> SimulationConfig.load(path)), path.toString(), null);
    }

    @Test
    void loadedConsentAndCopiesRemainSnapshotsAfterFileReplacement() throws Exception {
        Path path = write("{\"consent\":{\"fields\":[\"z\",\"a\"],\"rate\":0,\"yes\":\"Y\",\"no\":\"N\"},"
                + "\"problems\":{\"faults\":[\"fakeRefusal\"]}}");
        SimulationConfig original = SimulationConfig.load(path);
        List<SimulationConfig> copies = List.of(original, original.withSeed(99), original.withSubmissions(2),
                original.withProblems(original.problems().orElseThrow()));
        Files.writeString(path, consentJson("\"fields\":[\"other\"],\"rate\":1"), StandardCharsets.UTF_8);
        assertEquals(List.of("other"), SimulationConfig.load(path).consent().orElseThrow().fields());
        Files.delete(path);
        for (SimulationConfig config : copies) {
            assertEquals(new Consent(List.of("z", "a"), 0, "Y", "N"), config.consent().orElseThrow());
            assertEquals(List.of("fakeRefusal"), config.problems().orElseThrow().faults());
        }
    }
}
