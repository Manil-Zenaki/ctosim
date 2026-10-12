package ctosim;

import java.util.List;
import org.junit.jupiter.api.Test;
import static ctosim.ConstraintHintsAmendment2Support.*;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintHintsAmendment2ContractTest {
    @Test void noneHasNullScalarsAndNonNullEmptyCollections() {
        assertNewDefaults(ConstraintHints.none());
    }

    @Test void sevenArgumentConstructorPreservesOldComponentsAndDefaultsNewOnes() {
        var h = new ConstraintHints(new Bound(1, true), new Bound(9, false), null, null, 2, 8, "[a-z]+");
        assertEquals(new Bound(1, true), h.lower());
        assertEquals(new Bound(9, false), h.upper());
        assertEquals(2, h.minLength());
        assertEquals(8, h.maxLength());
        assertEquals("[a-z]+", h.pattern());
        assertNewDefaults(h);
    }

    private static void assertNewDefaults(ConstraintHints h) {
        assertAll(() -> assertNull(h.prefix()), () -> assertNull(h.minSelected()), () -> assertNull(h.maxSelected()),
                () -> assertEquals(List.of(), List.copyOf(h.excluded())),
                () -> assertEquals(List.of(), List.copyOf(h.included())),
                () -> assertEquals(List.of(), h.alternatives()), () -> assertEquals(List.of(), h.candidates()));
    }

    @Test void canonicalConstructorCopiesAllCollectionsAndPreservesTheirOrder() {
        var data = hints().prefix("09").count(1, 3).exclude("z", "a", "é").include("c", "b")
                .alternatives(hints().range(7, 7).build(), hints().range(2, 2).build()).candidates("7", "2", "é");
        var h = data.build();
        var expectedAlternatives = List.copyOf(data.alternatives);
        data.excluded.clear(); data.included.clear(); data.alternatives.clear(); data.candidates.clear();
        assertAll(() -> assertEquals(List.of("z", "a", "é"), List.copyOf(h.excluded())),
                () -> assertEquals(List.of("c", "b"), List.copyOf(h.included())),
                () -> assertEquals(expectedAlternatives, h.alternatives()),
                () -> assertEquals(List.of("7", "2", "é"), h.candidates()),
                () -> assertEquals("09", h.prefix()), () -> assertEquals(1, h.minSelected()),
                () -> assertEquals(3, h.maxSelected()));
    }

    @Test void everyCollectionRejectsMutationIncludingEmptyDefaults() {
        for (var h : List.of(ConstraintHints.none(), hints().exclude("a").include("b")
                .alternatives(ConstraintHints.none()).candidates("2").build())) {
            assertAll(() -> assertThrows(UnsupportedOperationException.class, () -> h.excluded().add("x")),
                    () -> assertThrows(UnsupportedOperationException.class, () -> h.included().add("x")),
                    () -> assertThrows(UnsupportedOperationException.class, () -> h.alternatives().add(ConstraintHints.none())),
                    () -> assertThrows(UnsupportedOperationException.class, () -> h.candidates().add("x")));
        }
    }
}
