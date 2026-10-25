package ctosim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ConsentAmendment8Test {
    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment8Support#acceptedFields")
    void constructorKeepsExactFieldNamesAndTheirOrder(List<String> fields) {
        Consent consent = new Consent(fields, .375, "oui", "non");
        assertEquals(fields, consent.fields());
        assertEquals(.375, consent.rate());
        assertEquals("oui", consent.yes());
        assertEquals("non", consent.no());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -0.0, 1, .9, .125, Double.MIN_VALUE, 0.9999999999999999})
    void constructorAcceptsEveryRateBoundary(double rate) {
        assertEquals(rate, new Consent(List.of("c"), rate, "Y", "N").rate(), 0.0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-Double.MIN_VALUE, -1, 1.0000000000000002, Double.MAX_VALUE,
            Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void constructorRejectsOutOfRangeAndNonfiniteRates(double rate) {
        assertThrows(IllegalArgumentException.class, () -> new Consent(List.of("c"), rate, "Y", "N"));
    }

    @Test
    void constructorRejectsAnEmptyFieldList() {
        assertThrows(IllegalArgumentException.class, () -> new Consent(List.of(), .9, "1", "0"));
    }

    @ParameterizedTest
    @MethodSource("ctosim.SimulationConfigAmendment8Support#invalidNames")
    void constructorRejectsEmptyAndEdgeWhitespaceFieldNames(String name) {
        assertThrows(IllegalArgumentException.class, () -> new Consent(List.of("ok", name), .9, "1", "0"));
    }

    @Test
    void constructorRejectsNonadjacentDuplicateFields() {
        assertThrows(IllegalArgumentException.class, () -> new Consent(List.of("a", "b", "a"), .9, "1", "0"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void constructorRejectsEachNullComponentOrElementWithNullPointerException(int component) {
        assertThrows(NullPointerException.class, () -> new Consent(
                component == 0 ? null : component == 1 ? Arrays.asList("ok", null) : List.of("ok"),
                .9, component == 2 ? null : "1", component == 3 ? null : "0"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void constructorRejectsEitherEmptyChoiceCode(boolean yes) {
        assertThrows(IllegalArgumentException.class,
                () -> new Consent(List.of("c"), .9, yes ? "" : "1", yes ? "0" : ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "same", "同意", " "})
    void constructorRejectsEqualChoiceCodes(String code) {
        assertThrows(IllegalArgumentException.class, () -> new Consent(List.of("c"), .9, code, code));
    }

    @Test
    void choiceCodesAreExactNonemptyStringsWithoutFieldNameWhitespaceRestrictions() {
        Consent consent = new Consent(List.of("c"), .9, " \t", " Y ");
        assertEquals(" \t", consent.yes());
        assertEquals(" Y ", consent.no());
        assertEquals("Y", new Consent(List.of("c"), .9, "Y", "y").yes());
    }

    @Test
    void constructorTakesADefensiveSnapshotOfTheCallerList() {
        List<String> caller = new ArrayList<>(List.of("z", "a"));
        Consent consent = new Consent(caller, .9, "1", "0");
        caller.set(0, "changed");
        caller.clear();
        assertEquals(List.of("z", "a"), consent.fields());
    }

    static Stream<Consumer<List<String>>> mutations() {
        return Stream.of(list -> list.add("new"), list -> list.set(0, "new"), list -> list.remove(0),
                List::clear, list -> { var it = list.iterator(); it.next(); it.remove(); },
                list -> { var it = list.listIterator(); it.next(); it.set("new"); },
                list -> list.subList(0, 1).clear(), list -> list.replaceAll(value -> "new"),
                list -> list.removeIf(value -> true));
    }

    @ParameterizedTest
    @MethodSource("mutations")
    void fieldsRejectMutationThroughListIteratorsAndViews(Consumer<List<String>> mutation) {
        Consent consent = new Consent(new ArrayList<>(List.of("z", "a")), .9, "1", "0");
        assertThrows(UnsupportedOperationException.class, () -> mutation.accept(consent.fields()));
        assertEquals(List.of("z", "a"), consent.fields());
    }
}
