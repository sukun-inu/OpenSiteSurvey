package com.opensitesurvey.tool.model;

import com.opensitesurvey.tool.gps.GeoReference;

import java.util.ArrayList;
import java.util.List;

/** Persisted site-survey project: floor plan reference, scale, and every recorded point. */
public class SurveyProject {

    public String floorPlanPath;
    public double metersPerPixel;
    public List<SurveyPoint> points = new ArrayList<>();

    /**
     * Base64-encoded raw bytes of the floor plan image file, embedded directly in the project so
     * the project stays self-contained and portable (movable/shareable without also having to
     * carry the original image file at its original absolute path). {@code null} for project
     * files saved before this field existed, or if the source file couldn't be read at save time -
     * those fall back to resolving {@link #floorPlanPath} on load, same as before.
     */
    public String floorPlanImageBase64;

    /**
     * GPS calibration reference points (see {@code com.opensitesurvey.tool.gps.GeoReference}),
     * empty for a project that has never been GPS-calibrated. Persisting the raw reference points
     * (rather than the fitted transform coefficients) means the fit can be recomputed - e.g. after
     * adding another calibration point, or if the fitting algorithm itself ever changes - without
     * needing to re-walk to every reference location again.
     */
    public List<GeoReference.CalibrationPoint> calibrationPoints = new ArrayList<>();

    public SurveyProject() {
    }

    public SurveyProject(String floorPlanPath, double metersPerPixel, List<SurveyPoint> points) {
        this.floorPlanPath = floorPlanPath;
        this.metersPerPixel = metersPerPixel;
        this.points = points;
    }
}
