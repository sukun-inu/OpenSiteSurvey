package com.opensitesurvey.tool.api;

import com.opensitesurvey.tool.model.ScanSnapshot;
import com.opensitesurvey.tool.model.SurveyPoint;
import com.opensitesurvey.tool.persistence.GeoPackageExporter;
import com.opensitesurvey.tool.persistence.JsonExporter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * A minimal, read-only local REST API exposing the current Wi-Fi scan and Site Survey data as
 * JSON - deliberately built directly on the JDK's own {@code com.sun.net.httpserver.HttpServer}
 * (part of the {@code jdk.httpserver} module) rather than adding an embedded-server dependency
 * (Jetty/Undertow/etc.), consistent with this project's "no heavy dependencies, vendor what's
 * needed locally" approach elsewhere (see {@code GeoPackageExporter}, hand-rolled against
 * {@code sqlite-jdbc} rather than pulling in GeoTools).
 *
 * <p><b>Security posture:</b> always bound to the loopback address (127.0.0.1) - never reachable
 * from another machine on the network, regardless of firewall configuration - and only started at
 * all when {@code AppConfig.restApiEnabled} is turned on (default off). There is no further
 * authentication: any other local process/user session on the same machine can read this data
 * once enabled, the same trust boundary as many local developer-tool HTTP ports. This is judged an
 * acceptable default for a single-user desktop tool; it is not intended to ever be exposed beyond
 * loopback.
 *
 * <p>This sits directly on top of the existing UI-owned data (survey points, AP position
 * estimates) rather than a dedicated {@code survey-core} service layer, since that module
 * extraction has not happened yet - see the project's own roadmap notes on this tradeoff.
 */
public final class ApiServer {

    private final HttpServer httpServer;

    private ApiServer(HttpServer httpServer) {
        this.httpServer = httpServer;
    }

    /**
     * Starts the server immediately (synchronously) and returns once it's accepting connections.
     *
     * @param latestSnapshotSupplier      returns the most recent {@link ScanSnapshot}, or {@code
     *                                    null} if no scan has completed yet - safe to call from
     *                                    any thread.
     * @param surveyPointsSupplier        returns a snapshot of the current Site Survey project's
     *                                    recorded points - may block briefly to marshal onto the
     *                                    JavaFX Application thread; safe to call from any thread.
     * @param apPositionEstimatesSupplier returns the current heuristic AP position estimates -
     *                                    same threading contract as {@code surveyPointsSupplier}.
     */
    public static ApiServer start(int port,
                                   Supplier<ScanSnapshot> latestSnapshotSupplier,
                                   Supplier<List<SurveyPoint>> surveyPointsSupplier,
                                   Supplier<List<GeoPackageExporter.ApPositionRow>> apPositionEstimatesSupplier)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);

        server.createContext("/api/v1/status", jsonGetHandler(exchange -> {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("app", "OpenSiteSurvey");
            status.put("apiVersion", 1);
            return status;
        }));

        server.createContext("/api/v1/aps", jsonGetHandler(exchange -> {
            ScanSnapshot snapshot = latestSnapshotSupplier.get();
            return JsonExporter.apSnapshotRows(snapshot == null ? List.of() : snapshot.accessPoints());
        }));

        server.createContext("/api/v1/survey/points", jsonGetHandler(exchange ->
                JsonExporter.surveyPointRows(surveyPointsSupplier.get())));

        server.createContext("/api/v1/survey/ap-estimates", jsonGetHandler(exchange ->
                apPositionEstimatesSupplier.get()));

        server.setExecutor(Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "opensitesurvey-api");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        return new ApiServer(server);
    }

    public void stop() {
        httpServer.stop(0);
    }

    /** Exposed for tests - lets a test start the server on an ephemeral port (0) and read back what was actually assigned. */
    public InetSocketAddress address() {
        return httpServer.getAddress();
    }

    private interface JsonBodySupplier {
        Object get(HttpExchange exchange) throws Exception;
    }

    private static HttpHandler jsonGetHandler(JsonBodySupplier bodySupplier) {
        return exchange -> {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendPlainText(exchange, 405, "Method Not Allowed");
                    return;
                }
                Object body = bodySupplier.get(exchange);
                byte[] json = JsonExporter.toJsonBytes(body);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, json.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(json);
                }
            } catch (Exception e) {
                sendPlainText(exchange, 500, "Internal error: " + e.getMessage());
            } finally {
                exchange.close();
            }
        };
    }

    private static void sendPlainText(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
