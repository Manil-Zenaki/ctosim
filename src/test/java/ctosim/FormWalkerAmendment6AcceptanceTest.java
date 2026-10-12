package ctosim;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.FormWalkerAmendment6SearchTest.nonblank;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class FormWalkerAmendment6AcceptanceTest {
    static Stream<Arguments> publishedSubmissions() throws Exception {
        try (Stream<Path> paths = Files.list(Path.of("samples/xforms"))) {
            var forms = paths.filter(p -> p.toString().endsWith(".xml")).sorted().toList();
            assertFalse(forms.isEmpty(), "Acceptance must discover published forms");
            return forms.stream().flatMap(p -> IntStream.rangeClosed(1, 50).mapToObj(n -> Arguments.of(p, n)));
        }
    }

    @ParameterizedTest(name = "{0}: submission {1}") @MethodSource("publishedSubmissions") @Timeout(180)
    void allFiftyPublishedSubmissionsHaveNoExhaustedConstraintWarnings(Path path, int number) throws Exception {
        LoadedForm form = path.getFileName().toString().equals("ctosim_coverage3.xml")
                ? FormLoader.load(path, Path.of("samples/coverage3")) : FormLoader.load(path);
        Submission result = new FormWalker(form, SimulationConfig.defaults()).walk(number);
        xml(result); // Also requires a well-formed serialized submission.
        for (String warning : result.warnings()) {
            String lower = warning.toLowerCase(Locale.ROOT);
            // All walker constraint warnings signal exhaustion, at navigation or at final repair.
            assertFalse(lower.contains("constraint"), warning);
        }
    }

    @ParameterizedTest @ValueSource(ints = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20}) @Timeout(120)
    void sixExplicitConstraintShapesReceiveValidAnswersInEverySubmission(int number) throws Exception {
        Submission result = new FormWalker(loadXml(resource("amendment6/acceptance.xml")),
                SimulationConfig.defaults()).walk(number);
        for (String path : new String[]{"integer_answer", "decimal_answer", "text_answer", "date_answer",
                "time_answer", "multiple_answer"}) nonblank(result, path);
        assertEquals(3, Integer.parseInt(value(result, "integer_answer")) % 7);
        BigDecimal decimal = new BigDecimal(value(result, "decimal_answer"));
        assertTrue(decimal.compareTo(BigDecimal.valueOf(1000)) > 0);
        assertEquals(0, decimal.multiply(BigDecimal.valueOf(4)).remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO));
        assertTrue(value(result, "text_answer").contains("@"));
        assertEquals(13, java.time.LocalDate.parse(value(result, "date_answer")).getDayOfMonth());
        assertTrue(value(result, "time_answer").startsWith("07:"));
        var tokens = Arrays.asList(value(result, "multiple_answer").split(" "));
        assertEquals(3, tokens.size());
        assertEquals(3, new HashSet<>(tokens).size());
        assertTrue(Set.of("b", "c", "d", "e", "f").containsAll(tokens));
        assertEquals(6, result.answered());
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
        assertElapsed(result, 6);
    }
}
