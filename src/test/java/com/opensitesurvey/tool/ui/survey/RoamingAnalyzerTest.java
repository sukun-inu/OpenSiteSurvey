package com.opensitesurvey.tool.ui.survey;

import com.opensitesurvey.tool.model.SurveyPoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoamingAnalyzerTest {

    private static SurveyPoint point(double x, double y, Map<String, Integer> rssiByBssid) {
        return new SurveyPoint(x, y, rssiByBssid, Instant.now());
    }

    @Test
    void closeSameSsidReadingsAreReportedAsOverlap() {
        SurveyPoint p = point(0.5, 0.5, Map.of("AA:AA:AA:AA:AA:AA", -60, "BB:BB:BB:BB:BB:BB", -65));
        Map<String, String> ssidByBssid = Map.of("AA:AA:AA:AA:AA:AA", "Office", "BB:BB:BB:BB:BB:BB", "Office");
        List<RoamingAnalyzer.OverlapPoint> overlaps = RoamingAnalyzer.analyze(List.of(p), ssidByBssid, 10);
        assertEquals(1, overlaps.size());
        assertEquals("Office", overlaps.get(0).ssid());
        assertEquals(5, overlaps.get(0).gapDb());
        assertEquals("AA:AA:AA:AA:AA:AA", overlaps.get(0).strongerBssid());
    }

    @Test
    void gapWiderThanMaxIsNotReported() {
        SurveyPoint p = point(0.5, 0.5, Map.of("AA:AA:AA:AA:AA:AA", -40, "BB:BB:BB:BB:BB:BB", -80));
        Map<String, String> ssidByBssid = Map.of("AA:AA:AA:AA:AA:AA", "Office", "BB:BB:BB:BB:BB:BB", "Office");
        List<RoamingAnalyzer.OverlapPoint> overlaps = RoamingAnalyzer.analyze(List.of(p), ssidByBssid, 10);
        assertTrue(overlaps.isEmpty());
    }

    @Test
    void differentSsidsAreNeverCompared() {
        SurveyPoint p = point(0.5, 0.5, Map.of("AA:AA:AA:AA:AA:AA", -60, "BB:BB:BB:BB:BB:BB", -61));
        Map<String, String> ssidByBssid = Map.of("AA:AA:AA:AA:AA:AA", "Office", "BB:BB:BB:BB:BB:BB", "Guest");
        List<RoamingAnalyzer.OverlapPoint> overlaps = RoamingAnalyzer.analyze(List.of(p), ssidByBssid, 10);
        assertTrue(overlaps.isEmpty());
    }

    @Test
    void hiddenSsidsAreNeverGroupedTogether() {
        SurveyPoint p = point(0.5, 0.5, Map.of("AA:AA:AA:AA:AA:AA", -60, "BB:BB:BB:BB:BB:BB", -61));
        Map<String, String> ssidByBssid = Map.of("AA:AA:AA:AA:AA:AA", "", "BB:BB:BB:BB:BB:BB", "");
        List<RoamingAnalyzer.OverlapPoint> overlaps = RoamingAnalyzer.analyze(List.of(p), ssidByBssid, 10);
        assertTrue(overlaps.isEmpty());
    }

    @Test
    void singleBssidPerSsidIsNeverAnOverlap() {
        SurveyPoint p = point(0.5, 0.5, Map.of("AA:AA:AA:AA:AA:AA", -60));
        Map<String, String> ssidByBssid = Map.of("AA:AA:AA:AA:AA:AA", "Office");
        List<RoamingAnalyzer.OverlapPoint> overlaps = RoamingAnalyzer.analyze(List.of(p), ssidByBssid, 10);
        assertTrue(overlaps.isEmpty());
    }

    @Test
    void onlyTheTwoStrongestOfThreeSameSsidBssidsAreCompared() {
        SurveyPoint p = point(0.5, 0.5, Map.of(
                "AA:AA:AA:AA:AA:AA", -50, "BB:BB:BB:BB:BB:BB", -55, "CC:CC:CC:CC:CC:CC", -90));
        Map<String, String> ssidByBssid = Map.of(
                "AA:AA:AA:AA:AA:AA", "Office", "BB:BB:BB:BB:BB:BB", "Office", "CC:CC:CC:CC:CC:CC", "Office");
        List<RoamingAnalyzer.OverlapPoint> overlaps = RoamingAnalyzer.analyze(List.of(p), ssidByBssid, 10);
        assertEquals(1, overlaps.size());
        assertEquals(5, overlaps.get(0).gapDb());
    }
}
