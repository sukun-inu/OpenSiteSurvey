package com.waj.tool.ui.survey;

import com.waj.tool.model.SurveyPoint;
import com.waj.tool.util.SignalColorScale;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Renders an IDW-interpolated RSSI heatmap into a small (coarse-grid) {@link WritableImage}.
 * The caller upscales it with smoothing onto the floor-plan canvas rather than recomputing IDW
 * per screen pixel every frame.
 */
public final class HeatmapRenderer {

    public static final int GRID_WIDTH = 96;
    public static final int GRID_HEIGHT = 72;

    private HeatmapRenderer() {
    }

    public static WritableImage render(List<SurveyPoint> points, String targetBssid) {
        WritableImage image = new WritableImage(GRID_WIDTH, GRID_HEIGHT);
        PixelWriter writer = image.getPixelWriter();
        for (int gy = 0; gy < GRID_HEIGHT; gy++) {
            double y = (gy + 0.5) / GRID_HEIGHT;
            for (int gx = 0; gx < GRID_WIDTH; gx++) {
                double x = (gx + 0.5) / GRID_WIDTH;
                Double value = IdwInterpolator.interpolate(x, y, points, targetBssid);
                Color color = value == null
                        ? Color.TRANSPARENT
                        : SignalColorScale.colorFor(value).deriveColor(0, 1, 1, 0.55);
                writer.setColor(gx, gy, color);
            }
        }
        return image;
    }

    /** Neutral gray a zero-delta cell renders as, before blending toward green (improved) or red (regressed). */
    private static final Color DELTA_NEUTRAL = Color.web("#8a8f98");
    private static final Color DELTA_IMPROVED = Color.web("#2ecc71");
    private static final Color DELTA_REGRESSED = Color.web("#e74c3c");
    private static final double DELTA_SCALE_DBM = 20.0;

    /**
     * Before/after coverage comparison: at every grid cell, IDW-interpolates {@code targetBssid}'s
     * RSSI independently from each point set and colors the cell by the difference (current -
     * baseline) rather than by absolute signal strength - green where coverage improved, red where
     * it regressed, gray near zero. A cell renders transparent unless <em>both</em> surveys have
     * data there, since a delta is meaningless with only one side present.
     */
    public static WritableImage renderDelta(List<SurveyPoint> currentPoints, List<SurveyPoint> baselinePoints,
                                             String targetBssid) {
        WritableImage image = new WritableImage(GRID_WIDTH, GRID_HEIGHT);
        PixelWriter writer = image.getPixelWriter();
        for (int gy = 0; gy < GRID_HEIGHT; gy++) {
            double y = (gy + 0.5) / GRID_HEIGHT;
            for (int gx = 0; gx < GRID_WIDTH; gx++) {
                double x = (gx + 0.5) / GRID_WIDTH;
                Double current = IdwInterpolator.interpolate(x, y, currentPoints, targetBssid);
                Double baseline = IdwInterpolator.interpolate(x, y, baselinePoints, targetBssid);
                Color color = (current == null || baseline == null)
                        ? Color.TRANSPARENT
                        : deltaColor(current - baseline).deriveColor(0, 1, 1, 0.55);
                writer.setColor(gx, gy, color);
            }
        }
        return image;
    }

    private static Color deltaColor(double deltaDbm) {
        double clamped = Math.max(-DELTA_SCALE_DBM, Math.min(DELTA_SCALE_DBM, deltaDbm));
        double t = Math.abs(clamped) / DELTA_SCALE_DBM;
        return clamped >= 0 ? DELTA_NEUTRAL.interpolate(DELTA_IMPROVED, t) : DELTA_NEUTRAL.interpolate(DELTA_REGRESSED, t);
    }
}
