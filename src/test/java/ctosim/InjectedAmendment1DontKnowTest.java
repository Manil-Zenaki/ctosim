package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment1DontKnowTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void selectAnswersAreReplacedAtOneQuarterRateWithOneOfferedSpecialCode(boolean multiple) throws Exception {
        var f = fixture("<answer/>", bind("answer", multiple ? "select" : "select1", ""),
                choices("answer", multiple, "a", "b", "-99", "-98"));
        String original = multiple ? "a b" : "a";
        var r = run(f, settings(1600, 2, "\"specialValues\":[\"-99\",\"-98\",\"unoffered\"],"
                + "\"fields\":{\"answer\":{\"value\":\"" + original + "\"}}")
                .withProblems(rates(0, "dontKnow")));
        var ps = records(r, "dontKnow"); rate(ps.size(), 800, .25); String who = soleEnumerator(r, "dontKnow");
        for (var p : ps) {
            assertEquals("answer", p.field()); assertEquals(original, p.cleanValue());
            assertTrue(Set.of("-99", "-98").contains(p.value()), "a select_multiple gets the special code alone");
        }
        rate((int) ps.stream().filter(p -> p.value().equals("-99")).count(), ps.size(), .5);
        for (var s : r.submissions()) if (!value(s, "username").equals(who)) assertEquals(original, value(s, "answer"));
        newMetadata(r);
    }
    @ParameterizedTest @ValueSource(strings = {"int", "decimal"})
    void numericCodesAreUniformAmongOnlyNumericEngineAcceptedSpecialValues(String type) throws Exception {
        var r = run(number(type, "constraint=\". &gt;= 0 or . = -99 or . = -98\""), settings(1600, 2,
                "\"specialValues\":[\"-99\",\"-98\",\"-97\",\"refused\"]").withProblems(rates(0, "dontKnow")));
        var ps = records(r, "dontKnow"); rate(ps.size(), 800, .25);
        for (var p : ps) {
            assertTrue(Set.of(-99.0, -98.0).contains(Double.parseDouble(p.value())));
            assertEquals(value(r.clean(p.submission()), "amount"), p.cleanValue());
        }
        rate((int) ps.stream().filter(p -> Double.parseDouble(p.value()) == -99).count(), ps.size(), .5);
        newMetadata(r);
    }
    @Test void fractionalSpecialCodesAreAcceptedForDecimalQuestions() throws Exception {
        var r = run(number("decimal", ""), settings(600, 2, "\"specialValues\":[\"-9.75\"]").withProblems(rates(0, "dontKnow")));
        rate(records(r, "dontKnow").size(), 300, .25);
        for (var p : records(r, "dontKnow")) assertEquals(-9.75, Double.parseDouble(p.value()));
    }
    @Test void unicodeOfferedSpecialCodesArePreservedExactly() throws Exception {
        var f = fixture("<answer/>", bind("answer", "select1", ""), choices("answer", false, "ok", "拒否"));
        var r = run(f, settings(600, 2, "\"specialValues\":[\"拒否\"],\"fields\":{\"answer\":{\"value\":\"ok\"}}")
                .withProblems(rates(0, "dontKnow")));
        rate(records(r, "dontKnow").size(), 300, .25);
        for (var p : records(r, "dontKnow")) assertEquals("拒否", p.value());
    }
    @Test void blankAnswersStayBlankAndProduceNoRecords() throws Exception {
        var f = number("int", "");
        var r = run(f, settings(100, 2, "\"fields\":{\"amount\":{\"missingRate\":1}},\"specialValues\":[\"-99\"]")
                .withProblems(rates(0, "dontKnow")));
        assertTrue(r.plan().problems().isEmpty());
        for (var s : r.submissions()) assertEquals("", value(s, "amount"));
    }
    @Test void choosingTheSameSpecialCodeDoesNotProduceAReplacementRecord() throws Exception {
        var f = fixture("<answer/>", bind("answer", "select1", ""), choices("answer", false, "ok", "-99"));
        var r = run(f, settings(100, 2, "\"specialValues\":[\"-99\"],\"fields\":{\"answer\":{\"value\":\"-99\"}}")
                .withProblems(rates(0, "dontKnow")));
        assertTrue(r.plan().problems().isEmpty());
        for (var s : r.submissions()) assertEquals("-99", value(s, "answer"));
    }
    @Test void noOfferedSpecialCodeLeavesSelectAnswersAlone() throws Exception {
        var f = fixture("<answer/>", bind("answer", "select1", ""), choices("answer", false, "a", "b"));
        var r = run(f, settings(100, 2, "\"specialValues\":[\"-99\"]").withProblems(rates(0, "dontKnow")));
        assertTrue(r.plan().problems().isEmpty()); for (var s : r.submissions()) ordinary(r, s.number());
    }
    @Test void noAcceptedNumericSpecialCodeLeavesNumbersAlone() throws Exception {
        var r = run(number("int", "constraint=\". &gt;= 0\""), settings(100, 2,
                "\"specialValues\":[\"-99\",\"words\"]").withProblems(rates(0, "dontKnow")));
        assertTrue(r.plan().problems().isEmpty()); for (var s : r.submissions()) ordinary(r, s.number());
    }
    @Test void anEmptySpecialValueListMakesDontKnowANoop() throws Exception {
        var r = run(number("decimal", ""), settings(100, 2, "\"specialValues\":[]").withProblems(rates(0, "dontKnow")));
        assertTrue(r.plan().problems().isEmpty()); for (var s : r.submissions()) ordinary(r, s.number());
    }
    @Test void textAndDateQuestionsAreNotReplacedEvenWhenTheirSpecialCodesWouldBeValid() throws Exception {
        var f = fixture("<text/><date/>", bind("text", "string", "") + bind("date", "date", ""), input("text") + input("date"));
        var r = run(f, settings(100, 2, "\"specialValues\":[\"refused\",\"2026-01-01\"]").withProblems(rates(0, "dontKnow")));
        assertTrue(r.plan().problems().isEmpty()); for (var s : r.submissions()) ordinary(r, s.number());
    }
    @Test void repeatCountQuestionsAreExcludedFromDontKnow() throws Exception {
        var r = run(roster(true, ""), settings(100, 2,
                "\"fields\":{\"size\":{\"value\":3}},\"specialValues\":[\"1\",\"2\"]").withProblems(rates(0, "dontKnow")));
        assertTrue(r.plan().problems().isEmpty());
        for (var s : r.submissions()) { assertEquals("3", value(s, "size")); assertEquals(3, count(s, "person")); }
    }
    @Test void readOnlyAndCalculatedNumericAnswersAreExcluded() throws Exception {
        var f = fixture("<readonly>17</readonly><calculated/>", bind("readonly", "int", "readonly=\"true()\"")
                + bind("calculated", "int", "calculate=\"42\""), input("readonly"));
        var r = run(f, settings(60, 2, "\"specialValues\":[\"-99\"]").withProblems(rates(0, "dontKnow")));
        assertTrue(r.plan().problems().isEmpty());
        for (var s : r.submissions()) { assertEquals("17", value(s, "readonly")); assertEquals("42", value(s, "calculated")); }
    }
    @Test void multipleReplacementsAreOrderedByFormFieldsAndKeepIndependentOrdinaryDraws() throws Exception {
        var f = fixture("<z/><a/>", bind("a", "int", "") + bind("z", "int", ""), input("z") + input("a"));
        var r = run(f, settings(1000, 2, "\"specialValues\":[\"-99\"]").withProblems(rates(0, "dontKnow")));
        int paired = 0;
        for (var s : r.submissions()) {
            var ps = records(r, "dontKnow").stream().filter(p -> p.submission() == s.number()).toList();
            if (ps.size() == 2) { paired++; assertEquals(List.of("z", "a"), ps.stream().map(InjectedProblem::field).toList()); }
            var clean = r.clean(s.number());
            for (String field : List.of("z", "a")) {
                var p = ps.stream().filter(x -> x.field().equals(field)).findFirst();
                if (p.isPresent()) assertEquals(value(clean, field), p.get().cleanValue());
                else assertEquals(value(clean, field), value(s, field), "fault draws are separate from ordinary answer draws");
            }
        }
        assertTrue(paired > 0, "exercise more than one record on a submission"); newMetadata(r);
    }
}
