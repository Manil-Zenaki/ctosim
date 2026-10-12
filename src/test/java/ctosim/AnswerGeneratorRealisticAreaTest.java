package ctosim;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.AnswerGeneratorRealisticSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AnswerGeneratorRealisticAreaTest {
    private static double[][] points(String answer, QuestionKind kind) {
        assertNotNull(answer);
        String[] parts = answer.split("; ", -1);
        assertEquals(kind == QuestionKind.GEOPOINT ? 1 : kind == QuestionKind.GEOTRACE ? 4 : 5, parts.length);
        double[][] points = new double[parts.length][2];
        for (int i = 0; i < parts.length; i++) {
            assertTrue(parts[i].matches("-?[0-9]+\\.[0-9]{6} -?[0-9]+\\.[0-9]{6} 0 5"),
                    "Six decimal coordinates, altitude 0 and accuracy 5: " + parts[i]);
            String[] components = parts[i].split(" ");
            points[i][0] = Double.parseDouble(components[0]);
            points[i][1] = Double.parseDouble(components[1]);
        }
        if (kind == QuestionKind.GEOSHAPE) assertEquals(parts[0], parts[4], "Shape closes with exact first text");
        return points;
    }

    static Stream<Arguments> areaCases() {
        return Stream.of(QuestionKind.GEOPOINT, QuestionKind.GEOTRACE, QuestionKind.GEOSHAPE)
                .flatMap(kind -> Stream.of(
                        Arguments.of(kind, "", null, -1.0, 1.0, -1.0, 1.0),
                        Arguments.of(kind, area(-4, -2, 35, 39), null, -4.0, -2.0, 35.0, 39.0),
                        Arguments.of(kind, area(-4, -2, 35, 39), area(50, 54, -80, -78), 50.0, 54.0, -80.0, -78.0),
                        Arguments.of(kind, "", area(8, 12, 38, 40), 8.0, 12.0, 38.0, 40.0)));
    }

    @ParameterizedTest
    @MethodSource("areaCases")
    void firstPointUsesFieldThenGlobalThenDefaultAreaAndUniformCoordinates(
            QuestionKind kind, String configMembers, String overrideMembers,
            double south, double north, double west, double east) throws Exception {
        int[] latitudeBins = new int[4];
        int[] longitudeBins = new int[4];
        for (String answer : draws(config(configMembers), question(kind),
                overrideMembers == null ? null : field(overrideMembers), DRAWS)) {
            double[] first = points(answer, kind)[0];
            assertTrue(first[0] >= south && first[0] <= north, "Latitude inside selected area");
            assertTrue(first[1] >= west && first[1] <= east, "Longitude inside selected area");
            latitudeBins[Math.min(3, (int) (4 * (first[0] - south) / (north - south)))]++;
            longitudeBins[Math.min(3, (int) (4 * (first[1] - west) / (east - west)))]++;
        }
        for (int count : latitudeBins) rate(count, DRAWS, 0.25);
        for (int count : longitudeBins) rate(count, DRAWS, 0.25);
    }

    static Stream<Arguments> singletonAreas() {
        return Stream.of(QuestionKind.GEOPOINT, QuestionKind.GEOTRACE, QuestionKind.GEOSHAPE)
                .flatMap(kind -> Stream.of(Arguments.of(kind, -90.0, -180.0),
                        Arguments.of(kind, 90.0, 180.0), Arguments.of(kind, 8.125, 38.75),
                        Arguments.of(kind, 0.0, 0.0)));
    }

    @ParameterizedTest
    @MethodSource("singletonAreas")
    void singletonAreaAllowsGeographicBoundariesAndProducesExactFirstPoint(
            QuestionKind kind, double latitude, double longitude) throws Exception {
        for (String answer : draws(config(area(latitude, latitude, longitude, longitude)), question(kind), null, 10)) {
            double[] first = points(answer, kind)[0];
            assertEquals(latitude, first[0], 0.0);
            assertEquals(longitude, first[1], 0.0);
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"GEOPOINT", "GEOTRACE", "GEOSHAPE"})
    void oneCoordinateCanBeFixedWhileTheOtherStillVaries(QuestionKind kind) throws Exception {
        List<String> values = draws(config(area(8.125, 8.125, 38, 40)), question(kind), null, 1000);
        int lowerHalf = 0;
        for (String answer : values) {
            double[] first = points(answer, kind)[0];
            assertEquals(8.125, first[0], 0.0);
            assertTrue(first[1] >= 38 && first[1] <= 40);
            if (first[1] < 39) lowerHalf++;
        }
        rate(lowerHalf, values.size(), 0.5);
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"GEOTRACE", "GEOSHAPE"})
    void laterPointsTakeUniformStepsFromThePreviousPointAndCanLeaveTheArea(QuestionKind kind) throws Exception {
        List<String> values = draws(config(area(8, 8, 38, 38)), question(kind), null, 4000);
        int[][] positive = new int[3][2];
        int[][] outerHalf = new int[3][2];
        boolean leftArea = false;
        boolean beyondOneStepFromOrigin = false;
        for (String answer : values) {
            double[][] p = points(answer, kind);
            for (int i = 1; i <= 3; i++) {
                for (int axis = 0; axis < 2; axis++) {
                    double step = p[i][axis] - p[i - 1][axis];
                    assertTrue(Math.abs(step) <= 0.0010011, "Each step is bounded, allowing six-place rounding");
                    if (step > 0) positive[i - 1][axis]++;
                    if (Math.abs(step) > 0.0005) outerHalf[i - 1][axis]++;
                    leftArea |= p[i][axis] != p[0][axis];
                    beyondOneStepFromOrigin |= Math.abs(p[i][axis] - p[0][axis]) > 0.00101;
                }
            }
        }
        assertTrue(leftArea, "Later points are not clamped to the singleton area");
        assertTrue(beyondOneStepFromOrigin, "Steps accumulate from the previous point");
        for (int i = 0; i < 3; i++) for (int axis = 0; axis < 2; axis++) {
            rate(positive[i][axis], values.size(), 0.5);
            rate(outerHalf[i][axis], values.size(), 0.5);
        }
    }
}
