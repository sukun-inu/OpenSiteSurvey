package com.opensitesurvey.tool.util;

import com.opensitesurvey.tool.i18n.Messages;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

/**
 * Shared bits between the two independent click-pinnable hover-panel implementations in this app
 * ({@code ChartCrosshair} and {@code ChannelPlanningView}'s own hand-rolled hover panel) - both
 * wrap their entries list in an identically-configured {@link ScrollPane} and append the same
 * "pinned" suffix to their header text, but are otherwise structurally different enough (one
 * tracks a single plot-wide mouse position, the other tracks per-shape hover) that unifying them
 * into one class isn't a clean fit.
 */
public final class HoverPanelSupport {

    private HoverPanelSupport() {
    }

    /**
     * Wraps {@code content} in a {@link ScrollPane} configured for a hover-panel's entries list:
     * never squeezes content down to the viewport width/height (so a long line scrolls into view
     * instead of getting silently clipped with an ellipsis), with scrollbars shown only as needed.
     */
    public static ScrollPane scrollableEntries(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("crosshair-scroll");
        return scroll;
    }

    /** Suffix to append to a hover-panel's header text while its content is click-pinned in place. */
    public static String pausedSuffix(boolean pinned) {
        return pinned ? " " + Messages.get("common.chart.pausedSuffix") : "";
    }
}
