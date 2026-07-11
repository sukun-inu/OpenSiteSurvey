package com.opensitesurvey.tool.gps;

/**
 * Turns a continuous, noisy GPS position stream into well-spaced discrete survey points: rejects
 * positions with a poor reported accuracy outright, and only accepts a position for recording once
 * it has moved at least {@code minDistanceNorm} (in the same normalized 0..1 image-space units as
 * {@link GeoReference}) from the last <em>recorded</em> point - not merely the last position seen
 * - so standing still doesn't fill the survey with near-duplicate points from ordinary GPS jitter,
 * and a temporarily poor fix doesn't record a misleadingly-placed point.
 */
public final class PathSampler {

    private final double minDistanceNorm;
    private final double maxAccuracyMeters;
    private GeoReference.ImagePoint lastRecorded;

    public PathSampler(double minDistanceNorm, double maxAccuracyMeters) {
        this.minDistanceNorm = minDistanceNorm;
        this.maxAccuracyMeters = maxAccuracyMeters;
    }

    /** @return true if {@code candidate} is both accurate enough and far enough from the last recorded point to warrant recording a new survey point. */
    public boolean shouldRecord(GeoReference.ImagePoint candidate, double accuracyMeters) {
        if (accuracyMeters > maxAccuracyMeters) {
            return false;
        }
        if (lastRecorded == null) {
            return true;
        }
        double dx = candidate.xNorm() - lastRecorded.xNorm();
        double dy = candidate.yNorm() - lastRecorded.yNorm();
        return Math.sqrt(dx * dx + dy * dy) >= minDistanceNorm;
    }

    /** Call after actually recording a survey point at {@code p}, so subsequent distance checks measure from here. */
    public void markRecorded(GeoReference.ImagePoint p) {
        lastRecorded = p;
    }

    /** Clears the last-recorded reference point, e.g. when starting a fresh walking session. */
    public void reset() {
        lastRecorded = null;
    }
}
