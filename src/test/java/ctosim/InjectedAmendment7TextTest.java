package ctosim;

import java.util.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment7Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment7TextTest {
    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void textCodesAreUniformAtOneQuarterRateAndKeepExactStrings(String fault) throws Exception {
        var codes = List.of("-999", "0097", "97.00", "拒答", "DK", "dk");
        var r = textRun(fault, textQuestion(""), 3200,
                teamCodes(fault, codes.toArray(String[]::new)) + ",\"specialValues\":[],"
                        + fixedText("answer", "ordinary"));
        var ps = records(r, fault);
        rate(ps.size(), 1600, .25);
        textRecords(r, fault, "answer", "ordinary", codes);
        for (String code : codes) {
            int count = (int) ps.stream().filter(p -> p.value().equals(code)).count();
            assertTrue(count > 0, "each usable string must be drawn: " + code);
            rate(count, ps.size(), 1.0 / codes.size());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void regexFiltersCodesBeforeUniformSelectionRatherThanReducingTheInjectionRate(String fault) throws Exception {
        var f = textQuestion("constraint=\"regex(., '^(ordinary|-999|-888)$')\"");
        var r = textRun(fault, f, 2400, teamCodes(fault, "bad", "-999", "NO", "-888", "999", "-99")
                + "," + fixedText("answer", "ordinary"));
        textRecords(r, fault, "answer", "ordinary", List.of("-999", "-888"));
        var ps = records(r, fault);
        rate(ps.size(), 1200, .25);
        rate((int) ps.stream().filter(p -> p.value().equals("-999")).count(), ps.size(), .5);
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void acceptanceUsesTheOtherAnswersOfThisInterview(String fault) throws Exception {
        var f = fixture("<gate/><answer/>",
                bind("gate", "string", "constraint=\". = 'A' or . = 'B'\"")
                        + bind("answer", "string", "constraint=\". = 'ordinary' or . = concat('-9', /data/gate)\""),
                input("gate") + input("answer"));
        var r = textRun(fault, f, 1600, teamCodes(fault, "-9A", "-9B")
                + ",\"fields\":{\"gate\":{\"texts\":[\"A\",\"B\"]},\"answer\":{\"value\":\"ordinary\"}}");
        var ps = records(r, fault);
        rate(ps.size(), 800, .25);
        Set<String> seen = new HashSet<>();
        for (var p : ps) {
            assertEquals("answer", p.field());
            String gate = value(r.target(p), "gate");
            seen.add(gate);
            assertEquals("-9" + gate, p.value());
            assertEquals("ordinary", p.cleanValue());
        }
        assertEquals(Set.of("A", "B"), seen, "exercise both dynamic acceptance sets");
        metadata(r);
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void noAcceptedCodeKeepsTheOrdinaryAnswerWithoutFallingBackToOtherLists(String fault) throws Exception {
        String other = fault.equals("dontKnow") ? "refuse" : "dontKnow";
        var f = textQuestion("constraint=\". = 'ordinary' or . = 'OTHER' or . = '97'\"");
        var r = textRun(fault, f, 240, teamCodes(fault, "-999", "bad") + ","
                + teamCodes(other, "OTHER") + ",\"specialValues\":[\"97\"]," + fixedText("answer", "ordinary"));
        noChanges(r, fault);
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void textCodesDoNotUseNumericEquality(String fault) throws Exception {
        var r = textRun(fault, textQuestion(""), 800, teamCodes(fault, "097") + "," + fixedText("answer", "97"));
        textRecords(r, fault, "answer", "97", List.of("097"));
        rate(records(r, fault).size(), 400, .25);
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void anAlreadyPresentTextCodeIsNotRecordedAsAChange(String fault) throws Exception {
        noChanges(textRun(fault, textQuestion(""), 240,
                teamCodes(fault, "-999") + "," + fixedText("answer", "-999")), fault);
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void anAcceptedCodeEqualToTheOrdinaryTextKeepsItsShareOfUniformDraws(String fault) throws Exception {
        var r = textRun(fault, textQuestion(""), 2400,
                teamCodes(fault, "ordinary", "TEAM") + "," + fixedText("answer", "ordinary"));
        // Half of successful quarter-probability code draws leave the answer unchanged.
        rate(records(r, fault).size(), 1200, .25 * .5);
        textRecords(r, fault, "answer", "ordinary", List.of("TEAM"));
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void aBlankTextAnswerStaysBlank(String fault) throws Exception {
        var r = textRun(fault, textQuestion(""), 160,
                teamCodes(fault, "-999") + ",\"fields\":{\"answer\":{\"missingRate\":1}}");
        noChanges(r, fault);
        for (var s : r.submissions()) assertEquals("", value(s, "answer"));
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void irrelevantTextIsNeverFilledOrRecorded(String fault) throws Exception {
        var r = textRun(fault, textQuestion("relevant=\"false()\""), 160,
                teamCodes(fault, "-999") + "," + fixedText("answer", "ordinary"));
        noChanges(r, fault);
        for (var s : r.submissions()) assertEquals("", value(s, "answer"));
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void otherSpecifyReceivesCodesOnlyInInterviewsWhereOtherWasSelected(String fault) throws Exception {
        LoadedForm f;
        try (var in = getClass().getResourceAsStream("/ctosim/injected-problems/amendment7/other-specify.xml")) {
            assertNotNull(in);
            f = FormLoader.load(in, "other-specify.xml");
        }
        var r = textRun(fault, f, 1600, teamCodes(fault, "-999") + "," + fixedText("answer", "detail"));
        String faulty = soleEnumerator(r, fault);
        int opportunities = 0, closed = 0;
        for (var s : r.submissions()) {
            if (value(s, "gate").equals("other")) {
                if (value(s, "username").equals(faulty)) opportunities++;
            } else {
                closed++;
                assertEquals("", value(s, "answer"));
            }
        }
        assertTrue(closed > 0);
        rate(records(r, fault).size(), opportunities, .25);
        for (var p : records(r, fault)) {
            assertEquals("answer", p.field());
            assertEquals("other", value(r.target(p), "gate"));
            assertEquals("detail", p.cleanValue());
            assertEquals("-999", p.value());
        }
        metadata(r);
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void aTextConstraintSeesEarlierInjectedAnswersRatherThanTheCleanInterview(String fault) throws Exception {
        var f = fixture("<gate/><answer/>", bind("gate", "string", "")
                + bind("answer", "string", "constraint=\". = 'ordinary' or (. = 'TEAM' and /data/gate = 'TEAM')\""),
                input("gate") + input("answer"));
        var r = textRun(fault, f, 2000, teamCodes(fault, "TEAM")
                + ",\"fields\":{\"gate\":{\"value\":\"A\"},\"answer\":{\"value\":\"ordinary\"}}");
        var gateRecords = records(r, fault).stream().filter(p -> p.field().equals("gate")).toList();
        var answerRecords = records(r, fault).stream().filter(p -> p.field().equals("answer")).toList();
        rate(gateRecords.size(), 1000, .25);
        assertFalse(answerRecords.isEmpty(), "must inject after the earlier replacement opened the constraint");
        rate(answerRecords.size(), gateRecords.size(), .25);
        for (var p : answerRecords) {
            assertEquals("TEAM", value(r.target(p), "gate"));
            assertEquals("TEAM", p.value());
            assertEquals("ordinary", p.cleanValue());
        }
        for (var s : r.submissions()) if (value(s, "gate").equals("A"))
            assertEquals("ordinary", value(s, "answer"));
        metadata(r);
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void textWithNumericAppearanceStillTakesAcceptedNonnumericTeamCodes(String fault) throws Exception {
        for (String appearance : List.of("numbers", "numbers_phone", "numbers_decimal")) {
            var f = fixture("<answer/>", bind("answer", "string", ""),
                    "<input ref=\"/data/answer\" appearance=\"" + appearance + "\"><label>Amount</label></input>");
            var r = textRun(fault, f, 600, teamCodes(fault, "UNKNOWN") + "," + fixedText("answer", "12"));
            textRecords(r, fault, "answer", "12", List.of("UNKNOWN"));
            rate(records(r, fault).size(), 300, .25);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void supplyingTextCodesAloneDoesNotRequestInjection(String fault) throws Exception {
        var f = textQuestion("");
        var c = settings(40, 2, teamCodes(fault, "-999") + "," + fixedText("answer", "ordinary"));
        noChanges(run(f, c), fault);
        noChanges(run(f, c.withProblems(rates(0))), fault);
    }

    @ParameterizedTest @ValueSource(strings = {"dontKnow", "refuse"})
    void anOmittedCodeListDoesNotInjectCodesIntoText(String fault) throws Exception {
        var r = textRun(fault, textQuestion(""), 40,
                "\"specialValues\":[\"-999\"]," + fixedText("answer", "ordinary"));
        noChanges(r, fault);
        assertEquals(1, r.plan().warnings().stream().filter(w -> w.contains(fault)).count());
    }
}
