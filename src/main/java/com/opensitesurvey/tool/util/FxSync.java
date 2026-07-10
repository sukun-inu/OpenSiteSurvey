package com.opensitesurvey.tool.util;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Marshals a read of JavaFX-Application-thread-owned state onto that thread and blocks the caller
 * until it completes - for callers (e.g. an HTTP handler thread in {@code
 * com.opensitesurvey.tool.api.ApiServer}) that need a consistent snapshot of UI-held data (like
 * {@code SurveyView}'s recorded points) without that view needing its own synchronized/volatile
 * fields just to support cross-thread reads.
 */
public final class FxSync {

    private FxSync() {
    }

    /**
     * @throws RuntimeException if {@code action} throws, or the JavaFX Application thread doesn't
     *                          respond within 3 seconds (e.g. it's stuck/shutting down) - callers
     *                          should treat this as "temporarily unavailable" rather than crash.
     */
    public static <T> T callAndWait(Callable<T> action) {
        if (Platform.isFxApplicationThread()) {
            try {
                return action.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(action.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Timed out waiting for the JavaFX Application thread", e);
        }
    }
}
