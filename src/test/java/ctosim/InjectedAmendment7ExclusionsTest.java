package ctosim;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment7Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment7ExclusionsTest {
    record ProtectedKind(String name, String type, String attrs, String body, String code) { }

    static Stream<Arguments> protectedQuestions() {
        List<ProtectedKind> kinds = new ArrayList<>();
        kinds.add(new ProtectedKind("date", "date", "", input("protected"), "2026-01-01"));
        kinds.add(new ProtectedKind("time", "time", "", input("protected"), "12:34:00"));
        kinds.add(new ProtectedKind("datetime", "dateTime", "", input("protected"), "2026-01-01T12:34:00"));
        kinds.add(new ProtectedKind("geopoint", "geopoint", "", input("protected"), "0 0 0 5"));
        kinds.add(new ProtectedKind("geotrace", "geotrace", "", input("protected"), "0 0 0 5;0 1 0 5"));
        kinds.add(new ProtectedKind("geoshape", "geoshape", "", input("protected"), "0 0 0 5;0 1 0 5;1 0 0 5;0 0 0 5"));
        kinds.add(new ProtectedKind("barcode", "barcode", "", input("protected"), "0123456789"));
        for (String media : List.of("image", "audio", "video", "file", "signature", "draw")) {
            String mime = switch (media) {
                case "audio", "video" -> media + "/*";
                case "file" -> "application/octet-stream";
                default -> "image/*";
            };
            String ext = switch (media) { case "audio" -> "m4a"; case "video" -> "mp4"; case "file" -> "bin"; default -> "jpg"; };
            String appearance = List.of("signature", "draw").contains(media) ? " appearance=\"" + media + "\"" : "";
            kinds.add(new ProtectedKind(media, "binary", "", "<upload ref=\"/data/protected\" mediatype=\""
                    + mime + "\"" + appearance + "><label>Capture</label></upload>", "team_code." + ext));
        }
        for (String type : List.of("int", "decimal")) {
            kinds.add(new ProtectedKind("range-" + type, type, "",
                    "<range ref=\"/data/protected\" start=\"0\" end=\"20\" step=\"1\"><label>Range</label></range>", "12"));
        }
        kinds.add(new ProtectedKind("rank", "string", "", "<rank ref=\"/data/protected\"><label>Rank</label>"
                + "<item><label>A</label><value>A</value></item><item><label>B</label><value>B</value></item></rank>", "B A"));
        kinds.add(new ProtectedKind("acknowledge", "string", "", "<trigger ref=\"/data/protected\"><label>OK</label></trigger>", "TEAM"));
        kinds.add(new ProtectedKind("note", "string", "readonly=\"true()\"", input("protected"), "TEAM"));
        kinds.add(new ProtectedKind("readonly", "string", "readonly=\"true()\"", input("protected"), "TEAM"));
        kinds.add(new ProtectedKind("calculation", "string", "calculate=\"'ordinary'\"", "", "TEAM"));
        kinds.add(new ProtectedKind("calculate-here", "string", "ct:calculate-here=\"'ordinary'\" readonly=\"true()\"", input("protected"), "TEAM"));
        return kinds.stream().flatMap(k -> Stream.of("dontKnow", "refuse").map(f -> Arguments.of(f, k.name(), k)));
    }

    @ParameterizedTest(name = "{0} leaves {1} alone") @MethodSource("protectedQuestions")
    void excludedKindsKeepTheirSameVisitValueEvenWithUsableTeamCodes(String fault, String name, ProtectedKind k)
            throws Exception {
        String node = name.equals("readonly") ? "<protected>ordinary</protected>" : "<protected/>";
        var f = fixture(node + "<answer/>", bind("protected", k.type(), k.attrs()) + bind("answer", "string", ""),
                k.body() + input("answer"));
        // The ordinary text companion proves that an assigned, active fault was exercised.
        var r = textRun(fault, f, 240, teamCodes(fault, k.code()) + "," + fixedText("answer", "ordinary"));
        assertFalse(records(r, fault).isEmpty(), "the exclusion must not be tested with a globally inactive fault");
        for (var p : records(r, fault)) assertEquals("answer", p.field());
        for (var s : r.submissions()) {
            assertEquals(value(r.clean(s.number()), "protected"), value(s, "protected"), name);
        }
        metadata(r);
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void textSurveyIdsRemainUniqueAndUnchanged(String fault) throws Exception {
        var f = fixture("<survey_id/><answer/>", bind("survey_id", "string", "") + bind("answer", "string", ""),
                input("survey_id") + input("answer"));
        String possibleIds = java.util.stream.IntStream.rangeClosed(1, 240)
                .mapToObj(n -> SimulationConfigTestSupport.quote("household-" + n))
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        var r = textRun(fault, f, 240, teamCodes(fault, "-999") + ",\"id\":\"survey_id\","
                + "\"fields\":{\"survey_id\":{\"texts\":" + possibleIds + "},\"answer\":{\"value\":\"ordinary\"}}");
        assertFalse(records(r, fault).isEmpty());
        Set<String> ids = new HashSet<>();
        for (var s : r.submissions()) {
            assertEquals(value(r.clean(s.number()), "survey_id"), value(s, "survey_id"));
            assertTrue(ids.add(value(s, "survey_id")));
        }
        for (var p : records(r, fault)) assertEquals("answer", p.field());
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void textConsentQuestionsStayProtectedIncludingTheFinalRefusingAnswer(String fault) throws Exception {
        var f = fixture("<understand/><agree/><answer/>", bind("understand", "string", "")
                + bind("agree", "string", "") + bind("answer", "string", ""),
                input("understand") + input("agree") + input("answer"));
        var r = textRun(fault, f, 400, teamCodes(fault, "TEAM") + ","
                + InjectedAmendment2Support.consent(.5) + "," + fixedText("answer", "ordinary"));
        assertFalse(records(r, fault).isEmpty());
        Set<String> finalAnswers = new HashSet<>();
        for (var s : r.submissions()) {
            assertEquals("oui", value(s, "understand"));
            assertEquals(value(r.clean(s.number()), "agree"), value(s, "agree"));
            finalAnswers.add(value(s, "agree"));
        }
        assertEquals(Set.of("oui", "non"), finalAnswers);
        for (var p : records(r, fault)) assertEquals("answer", p.field());
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void textThatControlsAnIndirectRepeatCountCannotBeReplaced(String fault) throws Exception {
        var f = fixture("<size/><total/><person jr:template=\"\"><note>kept</note></person><answer/>",
                bind("size", "string", "") + bind("total", "int", "calculate=\"number(/data/size)\"")
                        + bind("person/note", "string", "readonly=\"true()\"") + bind("answer", "string", ""),
                input("size") + "<repeat nodeset=\"/data/person\" jr:count=\"/data/total\">"
                        + input("person/note") + "</repeat>" + input("answer"));
        var r = textRun(fault, f, 240, teamCodes(fault, "1")
                + ",\"fields\":{\"size\":{\"value\":\"3\"},\"answer\":{\"value\":\"ordinary\"}}");
        assertFalse(records(r, fault).isEmpty());
        for (var s : r.submissions()) {
            assertEquals("3", value(s, "size"));
            assertEquals(3, count(s, "person"));
        }
        for (var p : records(r, fault)) assertEquals("answer", p.field());
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void numericRepeatCountsAreStillProtected(String fault) throws Exception {
        var r = textRun(fault, roster(true, "constraint=\". &gt;= 1 and . &lt;= 3\""), 240,
                teamCodes(fault, "1") + "," + fixedText("size", "3"));
        assertFalse(records(r, fault).isEmpty(), "the ordinary roster text is eligible");
        for (var s : r.submissions()) {
            assertEquals("3", value(s, "size"));
            assertEquals(3, count(s, "person"));
        }
        assertTrue(records(r, fault).stream().noneMatch(p -> p.field().equals("size")));
    }
}
