package com.opensitesurvey.tool.ping;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Measures rough "active survey" download throughput at a recorded point by timing how many bytes
 * an HTTP GET to a user-specified URL transfers within a bounded window. This is deliberately not
 * a dedicated throughput-test protocol like iperf3 - no such tool/binary is vendored with this
 * project - just enough to attach a relative Mbps figure to each survey point the same way
 * {@link PingProbe} attaches an optional RTT, using only {@code java.net.http.HttpClient} (already
 * on the classpath, no new dependency).
 */
public final class ThroughputProbe {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private ThroughputProbe() {
    }

    /**
     * @param maxDurationMillis the request (including streaming the response body) is bounded to
     *                          this long - a large or slow-ending resource has its rate computed
     *                          from however many bytes arrived before the cutoff, rather than
     *                          blocking a survey point recording indefinitely.
     * @return measured throughput in Mbps, or empty if the URL is blank, unreachable, or no bytes
     *         were received in time.
     */
    public static Optional<Double> measure(String url, int maxDurationMillis) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        long totalBytes = 0;
        long startNanos = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofMillis(maxDurationMillis))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                return Optional.empty();
            }
            byte[] buffer = new byte[64 * 1024];
            try (InputStream in = response.body()) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    totalBytes += read;
                }
            }
        } catch (IOException e) {
            // Expected once maxDurationMillis elapses mid-stream for a large/slow resource -
            // HttpTimeoutException (itself an IOException) is the common case, but a plain
            // IOException can also surface depending on exactly when the client-side cutoff lands
            // while still reading the body - either way, fall through and compute the rate from
            // whatever arrived before the cutoff.
        } catch (Exception e) {
            return Optional.empty();
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        return computeMbps(totalBytes, elapsedNanos);
    }

    /** Exposed for testing the rate arithmetic without a real network call. */
    static Optional<Double> computeMbps(long totalBytes, long elapsedNanos) {
        if (totalBytes <= 0 || elapsedNanos <= 0) {
            return Optional.empty();
        }
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double mbps = (totalBytes * 8.0) / elapsedSeconds / 1_000_000.0;
        return Optional.of(mbps);
    }
}
