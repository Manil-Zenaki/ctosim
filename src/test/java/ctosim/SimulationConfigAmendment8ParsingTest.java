package ctosim;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SimulationConfigAmendment8Support.*;
import static ctosim.SimulationConfigTestSupport.SOURCE;
import static ctosim.SimulationConfigTestSupport.assertDiagnostic;
import static ctosim.SimulationConfigTestSupport.parse;
import static org.junit.jupiter.api.Assertions.*;

class SimulationConfigAmendment8ParsingTest {
    @Test
    void defaultsDoNotEnableConsentOrProblems() {
        assertTrue(SimulationConfig.defaults().consent().isEmpty());
        assertTrue(SimulationConfig.defaults().problems().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"problems\":{}}", "{\"fields\":{\"consent\":{\"value\":\"1\"}}}",
            "{\"problems\":{\"faults\":[\"fakeRefusal\"]}}"})
    void omissionDoesNotInferConsentFromFaultsOrAnOverride(String json) throws Exception {
        assertTrue(parse(json).consent().isEmpty());
    }

    @Test
    void requiredFieldsAloneSupplyOnlyTheDocumentedConsentDefaults() throws Exception {
        SimulationConfig config = parse(consentJson("\"fields\":[\"c\"]"));
        Consent consent = config.consent().orElseThrow();
        assertEquals(List.of("c"), consent.fields());
        assertEquals(.9, consent.rate());
        assertEquals("1", consent.yes());
        assertEquals("0", consent.no());
        assertTrue(config.problems().isEmpty());
        SimulationConfigProblemsCopiesTest.assertOnlyProblemsMayDiffer(SimulationConfig.defaults(), config);
    }

    static Stream<Arguments> partialConsent() {
        return Stream.of(
                Arguments.of("\"rate\":0", 0.0, "1", "0"),
                Arguments.of("\"yes\":\"Y\"", .9, "Y", "0"),
                Arguments.of("\"no\":\"N\"", .9, "1", "N"),
                Arguments.of("\"no\":\"1\",\"yes\":\"0\"", .9, "0", "1"),
                Arguments.of("\"no\":\"N\",\"rate\":0.625,\"yes\":\"Y\"", .625, "Y", "N"));
    }

    @ParameterizedTest
    @MethodSource("partialConsent")
    void optionalConsentMembersDefaultIndependentlyOfKeyOrder(String members, double rate, String yes, String no)
            throws Exception {
        Consent consent = parse(consentJson(members + ",\"fields\":[\"c\"]")).consent().orElseThrow();
        assertEquals(rate, consent.rate());
        assertEquals(yes, consent.yes());
        assertEquals(no, consent.no());
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment8Support#acceptedRates")
    void consentRateAcceptsInclusiveEndpointsAndOrdinaryNumericForms(String token) throws Exception {
        Consent consent = parse(consentJson("\"fields\":[\"c\"],\"rate\":" + token)).consent().orElseThrow();
        assertEquals(Double.parseDouble(token), consent.rate(), 0.0);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment8Support#acceptedFields")
    void fieldNamesArePreservedInSuppliedOrderWithoutConsultingAForm(List<String> fields) throws Exception {
        Consent consent = parse(consentJson("\"fields\":" + array(fields))).consent().orElseThrow();
        assertEquals(fields, consent.fields());
    }

    static Stream<Arguments> exactCodes() {
        return Stream.of(Arguments.of("同意", "拒否"), Arguments.of("1", "01"), Arguments.of("Y", "y"),
                Arguments.of(" ", "\t"), Arguments.of(" Y ", " N "), Arguments.of("line\nbreak", "quote\"slash\\"));
    }

    @ParameterizedTest
    @MethodSource("exactCodes")
    void choiceCodesKeepExactDecodedNonemptyStrings(String yes, String no) throws Exception {
        Consent consent = parse(consentJson("\"fields\":[\"c\"],\"yes\":" + quote(yes) + ",\"no\":" + quote(no)))
                .consent().orElseThrow();
        assertEquals(yes, consent.yes());
        assertEquals(no, consent.no());
    }

    @Test
    void escapedKeysFieldNamesAndFaultNamesAreDecodedBeforeRecognition() throws Exception {
        SimulationConfig config = parse("{\"cons\\u0065nt\":{\"f\\u0069elds\":[\"c\\u006fnfirm\"],"
                + "\"r\\u0061te\":0.5,\"y\\u0065s\":\"Y\",\"n\\u006f\":\"N\"},"
                + "\"problems\":{\"faults\":[\"fakeRefus\\u0061l\"]}}");
        assertEquals(new Consent(List.of("confirm"), .5, "Y", "N"), config.consent().orElseThrow());
        assertEquals(List.of("fakeRefusal"), config.problems().orElseThrow().faults());
    }

    @Test
    void consentIsIndependentOfOtherProbabilitiesNamesAndOverrides() throws Exception {
        SimulationConfig config = parse("""
                {"submissions":1,"enumerators":1,"fieldworkDays":1,"interviewsPerDay":1,
                 "id":"consent","missingRate":1,"specialRate":1,"speedViolationRate":1,
                 "specialValues":["Y","N"],"profiles":{"consent":1,"fakeRefusal":1},
                 "consent":{"fields":["consent","fakeRefusal"],"rate":1,"yes":"Y","no":"N"},
                 "fields":{"consent":{"value":"N"},"fakeRefusal":{"weights":{"consent":1,"fakeRefusal":1}}},
                 "problems":{"faults":["fakeRefusal"],"outliers":1}}
                """);
        assertEquals(new Consent(List.of("consent", "fakeRefusal"), 1, "Y", "N"), config.consent().orElseThrow());
        assertEquals("N", config.field("consent").orElseThrow().value().orElseThrow());
        assertEquals(List.of("fakeRefusal"), config.problems().orElseThrow().faults());
    }

    @ParameterizedTest
    @MethodSource({"ctosim.SimulationConfigAmendment8Support#invalidConsent",
            "ctosim.SimulationConfigAmendment8Support#unknownKeys",
            "ctosim.SimulationConfigAmendment8Support#invalidFakeFaults"})
    void invalidNewSettingsReportSourceFullLocationAndReason(String json, String location) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, location);
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment8Support#duplicateKeys")
    void duplicateConsentKeysAreRejectedEvenWhenEscapedOrIdentical(String json) {
        assertDiagnostic(assertThrows(ConfigException.class, () -> parse(json)), SOURCE, null);
    }

    @ParameterizedTest
    @MethodSource("ctosim.ConsentAmendment8Test#mutations")
    void parsedConsentFieldsAreUnmodifiable(java.util.function.Consumer<List<String>> mutation) throws Exception {
        Consent consent = parse(consentJson("\"fields\":[\"z\",\"a\"]")).consent().orElseThrow();
        assertThrows(UnsupportedOperationException.class, () -> mutation.accept(consent.fields()));
        assertEquals(List.of("z", "a"), consent.fields());
    }

    @Test
    void repeatedParsingRetainsTheSameConsentAndFaultSettings() throws Exception {
        String json = "{\"consent\":{\"fields\":[\"z\",\"a\"],\"rate\":0.75},\"problems\":{}}";
        SimulationConfig first = parse(json);
        SimulationConfig second = parse(json);
        assertEquals(first.consent(), second.consent());
        assertEquals(first.problems(), second.problems());
    }
}
