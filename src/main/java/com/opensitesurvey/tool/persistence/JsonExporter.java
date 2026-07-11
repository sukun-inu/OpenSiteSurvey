package com.opensitesurvey.tool.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.opensitesurvey.tool.model.ApSnapshot;
import com.opensitesurvey.tool.model.SurveyPoint;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON export for AP snapshots and survey points. Builds plain {@code Map}/{@code List}
 * structures with timestamps pre-formatted as ISO-8601 strings rather than adding the
 * jackson-datatype-jsr310 module just to serialize {@link Instant} fields.
 */
public final class JsonExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private JsonExporter() {
    }

    public static void exportApSnapshots(List<ApSnapshot> aps, File file) throws IOException {
        MAPPER.writeValue(file, apSnapshotRows(aps));
    }

    public static void exportSurveyPoints(List<SurveyPoint> points, File file) throws IOException {
        MAPPER.writeValue(file, surveyPointRows(points));
    }

    /** Same row shape as {@link #exportApSnapshots}, exposed for callers (e.g. the REST API) that need the JSON bytes rather than a file. */
    public static List<Map<String, Object>> apSnapshotRows(List<ApSnapshot> aps) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApSnapshot ap : aps) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp", ap.timestamp().toString());
            row.put("ssid", ap.ssid());
            row.put("bssid", ap.bssid());
            row.put("channel", ap.channel());
            row.put("band", ap.band());
            row.put("rssiDbm", ap.rssiDbm());
            row.put("linkQuality", ap.linkQuality());
            row.put("phyType", ap.phyType());
            row.put("securityType", ap.securityType().name());
            row.put("privacyEnabled", ap.privacyEnabled());
            row.put("ehtCapable", ap.ehtCapable());
            row.put("mloCapable", ap.mloCapable());
            rows.add(row);
        }
        return rows;
    }

    /** Same row shape as {@link #exportSurveyPoints}, exposed for callers (e.g. the REST API) that need the JSON bytes rather than a file. */
    public static List<Map<String, Object>> surveyPointRows(List<SurveyPoint> points) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SurveyPoint p : points) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp", Instant.ofEpochSecond(p.epochSecond).toString());
            row.put("xNorm", p.xNorm);
            row.put("yNorm", p.yNorm);
            row.put("rssiByBssid", p.rssiByBssid);
            row.put("pingHost", p.pingHost);
            row.put("pingRttMs", p.pingRttMs);
            row.put("throughputMbps", p.throughputMbps);
            rows.add(row);
        }
        return rows;
    }

    public static byte[] toJsonBytes(Object value) throws IOException {
        return MAPPER.writeValueAsBytes(value);
    }
}
