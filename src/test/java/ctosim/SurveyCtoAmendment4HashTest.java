package ctosim;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.javarosa.xpath.XPathParseTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoAmendment4HashTest {
    static Stream<Arguments> vectors() {
        return Stream.of(
                Arguments.of("'abc'", "abc"), Arguments.of("''", ""),
                Arguments.of("'a', 'bc'", "abc"), Arguments.of("'ab', 'c'", "abc"),
                Arguments.of("'', 'abc', ''", "abc"), Arguments.of("'', ''", ""),
                Arguments.of("' a ', 'b '", " a b "),
                Arguments.of("'caf\u00e9', '\u6771\u4eac', '\ud83c\udf0d'", "caf\u00e9\u6771\u4eac\ud83c\udf0d"),
                Arguments.of("'e\u0301'", "e\u0301"), Arguments.of("'\u00e9'", "\u00e9"),
                Arguments.of("42, 2.5, -7, true(), false(), /data/blank", "422.5-7truefalse"),
                Arguments.of("/data/zero, /data/number, /data/choice", "01234.5a"),
                Arguments.of("concat('a', 'b'), upper('c')", "abC"),
                Arguments.of("'a', linebreak(), 'b'", "a\nb"));
    }

    @ParameterizedTest @MethodSource("vectors")
    void hashesUtf8StringValuesWithMd5AndNoArgumentSeparator(String args, String input) throws Exception {
        String call = "hash(" + args + ")";
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8)));
        var session = expressionForm(call).newEntryController();
        assertValue(expected, evaluate(session, call));
        assertEquals("[" + expected + "]", value(child(root(session), "result")));
    }

    @Test void publishedAbcVectorHasExactly32LowercaseHexDigits() throws Exception {
        assertValue("900150983cd24fb0d6963f7d28e17f72", evaluate(expressionForm("hash('abc')").newEntryController(), "hash('abc')"));
    }

    @Test void arbitraryArgumentCountsIncludeEveryArgument() throws Exception {
        String args = String.join(", ", java.util.Collections.nCopies(64, "'x'"));
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                .digest("x".repeat(64).getBytes(StandardCharsets.UTF_8)));
        assertValue(expected, evaluate(expressionForm("hash(" + args + ")").newEntryController(), "hash(" + args + ")"));
    }

    @Test void zeroArgumentsStillFailDuringEvaluation() throws Exception {
        var session = expressionForm("''").newEntryController();
        var expression = XPathParseTool.parseXPath("hash()");
        var form = session.getModel().getForm();
        assertThrows(Exception.class, () -> expression.eval(form.getMainInstance(), form.getEvaluationContext()));
    }
}
