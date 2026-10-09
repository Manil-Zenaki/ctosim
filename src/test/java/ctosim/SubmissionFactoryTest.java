package ctosim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionFactoryTest {
    @ParameterizedTest @ValueSource(ints = {1, 8, Integer.MAX_VALUE})
    void factoryPreservesArgumentsAndStartsWithZeroAnswersAndNoWarnings(int number) {
        String xml = "<not-even-validated>é\n";
        Submission s = Submission.of(number, xml, START, END);
        assertAll(() -> assertEquals(number, s.number()), () -> assertEquals(xml, s.instanceXml()),
                () -> assertEquals(START, s.start()), () -> assertEquals(END, s.end()),
                () -> assertEquals(0, s.answered()), () -> assertTrue(s.warnings().isEmpty()));
    }

    @ParameterizedTest @ValueSource(strings = {"", "plain text", "<data>", "<wrong/>"})
    void factoryDoesNotValidateXml(String xml) {
        assertEquals(xml, Submission.of(1, xml, START, END).instanceXml());
    }

    @ParameterizedTest @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void nonpositiveSubmissionNumbersAreRejected(int number) {
        assertThrows(IllegalArgumentException.class, () -> Submission.of(number, "", START, END));
    }

    @Test void eachReferenceArgumentIsRequired() {
        assertAll(() -> assertThrows(NullPointerException.class, () -> Submission.of(1, null, START, END)),
                () -> assertThrows(NullPointerException.class, () -> Submission.of(1, "", null, END)),
                () -> assertThrows(NullPointerException.class, () -> Submission.of(1, "", START, null)));
    }

    @Test void warningsRemainUnmodifiableAsRequiredBySubmissionContract() {
        Submission s = Submission.of(1, "", START, END);
        assertThrows(UnsupportedOperationException.class, () -> s.warnings().add("x"));
    }
}
