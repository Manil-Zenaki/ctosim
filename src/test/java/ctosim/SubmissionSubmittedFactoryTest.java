package ctosim;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Spec 08, Amendment 1: the added factory and compatibility of the old one. */
class SubmissionSubmittedFactoryTest {
    private static final LocalDateTime SUBMITTED = LocalDateTime.of(2027, 1, 2, 13, 4, 5, 123456789);

    @ParameterizedTest @ValueSource(ints = {1, 19, Integer.MAX_VALUE})
    void fiveArgumentFactoryPreservesEveryArgumentAndHasNoAnswersOrWarnings(int number) {
        String xml = "<unvalidated>\u00e9\n";
        var s = Submission.of(number, xml, START, END, SUBMITTED);
        assertAll(() -> assertEquals(number, s.number()),
                () -> assertEquals(xml, s.instanceXml()),
                () -> assertEquals(START, s.start()),
                () -> assertEquals(END, s.end()),
                () -> assertEquals(SUBMITTED, s.submitted()),
                () -> assertEquals(0, s.answered()),
                () -> assertTrue(s.warnings().isEmpty()));
    }

    @Test void fourArgumentFactoryDefaultsSubmittedToEndIncludingNanoseconds() {
        assertEquals(END, Submission.of(1, "", START, END).submitted());
    }

    @Test void explicitlySupplyingEndAsSubmittedAgreesWithFourArgumentFactory() {
        assertEquals(Submission.of(1, "", START, END).submitted(),
                Submission.of(1, "", START, END, END).submitted());
    }

    @ParameterizedTest @ValueSource(strings = {"", "plain text", "<data>", "<wrong/>"})
    void fiveArgumentFactoryDoesNotParseOrValidateXml(String xml) {
        assertEquals(xml, Submission.of(1, xml, START, END, SUBMITTED).instanceXml());
    }

    @ParameterizedTest @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void fiveArgumentFactoryRejectsNonpositiveNumbers(int number) {
        assertThrows(IllegalArgumentException.class, () -> Submission.of(number, "", START, END, SUBMITTED));
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void fiveArgumentFactoryRequiresEachReferenceArgument(int argument) {
        assertThrows(NullPointerException.class, () -> Submission.of(1,
                argument == 0 ? null : "", argument == 1 ? null : START,
                argument == 2 ? null : END, argument == 3 ? null : SUBMITTED));
    }

    @Test void fiveArgumentFactoryWarningsAreUnmodifiable() {
        var s = Submission.of(1, "", START, END, SUBMITTED);
        assertThrows(UnsupportedOperationException.class, () -> s.warnings().add("unexpected"));
    }
}
