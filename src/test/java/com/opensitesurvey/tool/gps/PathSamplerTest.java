package com.opensitesurvey.tool.gps;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathSamplerTest {

    private static GeoReference.ImagePoint point(double x, double y) {
        return new GeoReference.ImagePoint(x, y);
    }

    @Test
    void firstPositionIsAlwaysRecordedIfAccurateEnough() {
        PathSampler sampler = new PathSampler(0.05, 20.0);
        assertTrue(sampler.shouldRecord(point(0.5, 0.5), 5.0));
    }

    @Test
    void firstPositionIsRejectedIfAccuracyIsTooCoarse() {
        PathSampler sampler = new PathSampler(0.05, 20.0);
        assertFalse(sampler.shouldRecord(point(0.5, 0.5), 25.0));
    }

    @Test
    void positionWithinThresholdOfLastRecordedIsNotRecorded() {
        PathSampler sampler = new PathSampler(0.1, 20.0);
        sampler.markRecorded(point(0.5, 0.5));
        assertFalse(sampler.shouldRecord(point(0.55, 0.5), 5.0)); // distance 0.05 < 0.1 threshold
    }

    @Test
    void positionBeyondThresholdOfLastRecordedIsRecorded() {
        PathSampler sampler = new PathSampler(0.1, 20.0);
        sampler.markRecorded(point(0.5, 0.5));
        assertTrue(sampler.shouldRecord(point(0.65, 0.5), 5.0)); // distance 0.15 >= 0.1 threshold
    }

    @Test
    void poorAccuracyIsRejectedRegardlessOfDistanceTravelled() {
        PathSampler sampler = new PathSampler(0.1, 20.0);
        sampler.markRecorded(point(0.0, 0.0));
        assertFalse(sampler.shouldRecord(point(1.0, 1.0), 50.0)); // far away, but accuracy too coarse
    }

    @Test
    void distanceIsMeasuredFromTheMostRecentlyRecordedPointNotTheFirstOne() {
        PathSampler sampler = new PathSampler(0.1, 20.0);
        sampler.markRecorded(point(0.0, 0.0));
        sampler.markRecorded(point(0.5, 0.0)); // now the reference point moves here
        assertFalse(sampler.shouldRecord(point(0.55, 0.0), 5.0)); // close to the *new* reference
    }

    @Test
    void resetClearsTheReferencePointSoNextPositionIsAlwaysRecorded() {
        PathSampler sampler = new PathSampler(0.5, 20.0);
        sampler.markRecorded(point(0.5, 0.5));
        sampler.reset();
        assertTrue(sampler.shouldRecord(point(0.5, 0.5), 5.0)); // same spot, but reference was cleared
    }
}
