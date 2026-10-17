package ctosim;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class FakeAttachmentsValuesTest extends FakeAttachmentsTestSupport {
    @ParameterizedTest @ValueSource(strings = {"phone", "tel", "telephone", "mobile", "cell", "contactPhone", "PHONE_NUMBER", "hh-phone2", "phone_lat_name_age_area_count"})
    void phoneWordsProduceExactlyTenDigitsStartingWithZero(String column) throws Exception {
        for (String value : table(FakeAttachments.of(pulls("string", column), SEED), "people").column(column)) {
            assertTrue(value.matches("0[0-9]{9}"), value);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"lat", "latitude", "lon", "lng", "long", "longitude", "gpsLatitude", "GPS_LONGITUDE", "latitude_name_age", "longitude_name_age"})
    void coordinateWordsProduceSixDecimalPlacesWithinMinusOneAndOne(String column) throws Exception {
        for (String value : table(FakeAttachments.of(pulls("string", column), SEED), "people").column(column)) {
            assertTrue(value.matches("-?[01]\\.[0-9]{6}"), value);
            assertTrue(Double.parseDouble(value) >= -1 && Double.parseDouble(value) <= 1, value);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"name", "nom", "nm", "prenom", "firstname", "surname", "lastname", "fullname", "headName", "head-name2", "person3nom", "NAME", "name_age_area_total"})
    void everyNameAliasProducesAFullNameIncludingFirstAndLastName(String column) throws Exception {
        for (String value : table(FakeAttachments.of(pulls("string", column), SEED), "people").column(column)) {
            assertEquals(value.strip(), value);
            assertTrue(value.matches("\\S.* \\S.*"), "Expected <first> <last>, got " + value);
        }
    }

    static Stream<Arguments> numericColumns() {
        return Stream.of(
                Arguments.of("age", 1, 80, 0), Arguments.of("personAge", 1, 80, 0),
                Arguments.of("age_area_count", 1, 80, 0),
                Arguments.of("area", .25, 5., 2), Arguments.of("surface", .25, 5., 2),
                Arguments.of("superficie", .25, 5., 2), Arguments.of("plotArea", .25, 5., 2),
                Arguments.of("area_total", .25, 5., 2),
                Arguments.of("nb", 1, 4, 0), Arguments.of("num", 1, 4, 0),
                Arguments.of("nbr", 1, 4, 0), Arguments.of("number", 1, 4, 0),
                Arguments.of("count", 1, 4, 0), Arguments.of("total", 1, 4, 0),
                Arguments.of("householdCount", 1, 4, 0), Arguments.of("plot2total", 1, 4, 0));
    }

    @ParameterizedTest @MethodSource("numericColumns")
    void numericWordFamiliesRespectTheirRangesAndPrecision(String column, double min, double max, int decimals) throws Exception {
        List<String> values = table(FakeAttachments.of(pulls("string", column), SEED), "people").column(column);
        for (String value : values) {
            assertTrue(value.matches(decimals == 0 ? "[0-9]+" : "[0-9]+\\.[0-9]{2}"), value);
            assertTrue(Double.parseDouble(value) >= min && Double.parseDouble(value) <= max, column + "=" + value);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"status", "telephonebook", "rename", "village", "surfacewater", "countdown", "platform", "longitudeish", "école"})
    void unrecognizedWordsAndSubstringMatchesUseOnlyOneTwoOrThree(String column) throws Exception {
        for (String value : table(FakeAttachments.of(pulls("string", column), SEED), "people").column(column)) {
            assertTrue(Set.of("1", "2", "3").contains(value), column + "=" + value);
        }
    }

    @Test void coordinateDrawsHaveBroadUniformCoverageWithAFixedSeedSchedule() throws Exception {
        var form = pulls("string", "lat", "lon");
        double[] sum = new double[2];
        int[][] bins = new int[2][4];
        int samples = 0;
        for (int seed = 1000; seed < 1040; seed++) {
            Table table = table(FakeAttachments.of(form, seed), "people");
            for (int axis = 0; axis < 2; axis++) {
                for (String text : table.column(axis == 0 ? "lat" : "lon")) {
                    double value = Double.parseDouble(text);
                    assertTrue(value >= -1 && value <= 1);
                    sum[axis] += value;
                    bins[axis][Math.min(3, (int) ((value + 1) * 2))]++;
                }
            }
            samples += table.rows().size();
        }
        assertEquals(2000, samples);
        for (int axis = 0; axis < 2; axis++) {
            assertEquals(0, sum[axis] / samples, .08, "Coordinate mean should be near zero");
            for (int count : bins[axis]) assertTrue(count >= 350 && count <= 650, "Broad uniform quartile tolerance: " + count);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"/data/v = 'yes'", "/data/v != 'yes'", "'yes' = /data/v", "'yes' != /data/v"})
    void comparisonToASingleStringLiteralProvidesThatLiteral(String comparison) throws Exception {
        var form = form("<key/><v/><q/>", bind("key", "string", "") + calc("v", pull("people", "status", "id", "/data/key"))
                + bind("q", "string", "relevant=\"" + attr(comparison) + "\""), input("key") + input("q"));
        assertEquals(Set.of("yes"), Set.copyOf(table(FakeAttachments.of(form, SEED), "people").column("status")));
    }

    @Test void comparisonsCollectNumericAndStringLiteralsAcrossExpressions() throws Exception {
        var form = form("<key/><v/><q/><r/>", bind("key", "string", "") + calc("v", pull("people", "status", "id", "/data/key"))
                + bind("q", "string", "relevant=\"/data/v = 7 or /data/v != 'yes'\"")
                + bind("r", "string", "constraint=\"/data/v = 'no'\""), input("key") + input("q") + input("r"));
        List<String> values = table(FakeAttachments.of(form, SEED), "people").column("status");
        assertTrue(values.stream().allMatch(Set.of("7", "yes", "no")::contains));
    }

    @ParameterizedTest @ValueSource(strings = {"/data/v > 9", "/data/v < 9", "/data/v >= 9", "/data/v <= 9", "contains(/data/v, 'yes')", "/data/v = /data/key"})
    void otherComparisonOperatorsAndNonliteralOperandsDoNotSupplyComparedValues(String comparison) throws Exception {
        var form = form("<key/><v/><q/>", bind("key", "string", "") + calc("v", pull("people", "status", "id", "/data/key"))
                + bind("q", "string", "relevant=\"" + attr(comparison) + "\""), input("key") + input("q"));
        assertTrue(table(FakeAttachments.of(form, SEED), "people").column("status").stream().allMatch(Set.of("1", "2", "3")::contains));
    }

    @Test void wrappedPulldataCalculateDoesNotQualifyForComparedValueInference() throws Exception {
        var form = form("<key/><v/><q/>", bind("key", "string", "")
                + calc("v", "concat(" + pull("people", "status", "id", "/data/key") + ",'')")
                + bind("q", "string", "relevant=\"/data/v = 'yes'\""), input("key") + input("q"));
        assertTrue(table(FakeAttachments.of(form, SEED), "people").column("status").stream().allMatch(Set.of("1", "2", "3")::contains));
    }

    @Test void comparisonLiteralsAreScopedToTheFileAndColumnTheyDescribe() throws Exception {
        var form = form("<key/><a/><b/><c/><q/>", bind("key", "string", "")
                + calc("a", pull("first", "status", "id", "/data/key"))
                + calc("b", pull("first", "other", "id", "/data/key"))
                + calc("c", pull("second", "status", "id", "/data/key"))
                + bind("q", "string", "relevant=\"/data/a = 'yes'\""), input("key") + input("q"));
        var fake = FakeAttachments.of(form, SEED);
        assertEquals(Set.of("yes"), Set.copyOf(table(fake, "first").column("status")));
        assertTrue(table(fake, "first").column("other").stream().allMatch(Set.of("1", "2", "3")::contains));
        assertTrue(table(fake, "second").column("status").stream().allMatch(Set.of("1", "2", "3")::contains));
    }

    @Test void recognizedValueNameTakesPriorityOverComparedLiteral() throws Exception {
        var form = form("<key/><v/><q/>", bind("key", "string", "") + calc("v", pull("people", "phone", "id", "/data/key"))
                + bind("q", "string", "relevant=\"/data/v = 'yes'\""), input("key") + input("q"));
        assertTrue(table(FakeAttachments.of(form, SEED), "people").column("phone").stream().allMatch(v -> v.matches("0[0-9]{9}")));
    }

    @Test void keyColumnTakesPriorityOverValueNameAndComparedLiteral() throws Exception {
        var form = form("<key/><v/><q/>", bind("key", "string", "") + calc("v", pull("people", "phone", "phone", "/data/key"))
                + bind("q", "string", "relevant=\"/data/v = 'yes'\""), input("key") + input("q"));
        var fake = FakeAttachments.of(form, SEED);
        Table table = table(fake, "people");
        assertEquals(List.of("phone"), table.header());
        assertEquals("K0001", table.column("phone").get(0));
        assertEquals("K0050", table.column("phone").get(49));
    }

    @Test void labelColumnTakesPriorityOverKeyAndRecognizedValueName() throws Exception {
        var form = form("<key/><q/><v/>", bind("key", "string", "") + bind("q", "string", "")
                + calc("v", pull("places", "phone", "phone", "/data/key")), input("key")
                + select("q", "search('places')", "code", "phone"));
        var fake = FakeAttachments.of(form, SEED);
        Table table = table(fake, "places");
        for (int i = 0; i < 3; i++) assertEquals("phone " + table.column("code").get(i), table.column("phone").get(i));
        assertEquals(table.column("phone"), fake.keyAnswers().get("key"));
    }

    @Test void mappedMatchAndFilterColumnsTakePriorityOverLabelsKeysAndValueNames() throws Exception {
        var form = form("<key/><parent/><child/><v/><w/>", bind("key", "string", "")
                + bind("parent", "string", "") + bind("child", "string", "")
                + calc("v", pull("places", "phone", "phone", "/data/key"))
                + calc("w", pull("places", "age", "id", "/data/key")), input("key")
                + select("parent", "search('places')", "parent_code", "PARENT")
                + select("child", "search('places','matches','phone',/data/parent,'age',/data/parent)", "child_code", "phone"));
        Table table = table(FakeAttachments.of(form, SEED), "places");
        assertEquals(table.column("parent_code"), table.column("phone"));
        assertEquals(table.column("parent_code"), table.column("age"));
    }

    @Test void unmappedMatchColumnFallsToRuleSixRatherThanLabelOrNameRules() throws Exception {
        var form = form("<q/><key/><v/>", bind("key", "string", "") + bind("q", "string", "")
                + calc("v", pull("places", "phone", "id", "/data/key")), input("key")
                + select("q", "search('places','matches','phone','literal')", "code", "phone"));
        assertTrue(table(FakeAttachments.of(form, SEED), "places").column("phone").stream().allMatch(Set.of("1", "2", "3")::contains));
    }

    @Test void levelColumnTakesPriorityOverAllOtherRoles() throws Exception {
        var form = form("<key/><parent/><child/><v/>", bind("key", "string", "")
                + bind("parent", "string", "") + bind("child", "string", "")
                + calc("v", pull("places", "phone", "phone", "/data/key")), input("key")
                + select("parent", "search('places')", "phone", "phone")
                + select("child", "search('places','matches','phone',/data/child)", "child_code", "CHILD"));
        Table table = table(FakeAttachments.of(form, SEED), "places");
        assertEquals(Set.of("1", "2", "3"), new HashSet<>(table.column("phone")));
        for (int i = 0; i < 9; i++) assertEquals(table.column("child_code").get(i).substring(0, 1), table.column("phone").get(i));
    }
}
