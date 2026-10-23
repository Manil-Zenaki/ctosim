package ctosim;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static ctosim.CliIdSizingSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class CliIdSizingBoundariesTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    @ParameterizedTest
    @CsvSource({"district,1,27", "district,3,27", "district,4,4", "district,20,20",
            "village,9,27", "village,10,12", "village,28,30", "village,31,33",
            "household,27,27", "household,28,36", "household,81,81",
            "household,82,90", "household,501,504"})
    void effectiveIdLevelAndCountDetermineAttachmentSize(String id, int count, int expectedRows) throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings(id, count);
        Path output = temp.resolve("out");
        success(simulate(input, output, "--fake-attachments", "-c", config(temp, settings.toString()).toString()));
        assertEquals(expectedRows, rows(output, "places").size() - 1);
        assertEquals(3, rows(output, "categories").size() - 1, "Unrelated search file is not expanded");
        CliAmendment2Support.assertAttachmentBytes(generated(input, input.form().getParent(), settings), output);
    }

    @ParameterizedTest @CsvSource({"village,28,10", "village,31,11", "household,81,9", "household,82,10"})
    void expandedCodesUseRoundedUpBranchCountAndCorrectZeroPadding(String id, int count, int branches)
            throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings(id, count);
        Path output = temp.resolve("out");
        success(simulate(input, output, "--fake-attachments", "-c", config(temp, settings.toString()).toString()));
        List<String> parents = id.equals("village") ? List.of("1", "2", "3")
                : List.of("11", "12", "13", "21", "22", "23", "31", "32", "33");
        int width = Integer.toString(branches).length();
        var expected = new HashSet<String>();
        for (String parent : parents) IntStream.rangeClosed(1, branches).forEach(child ->
                expected.add(parent + String.format(Locale.ROOT, "%0" + width + "d", child)));
        assertEquals(expected, new HashSet<>(values(output, "places", id)));
        if (id.equals("village")) {
            var villages = values(output, "places", "village");
            var households = values(output, "places", "household");
            for (int i = 0; i < villages.size(); i++) assertEquals(villages.get(i) + "1", households.get(i));
        }
    }

    @ParameterizedTest @NullSource @ValueSource(strings = {"absent_question", "manual"})
    void noIdOrAnIneligibleIdKeepsLegacyAttachmentBytesEvenForLargeN(String id) throws Exception {
        Input input = input(temp.resolve("source"));
        var settings = settings(id, 60);
        Path output = temp.resolve("out");
        success(simulate(input, output, "--fake-attachments", "-c", config(temp, settings.toString()).toString()));
        var legacy = FakeAttachments.of(FormLoader.load(input.form(), input.form().getParent()), settings.get("seed").longValue());
        CliAmendment2Support.assertAttachmentBytes(legacy, output);
        assertEquals(27, rows(output, "places").size() - 1);
    }

    @Test void textPulldataIdStillUsesFiftyKeysRatherThanExpandingNonSearchFiles() throws Exception {
        Input input = fixture(temp.resolve("source"), "amendment4/keys", "survey.xml");
        var settings = settings("key", 51);
        Path output = temp.resolve("out");
        success(simulate(input, output, "--fake-attachments", "-c", config(temp, settings.toString()).toString()));
        assertEquals(50, rows(output, "people").size() - 1);
        var ids = column(output.resolve("CLI plan keys_WIDE.csv"), "key");
        assertEquals(50, new HashSet<>(ids).size());
        assertEquals(ids.get(0), ids.get(50));
        assertEquals(1, warnings(output).size(), "Non-search key pools still exhaust");
    }
}
