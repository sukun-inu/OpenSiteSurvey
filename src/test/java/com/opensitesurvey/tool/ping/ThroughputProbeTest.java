package com.opensitesurvey.tool.ping;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThroughputProbeTest {

    @Test
    void oneMegabyteInOneSecondIsEightMegabitsPerSecond() {
        Optional<Double> mbps = ThroughputProbe.computeMbps(1_000_000, 1_000_000_000L);
        assertEquals(8.0, mbps.orElseThrow(), 0.001);
    }

    @Test
    void halfASecondDoublesTheRate() {
        Optional<Double> mbps = ThroughputProbe.computeMbps(1_000_000, 500_000_000L);
        assertEquals(16.0, mbps.orElseThrow(), 0.001);
    }

    @Test
    void zeroBytesYieldsEmpty() {
        assertTrue(ThroughputProbe.computeMbps(0, 1_000_000_000L).isEmpty());
    }

    @Test
    void zeroElapsedTimeYieldsEmpty() {
        assertTrue(ThroughputProbe.computeMbps(1_000_000, 0).isEmpty());
    }

    @Test
    void blankUrlYieldsEmptyWithoutAnyNetworkCall() {
        assertTrue(ThroughputProbe.measure("", 1000).isEmpty());
        assertTrue(ThroughputProbe.measure(null, 1000).isEmpty());
    }

    @Test
    void unreachableUrlYieldsEmpty() {
        assertTrue(ThroughputProbe.measure("http://127.0.0.1:1/does-not-exist", 1000).isEmpty());
    }
}
