/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui.viewer;


import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.TextAlignment;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.CommandFinderTools;
import qupath.lib.images.servers.ImageServer;

/**
 * A whole slide viewer with optional extras... i.e. an overview, scalebar, location string...
 * 
 * @author Pete Bankhead
 */
public class QuPathViewerPlus extends QuPathViewer {

	private final DimensionControls dimensionControls;
	private final ViewerPlusDisplayOptions viewerDisplayOptions;
	
	private final ChangeListener<Boolean> locationListener = (v, o, n) -> setLocationVisible(n);
	private final ChangeListener<Boolean> overviewListener = (v, o, n) -> setOverviewVisible(n);
	private final ChangeListener<Boolean> scalebarListener = (v, o, n) -> setScalebarVisible(n);

	private AnchorPane basePane = new AnchorPane();
	
	private final ImageOverview overview = new ImageOverview(this);
	private final Scalebar scalebar = new Scalebar(this);

	private final BorderPane panelLocation = new BorderPane();
	private final Label labelLocation = new Label(" ");
	private final BooleanProperty useCalibratedLocationString = PathPrefs.useCalibratedLocationStringProperty();

	private final int padding = 10;

	
	/**
	 * Create a new viewer.
	 * @param regionStore store used to tile caching
	 * @param overlayOptions overlay options to control the viewer display
	 * @param viewerDisplayOptions viewer options to control additional panes and labels
	 */
	public QuPathViewerPlus(final DefaultImageRegionStore regionStore, final OverlayOptions overlayOptions,
			final ViewerPlusDisplayOptions viewerDisplayOptions) {
		super(regionStore, overlayOptions);

		dimensionControls = new DimensionControls();
		dimensionControls.zPositionProperty().bindBidirectional(zPositionProperty());
		dimensionControls.tPositionProperty().bindBidirectional(tPositionProperty());

		useCalibratedLocationString.addListener(v -> updateLocationString());
		
		Pane view = super.getView();
		view.getChildren().add(basePane);
		
		basePane.prefWidthProperty().bind(view.widthProperty());
		basePane.prefHeightProperty().bind(view.heightProperty());
		view.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, e -> {
			updateLocationString();
		});
		
		// Add the overview (preview image for navigation)
		Node overviewNode = overview.getNode();
		basePane.getChildren().add(overviewNode);
		AnchorPane.setTopAnchor(overviewNode, (double)padding);
		AnchorPane.setRightAnchor(overviewNode, (double)padding);

		// Add the location label
		labelLocation.setTextAlignment(TextAlignment.CENTER);
		var fontBinding = Bindings.createStringBinding(() -> {
				var temp = PathPrefs.locationFontSizeProperty().get();
				return temp == null ? null : "-fx-font-size: " + temp.getFontSize();
		}, PathPrefs.locationFontSizeProperty());
		labelLocation.styleProperty().bind(fontBinding);
		panelLocation.getStyleClass().add("viewer-overlay");
		panelLocation.setMinSize(140, 40);
		panelLocation.setCenter(labelLocation);
		panelLocation.setPadding(new Insets(5));
		basePane.getChildren().add(panelLocation);
		AnchorPane.setBottomAnchor(panelLocation, (double)padding);
		AnchorPane.setRightAnchor(panelLocation, (double)padding);
		
		// Add the scalebar label
//		Node scalebarNode = PanelToolsFX.createSwingNode(scalebar);
		Node scalebarNode = scalebar.getNode();
		basePane.getChildren().add(scalebarNode);
		AnchorPane.setBottomAnchor(scalebarNode, (double)padding);
		AnchorPane.setLeftAnchor(scalebarNode, (double)padding);

		
		// Set spinners' position so they make space for command bar (only if needed!)
		var commandBarDisplay = CommandFinderTools.commandBarDisplayProperty().getValue();
		setSpinnersPosition(!commandBarDisplay.equals(CommandFinderTools.CommandBarDisplay.NEVER));

		basePane.getChildren().addAll(dimensionControls.getPane());

		updateSpinners();
		
		zPositionProperty().addListener(v -> updateLocationString());
		tPositionProperty().addListener(v -> updateLocationString());
		
		this.viewerDisplayOptions = viewerDisplayOptions;
		
