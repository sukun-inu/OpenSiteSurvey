package com.opensitesurvey.tool.ui.survey;

import com.opensitesurvey.tool.model.ApSnapshot;
import com.opensitesurvey.tool.security.SecurityType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WifiPositionEstimatorTest {

    private static ApSnapshot ap(String bssid, int rssi) {
        return new ApSnapshot("SSID", bssid, 6, 2437000, "2.4GHz", rssi, 90, "n/a", true, SecurityType.WPA2, null, Instant.now());
    }

    private static ApPositionEstimator.Estimate known(double x, double y) {
        return new ApPositionEstimator.Estimate(x, y, 5);
    }

    @Test
    void noVisibleApHasAKnownPositionReturnsNull() {
        Map<String, ApPositionEstimator.Estimate> known = Map.of("AA:AA:AA:AA:AA:AA", known(0.2, 0.2));
        List<ApSnapshot> live = List.of(ap("BB:BB:BB:BB:BB:BB", -50));
        assertNull(WifiPositionEstimator.estimate(known, live));
    }

    @Test
    void emptyKnownPositionsReturnsNull() {
        List<ApSnapshot> live = List.of(ap("AA:AA:AA:AA:AA:AA", -50));
        assertNull(WifiPositionEstimator.estimate(Map.of(), live));
    }

    @Test
    void singleKnownApSnapsEstimateToThatApsPosition() {
        Map<String, ApPositionEstimator.Estimate> known = Map.of("AA:AA:AA:AA:AA:AA", known(0.3, 0.7));
        List<ApSnapshot> live = List.of(ap("AA:AA:AA:AA:AA:AA", -60));
        WifiPositionEstimator.Estimate estimate = WifiPositionEstimator.estimate(known, live);
        assertEquals(0.3, estimate.xNorm(), 0.001);
        assertEquals(0.7, estimate.yNorm(), 0.001);
        assertEquals(1, estimate.apCount());
    }

    @Test
    void equalSignalStrengthFromTwoKnownApsAveragesToTheMidpoint() {
        Map<String, ApPositionEstimator.Estimate> known = Map.of(
                "AA:AA:AA:AA:AA:AA", known(0.0, 0.0),
                "BB:BB:BB:BB:BB:BB", known(1.0, 1.0));
        List<ApSnapshot> live = List.of(ap("AA:AA:AA:AA:AA:AA", -50), ap("BB:BB:BB:BB:BB:BB", -50));
        WifiPositionEstimator.Estimate estimate = WifiPositionEstimator.estimate(known, live);
        assertEquals(0.5, estimate.xNorm(), 0.001);
        assertEquals(0.5, estimate.yNorm(), 0.001);
        assertEquals(2, estimate.apCount());
    }

    @Test
    void strongerVisibleApPullsTheEstimateTowardItself() {
        Map<String, ApPositionEstimator.Estimate> known = Map.of(
                "AA:AA:AA:AA:AA:AA", known(0.0, 0.5),
                "BB:BB:BB:BB:BB:BB", known(1.0, 0.5));
        List<ApSnapshot> live = List.of(ap("AA:AA:AA:AA:AA:AA", -30), ap("BB:BB:BB:BB:BB:BB", -90));
        WifiPositionEstimator.Estimate estimate = WifiPositionEstimator.estimate(known, live);
        assertEquals(2, estimate.apCount());
        assertEquals(true, estimate.xNorm() < 0.5, "estimate should be pulled toward the much stronger -30dBm AP at x=0");
    }

    @Test
    void ignoresVisibleApsWithNoKnownPosition() {
        Map<String, ApPositionEstimator.Estimate> known = Map.of("AA:AA:AA:AA:AA:AA", known(0.4, 0.4));
        List<ApSnapshot> live = List.of(ap("AA:AA:AA:AA:AA:AA", -50), ap("FF:FF:FF:FF:FF:FF", -20));
        WifiPositionEstimator.Estimate estimate = WifiPositionEstimator.estimate(known, live);
        assertEquals(1, estimate.apCount());
        assertEquals(0.4, estimate.xNorm(), 0.001);
    }
}
