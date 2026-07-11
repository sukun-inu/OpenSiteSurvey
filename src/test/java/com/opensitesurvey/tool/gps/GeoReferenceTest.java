package com.opensitesurvey.tool.gps;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoReferenceTest {

    private static GeoReference.CalibrationPoint point(double lat, double lon, double x, double y) {
        return new GeoReference.CalibrationPoint(lat, lon, x, y);
    }

    @Test
    void twoPointSimilarityTransformRecoversScaleRotationAndTranslation() {
        // True transform: xNorm = -2*lon + 0.1, yNorm = 2*lat + 0.2 (a 90deg-rotated, 2x-scaled
        // similarity transform) - a genuine rotation+scale, not just a translation, so the
        // complex-number fitting math is meaningfully exercised.
        GeoReference.CalibrationPoint p1 = point(0, 0, 0.1, 0.2);
        GeoReference.CalibrationPoint p2 = point(1, 2, -3.9, 2.2);
        GeoReference ref = GeoReference.fit(List.of(p1, p2));

        GeoReference.ImagePoint projected = ref.project(3, 5);
        assertEquals(-9.9, projected.xNorm(), 1e-9);
        assertEquals(6.2, projected.yNorm(), 1e-9);
    }

    @Test
    void twoIdenticalCalibrationPointsAreDegenerate() {
        GeoReference.CalibrationPoint p1 = point(10, 20, 0.5, 0.5);
        GeoReference.CalibrationPoint p2 = point(10, 20, 0.5, 0.5);
        assertNull(GeoReference.fit(List.of(p1, p2)));
    }

    @Test
    void fewerThanTwoPointsReturnsNull() {
        assertNull(GeoReference.fit(List.of()));
        assertNull(GeoReference.fit(List.of(point(1, 2, 0.1, 0.2))));
        assertNull(GeoReference.fit(null));
    }

    @Test
    void threeOrMorePointsFitsAGeneralAffineTransform() {
        // True transform includes shear/anisotropic scale - not expressible as a pure
        // similarity, so this specifically exercises the >=3-point least-squares path.
        double a = 1.5, b = 0.3, c = 0.05, d = -0.2, e = 2.0, f = 0.1;
        List<GeoReference.CalibrationPoint> points = List.of(
                point(0, 0, c, f),
                point(1, 0, a + c, d + f),
                point(0, 1, b + c, e + f),
                point(2, 3, 2 * a + 3 * b + c, 2 * d + 3 * e + f));

        GeoReference ref = GeoReference.fit(points);

        GeoReference.ImagePoint projected = ref.project(4, -1);
        assertEquals(4 * a - b + c, projected.xNorm(), 1e-6);
        assertEquals(4 * d - e + f, projected.yNorm(), 1e-6);
    }

    @Test
    void metersPerNormUnitReflectsRealWorldScale() {
        // One degree of latitude is approximately 111.32km - a widely known constant, used here as
        // a sanity bound rather than requiring bit-exact agreement with any one specific formula.
        GeoReference.CalibrationPoint p1 = point(0, 0, 0, 0);
        GeoReference.CalibrationPoint p2 = point(1, 0, 1, 0); // 1 degree of latitude -> 1 normalized unit
        GeoReference ref = GeoReference.fit(List.of(p1, p2));
        assertTrue(ref.metersPerNormUnit() > 110_000 && ref.metersPerNormUnit() < 112_000,
                "expected ~111km per normalized unit, got " + ref.metersPerNormUnit());
    }

    @Test
    void collinearCalibrationPointsAreDegenerate() {
        // lon = 2*lat for every point - the (lat, lon, 1) design matrix columns become linearly
        // dependent, so the least-squares normal equations are singular.
        List<GeoReference.CalibrationPoint> points = List.of(
                point(0, 0, 0.1, 0.1),
                point(1, 2, 0.4, 0.3),
                point(2, 4, 0.7, 0.5));
        assertNull(GeoReference.fit(points));
    }
}
