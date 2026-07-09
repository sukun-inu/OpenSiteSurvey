package com.waj.tool.ui.survey;

import com.waj.tool.i18n.Messages;
import com.waj.tool.model.ApSnapshot;
import com.waj.tool.model.ScanSnapshot;
import com.waj.tool.model.SurveyPoint;
import com.waj.tool.model.SurveyProject;
import com.waj.tool.persistence.CsvExporter;
import com.waj.tool.persistence.JsonExporter;
import com.waj.tool.persistence.SurveyProjectStore;
import com.waj.tool.ping.PingProbe;
import com.waj.tool.report.HtmlReportGenerator;
import com.waj.tool.report.PdfReportGenerator;
import com.waj.tool.report.ReportData;
import com.waj.tool.util.AppTheme;
import com.waj.tool.util.SignalColorScale;
import com.waj.tool.util.TooltipSupport;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Floor-plan site survey: click a point on a loaded floor plan to record the current Wi-Fi
 * snapshot there, then render an IDW-interpolated coverage heatmap for a chosen target AP.
 */
public final class SurveyView {

    private static final String STRONGEST_KEY = "";

    private final BorderPane root = new BorderPane();
    private final Canvas canvas = new Canvas(900, 600);
    private final StackPane canvasHolder = new StackPane(canvas);
    private final ComboBox<String> targetSelector = new ComboBox<>();
    private final ToggleButton surveyModeToggle = new ToggleButton(Messages.get("survey.toggle.measureModeOff"));
    private final Label statusLabel = new Label(Messages.get("survey.status.loadFloorPlanPrompt"));

