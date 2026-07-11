package com.opensitesurvey.tool.ui.survey;

import com.opensitesurvey.tool.model.SurveyPoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalNeighborInterpolatorTest {

    private static SurveyPoint point(double x, double y, int rssi) {
        return new SurveyPoint(x, y, Map.of("AA:AA:AA:AA:AA:AA", rssi), Instant.now());
    }

    @Test
    void exactMatchAtSamplePointReturnsItsValue() {
        List<SurveyPoint> points = List.of(
                point(0.2, 0.2, -40), point(0.8, 0.8, -80), point(0.2, 0.8, -60), point(0.8, 0.2, -55));
        Double value = NaturalNeighborInterpolator.INSTANCE.interpolate(0.2, 0.2, points, "AA:AA:AA:AA:AA:AA");
        assertEquals(-40.0, value);
    }

    @Test
    void centerOfSymmetricSquareAveragesAllFourCorners() {
        List<SurveyPoint> points = List.of(
                point(0.0, 0.0, -40), point(1.0, 0.0, -40), point(0.0, 1.0, -40), point(1.0, 1.0, -40));
        Double value = NaturalNeighborInterpolator.INSTANCE.interpolate(0.5, 0.5, points, "AA:AA:AA:AA:AA:AA");
        assertEquals(-40.0, value, 0.5);
    }

    @Test
    void closerClusterDominatesTheEstimate() {
        List<SurveyPoint> points = List.of(
                point(0.0, 0.5, -30), point(0.05, 0.5, -30), point(0.1, 0.5, -30), point(1.0, 0.5, -90));
        Double nearCluster = NaturalNeighborInterpolator.INSTANCE.interpolate(0.15, 0.5, points, "AA:AA:AA:AA:AA:AA");
        assertTrue(nearCluster > -60, "point near the strong cluster should read closer to -30 than -90");
    }

    @Test
    void fewerThanThreePointsFallsBackToIdw() {
        List<SurveyPoint> points = List.of(point(0.0, 0.5, -40), point(1.0, 0.5, -60));
        Double natural = NaturalNeighborInterpolator.INSTANCE.interpolate(0.5, 0.5, points, "AA:AA:AA:AA:AA:AA");
        Double idw = IdwInterpolator.INSTANCE.interpolate(0.5, 0.5, points, "AA:AA:AA:AA:AA:AA");
        assertEquals(idw, natural);
    }

    @Test
    void unknownTargetReturnsNull() {
        List<SurveyPoint> points = List.of(point(0.2, 0.2, -40), point(0.8, 0.8, -80), point(0.5, 0.1, -50));
        assertNull(NaturalNeighborInterpolator.INSTANCE.interpolate(0.5, 0.5, points, "FF:FF:FF:FF:FF:FF"));
    }

    @Test
    void noPointsReturnsNull() {
        assertNull(NaturalNeighborInterpolator.INSTANCE.interpolate(0.5, 0.5, List.of(), "AA:AA:AA:AA:AA:AA"));
    }

    @Test
    void weightsAlwaysAverageWithinInputRange() {
        List<SurveyPoint> points = List.of(
                point(0.1, 0.1, -30), point(0.9, 0.1, -50), point(0.5, 0.9, -70));
        Double value = NaturalNeighborInterpolator.INSTANCE.interpolate(0.4, 0.35, points, "AA:AA:AA:AA:AA:AA");
        assertTrue(value >= -70.0 && value <= -30.0, "interpolated value must stay within the input value range");
    }
}
