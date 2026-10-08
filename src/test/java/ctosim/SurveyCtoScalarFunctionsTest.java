package ctosim;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoScalarFunctionsTest {
    static Stream<Arguments> scalarCases() {
        return Stream.of(
                Arguments.of("index()", 1.0),
                Arguments.of("linebreak()", "\n"),
                Arguments.of("lower('AbC ÉCOLE ΑΒΓ')", "abc école αβγ"),
                Arguments.of("upper('AbC école αβγ')", "ABC ÉCOLE ΑΒΓ"),
                Arguments.of("lower('')", ""), Arguments.of("upper('')", ""),
                Arguments.of("lower('123 東京 😀 !')", "123 東京 😀 !"),
                Arguments.of("upper('123 東京 😀 !')", "123 東京 😀 !"),
                Arguments.of("lower(/data/text)", "abc café 東京"),
                Arguments.of("upper(concat('ab', 'cd'))", "ABCD"),
                Arguments.of("lower(/data/choice)", "a"),
                Arguments.of("upper(/data/many)", "A B"),
                Arguments.of("lower(/data/blank)", ""),
                Arguments.of("empty('')", true), Arguments.of("empty(/data/blank)", true),
                Arguments.of("empty(/data/hidden)", true), Arguments.of("empty(0)", false),
                Arguments.of("empty(/data/zero)", false), Arguments.of("empty(false())", false),
                Arguments.of("empty(' ')", false), Arguments.of("empty('0')", false),
                Arguments.of("empty('東京')", false), Arguments.of("empty(/data/text)", false),
                Arguments.of("empty(/data/choice)", false), Arguments.of("empty(/data/many)", false),
                Arguments.of("empty(substring('abc', 5))", true),
                Arguments.of("format-number(/data/number)", "1,234.5"),
                Arguments.of("format-number(1000 + 2)", "1,002"),
                Arguments.of("format-number(/data/blank)", ""),
                Arguments.of("enumerator-name()", "simulated"),
                Arguments.of("enumerator-id()", "simulated"),
                Arguments.of("device-info()", "ctosim"),
                Arguments.of("collect-is-phone-app()", false));
    }

    @ParameterizedTest(name = "{0} returns {1} with its specified type") @MethodSource("scalarCases")
    void scalarFunctionsHaveSpecifiedValuesInEverySession(String expression, Object expected) throws Exception {
        LoadedForm loaded = expressionForm(expression);
        for (int i = 0; i < 2; i++) {
            var session = loaded.newEntryController();
            assertValue(expected, evaluate(session, expression));
            String rendered = expected instanceof Double number && number == Math.rint(number)
                    ? Long.toString(((Double) expected).longValue()) : expected.toString();
            assertEquals("[" + rendered + "]", value(child(root(session), "result")), "Initialized calculation");
        }
    }

    static Stream<Arguments> formattingCases() {
        return Stream.of(
                Arguments.of("1234567.5", "1,234,567.5"), Arguments.of("2", "2"),
                Arguments.of("0.12345", "0.123"), Arguments.of("-1234.5678", "-1,234.568"),
                Arguments.of("-0.0004", "0"), Arguments.of("0.0004", "0"),
                Arguments.of("0", "0"), Arguments.of("-0", "0"),
                Arguments.of("999", "999"), Arguments.of("1000", "1,000"),
                Arguments.of("999.9999", "1,000"), Arguments.of("-999.9999", "-1,000"),
                Arguments.of("1.2000", "1.2"), Arguments.of("1.0200", "1.02"),
                Arguments.of("0.001", "0.001"), Arguments.of("-0.001", "-0.001"),
                // Exact binary fractions avoid assumptions about decimal-to-double midpoint conversion.
                Arguments.of("1.0625", "1.062"), Arguments.of("1.1875", "1.188"),
                Arguments.of("-1.0625", "-1.062"), Arguments.of("-1.1875", "-1.188"),
                Arguments.of("1234567890123", "1,234,567,890,123"),
                Arguments.of("'1234.5'", "1,234.5"), Arguments.of("'nonsense'", ""),
                Arguments.of("''", ""), Arguments.of("number('bad')", ""));
    }

    @ParameterizedTest(name = "format-number({0}) = {1}") @MethodSource("formattingCases")
    void numbersUseGroupingHalfEvenRoundingAndNoTrailingZeros(String argument, String expected) throws Exception {
        String expression = "format-number(" + argument + ")";
        assertValue(expected, evaluate(expressionForm(expression).newEntryController(), expression));
    }

    @ResourceLock("java.util.Locale")
    @ParameterizedTest @ValueSource(strings = {"tr-TR", "de-DE", "fr-FR", "ar-EG"})
    void caseConversionAndFormattingIgnoreDefaultLocale(String languageTag) throws Exception {
        Locale original = Locale.getDefault();
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.forLanguageTag(languageTag));
            var session = expressionForm("concat(lower('I'), upper('i'), format-number(1234567.5))").newEntryController();
            assertEquals("i", evaluate(session, "lower('I')"));
            assertEquals("I", evaluate(session, "upper('i')"));
            assertEquals("1,234,567.5", evaluate(session, "format-number(1234567.5)"));
        } finally {
            Locale.setDefault(original);
            Locale.setDefault(Locale.Category.DISPLAY, display);
            Locale.setDefault(Locale.Category.FORMAT, format);
        }
    }

    static Stream<Arguments> hashes() {
        return Stream.of(Arguments.of("''", ""), Arguments.of("'abc'", "abc"),
                Arguments.of("'a', 'b'", "a\u001fb"), Arguments.of("'b', 'a'", "b\u001fa"),
                Arguments.of("'ab', 'c'", "ab\u001fc"),
                Arguments.of("'a', 'bc'", "a\u001fbc"),
                Arguments.of("'', 'a', ''", "\u001fa\u001f"),
                Arguments.of("'', ''", "\u001f"),
                Arguments.of("'café 東京 😀'", "café 東京 😀"),
                Arguments.of("'é'", "é"), Arguments.of("'e\u0301'", "e\u0301"),
                Arguments.of("42, true(), false(), /data/blank", "42\u001ftrue\u001ffalse\u001f"),
                Arguments.of("/data/text, /data/number, /data/choice, /data/many",
                        "AbC café 東京\u001f1234.5\u001fa\u001fa b"),
                Arguments.of("concat('ab', 'c'), upper('d')", "abc\u001fD"));
    }

    @ParameterizedTest @MethodSource("hashes")
    void hashIsLowercaseSha256OfUtf8ValuesSeparatedByUnitSeparator(String arguments, String joined) throws Exception {
        String call = "hash(" + arguments + ")";
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(joined.getBytes(StandardCharsets.UTF_8)));
        var loaded = expressionForm(call);
        assertValue(expected, evaluate(loaded.newEntryController(), call));
        assertValue(expected, evaluate(loaded.newEntryController(), call));
    }

    @Test void hashAcceptsManyArgumentsAndIncludesEveryArgument() throws Exception {
        String[] items = java.util.stream.IntStream.range(0, 64).mapToObj(i -> "v" + i).toArray(String[]::new);
        String call = "hash(" + String.join(",", java.util.Arrays.stream(items).map(s -> "'" + s + "'").toList()) + ")";
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(String.join("\u001f", items).getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, evaluate(expressionForm(call).newEntryController(), call));
    }
}
