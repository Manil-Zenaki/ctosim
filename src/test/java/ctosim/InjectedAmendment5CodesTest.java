package ctosim;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment5Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment5CodesTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void selectsDrawUniformlyFromOfferedTeamCodesAtOneQuarterRate(boolean multiple) throws Exception {
        var form = selection(multiple, "a", "b", "97", "DK", "REF");
        String clean = multiple ? "a b" : "a";
        var run = faulty(form, 1600, codes("DK", "REF", "not-offered")
                + ",\"specialValues\":[\"97\"]," + fixed("answer", clean));
        var records = records(run, "dontKnow");
        rate(records.size(), 800, .25);
        truthfulCodeRecords(run, "answer", clean, List.of("DK", "REF"));
        rate((int) records.stream().filter(p -> p.value().equals("DK")).count(), records.size(), .5);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aSingleTeamCodeWorksEvenWhenSpecialValuesIsEmpty(boolean multiple) throws Exception {
        var run = faulty(selection(multiple, "ok", "DK"), 600,
                codes("DK") + ",\"specialValues\":[]," + fixed("answer", "ok"));
        rate(records(run, "dontKnow").size(), 300, .25);
        truthfulCodeRecords(run, "answer", "ok", List.of("DK"));
    }

    @Test void aCodeMayBelongToBothListsWithoutBeingExcludedFromTheFault() throws Exception {
        var run = faulty(selection(false, "ok", "97"), 600,
                codes("97") + ",\"specialValues\":[\"97\"]," + fixed("answer", "ok"));
        rate(records(run, "dontKnow").size(), 300, .25);
        truthfulCodeRecords(run, "answer", "ok", List.of("97"));
    }

    @ParameterizedTest @ValueSource(strings = {"097", "DK", "\u62d2\u7b54"})
    void selectCodesAreExactStringsIncludingLeadingZerosCaseAndUnicode(String code) throws Exception {
        var run = faulty(selection(false, "ok", "97", "dk", code), 400,
                codes(code) + ",\"specialValues\":[\"97\",\"dk\"]," + fixed("answer", "ok"));
        rate(records(run, "dontKnow").size(), 200, .25);
        truthfulCodeRecords(run, "answer", "ok", List.of(code));
        assertTrue(AnswerKey.csv(run.plan().problems()).contains(code));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unofferedTeamCodesDoNotFallBackToAnOfferedSpecialValue(boolean multiple) throws Exception {
        var run = faulty(selection(multiple, "ok", "97"), 120,
                codes("DK", "097") + ",\"specialValues\":[\"97\"]," + fixed("answer", "ok"));
        unchanged(run);
    }

    @ParameterizedTest @ValueSource(strings = {"int", "decimal"})
    void numericCodesAreUniformAmongNumericValuesAcceptedByTheCurrentConstraint(String type) throws Exception {
        var form = fixture("<floor>-98</floor><amount/>",
                bind("floor", "int", "readonly=\"true()\"")
                        + bind("amount", type, "constraint=\". &gt;= /data/floor\""),
                input("floor") + input("amount"));
        var run = faulty(form, 1600, codes("-99", "-98", "-97", "words")
                + ",\"specialValues\":[\"97\"]," + fixed("amount", "10"));
        var records = records(run, "dontKnow");
        rate(records.size(), 800, .25);
        assertFalse(records.isEmpty());
        String faulty = soleEnumerator(run, "dontKnow");
        var targets = targets(run, "dontKnow");
        assertEquals(records.size(), targets.size());
        for (var record : records) {
            assertEquals("amount", record.field());
            assertEquals(10, Double.parseDouble(record.cleanValue()));
            assertTrue(Set.of(-98.0, -97.0).contains(Double.parseDouble(record.value())));
        }
        rate((int) records.stream().filter(p -> Double.parseDouble(p.value()) == -98).count(), records.size(), .5);
        for (var submission : run.submissions()) {
            double actual = Double.parseDouble(value(submission, "amount"));
            if (targets.contains(submission.number())) {
                assertEquals(faulty, value(submission, "username"));
                assertTrue(Set.of(-98.0, -97.0).contains(actual));
            } else assertEquals(10, actual, "only recorded replacements change an ordinary answer");
        }
        newMetadata(run);
    }

    @Test void fractionalTeamCodeIsAcceptedForDecimalQuestions() throws Exception {
        var run = faulty(number("decimal", "constraint=\". &gt;= 0 or . = -9.75\""), 600,
                codes("-9.75", "refused") + ",\"specialValues\":[\"97\"]," + fixed("amount", "10"));
        rate(records(run, "dontKnow").size(), 300, .25);
        for (var record : records(run, "dontKnow")) assertEquals(-9.75, Double.parseDouble(record.value()));
        newMetadata(run);
    }

    @Test void aFractionalCodeIsNotRoundedIntoAnIntegerCode() throws Exception {
        var run = faulty(number("int", ""), 120,
                codes("-9.75") + ",\"specialValues\":[\"97\"]," + fixed("amount", "10"));
        unchanged(run);
    }

    @ParameterizedTest @ValueSource(strings = {"int", "decimal"})
    void absentNumericCandidatesKeepOrdinaryAnswersInsteadOfUsingSpecialValues(String type) throws Exception {
        var run = faulty(number(type, "constraint=\". &gt;= 0\""), 120,
                codes("-99", "refused") + ",\"specialValues\":[\"97\"]," + fixed("amount", "10"));
        unchanged(run);
    }

    @ParameterizedTest @ValueSource(strings = {"int", "decimal"})
    void aNumericTeamCodeNeedNotLieInTheOrdinaryDrawingRange(String type) throws Exception {
        var run = faulty(number(type, ""), 600,
                codes("-888") + ",\"specialValues\":[]," + fixed("amount", "10"));
        rate(records(run, "dontKnow").size(), 300, .25);
        for (var record : records(run, "dontKnow")) assertEquals(-888, Double.parseDouble(record.value()));
        newMetadata(run);
    }

    @Test void blankAnswersAreNotReplacedByTeamCodes() throws Exception {
        var run = faulty(number("int", ""), 100,
                codes("-888") + ",\"fields\":{\"amount\":{\"missingRate\":1}}");
        unchanged(run);
        for (var submission : run.submissions()) assertEquals("", value(submission, "amount"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void selectingTheAlreadyPresentCodeDoesNotInventARecord(boolean multiple) throws Exception {
        var run = faulty(selection(multiple, "ok", "DK"), 120,
                codes("DK") + "," + fixed("answer", "DK"));
        unchanged(run);
    }

    @Test void textAndDatesKeepTheirOrdinaryAnswersDespiteMatchingTeamCodeKinds() throws Exception {
        var form = fixture("<text/><date/>", bind("text", "string", "") + bind("date", "date", ""),
                input("text") + input("date"));
        unchanged(faulty(form, 100, codes("refused", "2026-01-01")));
    }

    @Test void currentlyFilteredOutCodesAreNeverUsedByTheFault() throws Exception {
        LoadedForm form;
        try (var in = getClass().getResourceAsStream("/ctosim/injected-problems/amendment5/filtered-codes.xml")) {
            assertNotNull(in);
            form = FormLoader.load(in, "filtered-codes.xml");
        }
        var run = faulty(form, 600, codes("hidden", "DK") + "," + fixed("answer", "ok"));
        rate(records(run, "dontKnow").size(), 300, .25);
        truthfulCodeRecords(run, "answer", "ok", List.of("DK"));
    }
}
