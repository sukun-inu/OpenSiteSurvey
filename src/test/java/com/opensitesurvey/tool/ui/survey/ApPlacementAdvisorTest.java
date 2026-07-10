package com.opensitesurvey.tool.ui.survey;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApPlacementAdvisorTest {

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
    void emptyGridReturnsNoSuggestions() {
        Double[][] grid = new Double[0][0];
        assertTrue(ApPlacementAdvisor.suggest(grid, -75, 3).isEmpty());
    }

    @Test
    void fullyStrongGridReturnsNoSuggestions() {
        Double[][] grid = uniformGrid(20, 20, -40.0); // well above the -75 threshold everywhere
        assertTrue(ApPlacementAdvisor.suggest(grid, -75, 3).isEmpty());
    }

    @Test
    void zeroMaxSuggestionsReturnsEmptyList() {
        Double[][] grid = uniformGrid(20, 20, null); // fully uncovered
        assertTrue(ApPlacementAdvisor.suggest(grid, -75, 0).isEmpty());
    }

    @Test
    void nullCellsCountAsWeakEvenWithoutExplicitThresholdCrossing() {
        Double[][] grid = uniformGrid(10, 10, null); // no data at all - must be treated as uncovered
        List<ApPlacementAdvisor.Suggestion> suggestions = ApPlacementAdvisor.suggest(grid, -75, 1);
        assertEquals(1, suggestions.size());
        assertTrue(suggestions.get(0).cellsImproved() > 0);
    }

    @Test
    void singleTightClusterProducesOneSuggestionCoveringAllWeakCells() {
        Double[][] grid = uniformGrid(20, 20, -40.0);
        // A small 5x5 coverage hole in the corner - close enough together that one AP placement
        // can plausibly cover all of it at once (see ApPlacementAdvisor's path-loss constants).
        for (int gy = 0; gy <= 4; gy++) {
            for (int gx = 0; gx <= 4; gx++) {
                grid[gy][gx] = null;
            }
        }
        List<ApPlacementAdvisor.Suggestion> suggestions = ApPlacementAdvisor.suggest(grid, -75, 3);
        assertEquals(1, suggestions.size(), "all 25 weak cells should be covered by a single suggestion");
        assertEquals(25, suggestions.get(0).cellsImproved());
        assertTrue(suggestions.get(0).xNorm() < 0.25 && suggestions.get(0).yNorm() < 0.25,
                "suggested position should land inside/near the weak region, not somewhere in the strong area");
    }

    @Test
    void greedyPicksTheLargerClusterFirst() {
        Double[][] grid = uniformGrid(40, 40, -40.0);
        // Big cluster (25 cells) near one corner...
        for (int gy = 0; gy <= 4; gy++) {
            for (int gx = 0; gx <= 4; gx++) {
                grid[gy][gx] = null;
            }
        }
        // ...a small cluster (4 cells) near the opposite corner, far enough away that no single
        // candidate position's estimated range covers cells from both clusters at once.
        for (int gy = 36; gy <= 37; gy++) {
            for (int gx = 36; gx <= 37; gx++) {
                grid[gy][gx] = null;
            }
        }
        List<ApPlacementAdvisor.Suggestion> suggestions = ApPlacementAdvisor.suggest(grid, -75, 2);
        assertEquals(2, suggestions.size());
        assertEquals(25, suggestions.get(0).cellsImproved(), "the larger cluster must be suggested first");
        assertEquals(4, suggestions.get(1).cellsImproved());
    }

    @Test
    void maxSuggestionsCapsReturnedListEvenWhenMoreClustersRemain() {
        Double[][] grid = uniformGrid(60, 60, -40.0);
        // Three widely-separated small clusters, each coverable by its own single suggestion.
        int[][] corners = {{0, 0}, {28, 28}, {56, 56}};
        for (int[] corner : corners) {
            for (int gy = corner[0]; gy <= corner[0] + 1 && gy < 60; gy++) {
                for (int gx = corner[1]; gx <= corner[1] + 1 && gx < 60; gx++) {
                    grid[gy][gx] = null;
                }
            }
        }
        List<ApPlacementAdvisor.Suggestion> suggestions = ApPlacementAdvisor.suggest(grid, -75, 2);
        assertEquals(2, suggestions.size(), "must not exceed maxSuggestions even though a 3rd cluster remains uncovered");
    }
}
