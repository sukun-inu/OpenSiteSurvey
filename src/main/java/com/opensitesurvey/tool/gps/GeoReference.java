package com.opensitesurvey.tool.gps;

import java.util.List;

/**
 * Calibrates real-world GPS coordinates against a background image's normalized (0..1) pixel
 * space, from 2 or more (latitude, longitude) &lt;-&gt; (xNorm, yNorm) reference point pairs - the
 * same mechanism serves both an outdoor survey (background image = a user-supplied map/satellite
 * screenshot) and an indoor survey (background image = the existing floor plan), since neither
 * this class nor anything downstream (heatmap rendering, interpolation, export) needs to know
 * which kind of image it is.
 *
 * <p>Internally every fit is stored as a plain 2D affine transform
 * (<code>xNorm = a·lat + b·lon + c</code>, <code>yNorm = d·lat + e·lon + f</code>) regardless of
 * which fitting path produced it:
 *
 * <ul>
 *   <li><b>Exactly 2 points</b> - a similarity transform (uniform scale + rotation + translation,
 *   4 degrees of freedom), solved via the standard trick of treating both coordinate systems as
 *   complex numbers (<code>k = (w2-w1)/(z2-z1)</code>, <code>t = w1 - k·z1</code>) and converting
 *   the resulting complex scale/rotation + translation into the 6 affine coefficients above. This
 *   is the expected common case: walk to one corner, capture a GPS reading and click the image;
 *   walk to another corner, repeat.</li>
 *   <li><b>3 or more points</b> - a full 6-parameter affine fit fit by least squares, letting a
 *   3rd (or later) point improve accuracy if the background image has any skew or non-uniform
 *   scale (e.g. a photographed rather than scanned floor plan). Because the x and y equations
 *   don't share coefficients, this decomposes into two independent 3-variable linear regressions
 *   against the same design matrix, each solved via a 3x3 normal-equations system.</li>
 * </ul>
 */
public final class GeoReference {

    /** One (real-world, image-pixel) reference point pair used to calibrate a fit. */
    public record CalibrationPoint(double latitude, double longitude, double xNorm, double yNorm) {
    }

    /** A projected position in the same normalized (0..1) space {@code SurveyPoint} uses. */
    public record ImagePoint(double xNorm, double yNorm) {
    }

    private final double a, b, c, d, e, f;
    private final double metersPerNormUnit;

