package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class FormWalkerRealFormTest {
    static Stream<Path> publishedForms() throws Exception {
        try (Stream<Path> files = Files.list(Path.of("samples", "xforms"))) {
            List<Path> forms = files.filter(p -> p.getFileName().toString().endsWith(".xml")).sorted().toList();
            assertFalse(forms.isEmpty(), "Real-form acceptance must not silently run zero cases");
            return forms.stream();
        }
    }

    @ParameterizedTest(name = "{0}: default submissions 1 through 3")
    @MethodSource("publishedForms") @Timeout(180)
    void defaultsCompleteTheFirstThreeSubmissionsOfEveryPublishedForm(Path path) throws Exception {
        LoadedForm loaded = FormLoader.load(path);
        SimulationConfig config = SimulationConfig.defaults();
        FormWalker walker = new FormWalker(loaded, config);
        for (int number = 1; number <= 3; number++) {
            Submission result = walker.walk(number);
            assertEquals(number, result.number());
            assertEquals(loaded.formId(), xml(result).getDocumentElement().getAttribute("id"));
            assertStart(result, config);
            assertFalse(result.end().isBefore(result.start()));
        }
    }
}
