package com.opensitesurvey.tool.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensitesurvey.tool.model.ApSnapshot;
import com.opensitesurvey.tool.model.ScanSnapshot;
import com.opensitesurvey.tool.model.SurveyPoint;
import com.opensitesurvey.tool.persistence.GeoPackageExporter;
import com.opensitesurvey.tool.security.SecurityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private ApiServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    /** Binds to an OS-assigned ephemeral port (0) so tests never collide over a fixed port. */
    private ApiServer startServer(ScanSnapshot snapshot, List<SurveyPoint> points,
                                   List<GeoPackageExporter.ApPositionRow> apEstimates) throws Exception {
        server = ApiServer.start(0, () -> snapshot, () -> points, () -> apEstimates);
        return server;
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.address().getPort() + path))
                .GET()
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void bindsToLoopbackAddressOnly() throws Exception {
        startServer(null, List.of(), List.of());
        assertTrue(server.address().getAddress().isLoopbackAddress());
    }

    @Test
    void statusEndpointReturnsOk() throws Exception {
        startServer(null, List.of(), List.of());
        HttpResponse<String> response = get("/api/v1/status");
        assertEquals(200, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body());
        assertEquals("OpenSiteSurvey", body.get("app").asText());
    }

    @Test
    void apsEndpointReturnsEmptyListWhenNoScanYet() throws Exception {
        startServer(null, List.of(), List.of());
        HttpResponse<String> response = get("/api/v1/aps");
        assertEquals(200, response.statusCode());
        assertEquals(0, MAPPER.readTree(response.body()).size());
    }

    @Test
    void apsEndpointReturnsLatestSnapshotRows() throws Exception {
        ApSnapshot ap = new ApSnapshot("MySSID", "AA:BB:CC:DD:EE:FF", 6, 2437000, "2.4GHz", -55, 80,
                "802.11ac", true, SecurityType.WPA2, 30, Instant.now());
        startServer(new ScanSnapshot(Instant.now(), List.of(ap)), List.of(), List.of());
        HttpResponse<String> response = get("/api/v1/aps");
        assertEquals(200, response.statusCode());
        JsonNode rows = MAPPER.readTree(response.body());
        assertEquals(1, rows.size());
        assertEquals("MySSID", rows.get(0).get("ssid").asText());
        assertEquals("AA:BB:CC:DD:EE:FF", rows.get(0).get("bssid").asText());
        assertEquals(-55, rows.get(0).get("rssiDbm").asInt());
    }

    @Test
    void surveyPointsEndpointReturnsPointRows() throws Exception {
        SurveyPoint point = new SurveyPoint(0.3, 0.4, Map.of("AA:BB:CC:DD:EE:FF", -60), Instant.now());
        startServer(null, List.of(point), List.of());
        HttpResponse<String> response = get("/api/v1/survey/points");
        assertEquals(200, response.statusCode());
        JsonNode rows = MAPPER.readTree(response.body());
        assertEquals(1, rows.size());
        assertEquals(0.3, rows.get(0).get("xNorm").asDouble(), 1e-9);
        assertEquals(0.4, rows.get(0).get("yNorm").asDouble(), 1e-9);
    }

    @Test
    void apEstimatesEndpointReturnsRows() throws Exception {
        GeoPackageExporter.ApPositionRow row =
                new GeoPackageExporter.ApPositionRow("AA:BB:CC:DD:EE:FF", "MySSID", 5, 0.6, 0.7);
        startServer(null, List.of(), List.of(row));
        HttpResponse<String> response = get("/api/v1/survey/ap-estimates");
        assertEquals(200, response.statusCode());
        JsonNode rows = MAPPER.readTree(response.body());
        assertEquals(1, rows.size());
        assertEquals("AA:BB:CC:DD:EE:FF", rows.get(0).get("bssid").asText());
        assertEquals(5, rows.get(0).get("sampleCount").asInt());
    }

    @Test
    void nonGetMethodIsRejected() throws Exception {
        startServer(null, List.of(), List.of());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.address().getPort() + "/api/v1/status"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
    }
}
