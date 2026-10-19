package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class CliAmendment4UsageTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    private void invalid(String... idArgs) throws Exception {
        Path output = temp.resolve("uncreated/output");
        Path sentinel = write(temp.resolve("keep.txt"), "untouched");
        var args = new ArrayList<>(List.of(temp.resolve("missing.xml").toString(),
                "--config", temp.resolve("missing.json").toString(), "--output", output.toString()));
        args.addAll(List.of(idArgs));
        failure(run(args.toArray(String[]::new)), 2, "id");
        assertFalse(Files.exists(output.getParent()), "usage validation precedes output creation");
        assertEquals("untouched", Files.readString(sentinel));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\r", "\n", "\f", " household", "household ",
            "\thousehold", "household\t", "\rhousehold", "household\r", "\nhousehold", "household\n",
            "\fhousehold", "household\f"})
    void emptyOrEdgeWhitespaceIdIsRejectedBeforeAnyInputOrOutput(String id) throws Exception {
        invalid("--id", id);
    }

    @Test void idWithoutAValueIsAUsageErrorBeforeIo() throws Exception {
        invalid("--id");
    }

    @ParameterizedTest @ValueSource(strings = {"household", "respondent"})
    void aSecondIdIsAnErrorEvenWhenItsValueIsTheSame(String second) throws Exception {
        invalid("--id", "household", "--id", second);
    }

    @Test void duplicateIdsOnOppositeSidesOfFormAreStillAUsageError() {
        Path output = temp.resolve("out");
        failure(run("--id", "household", temp.resolve("missing.xml").toString(),
                "--output", output.toString(), "--id", "respondent"), 2, "id");
        assertFalse(Files.exists(output));
    }

    @ParameterizedTest @ValueSource(strings = {"-h", "--help"})
    void standaloneHelpStillOverridesInvalidAndDuplicateIds(String help) {
        Result result = run(help, "--id", "", "--id", " padded ", "--id");
        success(result);
        usage(result.out());
    }

    @ParameterizedTest @ValueSource(strings = {"household", "ménage", "内部 名", "path/with:punctuation", "id"})
    void nonemptyUnpaddedNamesPassUsageValidationWithoutAnExtraNameGrammar(String id) {
        Path config = temp.resolve("missing-settings.json");
        Result result = run(temp.resolve("missing.xml").toString(), "--id", id, "-c", config.toString());
        failureAt(result, config.getFileName().toString(), "config");
    }

    @Test void invalidIdDoesNotReplaceExistingOutputFiles() throws Exception {
        Path output = Files.createDirectories(temp.resolve("out"));
        write(output.resolve("run-report.txt"), "previous report");
        write(output.resolve("data.csv"), "previous data");
        var before = snapshot(output);
        failure(run(temp.resolve("missing.xml").toString(), "--id", "x ", "-o", output.toString()), 2, "id");
        identical(before, snapshot(output));
    }
}
