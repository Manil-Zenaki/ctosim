package ctosim;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static ctosim.AnswerGeneratorTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AnswerGeneratorLocationTest {
    // Each printed coordinate can differ from the unrounded value by 0.5e-6.
    private static final double SHIFT_TOLERANCE = 1.000001e-6;

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"GEOPOINT", "GEOTRACE", "GEOSHAPE"})
    void locationsUseExactPointCountSeparatorsPrecisionAltitudeAndAccuracy(QuestionKind kind) throws Exception {
        for (String answer : draws(generator(""), question(kind), 500)) {
            String[] points = answer.split("; ", -1);
            assertEquals(kind == QuestionKind.GEOPOINT ? 1 : kind == QuestionKind.GEOTRACE ? 4 : 5, points.length);
            for (String point : points) parsePoint(point);
            if (kind == QuestionKind.GEOSHAPE) assertEquals(points[0], points[4], "Closure must copy the first point");
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"GEOPOINT", "GEOTRACE", "GEOSHAPE"})
    void initialLatitudeAndLongitudeAreUniformInMinusOneThroughOne(QuestionKind kind) throws Exception {
        int[] latitude = new int[4];
        int[] longitude = new int[4];
        int[][] joint = new int[4][4];
        for (String answer : draws(generator(""), question(kind), DRAWS)) {
            double[] first = parsePoint(answer.split("; ", -1)[0]);
            int lat = coordinateBin(first[0]);
            int lon = coordinateBin(first[1]);
            latitude[lat]++;
            longitude[lon]++;
            joint[lat][lon]++;
        }
        for (int i = 0; i < 4; i++) {
            assertRate(latitude[i], DRAWS, 0.25, "latitude bin " + i);
            assertRate(longitude[i], DRAWS, 0.25, "longitude bin " + i);
            for (int j = 0; j < 4; j++) assertRate(joint[i][j], DRAWS, 1.0 / 16, "coordinate pair " + i + "/" + j);
        }
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"GEOTRACE", "GEOSHAPE"})
    void eachNewPointIsShiftedFromItsPredecessorByAtMostOneThousandth(QuestionKind kind) throws Exception {
        boolean movedAwayFromInitialPointByMoreThanOneStep = false;
        for (String answer : draws(generator(""), question(kind), 2000)) {
            String[] points = answer.split("; ", -1);
            double[] first = parsePoint(points[0]);
            double[] previous = first;
            for (int i = 1; i < 4; i++) {
                double[] next = parsePoint(points[i]);
                for (int axis = 0; axis < 2; axis++) {
                    assertTrue(Math.abs(next[axis] - previous[axis]) <= 0.001 + SHIFT_TOLERANCE, answer);
                    if (Math.abs(next[axis] - first[axis]) > 0.001 + SHIFT_TOLERANCE) {
                        movedAwayFromInitialPointByMoreThanOneStep = true;
                    }
                }
                previous = next;
            }
        }
        assertTrue(movedAwayFromInitialPointByMoreThanOneStep, "Shifts accumulate relative to each previous point");
    }

    @ParameterizedTest
    @EnumSource(value = QuestionKind.class, names = {"GEOTRACE", "GEOSHAPE"})
    void eachStepAndEachAxisUsesUniformShifts(QuestionKind kind) throws Exception {
        int[][][] bins = new int[3][2][4];
        int[][] totals = new int[3][2];
        for (String answer : draws(generator(""), question(kind), DRAWS)) {
            String[] points = answer.split("; ", -1);
            double[] previous = parsePoint(points[0]);
            for (int step = 0; step < 3; step++) {
                double[] next = parsePoint(points[step + 1]);
                for (int axis = 0; axis < 2; axis++) {
                    double shift = next[axis] - previous[axis];
                    assertTrue(Math.abs(shift) <= 0.001 + SHIFT_TOLERANCE, answer);
                    // Ignore tiny neighborhoods of all bin boundaries so printed
                    // rounding cannot change which uniform bin an observation uses.
                    double distance = Math.min(Math.abs(shift), Math.min(Math.abs(shift - 0.0005),
                            Math.min(Math.abs(shift + 0.0005), Math.abs(Math.abs(shift) - 0.001))));
                    if (distance <= 0.000002) continue;
                    int bin = (int) ((shift + 0.001) / 0.0005);
                    bins[step][axis][bin]++;
                    totals[step][axis]++;
                }
                previous = next;
            }
        }
        for (int step = 0; step < 3; step++) {
            for (int axis = 0; axis < 2; axis++) {
                assertTrue(totals[step][axis] > DRAWS * 0.9, "Most shifts should be away from bin boundaries");
                for (int bin = 0; bin < 4; bin++) {
                    assertRate(bins[step][axis][bin], totals[step][axis], 0.25, "step/axis/bin " + step + "/" + axis + "/" + bin);
                }
            }
        }
    }

    private static int coordinateBin(double value) {
        assertTrue(value >= -1 && value <= 1, "Initial coordinate outside [-1,1]: " + value);
        return Math.min(3, (int) ((value + 1) * 2));
    }

    private static double[] parsePoint(String point) {
        assertTrue(point.matches("-?[0-9]+\\.[0-9]{6} -?[0-9]+\\.[0-9]{6} 0 5"), point);
        String[] parts = point.split(" ", -1);
        return new double[] {Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
    }
}
