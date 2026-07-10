package com.opensitesurvey.tool.ui.survey;

import java.util.ArrayList;
import java.util.List;

/**
 * Suggests where to place additional APs to shrink the current coverage holes, via a greedy
 * set-cover search over a simple log-distance path-loss model - <b>not</b> a trained/learned
 * model, despite "AI-based AP placement" being the term used for this idea in the project's own
 * roadmap notes. Reads the same {@code Double[][]} grid {@link HeatmapRenderer#computeValueGrid}
 * already produces for the heatmap/coverage-hole overlay, so a suggestion is always computed
 * against exactly what the user is currently looking at (same points, target BSSID, and
 * interpolator).
 *
 * <p>Like {@link ApPositionEstimator}, this has no real-world distance/scale to work with ({@code
 * SurveyProject.metersPerPixel} is never populated) - {@link #REFERENCE_DISTANCE_NORM}/{@link
 * #PATH_LOSS_EXPONENT} are just plausible indoor defaults applied to normalized (0..1)
 * floor-plan coordinates, not a calibrated propagation model. Treat suggested positions as a
 * rough starting point for where to try a new AP, not a guaranteed fix.
 */
public final class ApPlacementAdvisor {

    /** One suggested new-AP position, plus how many currently-weak grid cells it would bring above the threshold. */
    public record Suggestion(double xNorm, double yNorm, int cellsImproved) {
    }

    // A hypothetical AP's estimated signal strength at REFERENCE_DISTANCE_NORM away, and how fast
    // it falls off with distance beyond that - typical indoor multi-wall assumptions, since this
    // app has no calibrated real-world path-loss model to draw on (see class javadoc).
    private static final double REFERENCE_DISTANCE_NORM = 0.01;
    private static final double REFERENCE_RSSI_AT_REFERENCE_DISTANCE = -30.0;
    private static final double PATH_LOSS_EXPONENT = 3.0;

    // Candidate placement positions are sampled on a coarser grid than the (96x72) evaluation
    // grid, since every candidate is scored against every still-weak cell - a full-resolution
    // candidate set would be ~48M distance checks per suggestion for no real gain in usefulness
    // (a "try roughly here" suggestion doesn't need pixel-level precision).
    private static final int CANDIDATE_STRIDE = 4;

    private ApPlacementAdvisor() {
    }

    /**
     * @param valueGrid       row-major {@code [gy][gx]} grid from {@link
     *                        HeatmapRenderer#computeValueGrid} - a {@code null} cell (no
     *                        interpolated value at all) counts as uncovered, same as a cell below
     *                        {@code thresholdDbm}.
     * @param maxSuggestions  upper bound on how many positions to return; fewer are returned once
     *                        no candidate would improve coverage any further.
     */
    public static List<Suggestion> suggest(Double[][] valueGrid, double thresholdDbm, int maxSuggestions) {
        int gridHeight = valueGrid.length;
        int gridWidth = gridHeight == 0 ? 0 : valueGrid[0].length;

        List<double[]> weakCells = new ArrayList<>(); // {xNorm, yNorm} of each still-uncovered cell
        for (int gy = 0; gy < gridHeight; gy++) {
            for (int gx = 0; gx < gridWidth; gx++) {
                Double value = valueGrid[gy][gx];
                if (value == null || value < thresholdDbm) {
                    weakCells.add(new double[]{(gx + 0.5) / gridWidth, (gy + 0.5) / gridHeight});
                }
            }
        }

        List<Suggestion> suggestions = new ArrayList<>();
        boolean[] covered = new boolean[weakCells.size()];
        for (int k = 0; k < maxSuggestions && !weakCells.isEmpty(); k++) {
            double bestX = 0, bestY = 0;
            int bestGain = 0;
            for (int cgy = 0; cgy < gridHeight; cgy += CANDIDATE_STRIDE) {
                double candidateY = (cgy + 0.5) / gridHeight;
                for (int cgx = 0; cgx < gridWidth; cgx += CANDIDATE_STRIDE) {
                    double candidateX = (cgx + 0.5) / gridWidth;
                    int gain = 0;
                    for (int i = 0; i < weakCells.size(); i++) {
                        if (covered[i]) {
                            continue;
                        }
                        if (wouldCover(candidateX, candidateY, weakCells.get(i), thresholdDbm)) {
                            gain++;
                        }
                    }
                    if (gain > bestGain) {
                        bestGain = gain;
                        bestX = candidateX;
                        bestY = candidateY;
                    }
                }
            }
            if (bestGain == 0) {
                break; // no remaining candidate would improve coverage any further
            }
            suggestions.add(new Suggestion(bestX, bestY, bestGain));
            for (int i = 0; i < weakCells.size(); i++) {
                if (!covered[i] && wouldCover(bestX, bestY, weakCells.get(i), thresholdDbm)) {
                    covered[i] = true;
                }
            }
        }
        return suggestions;
    }

    private static boolean wouldCover(double candidateX, double candidateY, double[] cell, double thresholdDbm) {
        double dx = cell[0] - candidateX;
        double dy = cell[1] - candidateY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        return estimateRssiAtDistance(distance) >= thresholdDbm;
    }

    private static double estimateRssiAtDistance(double distanceNorm) {
        double d = Math.max(distanceNorm, REFERENCE_DISTANCE_NORM);
        return REFERENCE_RSSI_AT_REFERENCE_DISTANCE - 10 * PATH_LOSS_EXPONENT * Math.log10(d / REFERENCE_DISTANCE_NORM);
    }
}