    private final TextField pingHostField = new TextField();
    private final ExecutorService pingExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "survey-ping");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, String> ssidByBssid = new HashMap<>();
    private final List<SurveyPoint> points = new ArrayList<>();

    private Image floorPlanImage;
    private String floorPlanPath;
    // Raw source bytes of the currently loaded floor plan, embedded into saved projects (see
    // SurveyProject.floorPlanImageBase64) - null when the source file couldn't be read (project
    // save then falls back to floorPlanPath-only, same as this app's original behavior).
    private byte[] floorPlanImageBytes;
    private ScanSnapshot latestSnapshot;

    // Coverage-hole highlighting: a checkbox + dBm threshold field, redrawn as diagonal hatching
    // over any heatmap-grid cell whose interpolated RSSI falls below the threshold.
    private final CheckBox coverageHoleToggle = new CheckBox(Messages.get("survey.toggle.coverageHoles"));
    private final TextField coverageThresholdField = new TextField("-75");
    private final Label coverageSummaryLabel = new Label("");

    // Before/after comparison: a second project's points, loaded independently of the current
    // floor plan/points, used only as a baseline for a delta heatmap (see HeatmapRenderer.renderDelta).
    private List<SurveyPoint> comparisonPoints;
    private String comparisonProjectName;
    private final ToggleButton comparisonModeToggle = new ToggleButton(Messages.get("survey.toggle.comparisonMode"));
    private final Label comparisonSummaryLabel = new Label("");

    private final Supplier<String> interfaceDescriptionSupplier;

    public SurveyView(String defaultPingHost, Supplier<String> interfaceDescriptionSupplier) {
        this.interfaceDescriptionSupplier = interfaceDescriptionSupplier;
        pingHostField.setText(defaultPingHost == null ? "" : defaultPingHost);
        buildToolbar();
        buildCanvas();
        // Wrapped in a holder (not the canvas directly) so the floor plan scales up/down with the
        // window instead of staying pinned at whatever size it happened to load at - Canvas isn't
        // resizable by JavaFX's own layout system, so this has to be driven explicitly.
        canvasHolder.widthProperty().addListener((obs, old, val) -> resizeCanvasToFit());
        canvasHolder.heightProperty().addListener((obs, old, val) -> resizeCanvasToFit());
        root.setCenter(canvasHolder);
        root.setRight(buildLegend());
        root.setBottom(statusLabel);
    }

    public javafx.scene.Node getRoot() {
        return root;
    }

    private void buildToolbar() {
        Button loadFloorPlanButton = new Button(Messages.get("survey.button.openFloorPlan"));
        loadFloorPlanButton.setOnAction(e -> onLoadFloorPlan());
        TooltipSupport.set(loadFloorPlanButton, Messages.get("tooltip.survey.loadFloorPlan"));

        targetSelector.getItems().add(STRONGEST_KEY);
        targetSelector.getSelectionModel().select(STRONGEST_KEY);
        targetSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(String bssid) {
                if (bssid == null || bssid.isEmpty()) {
                    return Messages.get("survey.target.strongestAuto");
                }
                String ssid = ssidByBssid.getOrDefault(bssid, "");
                return (ssid.isEmpty() ? "<hidden>" : ssid) + " (" + bssid + ")";
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        });
        targetSelector.setOnAction(e -> redraw());
        TooltipSupport.set(targetSelector, Messages.get("tooltip.survey.targetSelector"));

        surveyModeToggle.setOnAction(e -> surveyModeToggle.setText(
                surveyModeToggle.isSelected()
                        ? Messages.get("survey.toggle.measureModeOn") : Messages.get("survey.toggle.measureModeOff")));
        TooltipSupport.set(surveyModeToggle, Messages.get("tooltip.survey.measureMode"));

        Button clearButton = new Button(Messages.get("survey.button.clearPoints"));
        clearButton.setOnAction(e -> onClearPoints());
        TooltipSupport.set(clearButton, Messages.get("tooltip.survey.clearPoints"));

        Button saveButton = new Button(Messages.get("survey.button.saveProject"));
        saveButton.setOnAction(e -> onSaveProject());
        TooltipSupport.set(saveButton, Messages.get("tooltip.survey.saveProject"));

        Button loadButton = new Button(Messages.get("survey.button.loadProject"));
        loadButton.setOnAction(e -> onLoadProject());
        TooltipSupport.set(loadButton, Messages.get("tooltip.survey.loadProject"));

        Button exportCsvButton = new Button(Messages.get("survey.button.exportPointsCsv"));
        exportCsvButton.setOnAction(e -> exportPoints(true));
        TooltipSupport.set(exportCsvButton, Messages.get("tooltip.survey.exportPointsCsv"));
        Button exportJsonButton = new Button(Messages.get("survey.button.exportPointsJson"));
        exportJsonButton.setOnAction(e -> exportPoints(false));
        TooltipSupport.set(exportJsonButton, Messages.get("tooltip.survey.exportPointsJson"));

        pingHostField.setPromptText(Messages.get("survey.pingHost.prompt"));
        pingHostField.setPrefWidth(130);
        TooltipSupport.set(pingHostField, Messages.get("tooltip.survey.pingHost"));

        Button reportHtmlButton = new Button(Messages.get("survey.button.exportReportHtml"));
        reportHtmlButton.setOnAction(e -> exportReport(true));
        TooltipSupport.set(reportHtmlButton, Messages.get("tooltip.survey.reportHtml"));
        Button reportPdfButton = new Button(Messages.get("survey.button.exportReportPdf"));
        reportPdfButton.setOnAction(e -> exportReport(false));
        TooltipSupport.set(reportPdfButton, Messages.get("tooltip.survey.reportPdf"));

        coverageHoleToggle.setOnAction(e -> redraw());
        TooltipSupport.set(coverageHoleToggle, Messages.get("tooltip.survey.coverageHoleToggle"));
        coverageThresholdField.setPrefWidth(50);
        coverageThresholdField.setOnAction(e -> redraw());
        coverageThresholdField.focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                redraw();
            }
        });
        TooltipSupport.set(coverageThresholdField, Messages.get("tooltip.survey.coverageThreshold"));

        Button loadComparisonButton = new Button(Messages.get("survey.button.loadComparison"));
        loadComparisonButton.setOnAction(e -> onLoadComparison());
        TooltipSupport.set(loadComparisonButton, Messages.get("tooltip.survey.loadComparison"));
        comparisonModeToggle.setOnAction(e -> {
            if (comparisonModeToggle.isSelected() && comparisonPoints == null) {
                comparisonModeToggle.setSelected(false);
                showAlert(Messages.get("survey.comparison.noBaseline"));
                return;
            }
            redraw();
        });
        TooltipSupport.set(comparisonModeToggle, Messages.get("tooltip.survey.comparisonToggle"));

        HBox toolbarRow1 = new HBox(8, loadFloorPlanButton, new Label(Messages.get("survey.label.targetAp")), targetSelector,
                surveyModeToggle, new Label(Messages.get("survey.label.ping")), pingHostField,
                clearButton, saveButton, loadButton, exportCsvButton, exportJsonButton,
                reportHtmlButton, reportPdfButton);
        toolbarRow1.setAlignment(Pos.CENTER_LEFT);

        HBox toolbarRow2 = new HBox(8, coverageHoleToggle, new Label(Messages.get("survey.label.coverageThreshold")),
                coverageThresholdField, loadComparisonButton, comparisonModeToggle);
        toolbarRow2.setAlignment(Pos.CENTER_LEFT);

        VBox toolbar = new VBox(4, toolbarRow1, toolbarRow2);
        toolbar.setPadding(new Insets(8));
        root.setTop(toolbar);
    }

    private void buildCanvas() {
        TooltipSupport.install(canvas, Messages.get("tooltip.survey.canvas"));
        canvas.setOnMouseClicked(e -> {
            if (!surveyModeToggle.isSelected()) {
                return;
            }
            if (floorPlanImage == null) {
                showAlert(Messages.get("survey.alert.floorPlanRequired"));
                return;
            }
            if (latestSnapshot == null) {
                showAlert(Messages.get("survey.alert.noWifiDataYet"));
                return;
            }
            double xNorm = e.getX() / canvas.getWidth();
            double yNorm = e.getY() / canvas.getHeight();
            Map<String, Integer> rssiByBssid = new LinkedHashMap<>();
            for (ApSnapshot ap : latestSnapshot.accessPoints()) {
                rssiByBssid.put(ap.bssid(), ap.rssiDbm());
            }
            SurveyPoint point = new SurveyPoint(xNorm, yNorm, rssiByBssid, Instant.now());
            points.add(point);
            statusLabel.setText(Messages.get("survey.status.pointCount", points.size()));
            redraw();

            String pingHost = pingHostField.getText() == null ? "" : pingHostField.getText().trim();
            if (!pingHost.isEmpty()) {
                point.pingHost = pingHost;
                pingExecutor.execute(() -> {
                    Integer rttMs = PingProbe.ping(pingHost, 1000).orElse(null);
                    Platform.runLater(() -> {
                        point.pingRttMs = rttMs;
                        String pingResult = rttMs != null
                                ? Messages.get("survey.ping.rttMs", rttMs) : Messages.get("survey.ping.noResponse");
                        statusLabel.setText(Messages.get("survey.status.pointCountWithPing", points.size(), pingHost, pingResult));
                    });
                });
            }
        });
    }

    private javafx.scene.Node buildLegend() {
        VBox legend = new VBox(4);
        legend.setPadding(new Insets(8));
        legend.getChildren().add(new Label(Messages.get("survey.legend.title")));
        int[] samples = {-45, -55, -65, -75, -90};
        String[] labels = {
                Messages.get("survey.legend.excellent"), Messages.get("survey.legend.good"),
                Messages.get("survey.legend.fair"), Messages.get("survey.legend.poor"),
                Messages.get("survey.legend.outOfRange")
        };
        for (int i = 0; i < samples.length; i++) {
            Rectangle rect = new Rectangle(16, 16, SignalColorScale.colorFor(samples[i]));
            HBox row = new HBox(6, rect, new Label(labels[i]));
            row.setAlignment(Pos.CENTER_LEFT);
            legend.getChildren().add(row);
        }
        coverageSummaryLabel.setWrapText(true);
        coverageSummaryLabel.setMaxWidth(180);
        comparisonSummaryLabel.setWrapText(true);
        comparisonSummaryLabel.setMaxWidth(180);
        legend.getChildren().addAll(coverageSummaryLabel, comparisonSummaryLabel);
        return legend;
    }

    private void onLoadComparison() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("survey.chooser.loadComparison"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAJ Survey Project", "*.json"));
        File file = chooser.showOpenDialog(currentStage());
        if (file == null) {
            return;
        }
        try {
            SurveyProject project = SurveyProjectStore.load(file);
            comparisonPoints = project.points;
            comparisonProjectName = file.getName();
            statusLabel.setText(Messages.get("survey.comparison.loaded", comparisonProjectName, comparisonPoints.size()));
            redraw();
        } catch (Exception e) {
            showAlert(Messages.get("survey.alert.loadFailed", e.getMessage()));
        }
    }

    /** Must be called on the JavaFX Application thread. */
    public void onSnapshot(ScanSnapshot snapshot) {
        this.latestSnapshot = snapshot;
        boolean newTarget = false;
        for (ApSnapshot ap : snapshot.accessPoints()) {
            ssidByBssid.put(ap.bssid(), ap.ssid());
            if (!targetSelector.getItems().contains(ap.bssid())) {
                targetSelector.getItems().add(ap.bssid());
                newTarget = true;
            }
        }
        if (newTarget) {
            targetSelector.setButtonCell(null); // force converter re-render of the button area
        }
    }

    private void onLoadFloorPlan() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("survey.chooser.selectFloorPlan"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Messages.get("survey.chooser.imageFiles"), "*.png", "*.jpg", "*.jpeg", "*.bmp"));
        File file = chooser.showOpenDialog(currentStage());
        if (file == null) {
            return;
        }
        if (!confirmDiscardExistingPoints()) {
            return;
        }
        loadFloorPlanFile(file);
        points.clear();
        statusLabel.setText(Messages.get("survey.status.floorPlanLoaded", file.getName()));
        redraw();
    }

    /**
     * "点をクリア" already confirms before discarding recorded points - loading a new floor plan
     * or project silently did the exact same discard with no warning at all, so a user reloading
     * a floor plan (e.g. to fix a wrong image) could lose an entire survey's worth of points with
     * one misplaced click. Only asks when there's actually something to lose.
     */
    private boolean confirmDiscardExistingPoints() {
        if (points.isEmpty()) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                Messages.get("survey.confirm.discardPoints", points.size()));
        alert.setTitle(Messages.get("common.dialog.title.confirm"));
        alert.setHeaderText(null);
        AppTheme.apply(alert);
        return alert.showAndWait().map(r -> r.getButtonData().isDefaultButton()).orElse(false);
    }

    private void loadFloorPlanFile(File file) {
        Image image = new Image(file.toURI().toString());
        this.floorPlanImage = image;
        this.floorPlanPath = file.getAbsolutePath();
        try {
            this.floorPlanImageBytes = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            this.floorPlanImageBytes = null;
        }
        resizeCanvasToFit();
    }

    /** Restores a floor plan embedded in a loaded project (see {@link #onLoadProject()}). */
    private void loadFloorPlanFromBytes(byte[] bytes, String originalPath) {
        this.floorPlanImage = new Image(new ByteArrayInputStream(bytes));
        this.floorPlanImageBytes = bytes;
        this.floorPlanPath = originalPath;
        resizeCanvasToFit();
    }

    /**
     * Sizes the canvas to the largest scale of {@link #floorPlanImage} that still fits inside
     * {@link #canvasHolder}'s current size (letterboxed, aspect-ratio preserved - not stretched
     * to fill), then redraws. Called both right after loading a floor plan and whenever the
     * holder resizes (window resize, SplitPane divider drag), so the survey view keeps making use
     * of however much space is actually available instead of staying pinned at load-time size.
     */
    private void resizeCanvasToFit() {
        if (floorPlanImage == null) {
            return;
        }
        double maxW = canvasHolder.getWidth() > 0 ? canvasHolder.getWidth() : 1000;
        double maxH = canvasHolder.getHeight() > 0 ? canvasHolder.getHeight() : 700;
        double scale = Math.min(maxW / floorPlanImage.getWidth(), maxH / floorPlanImage.getHeight());
        scale = Math.min(scale, 1.0);
        canvas.setWidth(floorPlanImage.getWidth() * scale);
        canvas.setHeight(floorPlanImage.getHeight() * scale);
        redraw();
    }

    private void onClearPoints() {
        if (points.isEmpty()) {
            showAlert(Messages.get("survey.alert.noPointsToDelete"));
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                Messages.get("survey.confirm.clearAllPoints", points.size()));
        alert.setTitle(Messages.get("common.dialog.title.confirm"));
        alert.setHeaderText(null);
        AppTheme.apply(alert);
        alert.showAndWait().ifPresent(response -> {
            if (response.getButtonData().isDefaultButton()) {
                points.clear();
                statusLabel.setText(Messages.get("survey.status.pointCount", 0));
                redraw();
            }
        });
    }

    private void onSaveProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("survey.chooser.saveProject"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAJ Survey Project", "*.wajproj.json"));
        File file = chooser.showSaveDialog(currentStage());
        if (file == null) {
            return;
        }
        try {
            SurveyProject project = new SurveyProject(floorPlanPath, 0.0, points);
            if (floorPlanImageBytes != null) {
                project.floorPlanImageBase64 = Base64.getEncoder().encodeToString(floorPlanImageBytes);
            }
            SurveyProjectStore.save(project, file);
            statusLabel.setText(Messages.get("survey.status.saved", file.getName()));
        } catch (Exception e) {
            showAlert(Messages.get("survey.alert.saveFailed", e.getMessage()));
        }
    }

    private void onLoadProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("survey.chooser.loadProject"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAJ Survey Project", "*.json"));
        File file = chooser.showOpenDialog(currentStage());
        if (file == null) {
            return;
        }
        if (!confirmDiscardExistingPoints()) {
            return;
        }
        try {
            SurveyProject project = SurveyProjectStore.load(file);
            if (project.floorPlanImageBase64 != null && !project.floorPlanImageBase64.isEmpty()) {
                loadFloorPlanFromBytes(Base64.getDecoder().decode(project.floorPlanImageBase64), project.floorPlanPath);
            } else if (project.floorPlanPath != null) {
                // Older project file (saved before the floor plan was embedded) - fall back to
                // resolving the absolute path it was saved with.
                File floorPlanFile = new File(project.floorPlanPath);
                if (floorPlanFile.exists()) {
                    loadFloorPlanFile(floorPlanFile);
                } else {
                    showAlert(Messages.get("survey.alert.floorPlanNotFound", project.floorPlanPath));
                }
            }
            points.clear();
            points.addAll(project.points);
            statusLabel.setText(Messages.get("survey.status.projectLoaded", file.getName(), points.size()));
            redraw();
        } catch (Exception e) {
            showAlert(Messages.get("survey.alert.loadFailed", e.getMessage()));
        }
    }

    private void exportPoints(boolean csv) {
        if (points.isEmpty()) {
            showAlert(Messages.get("survey.alert.noPointsToExport"));
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("survey.chooser.exportPoints"));
        chooser.getExtensionFilters().add(csv
                ? new FileChooser.ExtensionFilter("CSV", "*.csv")
                : new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = chooser.showSaveDialog(currentStage());
        if (file == null) {
            return;
        }
        try {
            if (csv) {
                CsvExporter.exportSurveyPoints(points, file);
            } else {
                JsonExporter.exportSurveyPoints(points, file);
            }
            statusLabel.setText(Messages.get("survey.status.pointsExported", file.getName()));
        } catch (Exception e) {
            showAlert(Messages.get("common.export.failed", e.getMessage()));
        }
    }

    private void exportReport(boolean html) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Messages.get("survey.chooser.exportReport"));
        chooser.getExtensionFilters().add(html
                ? new FileChooser.ExtensionFilter("HTML", "*.html")
                : new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = chooser.showSaveDialog(currentStage());
        if (file == null) {
            return;
        }
        List<ApSnapshot> aps = latestSnapshot != null ? latestSnapshot.accessPoints() : List.of();
        Image snapshot = floorPlanImage != null ? canvas.snapshot(null, null) : null;
        ReportData data = new ReportData(Instant.now(), interfaceDescriptionSupplier.get(), aps, points, snapshot);
        try {
            if (html) {
                HtmlReportGenerator.generate(data, file);
            } else {
                PdfReportGenerator.generate(data, file);
            }
            statusLabel.setText(Messages.get("survey.status.reportExported", file.getName()));
        } catch (Exception e) {
            showAlert(Messages.get("survey.alert.reportExportFailed", e.getMessage()));
        }
    }

    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        if (floorPlanImage != null) {
            gc.drawImage(floorPlanImage, 0, 0, canvas.getWidth(), canvas.getHeight());
        } else {
            gc.setFill(Color.web("#333333"));
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        }

        String target = targetSelector.getSelectionModel().getSelectedItem();
        String targetBssid = (target == null || target.isEmpty()) ? null : target;
        boolean comparing = comparisonModeToggle.isSelected() && comparisonPoints != null;

        if (!points.isEmpty()) {
            javafx.scene.image.WritableImage heatmap = comparing
                    ? HeatmapRenderer.renderDelta(points, comparisonPoints, targetBssid)
                    : HeatmapRenderer.render(points, targetBssid);
            gc.setImageSmoothing(true);
            gc.drawImage(heatmap, 0, 0, HeatmapRenderer.GRID_WIDTH, HeatmapRenderer.GRID_HEIGHT,
                    0, 0, canvas.getWidth(), canvas.getHeight());
        }

        if (coverageHoleToggle.isSelected() && !points.isEmpty()) {
            drawCoverageHoles(gc, targetBssid);
        } else {
            coverageSummaryLabel.setText("");
        }
        updateComparisonSummary(targetBssid, comparing);

        for (SurveyPoint p : points) {
            double px = p.xNorm * canvas.getWidth();
            double py = p.yNorm * canvas.getHeight();
            // A dark halo behind the white/black marker so it stays visible regardless of
            // whatever heatmap color happens to sit underneath it (yellow/green markers on a
            // yellow/green heatmap region were hard to spot before this).
            gc.setFill(Color.web("#1e2228"));
            gc.fillOval(px - 6, py - 6, 12, 12);
            gc.setFill(Color.WHITE);
            gc.setStroke(Color.BLACK);
            gc.fillOval(px - 4, py - 4, 8, 8);
            gc.strokeOval(px - 4, py - 4, 8, 8);
        }
    }

    /**
     * Overlays a diagonal "X" hatch on every heatmap-grid cell (sampled at a coarser stride than
     * {@link HeatmapRenderer}'s own pixel grid, so the marks read as a hazard-stripe pattern rather
     * than a solid block) whose IDW-interpolated RSSI falls below {@link #coverageThresholdField}.
     * Also updates {@link #coverageSummaryLabel} with the fraction of surveyed area affected. An
     * unparsable threshold falls back to -75dBm (this app's own default RSSI alert threshold)
     * rather than silently disabling the feature.
     */
    private void drawCoverageHoles(GraphicsContext gc, String targetBssid) {
        double thresholdDbm;
        try {
            thresholdDbm = Double.parseDouble(coverageThresholdField.getText().trim());
        } catch (NumberFormatException e) {
            thresholdDbm = -75.0;
        }
        int stride = 6; // heatmap-grid cells per hatch mark
        double cellW = canvas.getWidth() / HeatmapRenderer.GRID_WIDTH * stride;
        double cellH = canvas.getHeight() / HeatmapRenderer.GRID_HEIGHT * stride;
        gc.setStroke(Color.web("#ffffff"));
        gc.setLineWidth(1.3);
        int coveredCells = 0;
        int holeCells = 0;
        for (int gy = 0; gy < HeatmapRenderer.GRID_HEIGHT; gy += stride) {
            double ny = (gy + stride / 2.0) / HeatmapRenderer.GRID_HEIGHT;
            for (int gx = 0; gx < HeatmapRenderer.GRID_WIDTH; gx += stride) {
                double nx = (gx + stride / 2.0) / HeatmapRenderer.GRID_WIDTH;
                Double value = IdwInterpolator.interpolate(nx, ny, points, targetBssid);
                if (value == null) {
                    continue;
                }
                coveredCells++;
                if (value < thresholdDbm) {
                    holeCells++;
                    double px = gx * (canvas.getWidth() / HeatmapRenderer.GRID_WIDTH);
                    double py = gy * (canvas.getHeight() / HeatmapRenderer.GRID_HEIGHT);
                    gc.strokeLine(px, py, px + cellW, py + cellH);
                    gc.strokeLine(px + cellW, py, px, py + cellH);
                }
            }
        }
        double coveragePercent = coveredCells == 0 ? 0 : 100.0 * (coveredCells - holeCells) / coveredCells;
        double holePercent = coveredCells == 0 ? 0 : 100.0 * holeCells / coveredCells;
        coverageSummaryLabel.setText(Messages.get("survey.coverage.summary", coveragePercent, holePercent));
    }

    /** Updates {@link #comparisonSummaryLabel} with avg/best/worst RSSI delta at each current point vs. the loaded baseline. */
    private void updateComparisonSummary(String targetBssid, boolean comparing) {
        if (!comparing) {
            comparisonSummaryLabel.setText("");
            return;
        }
        double sum = 0;
        int n = 0;
        double best = Double.NEGATIVE_INFINITY;
        double worst = Double.POSITIVE_INFINITY;
        for (SurveyPoint p : points) {
            Integer current = p.rssiFor(targetBssid);
            Double baseline = IdwInterpolator.interpolate(p.xNorm, p.yNorm, comparisonPoints, targetBssid);
            if (current == null || baseline == null) {
                continue;
            }
            double delta = current - baseline;
            sum += delta;
            n++;
            best = Math.max(best, delta);
            worst = Math.min(worst, delta);
        }
        if (n == 0) {
            comparisonSummaryLabel.setText(Messages.get("survey.comparison.noOverlap"));
            return;
        }
        comparisonSummaryLabel.setText(Messages.get("survey.comparison.legend") + "\n"
                + Messages.get("survey.comparison.summary", sum / n, best, worst, comparisonProjectName));
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.setTitle(Messages.get("common.dialog.title.warning"));
        alert.setHeaderText(null);
        AppTheme.apply(alert);
        alert.showAndWait();
    }

    private Stage currentStage() {
        return (Stage) root.getScene().getWindow();
    }
}
