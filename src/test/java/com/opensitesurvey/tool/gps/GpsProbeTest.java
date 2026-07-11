package com.opensitesurvey.tool.gps;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GpsProbeTest {

    @Test
    void parsesAWellFormedPositionLine() {
        GpsProbe.Position p = GpsProbe.parsePositionLine("35.681236,139.767125,8.50,1752200000000");
        assertEquals(35.681236, p.latitude(), 1e-9);
        assertEquals(139.767125, p.longitude(), 1e-9);
        assertEquals(8.50, p.horizontalAccuracyMeters(), 1e-9);
        assertEquals(1752200000000L, p.epochMillis());
    }

    @Test
    void parsesNegativeLatitudeAndLongitude() {
        GpsProbe.Position p = GpsProbe.parsePositionLine("-33.8688,-151.2093,15.00,1752200000000");
        assertEquals(-33.8688, p.latitude(), 1e-9);
        assertEquals(-151.2093, p.longitude(), 1e-9);
    }

    @Test
    void trimsSurroundingWhitespace() {
        GpsProbe.Position p = GpsProbe.parsePositionLine("  35.0,139.0,5.0,123  ");
        assertEquals(35.0, p.latitude(), 1e-9);
    }

    @Test
    void nullLineReturnsNull() {
        assertNull(GpsProbe.parsePositionLine(null));
    }

    @Test
    void errorLineIsNotParsedAsAPosition() {
        assertNull(GpsProbe.parsePositionLine("ERROR:System.Device assembly could not be loaded"));
    }

    @Test
    void malformedCsvReturnsNull() {
        assertNull(GpsProbe.parsePositionLine("35.681236,139.767125"));
        assertNull(GpsProbe.parsePositionLine("not,a,valid,line"));
        assertNull(GpsProbe.parsePositionLine(""));
    }

    @Test
    void nonNumericAccuracyLikeNaNReturnsNull() {
        assertNull(GpsProbe.parsePositionLine("35.0,139.0,NaN,123"));
    }
}
