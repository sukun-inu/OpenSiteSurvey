package com.opensitesurvey.tool.ui.survey;

import com.opensitesurvey.tool.model.SurveyPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A discretized (grid-sampled) approximation of Sibson's natural neighbor interpolation - rather
 * than exactly constructing a Voronoi diagram/Delaunay triangulation (which would need a
 * computational geometry library this project doesn't otherwise depend on, contrary to its "no
 * heavy dependencies" ethos - see {@link KrigingInterpolator}'s own javadoc), the area a
 * hypothetical Voronoi cell centered on the query point would "steal" from each neighbor's own
 * cell is estimated numerically: a fine grid of sample locations around the query point is each
 * assigned to whichever of {@code {query point} ∪ {nearest neighbors}} is closest, and the
 * fraction of samples whose closest-including-the-query-point is the query point itself (but
 * whose closest-excluding-it is a given neighbor) becomes that neighbor's weight. This converges
 * to the same result as exact Sibson's coordinates as the sample resolution grows, at the cost of
 * being an approximation at any finite resolution - an acceptable tradeoff here since the
 * underlying RSSI readings are themselves noisy.
 */
public final class NaturalNeighborInterpolator implements Interpolator {

    public static final NaturalNeighborInterpolator INSTANCE = new NaturalNeighborInterpolator();

    // Only the nearest candidates can ever be true natural neighbors of the query point, so
    // restricting the search to them keeps each interpolate() call bounded regardless of how many
    // survey points exist overall.
    private static final int NEIGHBOR_COUNT = 8;
    private static final int SAMPLE_RESOLUTION = 16; // SAMPLE_RESOLUTION^2 samples per call

    private NaturalNeighborInterpolator() {
    }

    private record Neighbor(double x, double y, double value) {
    }

    @Override
    public Double interpolate(double x, double y, List<SurveyPoint> points, String targetBssid) {
        List<Neighbor> all = new ArrayList<>();
        for (SurveyPoint p : points) {
            Integer value = p.rssiFor(targetBssid);
            if (value == null) {
                continue;
            }
            if (distanceSq(p.xNorm, p.yNorm, x, y) < 1e-9) {
                return value.doubleValue();
            }
            all.add(new Neighbor(p.xNorm, p.yNorm, value));
        }
        if (all.size() < 3) {
            // Too few surrounding points for a meaningful Voronoi-cell estimate - IDW degrades
            // more gracefully with sparse data (same fallback KrigingInterpolator uses).
            return IdwInterpolator.INSTANCE.interpolate(x, y, points, targetBssid);
        }

        all.sort(Comparator.comparingDouble(n -> distanceSq(n.x(), n.y(), x, y)));
        List<Neighbor> candidates = all.subList(0, Math.min(NEIGHBOR_COUNT, all.size()));

        double minX = x, maxX = x, minY = y, maxY = y;
        for (Neighbor n : candidates) {
            minX = Math.min(minX, n.x());
            maxX = Math.max(maxX, n.x());
            minY = Math.min(minY, n.y());
            maxY = Math.max(maxY, n.y());
        }
        // Small margin so the query point never sits exactly on the sampled region's edge.
        double marginX = Math.max((maxX - minX) * 0.1, 1e-4);
        double marginY = Math.max((maxY - minY) * 0.1, 1e-4);
        minX -= marginX;
        maxX += marginX;
        minY -= marginY;
        maxY += marginY;

        double[] stolenArea = new double[candidates.size()];
        double totalStolen = 0;
        for (int sy = 0; sy < SAMPLE_RESOLUTION; sy++) {
            double sampleY = minY + (maxY - minY) * (sy + 0.5) / SAMPLE_RESOLUTION;
            for (int sx = 0; sx < SAMPLE_RESOLUTION; sx++) {
                double sampleX = minX + (maxX - minX) * (sx + 0.5) / SAMPLE_RESOLUTION;

                double distToQuery = distanceSq(sampleX, sampleY, x, y);
                int nearestNeighborIdx = -1;
                double nearestNeighborDist = Double.MAX_VALUE;
                boolean queryIsNearest = true;
                for (int i = 0; i < candidates.size(); i++) {
                    Neighbor n = candidates.get(i);
                    double d = distanceSq(sampleX, sampleY, n.x(), n.y());
                    if (d < nearestNeighborDist) {
                        nearestNeighborDist = d;
                        nearestNeighborIdx = i;
                    }
                    if (d < distToQuery) {
                        queryIsNearest = false;
                    }
                }
                if (queryIsNearest) {
                    stolenArea[nearestNeighborIdx]++;
                    totalStolen++;
                }
            }
        }

        if (totalStolen == 0) {
            // Query point sits outside every candidate's practical influence at this sample
            // resolution (e.g. far beyond the convex hull of survey points).
            return IdwInterpolator.INSTANCE.interpolate(x, y, points, targetBssid);
        }

        double weightedSum = 0;
        for (int i = 0; i < candidates.size(); i++) {
            weightedSum += (stolenArea[i] / totalStolen) * candidates.get(i).value();
        }
        return weightedSum;
    }

    private static double distanceSq(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return dx * dx + dy * dy;
    }
}
