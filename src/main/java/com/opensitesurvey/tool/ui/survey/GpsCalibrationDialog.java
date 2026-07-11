package com.opensitesurvey.tool.ui.survey;

import com.opensitesurvey.tool.gps.GeoReference;
import com.opensitesurvey.tool.gps.GpsProbe;
import com.opensitesurvey.tool.i18n.Messages;
import com.opensitesurvey.tool.util.AppTheme;
import com.opensitesurvey.tool.util.TooltipSupport;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Modal dialog for calibrating {@link GeoReference}: click 2 or more reference points on the
 * currently-loaded background image (floor plan, or a user-supplied outdoor map/satellite
 * screenshot) and pair each with a real-world latitude/longitude - either captured live from GPS
 * or typed manually (the fallback most indoor surveys will need, since GPS commonly can't get a
 * fix indoors at all).
 */
public final class GpsCalibrationDialog {

    /** @param geoReference the fitted transform; {@code calibrationPoints} is what should be persisted into {@code SurveyProject}. */
    public record Result(GeoReference geoReference, List<GeoReference.CalibrationPoint> calibrationPoints) {
    }

    private static final double PREVIEW_MAX_WIDTH = 640;
    private static final double PREVIEW_MAX_HEIGHT = 480;

    private GpsCalibrationDialog() {
    }

    public static Optional<Result> show(Window owner, Image backgroundImage, List<GeoReference.CalibrationPoint> existingPoints) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(Messages.get("survey.gpsCalibration.title"));

        List<GeoReference.CalibrationPoint> points = new ArrayList<>(existingPoints);
        Result[] resultHolder = new Result[1];

        double scale = Math.min(PREVIEW_MAX_WIDTH / backgroundImage.getWidth(), PREVIEW_MAX_HEIGHT / backgroundImage.getHeight());
        scale = Math.min(scale, 1.0);
        double previewWidth = backgroundImage.getWidth() * scale;
        double previewHeight = backgroundImage.getHeight() * scale;

        ImageView imageView = new ImageView(backgroundImage);
        imageView.setFitWidth(previewWidth);
        imageView.setFitHeight(previewHeight);
        // A plain Pane, not StackPane - StackPane centers each child by adjusting its layoutX/Y to
        // compensate for the child's own local bounds, which would silently cancel out every
        // marker.setCenterX()/setCenterY() call below (a Circle's bounds shift with centerX/Y, and
        // StackPane just re-centers around that shifted shape instead of actually moving it).
        Pane imageStack = new Pane(imageView);
        imageStack.setPrefSize(previewWidth, previewHeight);

        double[] pendingClick = new double[2]; // xNorm, yNorm
        boolean[] hasPendingClick = {false};
        Circle marker = new Circle(5, Color.web("#ff9f1c"));
        marker.setVisible(false);
        imageStack.getChildren().add(marker);

        Label gpsStatusLabel = new Label(Messages.get("survey.gpsCalibration.waitingForFix"));
        TextField latField = new TextField();
        TextField lonField = new TextField();
        latField.setPromptText(Messages.get("survey.gpsCalibration.latPrompt"));
        lonField.setPromptText(Messages.get("survey.gpsCalibration.lonPrompt"));
        TooltipSupport.set(latField, Messages.get("tooltip.survey.gpsCalibrationLatLon"));
        TooltipSupport.set(lonField, Messages.get("tooltip.survey.gpsCalibrationLatLon"));

        double[] liveLatLon = {Double.NaN, Double.NaN};
        GpsProbe probe = new GpsProbe(
                position -> Platform.runLater(() -> {
                    liveLatLon[0] = position.latitude();
                    liveLatLon[1] = position.longitude();
                    gpsStatusLabel.setText(Messages.get("survey.gpsCalibration.currentFix",
                            position.latitude(), position.longitude(), position.horizontalAccuracyMeters()));
                }),
                status -> Platform.runLater(() -> gpsStatusLabel.setText(Messages.get("survey.gpsCalibration.error", status))));
        probe.start();

