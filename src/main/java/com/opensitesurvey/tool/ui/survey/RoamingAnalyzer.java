package com.opensitesurvey.tool.ui.survey;

import com.opensitesurvey.tool.model.SurveyPoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flags survey points where a client is likely to roam between two access points sharing the same
 * SSID - a "roaming boundary" - by looking, at each recorded point, for the two strongest BSSIDs of
 * a given SSID being within {@code maxGapDb} of each other. This is a static, single-snapshot
 * approximation: a real roaming decision depends on the connected client's own roaming
 * aggressiveness/thresholds (which this app cannot observe), so a reported boundary means "both
 * APs are plausibly competitive here," not "a client will definitely roam here."
 *
 * <p>Points with only a hidden/unknown SSID (recorded as an empty string in {@code ssidByBssid})
 * are skipped entirely, rather than grouping every hidden-SSID AP together as if they were one
 * network - an empty SSID string carries no identity to group by.
 */
public final class RoamingAnalyzer {

    /** Two same-SSID BSSIDs both visible at one point, close enough in RSSI to be a roaming boundary. */
    public record OverlapPoint(double xNorm, double yNorm, String ssid,
                                String strongerBssid, int strongerRssi,
                                String weakerBssid, int weakerRssi) {
        public int gapDb() {
            return strongerRssi - weakerRssi;
        }
    }

    private RoamingAnalyzer() {
    }

    public static List<OverlapPoint> analyze(List<SurveyPoint> points, Map<String, String> ssidByBssid, int maxGapDb) {
        List<OverlapPoint> result = new ArrayList<>();
        for (SurveyPoint p : points) {
            Map<String, List<Map.Entry<String, Integer>>> bySsid = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : p.rssiByBssid.entrySet()) {
                String ssid = ssidByBssid.get(e.getKey());
                if (ssid == null || ssid.isEmpty()) {
                    continue;
                }
                bySsid.computeIfAbsent(ssid, k -> new ArrayList<>()).add(e);
            }
            for (Map.Entry<String, List<Map.Entry<String, Integer>>> group : bySsid.entrySet()) {
                List<Map.Entry<String, Integer>> entries = group.getValue();
                if (entries.size() < 2) {
                    continue;
                }
                entries.sort((a, b) -> b.getValue() - a.getValue());
                Map.Entry<String, Integer> strongest = entries.get(0);
                Map.Entry<String, Integer> second = entries.get(1);
                int gap = strongest.getValue() - second.getValue();
                if (gap <= maxGapDb) {
                    result.add(new OverlapPoint(p.xNorm, p.yNorm, group.getKey(),
                            strongest.getKey(), strongest.getValue(),
                            second.getKey(), second.getValue()));
                }
            }
        }
        return result;
    }
}
