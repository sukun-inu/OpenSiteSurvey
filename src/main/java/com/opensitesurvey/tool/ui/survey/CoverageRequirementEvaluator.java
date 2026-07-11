package com.opensitesurvey.tool.ui.survey;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates a heatmap grid against fixed per-use-case minimum RSSI requirements, the way
 * enterprise Wi-Fi design guides typically phrase acceptance criteria (e.g. "-67dBm for voice").
 * Reuses the same {@code Double[][]} grid {@link HeatmapRenderer#computeValueGrid} produces for
 * the heatmap/coverage-hole overlay, so results always describe exactly what the user is
 * currently looking at (same points, target BSSID, and interpolator).
 *
 * <p>The three thresholds below are common industry defaults (see e.g. Cisco/Aruba/Ekahau Wi-Fi
 * design guidance) rather than measurements specific to any real deployment - treat them as a
 * reasonable starting point, not a certified requirement.
 */
public final class CoverageRequirementEvaluator {

    public record Requirement(String name, double minRssiDbm) {
    }

    public record Result(String name, double minRssiDbm, double coveragePercent) {
    }

    public static final Requirement VOICE = new Requirement("Voice", -67);
    public static final Requirement VIDEO = new Requirement("Video", -70);
    public static final Requirement DATA = new Requirement("Data", -80);

    public static final List<Requirement> DEFAULT_REQUIREMENTS = List.of(VOICE, VIDEO, DATA);

    private CoverageRequirementEvaluator() {
    }

    /** @return one {@link Result} per requirement, in the same order as {@code requirements}. */
    public static List<Result> evaluate(Double[][] valueGrid, List<Requirement> requirements) {
        int gridHeight = valueGrid.length;
        int gridWidth = gridHeight == 0 ? 0 : valueGrid[0].length;

        int totalCells = 0;
        int[] meetsCount = new int[requirements.size()];
        for (int gy = 0; gy < gridHeight; gy++) {
            for (int gx = 0; gx < gridWidth; gx++) {
                Double value = valueGrid[gy][gx];
                if (value == null) {
                    continue;
                }
                totalCells++;
                for (int i = 0; i < requirements.size(); i++) {
                    if (value >= requirements.get(i).minRssiDbm()) {
                        meetsCount[i]++;
                    }
                }
            }
        }

        List<Result> results = new ArrayList<>();
        for (int i = 0; i < requirements.size(); i++) {
            double percent = totalCells == 0 ? 0 : 100.0 * meetsCount[i] / totalCells;
            results.add(new Result(requirements.get(i).name(), requirements.get(i).minRssiDbm(), percent));
        }
        return results;
    }
}