        Button useCurrentLocationButton = new Button(Messages.get("survey.gpsCalibration.useCurrentLocation"));
        useCurrentLocationButton.setOnAction(e -> {
            if (!Double.isNaN(liveLatLon[0])) {
                latField.setText(String.format(Locale.ROOT, "%.6f", liveLatLon[0]));
                lonField.setText(String.format(Locale.ROOT, "%.6f", liveLatLon[1]));
            }
        });
        TooltipSupport.set(useCurrentLocationButton, Messages.get("tooltip.survey.gpsCalibrationUseCurrent"));

        ListView<String> pointsList = new ListView<>();
        for (GeoReference.CalibrationPoint p : points) {
            pointsList.getItems().add(formatPoint(p));
        }

        imageStack.setOnMouseClicked(e -> {
            pendingClick[0] = e.getX() / previewWidth;
            pendingClick[1] = e.getY() / previewHeight;
            hasPendingClick[0] = true;
            marker.setVisible(true);
            marker.setCenterX(e.getX());
            marker.setCenterY(e.getY());
        });

        Button addButton = new Button(Messages.get("survey.gpsCalibration.addPoint"));
        addButton.setOnAction(e -> {
            if (!hasPendingClick[0]) {
                showAlert(stage, Messages.get("survey.gpsCalibration.noClickYet"));
                return;
            }
            Double lat = parseOrNull(latField.getText());
            Double lon = parseOrNull(lonField.getText());
            if (lat == null || lon == null) {
                showAlert(stage, Messages.get("survey.gpsCalibration.invalidLatLon"));
                return;
            }
            points.add(new GeoReference.CalibrationPoint(lat, lon, pendingClick[0], pendingClick[1]));
            pointsList.getItems().add(formatPoint(points.get(points.size() - 1)));
            hasPendingClick[0] = false;
            marker.setVisible(false);
        });
        TooltipSupport.set(addButton, Messages.get("tooltip.survey.gpsCalibrationAddPoint"));

        Button removeButton = new Button(Messages.get("survey.gpsCalibration.removePoint"));
        removeButton.setOnAction(e -> {
            int selected = pointsList.getSelectionModel().getSelectedIndex();
            if (selected >= 0) {
                points.remove(selected);
                pointsList.getItems().remove(selected);
            }
        });

        Button okButton = new Button(Messages.get("common.button.ok"));
        Button cancelButton = new Button(Messages.get("common.button.cancel"));
        okButton.setOnAction(e -> {
            GeoReference fitted = GeoReference.fit(points);
            if (fitted == null) {
                showAlert(stage, Messages.get("survey.gpsCalibration.fitFailed"));
                return;
            }
            resultHolder[0] = new Result(fitted, points);
            stage.close();
        });
        cancelButton.setOnAction(e -> stage.close());

        VBox latLonRow = new VBox(4,
                new HBox(8, new Label(Messages.get("survey.gpsCalibration.latLabel")), latField,
                        new Label(Messages.get("survey.gpsCalibration.lonLabel")), lonField, useCurrentLocationButton),
                gpsStatusLabel);

        HBox pointButtons = new HBox(8, addButton, removeButton);
        HBox dialogButtons = new HBox(8, okButton, cancelButton);
        dialogButtons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10,
                new Label(Messages.get("survey.gpsCalibration.instructions")),
                imageStack, latLonRow, pointButtons,
                new Label(Messages.get("survey.gpsCalibration.pointsListLabel")), pointsList,
                dialogButtons);
        root.setPadding(new Insets(12));

        Scene scene = new Scene(root);
        AppTheme.apply(scene);
        stage.setScene(scene);
        try {
            stage.showAndWait();
        } finally {
            probe.stop();
        }
        return Optional.ofNullable(resultHolder[0]);
    }

    private static Double parseOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatPoint(GeoReference.CalibrationPoint p) {
        return String.format(Locale.ROOT, "(%.6f, %.6f) -> (%.3f, %.3f)", p.latitude(), p.longitude(), p.xNorm(), p.yNorm());
    }

    private static void showAlert(Stage owner, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message);
        alert.initOwner(owner);
        alert.setTitle(Messages.get("common.dialog.title.warning"));
        alert.setHeaderText(null);
        AppTheme.apply(alert);
        alert.showAndWait();
    }
}
