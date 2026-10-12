package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class ConstraintHintsAmendment2AcceptanceTest {
    static Stream<Arguments> publishedSubmissions() throws Exception {
        try (var files = Files.list(Path.of("samples/xforms"))) {
            var forms = files.filter(p -> p.getFileName().toString().endsWith(".xml")).sorted().toList();
            assertFalse(forms.isEmpty(), "Published acceptance forms must be present");
            return forms.stream().flatMap(path -> IntStream.rangeClosed(1, 50).mapToObj(n -> Arguments.of(path, n)));
        }
    }

    @ParameterizedTest(name = "{0}: submission {1}") @MethodSource("publishedSubmissions") @Timeout(180)
    void everyPublishedFormHasNoExhaustedConstraintOrFinalConstraintWarning(Path path, int number) throws Exception {
        LoadedForm loaded = path.getFileName().toString().equals("ctosim_coverage3.xml")
                ? FormLoader.load(path, Path.of("samples/coverage3")) : FormLoader.load(path);
        var s = new FormWalker(loaded, SimulationConfig.defaults()).walk(number);
        for (String warning : s.warnings()) {
            String lower = warning.toLowerCase(Locale.ROOT);
            // Other warnings (unsupported controls, absent attachments, etc.) are outside this criterion.
            assertFalse(lower.contains("constraint") && (lower.contains("no answer")
                    || lower.contains("satisf") || lower.contains("end of") || lower.contains("does not hold")),
                    path + " submission " + number + ": " + warning);
        }
    }
}
