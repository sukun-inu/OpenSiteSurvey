package com.opensitesurvey.tool.ui.survey;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoverageRequirementEvaluatorTest {

    private static Double[][] uniformGrid(int height, int width, Double value) {
        Double[][] grid = new Double[height][width];
        for (int gy = 0; gy < height; gy++) {
            for (int gx = 0; gx < width; gx++) {
                grid[gy][gx] = value;
            }
        }
        return grid;
    }

    @Test
    void emptyGridReturnsZeroPercentForEveryRequirement() {
        Double[][] grid = new Double[0][0];
        List<CoverageRequirementEvaluator.Result> results =
                CoverageRequirementEvaluator.evaluate(grid, CoverageRequirementEvaluator.DEFAULT_REQUIREMENTS);
        assertEquals(3, results.size());
        for (CoverageRequirementEvaluator.Result r : results) {
            assertEquals(0.0, r.coveragePercent());
        }
    }

    @Test
    void strongSignalEverywhereMeetsAllThreeRequirements() {
        Double[][] grid = uniformGrid(10, 10, -50.0); // stronger than voice/video/data thresholds
        List<CoverageRequirementEvaluator.Result> results =
                CoverageRequirementEvaluator.evaluate(grid, CoverageRequirementEvaluator.DEFAULT_REQUIREMENTS);
        for (CoverageRequirementEvaluator.Result r : results) {
            assertEquals(100.0, r.coveragePercent());
        }
    }

    @Test
    void weakSignalMeetsOnlyTheLenientDataRequirement() {
        Double[][] grid = uniformGrid(10, 10, -75.0); // weaker than voice(-67)/video(-70), meets data(-80)
        List<CoverageRequirementEvaluator.Result> results =
                CoverageRequirementEvaluator.evaluate(grid, CoverageRequirementEvaluator.DEFAULT_REQUIREMENTS);
        assertEquals(0.0, results.get(0).coveragePercent(), "voice (-67dBm) should not be met at -75dBm");
        assertEquals(0.0, results.get(1).coveragePercent(), "video (-70dBm) should not be met at -75dBm");
        assertEquals(100.0, results.get(2).coveragePercent(), "data (-80dBm) should be met at -75dBm");
    }

    @Test
    void nullCellsAreExcludedFromTheDenominator() {
        Double[][] grid = uniformGrid(10, 10, -50.0);
        // Half the grid has no data at all - only the covered half should count toward the percentage.
        for (int gy = 0; gy < 5; gy++) {
            for (int gx = 0; gx < 10; gx++) {
                grid[gy][gx] = null;
            }
        }
        List<CoverageRequirementEvaluator.Result> results =
                CoverageRequirementEvaluator.evaluate(grid, CoverageRequirementEvaluator.DEFAULT_REQUIREMENTS);
        assertEquals(100.0, results.get(0).coveragePercent(), "the covered half is all strong, so it's 100% of the counted cells");
    }

    @Test
    void mixedGridProducesProportionalPercentage() {
        Double[][] grid = uniformGrid(10, 10, -50.0); // 100 cells, all initially meeting every requirement
        for (int gy = 0; gy < 3; gy++) { // 30 cells too weak for any requirement
            for (int gx = 0; gx < 10; gx++) {
                grid[gy][gx] = -90.0;
            }
        }
        List<CoverageRequirementEvaluator.Result> results =
                CoverageRequirementEvaluator.evaluate(grid, CoverageRequirementEvaluator.DEFAULT_REQUIREMENTS);
        assertEquals(70.0, results.get(0).coveragePercent(), 0.001);
    }
}
