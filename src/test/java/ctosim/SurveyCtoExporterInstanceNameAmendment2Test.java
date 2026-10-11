package ctosim;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.SurveyCtoExporterAmendment2Support.*;
import static ctosim.SurveyCtoExporterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoExporterInstanceNameAmendment2Test {
    static Stream<Arguments> names() {
        return Stream.of(
                Arguments.of("C3-A ", "C3-A"),
                Arguments.of("C3-A     ", "C3-A"),
                Arguments.of(" ", ""),
                Arguments.of("     ", ""),
                Arguments.of("", ""),
                Arguments.of("C3-A", "C3-A"),
                Arguments.of("  C3-A  ", "  C3-A"),
                Arguments.of("C3  A  ", "C3  A"),
                Arguments.of("  Élodie 東京 😀  ", "  Élodie 東京 😀"),
                Arguments.of("A, \"B\"   ", "A, \"B\""),
                Arguments.of("first\nsecond  ", "first\nsecond"),
                Arguments.of("first\rsecond  ", "first\rsecond"));
    }

    @ParameterizedTest @MethodSource("names")
    void removesTrailingSpacesWhilePreservingTheRestInBothMainLayouts(String stored, String expected) throws Exception {
        var input = List.of(named(1, "uuid:a", "", "<instanceName>" + xmlText(stored) + "</instanceName>"));
        var e = exporter();
        assertAll(
                () -> assertEquals(expected, row(main(e, input, Layout.WIDE), 1).get("instanceName")),
                () -> assertEquals(expected, row(main(e, input, Layout.LONG), 1).get("instanceName")));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void absentAndEmptyNamesStayEmptyBetweenNamedSubmissions(Layout layout) throws Exception {
        var inputs = List.of(named(1, "uuid:a", "", "<instanceName>Alpha </instanceName>"),
                named(2, "uuid:b", "", ""), named(3, "uuid:c", "", "<instanceName/>"),
                named(4, "uuid:d", "", "<instanceName>Beta  </instanceName>"));
        String text = main(exporter(), inputs, layout);
        assertEquals(List.of("Alpha", "", "", "Beta"),
                java.util.stream.IntStream.rangeClosed(1, 4)
                        .mapToObj(i -> row(text, i).get("instanceName")).toList());
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void trimmingIsLimitedToInstanceNameAndDoesNotAlterStoredTextOrKeys(Layout layout) throws Exception {
        var input = List.of(named(1, "uuid:a ", "<value>  ordinary text  </value>",
                "<instanceName>  C3-A   </instanceName>"));
        var actual = row(main(exporter(), input, layout), 1);
        assertAll(
                () -> assertEquals("  C3-A", actual.get("instanceName")),
                () -> assertEquals("  ordinary text  ", actual.get("value")),
                () -> assertEquals("uuid:a ", actual.get("instanceID")),
                () -> assertEquals("uuid:a ", actual.get("KEY")));
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void nameContainingOnlyTrailingSpacesIsSerializedAsAnUnquotedEmptyCell(Layout layout) throws Exception {
        String text = main(exporter(), List.of(named(1, "uuid:a", "", "<instanceName>   </instanceName>")), layout);
        assertTrue(text.endsWith(",uuid:a,,v2,uuid:a"), text);
    }

    @ParameterizedTest @EnumSource(Layout.class)
    void csvQuotesAndEscapesTheNameAfterRemovingTrailingSpaces(Layout layout) throws Exception {
        String text = main(exporter(), List.of(named(1, "uuid:a", "",
                "<instanceName>A, &quot;B&quot;   </instanceName>")), layout);
        assertTrue(text.endsWith(",uuid:a,\"A, \"\"B\"\"\",v2,uuid:a"), text);
    }
}
