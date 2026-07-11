package com.opensitesurvey.tool.gps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Streams live GPS position updates by shelling out to a small generated PowerShell script that
 * drives .NET Framework's {@code System.Device.Location.GeoCoordinateWatcher}. Windows has no
 * simple CLI GPS tool to shell out to the way {@code ping}/{@code tracert} exist for {@link
 * com.opensitesurvey.tool.ping.PingProbe}/{@code TracerouteProbe} - PowerShell (which runs on
 * .NET Framework and can directly {@code Add-Type -AssemblyName System.Device}) is used as the
 * bridge instead, the same "shell out to a Windows-provided tool rather than ship a native
 * binary" approach used throughout this project.
 *
 * <p>{@code GeoCoordinateWatcher} aggregates whatever positioning source Windows has available (a
 * real GPS chip if present, otherwise Wi-Fi- or IP-based network location) - this class has no way
 * to know or control which one is actually in use. The reported {@code horizontalAccuracyMeters}
 * is the only signal of how much to trust a given reading; it can range from a few meters (real
 * GPS lock) to tens of meters or worse (network-based fallback, which is the common case indoors
 * or on a laptop with no GPS hardware at all).
 *
 * <p>All callbacks fire on a private background thread - callers must marshal to the JavaFX
 * Application thread themselves (e.g. wrap with {@code Platform.runLater}), same convention as
 * {@code WlanPoller}/{@code TraceroutePoller}.
 */
public final class GpsProbe {

    /** One position update. {@code horizontalAccuracyMeters} is Windows' own reported accuracy radius, not independently verified. */
    public record Position(double latitude, double longitude, double horizontalAccuracyMeters, long epochMillis) {
    }

    private static final Pattern POSITION_PATTERN = Pattern.compile(
            "^(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?),(\\d+)$");

    // Numbers are formatted with an explicit invariant-culture, fixed-point ("F6"/"F2") format
    // rather than a plain .NET ToString() interpolation, so the output is never accidentally
    // locale-dependent (some locales use "," as the decimal separator, which would break the CSV
    // parsing above) or in scientific notation.
    private static final String SCRIPT = """
            try {
                Add-Type -AssemblyName System.Device
                $watcher = New-Object System.Device.Location.GeoCoordinateWatcher
                Register-ObjectEvent -InputObject $watcher -EventName PositionChanged -Action {
                    $loc = $Event.SourceEventArgs.Position.Location
                    if (-not $loc.IsUnknown) {
                        $ic = [System.Globalization.CultureInfo]::InvariantCulture
                        $ts = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
                        $lat = $loc.Latitude.ToString("F6", $ic)
                        $lon = $loc.Longitude.ToString("F6", $ic)
                        $acc = $loc.HorizontalAccuracy.ToString("F2", $ic)
                        Write-Output "$lat,$lon,$acc,$ts"
                    }
                } | Out-Null
                $watcher.Start()
                while ($true) { Start-Sleep -Milliseconds 250 }
            } catch {
                Write-Output "ERROR:$($_.Exception.Message)"
            }
            """;

    private final Consumer<Position> onPosition;
    private final Consumer<String> onStatus;

    private Thread readerThread;
    private volatile Process process;
    private volatile Path scriptFile;

    /**
     * @param onPosition fired once per position update.
     * @param onStatus   fired with a human-readable message on startup failure or a script-side
     *                   error (e.g. {@code System.Device} unavailable) - not fired for ordinary
     *                   unparseable stray output lines, which are silently skipped.
     */
    public GpsProbe(Consumer<Position> onPosition, Consumer<String> onStatus) {
        this.onPosition = onPosition;
        this.onStatus = onStatus;
    }

    /** Starts streaming - safe to call again (e.g. to restart) after {@link #stop()}. */
    public synchronized void start() {
        stop();
        try {
            Path script = Files.createTempFile("opensitesurvey-gps", ".ps1");
            Files.writeString(script, SCRIPT, StandardCharsets.UTF_8);
            scriptFile = script;
            Process p = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString())
                    .redirectErrorStream(true)
                    .start();
            process = p;
            readerThread = new Thread(() -> readLoop(p), "gps-probe-reader");
            readerThread.setDaemon(true);
            readerThread.start();
        } catch (IOException e) {
            onStatus.accept("GPS probe failed to start: " + e.getMessage());
        }
    }

    private void readLoop(Process p) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Position position = parsePositionLine(line);
                if (position != null) {
                    onPosition.accept(position);
                } else if (line.startsWith("ERROR:")) {
                    onStatus.accept(line.substring("ERROR:".length()));
                }
                // Any other unparseable line (stray PowerShell warnings, etc.) is silently skipped.
            }
        } catch (IOException e) {
            // Expected once stop() force-destroys the process (closes this stream) - not an error.
        }
    }

    /** Stops streaming and releases the subprocess/temp script file. Safe to call even if not currently started. */
    public synchronized void stop() {
        Process p = process;
        if (p != null && p.isAlive()) {
            // A blocked BufferedReader#readLine() does not respond to Thread.interrupt() -
            // forcibly destroying the process is what actually unblocks it (EOF), same as
            // TraceroutePoller's discoveryProcess handling.
            p.destroyForcibly();
        }
        process = null;
        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }
        Path script = scriptFile;
        if (script != null) {
            try {
                Files.deleteIfExists(script);
            } catch (IOException ignored) {
                // best-effort cleanup only
            }
            scriptFile = null;
        }
    }

    /** Package-visible for testing without a real subprocess - mirrors {@code PingProbe.parseRttMillis}. */
    static Position parsePositionLine(String line) {
        if (line == null) {
            return null;
        }
        Matcher m = POSITION_PATTERN.matcher(line.trim());
        if (!m.matches()) {
            return null;
        }
        try {
            double lat = Double.parseDouble(m.group(1));
            double lon = Double.parseDouble(m.group(2));
            double accuracy = Double.parseDouble(m.group(3));
            long epochMillis = Long.parseLong(m.group(4));
            return new Position(lat, lon, accuracy, epochMillis);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