		setLocationVisible(viewerDisplayOptions.getShowLocation());
		setOverviewVisible(viewerDisplayOptions.getShowOverview());
		setScalebarVisible(viewerDisplayOptions.getShowScalebar());
		
		viewerDisplayOptions.showLocationProperty().addListener(locationListener);
		viewerDisplayOptions.showOverviewProperty().addListener(overviewListener);
		viewerDisplayOptions.showScalebarProperty().addListener(scalebarListener);
	}

	private void updateSpinners() {
		if (dimensionControls == null)
			return;

		ImageServer<?> server = getServer();
		if (server != null) {
			dimensionControls.zMaxProperty().set(server.nZSlices());
			dimensionControls.zPositionProperty().set(server.nZSlices() / 2);

			dimensionControls.tMaxProperty().set(server.nTimepoints());
			dimensionControls.tPositionProperty().set(server.nTimepoints() / 2);
		} else {
			dimensionControls.zMaxProperty().set(0);
			dimensionControls.tMaxProperty().set(0);
		}
	}

	
	@Override
	public void initializeForServer(ImageServer<BufferedImage> server) {
		super.initializeForServer(server);
		updateSpinners();
	}


	private void setLocationVisible(boolean showLocation) {
		panelLocation.setVisible(showLocation);
	}

	/**
	 * Returns true if the cursor location is visible, false otherwise.
	 * @return
	 */
	public boolean isLocationVisible() {
		return panelLocation.isVisible();
	}

	private void setScalebarVisible(boolean scalebarVisible) {
		scalebar.setVisible(scalebarVisible);
	}

	/**
	 * Returns true if the scalebar is visible, false otherwise.
	 * @return
	 */
	public boolean isScalebarVisible() {
		return scalebar.isVisible();
	}

	private void setOverviewVisible(boolean overviewVisible) {
		overview.setVisible(overviewVisible);
	}

	/**
	 * Returns true if the image overview is visible, false otherwise.
	 * @return
	 */
	public boolean isOverviewVisible() {
		return overview.isVisible();
	}

	/**
	 * Sets the Z and T spinner' position to allow space for command bar
	 * @param down
	 */
	public void setSpinnersPosition(boolean down) {
		double spinnersTopPadding = (double)padding + (down ? 20 : 0);
		var pane = dimensionControls.getPane();
		AnchorPane.setTopAnchor(pane, spinnersTopPadding);
		AnchorPane.setLeftAnchor(pane, (double) padding);
	}
	
	@Override
	public void closeViewer() {
		super.closeViewer();
		viewerDisplayOptions.showLocationProperty().removeListener(locationListener);
		viewerDisplayOptions.showOverviewProperty().removeListener(overviewListener);
		viewerDisplayOptions.showScalebarProperty().removeListener(scalebarListener);
		
	}

	// TODO: Make location string protected?
	void updateLocationString() {
		String s = null;
		if (labelLocation != null && hasServer())
			s = getFullLocationString(useCalibratedLocationString());
		if (s != null && !s.isEmpty()) {
			labelLocation.setText(s);
//			labelLocation.setTextAlignment(TextAlignment.CENTER);
			panelLocation.setOpacity(1);
		} else {
			panelLocation.setOpacity(0);
		}
	}

	
	private boolean useCalibratedLocationString() {
		return useCalibratedLocationString.get();
	}
	
	@Override
	protected void updateAffineTransform() {
		super.updateAffineTransform();
		updateLocationString();
	}

	@Override
	void paintCanvas() {
		boolean imageWasUpdated = imageUpdated || locationUpdated;
		
		super.paintCanvas();
		
		if (scalebar == null)
			return;
		
		// Ensure the scalebar color is set, if required
		Bounds boundsFX = scalebar.getNode().getBoundsInParent();
		Rectangle2D bounds = new Rectangle2D.Double(boundsFX.getMinX(), boundsFX.getMinY(), boundsFX.getMaxX(), boundsFX.getMaxY());
		if (imageWasUpdated) {
			if (getDisplayedClipShape(bounds).intersects(0, 0, getServerWidth(), getServerHeight())) {
				scalebar.setTextColor(getSuggestedOverlayColorFX());
			}
			else {
				scalebar.setTextColor(ColorToolsFX.TRANSLUCENT_WHITE_FX);
			}
		}
	}

	
	@Override
	public void repaintEntireImage() {
		super.repaintEntireImage();
		if (overview != null)
			overview.repaint();
	}



	private static class DimensionControls {

		private final IntegerProperty zPositionProperty = new SimpleIntegerProperty();
		private final IntegerProperty zMaxProperty = new SimpleIntegerProperty();

		private final IntegerProperty tPositionProperty = new SimpleIntegerProperty();
		private final IntegerProperty tMaxProperty = new SimpleIntegerProperty();

		private final DoubleProperty contentOpacityProperty = new SimpleDoubleProperty(1.0);

		private final Spinner<Integer> spinnerZ = createSpinner(zPositionProperty, zMaxProperty, "Z-slice");
		private final Spinner<Integer> spinnerT = createSpinner(tPositionProperty, tMaxProperty, "Time point");

		private final ProgressBar progressZ = createProgressBar(zPositionProperty, zMaxProperty);
		private final ProgressBar progressT = createProgressBar(tPositionProperty, tMaxProperty);

		private final Label labelZ = createLabel("Z: ", spinnerZ);
		private final Label labelT = createLabel("Time: ", spinnerT);

		private final GridPane pane = new GridPane();

		private DimensionControls() {
			pane.getStyleClass().addAll("viewer-overlay", "viewer-dims");
			pane.setOnMouseEntered(e -> contentOpacityProperty.set(1.0));
			pane.setOnMouseExited(e -> contentOpacityProperty.set(0.5));
			pane.setVgap(4);

			zMaxProperty.addListener(this::handleChange);
			tMaxProperty.addListener(this::handleChange);
			updateContents();
		}

		private ProgressBar createProgressBar(IntegerProperty property, IntegerProperty maxProperty) {
			var progress = new ProgressBar();
			progress.setMaxWidth(Double.MAX_VALUE);
			progress.setPrefHeight(10);
			progress.progressProperty().bind(Bindings.createDoubleBinding(() -> property.doubleValue() / (maxProperty.get() - 1),
				property, maxProperty));
			progress.opacityProperty().bind(contentOpacityProperty);
			return progress;
		}

		private Spinner<Integer> createSpinner(IntegerProperty property, IntegerProperty maxProperty,
													  String name) {
			var factory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 1);
			factory.maxProperty().bind(maxProperty);
			var spinner = new Spinner<>(factory);
			factory.valueProperty().addListener((v, o, n) -> property.setValue(n));
			property.addListener((v, o, n) -> factory.setValue((Integer) n));
			spinner.setPrefWidth(70);
			spinner.setEditable(true);
			FXUtils.resetSpinnerNullToPrevious(spinner);

			var tooltip = new Tooltip();
			tooltip.textProperty().bind(
					Bindings.createStringBinding(
							() -> name + " (" + property.get() + "/" + maxProperty.get() + ")",
							property, maxProperty
					));
			spinner.setTooltip(tooltip);

			spinner.opacityProperty().bind(contentOpacityProperty);
			return spinner;
		}

		private Label createLabel(String text, Node node) {
			var label = new Label(text);
			label.setLabelFor(node);
			label.setContentDisplay(ContentDisplay.RIGHT);
			label.opacityProperty().bind(contentOpacityProperty);
			return label;
		}

		private void handleChange(ObservableValue<? extends Number> val, Number oldValue, Number newValue) {
			updateContents();
		}

		private void updateContents() {
			pane.getChildren().clear();
			int row = 0;
			if (tMaxProperty.get() > 1) {
				pane.addRow(row++, labelT, spinnerT);
				pane.add(progressT, 0, row++, 2, 1);
			}
			if (zMaxProperty.get() > 1) {
				if (row > 0)
					pane.add(new Separator(), 0, row++, 2, 1);
				pane.addRow(row++, labelZ, spinnerZ);
				pane.add(progressZ, 0, row++, 2, 1);
			}
			pane.setVisible(!pane.getChildren().isEmpty());
		}

		IntegerProperty zMaxProperty() {
			return zMaxProperty;
		}

		IntegerProperty tMaxProperty() {
			return tMaxProperty;
		}

		IntegerProperty zPositionProperty() {
			return zPositionProperty;
		}

		IntegerProperty tPositionProperty() {
			return tPositionProperty;
		}


		Pane getPane() {
			return pane;
		}

	}


}