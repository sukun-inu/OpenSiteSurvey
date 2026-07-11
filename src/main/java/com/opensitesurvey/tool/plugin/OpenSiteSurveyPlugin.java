package com.opensitesurvey.tool.plugin;

import com.opensitesurvey.tool.model.ScanSnapshot;

/**
 * Service-provider interface for third-party plugins - see {@link PluginManager} for how
 * implementations are discovered and loaded.
 *
 * <p>This is deliberately a minimal, read-only observer contract for a first version: a plugin is
 * notified of each {@link ScanSnapshot} (the same data the app's own tabs receive) and can do
 * whatever it likes with it (log it, write it elsewhere, call an external API, sound an alarm) but
 * cannot inject UI, alerts, or export formats into the host app, or otherwise change its behavior.
 * A richer extension surface (custom alert rules, UI panels, exporters) is a natural next step but
 * was intentionally left out here rather than guessing at an API shape with no real plugin to
 * validate it against yet.
 */
public interface OpenSiteSurveyPlugin {

    /** A short human-readable name shown in the "Loaded Plugins" list (Help menu). */
    String name();

    /** Called once, right after the plugin is discovered and instantiated, before any snapshot is dispatched. */
    default void onLoad() {
    }

    /**
     * Called once per scan cycle, on the WLAN poller's own background thread (not the JavaFX
     * Application thread) - the same thread {@code AlertEngine} runs on. Must not block for long:
     * a slow plugin delays every other plugin's notification for that cycle, and (since the poller
     * thread also drives the app's own scan cadence) the UI's own next refresh.
     */
    default void onScanSnapshot(ScanSnapshot snapshot) {
    }
}
