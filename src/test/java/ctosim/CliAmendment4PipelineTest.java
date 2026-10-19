package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.CliTestSupport.*;
import static ctosim.CliAmendment4Support.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
class CliAmendment4PipelineTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void allExportsComeFromThePlanWithOrWithoutAnId(boolean id) throws Exception {
        Input input = input(temp);
        var settings = settings();
        if (id) settings.put("id", "household");
        Path config = configuration(temp, settings), output = temp.resolve("out");
        Result result = simulate(input, output, "-c", config.toString(), "--server", "fieldwork-test");
        success(result);
        Expected expected = expected(input, temp, settings);
        exports(input, output, expected, "fieldwork-test");
        assertEquals(8, column(output.resolve(CliAmendment4Support.WIDE), "household").size());
        assertEquals(17, csv(output.resolve("CLI fieldwork-children.csv")).size(), "header plus 16 repeat instances");
        assertEquals(expectedWarnings(expected), warnings(output));
        summary(result, output, 8, warnings(output).size());
        assertEquals(List.of("CLI fieldwork_WIDE.csv", "CLI fieldwork.csv", "CLI fieldwork-children.csv",
                "run-report.txt"), reportFiles(output));
    }

    @Test void evenDefaultsWithoutAnIdUsePlannedEnumeratorsAndSubmissions() throws Exception {
        Input input = input(temp);
        Path output = temp.resolve("out");
        success(simulate(input, output, "-n", "7"));
        var expected = expected(input, temp, (com.fasterxml.jackson.databind.node.ObjectNode)
                JSON.readTree("{\"submissions\":7}"));
        exports(input, output, expected, "example");
        assertEquals(7, column(output.resolve(CliAmendment4Support.WIDE), "username").size());
    }

    @Test void enumeratorsCycleAndTheirInterviewsHaveTravelTimeBetweenThem() throws Exception {
        Input input = input(temp);
        var settings = settings();
        Path output = temp.resolve("out");
        success(simulate(input, output, "-c", configuration(temp, settings).toString()));
        Path wide = output.resolve(CliAmendment4Support.WIDE);
        var users = column(wide, "username");
        var devices = column(wide, "deviceid");
        var format = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm:ss a", Locale.US);
        var starts = column(wide, "start").stream().map(s -> LocalDateTime.parse(s, format)).toList();
        var ends = column(wide, "end").stream().map(s -> LocalDateTime.parse(s, format)).toList();
        assertNotEquals(users.get(0), users.get(1));
        assertNotEquals(devices.get(0), devices.get(1));
        for (int i = 0; i < 8; i++) {
            assertEquals(users.get(i % 2), users.get(i));
            assertEquals(devices.get(i % 2), devices.get(i));
            assertEquals(LocalDateTime.of(2026, 3, 2, 8, 17, 23).toLocalDate(), starts.get(i).toLocalDate());
            if (i >= 2) {
                Duration gap = Duration.between(ends.get(i - 2), starts.get(i));
                assertTrue(gap.compareTo(Duration.ofMinutes(10)) >= 0, "nonoverlap and travel");
                assertTrue(gap.compareTo(Duration.ofMinutes(40)) <= 0, "short fixture stays on its first workday");
                assertEquals(0, gap.getSeconds() % 60);
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void idBeforeOrAfterFormOverridesConfigurationAndCombinesWithSeedAndCount(boolean before) throws Exception {
        Input input = input(temp);
        var settings = settings();
        settings.put("id", "missing_old_id").put("submissions", 2).put("seed", 1);
        settings.putObject("fields").putObject("respondent").putArray("texts")
                .add("ménage-é").add("家庭-一").add("بيت-٣");
        Path config = configuration(temp, settings), output = temp.resolve("out");
        String[] options = {"--id", "respondent"};
        Result result = before
                ? run(options[0], options[1], input.form().toString(), "-c", config.toString(),
                    "-n", "7", "-s", "-9223372036854775808", "-o", output.toString())
                : simulate(input, output, "-c", config.toString(), "-s", "-9223372036854775808",
                    "-n", "7", options[0], options[1]);
        success(result);
        settings.put("id", "respondent").put("submissions", 7).put("seed", Long.MIN_VALUE);
        exports(input, output, expected(input, temp, settings), "example");
        cycle(column(output.resolve(CliAmendment4Support.WIDE), "respondent"), Set.of("ménage-é", "家庭-一", "بيت-٣"));
        assertTrue(warnings(output).stream().noneMatch(w -> w.contains("missing_old_id")));
        assertTrue(report(output).contains("Seed: -9223372036854775808\nSubmissions: 7\n"));
    }

    @Test void unicodeQuestionNameIsPreservedAndIdIsNeverBlankEvenAtMissingRateOne() throws Exception {
        Input input = input(temp);
        var settings = settings();
        settings.put("missingRate", 1);
        var id = settings.putObject("fields").putObject("ménage");
        id.put("missingRate", 1).putArray("texts").add("é").add("家");
        Path output = temp.resolve("out");
        success(simulate(input, output, "--id", "ménage", "-c", configuration(temp, settings).toString()));
        cycle(column(output.resolve(CliAmendment4Support.WIDE), "ménage"), Set.of("é", "家"));
        settings.put("id", "ménage");
        exports(input, output, expected(input, temp, settings), "example");
    }

    @ParameterizedTest @ValueSource(ints = {1, 7, 8, 9})
    void finiteIdPoolIsUniqueBeforeCyclingAndWarnsOnlyWhenExhausted(int count) throws Exception {
        Input input = input(temp);
        var settings = settings();
        settings.put("submissions", count).put("id", "household");
        Path output = temp.resolve("out");
        Result result = simulate(input, output, "-c", configuration(temp, settings).toString());
        success(result);
        var ids = column(output.resolve(CliAmendment4Support.WIDE), "household");
        assertEquals(Math.min(count, 8), new HashSet<>(ids).size());
        if (count == 9) assertEquals(ids.get(0), ids.get(8));
        assertEquals(count > 8 ? 1 : 0, warnings(output).size());
        if (count > 8) {
            assertTrue(warnings(output).get(0).contains("household"));
            number(warnings(output).get(0), 8);
            number(warnings(output).get(0), count);
        }
        summary(result, output, count, warnings(output).size());
    }

    @Test void selectIdUsesChoiceValuesInsteadOfIndependentRandomAnswers() throws Exception {
        Input input = input(temp);
        Path output = temp.resolve("out");
        success(simulate(input, output, "--id", "code", "-c", configuration(temp, settings()).toString()));
        cycle(column(output.resolve(CliAmendment4Support.WIDE), "code"), Set.of("a", "b", "c"));
        assertEquals(1, warnings(output).size());
        assertTrue(warnings(output).get(0).contains("code"));
    }

    @Test void missingIdIsAPlanConfigurationWarningAfterWalkerConfigurationWarnings() throws Exception {
        Input input = input(temp);
        var settings = settings();
        settings.put("id", "absent_id").put("submissions", 2);
        var fields = settings.putObject("fields");
        fields.putObject("z_unknown").put("value", 1);
        fields.putObject("a_unknown").put("value", 2);
        fields.putObject("bad").put("value", "invalid\rinteger\nanswer");
        Path output = temp.resolve("out");
        Result result = simulate(input, output, "-c", configuration(temp, settings).toString());
        success(result);
        Expected expected = expected(input, temp, settings);
        assertEquals(3, expected.plan().configWarnings().size());
        var actual = warnings(output);
        assertTrue(actual.get(0).contains("a_unknown"));
        assertTrue(actual.get(1).contains("z_unknown"));
        assertTrue(actual.get(2).contains("absent_id"));
        assertFalse(expected.submissions().get(0).warnings().isEmpty(), "exercise ordering against submission warnings");
        assertEquals(expectedWarnings(expected), actual);
        assertFalse(report(output).contains("\r"));
        summary(result, output, 2, actual.size());
    }

    @Test void overflowAndIdWarningsAreAppendedAfterEverySubmissionWarningInPlanOrder() throws Exception {
        Input input = input(temp);
        write(input.form(), Files.readString(input.form()).replace("minimum-seconds=\"60\"", "minimum-seconds=\"32400\""));
        var settings = settings();
        settings.put("submissions", 3).put("enumerators", 1).put("fieldworkDays", 1).put("id", "respondent");
        var fields = settings.putObject("fields");
        fields.putObject("respondent").putArray("texts").add("only-id");
        fields.putObject("bad").put("value", "not-an-integer");
        Path output = temp.resolve("out");
        Result result = simulate(input, output, "-c", configuration(temp, settings).toString());
        success(result);
        Expected expected = expected(input, temp, settings);
        assertTrue(expected.submissions().stream().allMatch(s -> !s.warnings().isEmpty()));
        assertEquals(2, expected.plan().warnings().size(), "overflow then exhausted ids");
        number(expected.plan().warnings().get(0), 3);
        number(expected.plan().warnings().get(0), 1);
        assertTrue(expected.plan().warnings().get(1).contains("respondent"));
        var actual = warnings(output);
        assertEquals(expectedWarnings(expected), actual);
        assertEquals(expected.plan().warnings(), actual.subList(actual.size() - 2, actual.size()));
        summary(result, output, 3, actual.size());
    }

    @Test void idWithNoPossibleValuesStillCompletesAndAddsThePlanWarning() throws Exception {
        Input input = input(temp);
        var settings = settings();
        settings.put("id", "respondent");
        Path output = temp.resolve("out");
        Result result = simulate(input, output, "-c", configuration(temp, settings).toString());
        success(result);
        Expected expected = expected(input, temp, settings);
        assertEquals(1, expected.plan().warnings().size());
        assertTrue(expected.plan().warnings().get(0).contains("respondent"));
        assertEquals(expectedWarnings(expected), warnings(output));
        exports(input, output, expected, "example");
        summary(result, output, 8, 1);
    }

    @Test void overridingAnExistingIdWithAnAbsentNameDoesNotSilentlyRetainTheOldId() throws Exception {
        Input input = input(temp);
        var settings = settings();
        settings.put("id", "household");
        Path output = temp.resolve("out");
        success(simulate(input, output, "--id", "HOUSEHOLD", "-c", configuration(temp, settings).toString()));
        settings.put("id", "HOUSEHOLD");
        Expected expected = expected(input, temp, settings);
        exports(input, output, expected, "example");
        assertEquals(expectedWarnings(expected), warnings(output));
        assertTrue(warnings(output).get(0).contains("HOUSEHOLD"));
    }

    @Test void rerunningWithoutIdRemovesPriorPlanWarningsAndDoesNotLeakTheOldId() throws Exception {
        Input input = input(temp);
        var settings = settings();
        settings.putObject("fields").putObject("respondent").put("value", "fixed");
        Path config = configuration(temp, settings), output = temp.resolve("out");
        success(simulate(input, output, "--id", "respondent", "-c", config.toString()));
        assertEquals(1, warnings(output).size());
        success(simulate(input, output, "-c", config.toString()));
        assertTrue(warnings(output).isEmpty());
        exports(input, output, expected(input, temp, settings), "example");
    }

    @Test void sameSeedIsByteIdenticalAcrossWorkingDirectoriesAndJvmTimeZones() throws Exception {
        Map<String, byte[]> first = null;
        for (String zone : List.of("Pacific/Honolulu", "Asia/Tokyo")) {
            Path directory = temp.resolve(zone.replace('/', '-'));
            Input input = input(directory);
            Path config = configuration(directory, settings());
            Path output = directory.resolve("out");
            success(child(directory, Map.of(), zone, input.form().toString(), "-c", config.toString(),
                    "--id", "household", "-o", output.toString()));
            var bytes = snapshot(output);
            if (first == null) first = bytes; else identical(first, bytes);
        }
    }

    @Test void distinctSeedsReachThePlanAndChangeTheIdPermutation() throws Exception {
        Input input = input(temp);
        Path config = configuration(temp, settings());
        Set<List<String>> permutations = new HashSet<>();
        for (int seed = 1; seed <= 4; seed++) {
            Path output = temp.resolve("seed-" + seed);
            success(simulate(input, output, "--id", "household", "-c", config.toString(), "-s", Integer.toString(seed)));
            var settings = settings();
            settings.put("id", "household").put("seed", seed);
            exports(input, output, expected(input, temp, settings), "example");
            permutations.add(column(output.resolve(CliAmendment4Support.WIDE), "household"));
        }
        assertTrue(permutations.size() > 1);
    }

    @Test void plannedGpsAnswersStayNearTheirVisitAndTheOtherInterviewsOfThatEnumerator() throws Exception {
        Input input = input(temp);
        var settings = settings();
        Path output = temp.resolve("out");
        success(simulate(input, output, "-c", configuration(temp, settings).toString()));
        var expected = expected(input, temp, settings);
        Path wide = output.resolve(CliAmendment4Support.WIDE);
        double[][] coordinates = new double[8][2];
        for (String field : List.of("gps", "gps2")) {
            var latitudes = column(wide, field + "-Latitude");
            var longitudes = column(wide, field + "-Longitude");
            for (int i = 0; i < 8; i++) {
                double latitude = Double.parseDouble(latitudes.get(i));
                double longitude = Double.parseDouble(longitudes.get(i));
                Visit visit = expected.plan().visits().get(i);
                assertTrue(distance(latitude, longitude, visit.latitude(), visit.longitude()) <= 30.2,
                        "30 metre visit radius with tolerance for stored coordinate rounding");
                if (field.equals("gps")) coordinates[i] = new double[]{latitude, longitude};
            }
        }
        for (int i = 0; i < 8; i++) for (int j = i + 2; j < 8; j += 2)
            assertTrue(distance(coordinates[i][0], coordinates[i][1], coordinates[j][0], coordinates[j][1]) <= 3060.4,
                    "same enumerator/day: 3 km between visits plus two 30 metre answer offsets");
    }

    private static double distance(double a, double b, double c, double d) {
        double h = Math.pow(Math.sin(Math.toRadians(c - a) / 2), 2)
                + Math.cos(Math.toRadians(a)) * Math.cos(Math.toRadians(c))
                * Math.pow(Math.sin(Math.toRadians(d - b) / 2), 2);
        return 6371000 * 2 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, h))));
    }

    @Test void reportAndStdoutRetainConstraintRecordsFromPlannedSubmissions() throws Exception {
        Input input = input(temp);
        write(input.form(), Files.readString(input.form()).replace("nodeset=\"/data/bad\" type=\"int\"",
                "nodeset=\"/data/bad\" type=\"int\" constraint=\"false()\""));
        var settings = settings();
        settings.put("id", "household").put("submissions", 2);
        Path output = temp.resolve("out");
        Result result = simulate(input, output, "-c", configuration(temp, settings).toString());
        success(result);
        Expected expected = expected(input, temp, settings);
        assertTrue(expected.submissions().stream().allMatch(s -> s.unsatisfiedConstraints().size() == 1));
        exports(input, output, expected, "example");
        assertEquals(expectedWarnings(expected), warnings(output));
        assertTrue(report(output).contains("Unsatisfied constraints: 1\n  bad, row 12: false()\n"
                + "    submissions (2 of 2): 1, 2\nFiles:\n"));
        var lines = result.out().lines().toList();
        assertEquals(3, lines.size());
        assertTrue(lines.get(2).contains("bad") && lines.get(2).contains("run-report.txt"));
    }

    @Test void reportAndFinalAlertRetainFormErrorsFromPlannedSubmissions() throws Exception {
        Input input = input(temp);
        write(input.form(), Files.readString(input.form()).replace("nodeset=\"/data/respondent\" type=\"string\"",
                "nodeset=\"/data/respondent\" type=\"string\" constraint=\"regex(., '[')\""));
        var settings = settings();
        settings.put("id", "household").put("submissions", 2);
        Path output = temp.resolve("out");
        Result result = simulate(input, output, "-c", configuration(temp, settings).toString());
        success(result);
        Expected expected = expected(input, temp, settings);
        assertEquals(CliAmendment3Support.expected(expected.submissions(), Map.of("/data/respondent", 7)),
                CliAmendment3Support.section(report(output)));
        exports(input, output, expected, "example");
        assertEquals(expectedWarnings(expected), warnings(output));
        CliAmendment3Support.summary(result, 1, "respondent", 3);
    }

    @Test void converterLoaderConfigurationSubmissionAndPlanWarningsKeepTheirCategoryOrder() throws Exception {
        Path workbook = workbook(temp.resolve("source"), "warnings.xlsx", """
                {"fixture":"warnings","warnings":["converter-before-plan"]}
                """);
        var settings = settings();
        settings.put("id", "absent_id").put("submissions", 2);
        var fields = settings.putObject("fields");
        fields.putObject("unknown_override").put("value", 1);
        fields.putObject("bad").put("value", "not-an-integer");
        Path config = configuration(temp, settings), output = temp.resolve("out");
        Result result = child(temp.resolve("cwd"), converterEnvironment(), "UTC", workbook.toString(),
                "--python", python().toString(), "-c", config.toString(), "-o", output.toString());
        success(result);
        Input converted = new Input(output.resolve("form.xml"), output.resolve("form.fields.json"));
        Expected expected = expected(converted, workbook.getParent(), settings);
        var all = new java.util.ArrayList<>(List.of("converter-before-plan"));
        all.addAll(expectedWarnings(expected));
        assertEquals(all, warnings(output));
        assertEquals(2, expected.form().missingAttachments().size());
        assertEquals(2, expected.form().stubbedFunctions().size());
        assertEquals(2, expected.plan().configWarnings().size());
        assertTrue(expected.plan().configWarnings().get(1).contains("absent_id"));
        assertTrue(expected.submissions().stream().allMatch(s -> !s.warnings().isEmpty()));
    }
}