    private GeoReference(double a, double b, double c, double d, double e, double f, double metersPerNormUnit) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.e = e;
        this.f = f;
        this.metersPerNormUnit = metersPerNormUnit;
    }

    /** @return {@code null} if fewer than 2 points are given, or the points are degenerate (duplicate/collinear) and unsolvable. */
    public static GeoReference fit(List<CalibrationPoint> points) {
        if (points == null || points.size() < 2) {
            return null;
        }
        double metersPerNormUnit = averageMetersPerNormUnit(points);
        if (points.size() == 2) {
            return fitSimilarity(points.get(0), points.get(1), metersPerNormUnit);
        }
        return fitAffine(points, metersPerNormUnit);
    }

    /**
     * Real-world scale, in meters per unit of normalized (0..1) image distance - the same "how
     * many meters does one normalized unit represent" concept {@code SurveyProject.metersPerPixel}
     * always intended but could never actually compute (this app has no other way to relate
     * on-screen pixels to real-world distance). Since the fitted transform is linear, this scale
     * is constant everywhere in the image, so it's derived once, here, by averaging the
     * real-world-distance-to-image-distance ratio over every pair of calibration points (using the
     * Haversine great-circle distance for the real-world side).
     */
    public double metersPerNormUnit() {
        return metersPerNormUnit;
    }

    private static double averageMetersPerNormUnit(List<CalibrationPoint> points) {
        double totalRatio = 0;
        int pairCount = 0;
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                CalibrationPoint p1 = points.get(i);
                CalibrationPoint p2 = points.get(j);
                double meters = haversineMeters(p1.latitude(), p1.longitude(), p2.latitude(), p2.longitude());
                double normDistance = Math.hypot(p2.xNorm() - p1.xNorm(), p2.yNorm() - p1.yNorm());
                if (normDistance > 1e-9) {
                    totalRatio += meters / normDistance;
                    pairCount++;
                }
            }
        }
        return pairCount == 0 ? 0 : totalRatio / pairCount;
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusMeters = 6_371_000.0;
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);
        double sinPhi = Math.sin(deltaPhi / 2);
        double sinLambda = Math.sin(deltaLambda / 2);
        double h = sinPhi * sinPhi + Math.cos(phi1) * Math.cos(phi2) * sinLambda * sinLambda;
        return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    private static GeoReference fitSimilarity(CalibrationPoint p1, CalibrationPoint p2, double metersPerNormUnit) {
        double dzRe = p2.latitude() - p1.latitude();
        double dzIm = p2.longitude() - p1.longitude();
        double denom = dzRe * dzRe + dzIm * dzIm;
        if (denom < 1e-12) {
            return null; // the two calibration points are (numerically) the same location
        }
        double dwRe = p2.xNorm() - p1.xNorm();
        double dwIm = p2.yNorm() - p1.yNorm();
        // Complex division k = dw / dz.
        double kRe = (dwRe * dzRe + dwIm * dzIm) / denom;
        double kIm = (dwIm * dzRe - dwRe * dzIm) / denom;
        // t = w1 - k*z1, where k*z1 = (kRe*lat1 - kIm*lon1) + i*(kRe*lon1 + kIm*lat1).
        double tRe = p1.xNorm() - (kRe * p1.latitude() - kIm * p1.longitude());
        double tIm = p1.yNorm() - (kRe * p1.longitude() + kIm * p1.latitude());
        return new GeoReference(kRe, -kIm, tRe, kIm, kRe, tIm, metersPerNormUnit);
    }

    private static GeoReference fitAffine(List<CalibrationPoint> points, double metersPerNormUnit) {
        int n = points.size();
        double sumLat = 0, sumLon = 0, sumLatLat = 0, sumLonLon = 0, sumLatLon = 0;
        double sumX = 0, sumY = 0, sumLatX = 0, sumLonX = 0, sumLatY = 0, sumLonY = 0;
        for (CalibrationPoint p : points) {
            double lat = p.latitude(), lon = p.longitude(), x = p.xNorm(), y = p.yNorm();
            sumLat += lat;
            sumLon += lon;
            sumLatLat += lat * lat;
            sumLonLon += lon * lon;
            sumLatLon += lat * lon;
            sumX += x;
            sumY += y;
            sumLatX += lat * x;
            sumLonX += lon * x;
            sumLatY += lat * y;
            sumLonY += lon * y;
        }
        // Shared design matrix (A^T A) for both the x-fit and y-fit least-squares solves - x and
        // y are independent linear regressions against the same (lat, lon, 1) predictors.
        double[][] ata = {
                {sumLatLat, sumLatLon, sumLat},
                {sumLatLon, sumLonLon, sumLon},
                {sumLat, sumLon, n}
        };
        double[] xCoeffs = solve3x3(ata, new double[]{sumLatX, sumLonX, sumX});
        double[] yCoeffs = solve3x3(ata, new double[]{sumLatY, sumLonY, sumY});
        if (xCoeffs == null || yCoeffs == null) {
            return null; // degenerate (e.g. every calibration point collinear)
        }
        return new GeoReference(xCoeffs[0], xCoeffs[1], xCoeffs[2], yCoeffs[0], yCoeffs[1], yCoeffs[2], metersPerNormUnit);
    }

    /** Gauss-Jordan elimination with partial pivoting for a 3x3 system - returns {@code null} if singular. */
    private static double[] solve3x3(double[][] a, double[] b) {
        int n = 3;
        double[][] m = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, m[i], 0, n);
            m[i][n] = b[i];
        }
        for (int col = 0; col < n; col++) {
            int pivotRow = col;
            double maxAbs = Math.abs(m[col][col]);
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(m[row][col]) > maxAbs) {
                    maxAbs = Math.abs(m[row][col]);
                    pivotRow = row;
                }
            }
            if (maxAbs < 1e-9) {
                return null;
            }
            double[] tmp = m[col];
            m[col] = m[pivotRow];
            m[pivotRow] = tmp;
            double pivotVal = m[col][col];
            for (int j = col; j <= n; j++) {
                m[col][j] /= pivotVal;
            }
            for (int row = 0; row < n; row++) {
                if (row == col) {
                    continue;
                }
                double factor = m[row][col];
                for (int j = col; j <= n; j++) {
                    m[row][j] -= factor * m[col][j];
                }
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = m[i][n];
        }
        return x;
    }

    public ImagePoint project(double latitude, double longitude) {
        double x = a * latitude + b * longitude + c;
        double y = d * latitude + e * longitude + f;
        return new ImagePoint(x, y);
    }
}
