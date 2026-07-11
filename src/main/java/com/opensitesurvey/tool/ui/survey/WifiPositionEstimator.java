package com.opensitesurvey.tool.ui.survey;

import com.opensitesurvey.tool.model.ApSnapshot;

import java.util.List;
import java.util.Map;

/**
 * Mirror image of {@link ApPositionEstimator}: instead of estimating an AP's position from survey
 * points that saw it, this estimates the surveyor's <em>current</em> position from APs whose
 * position is already known (a prior {@link ApPositionEstimator} result for each BSSID, computed
 * from survey points recorded so far on the same floor plan) and their RSSI in the most recent
 * Wi-Fi scan.
 *
 * <p>Same RSSI-weighted-centroid heuristic and the same honesty caveat as {@link
 * ApPositionEstimator}: this is not true trilateration, just "a stronger nearby AP pulls the
 * estimate toward itself more". Unlike GPS, this needs no outdoor signal or lat/lon calibration at
 * all - it works purely from Wi-Fi data this app already collects, which makes it usable indoors
 * even when GPS can't get a fix. The tradeoff is that it needs a handful of points recorded first
 * (manually, or via GPS where available) before any BSSID has a known position to estimate from.
 */
public final class WifiPositionEstimator {

    /** @param apCount how many currently-visible APs had a known position and contributed to this estimate - shown alongside the estimate so a user can judge its reliability. */
    public record Estimate(double xNorm, double yNorm, int apCount) {
    }

    private WifiPositionEstimator() {
    }

    /**
     * @param knownApPositions each BSSID's previously-estimated position (see {@link ApPositionEstimator#estimate})
     * @param liveAccessPoints the most recent Wi-Fi scan
     * @return the estimated position, or {@code null} if none of the currently-visible APs have a known position yet
     */
    public static Estimate estimate(Map<String, ApPositionEstimator.Estimate> knownApPositions, List<ApSnapshot> liveAccessPoints) {
        double weightedX = 0;
        double weightedY = 0;
        double weightSum = 0;
        int apCount = 0;
        for (ApSnapshot ap : liveAccessPoints) {
            ApPositionEstimator.Estimate apPosition = knownApPositions.get(ap.bssid());
            if (apPosition == null) {
                continue;
            }
            // Same dBm -> linear power ratio weighting as ApPositionEstimator, so a strong nearby
            // AP outweighs a faint distant one by orders of magnitude rather than linearly.
            double weight = Math.pow(10.0, ap.rssiDbm() / 10.0);
            weightedX += weight * apPosition.xNorm();
            weightedY += weight * apPosition.yNorm();
            weightSum += weight;
            apCount++;
        }
        if (apCount == 0 || weightSum <= 0) {
            return null;
        }
        return new Estimate(weightedX / weightSum, weightedY / weightSum, apCount);
    }
}
