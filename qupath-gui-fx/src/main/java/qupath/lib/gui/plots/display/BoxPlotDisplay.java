package qupath.lib.gui.plots.display;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.Function;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.controlsfx.control.SearchableComboBox;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.measure.PathTableData;
import qupath.lib.gui.plots.SnapshotTools;
import qupath.lib.gui.plots.builders.Charts;
import qupath.lib.gui.plots.charts.BoxplotChart;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

// todo very heavily copied from scatterplotdisplay... needs to be refactored along with histogramdisplay
// todo create abstractplotdisplay class?
public class BoxPlotDisplay implements PlotDisplay {

    private final BoxplotChart<String, Number> boxplot;
    private final SearchableComboBox<String> comboNameY = new SearchableComboBox<>();
    private final DoubleProperty pointRadius = new SimpleDoubleProperty(2);
    private final DoubleProperty pointOpacity = new SimpleDoubleProperty(1);
    private final BooleanProperty showAxes = new SimpleBooleanProperty(true);
    private final BooleanProperty showGrid = new SimpleBooleanProperty(true);

    // todo showAllPoints as option

    private final BorderPane pane = new BorderPane();
    private ObjectProperty<PathTableData<?>> model = new SimpleObjectProperty<>();
    private boolean isUpdating = false;

    /**
     * Create a scatter plot from a table of PathObject measurements.
     */
    public BoxPlotDisplay() {
        this.model.addListener(this::handleModelChange);
        BorderPane panelMain = new BorderPane();

        boxplot = (BoxplotChart<String, Number>) Charts.boxPlot()
                .useCanvas(true)
                .viewer(QuPathGUI.getInstance().getViewer())
                .build();
        boxplot.setMarkerSize(pointRadius.get() * 2); // todo radius vs size
        boxplot.setMarkerOpacity(pointOpacity.get());


        var popup = new ContextMenu();
        var miCopy = new MenuItem(QuPathResources.getString("Charts.ScatterPlotDisplay.copyToClipboard"));
        miCopy.setOnAction(e -> SnapshotTools.copyScaledSnapshotToClipboard(boxplot, 4));
        popup.getItems().add(miCopy);
        boxplot.setOnContextMenuRequested(e -> popup.show(
                boxplot.getScene().getWindow(), e.getScreenX(), e.getScreenY()));

        panelMain.setCenter(boxplot);

        initProperties();

        comboNameY.getSelectionModel().selectedItemProperty().addListener((v, o, n) ->
                requestRefresh()
        );

        var topPane = new GridPane();
        var labelY = new Label(QuPathResources.getString("Charts.ScatterPlotDisplay.y"));
        comboNameY.setTooltip(new Tooltip(QuPathResources.getString("Charts.ScatterPlotDisplay.yDescription")));
        labelY.setLabelFor(comboNameY);
        topPane.addRow(1, labelY, comboNameY);
        topPane.setHgap(5);

        pane.setTop(topPane);
        comboNameY.prefWidthProperty().bind(pane.widthProperty());
        comboNameY.prefWidthProperty().bind(pane.widthProperty());
        panelMain.setMinSize(200, 200);
        panelMain.setPrefSize(400, 300);

        pane.setCenter(panelMain);
        pane.setBottom(createMainOptionsPane());

        pane.setPadding(new Insets(10, 10, 10, 10));
    }


    private void handleModelChange(ObservableValue<? extends PathTableData<?>> observable,
                                   PathTableData<?> oldValue, PathTableData<?> newValue) {
        isUpdating = true;
        if (newValue != null) {
            updateForModel(newValue);
        }
        isUpdating = false;
        requestRefresh();
    }

    private Pane createMainOptionsPane() {
        return new VBox(
                createDisplayOptionsPane()
        );
    }

    private void updateForModel(PathTableData<?> newValue) {
        comboNameY.getItems().setAll(newValue.getMeasurementNames());

        // Try to select the first column that isn't for 'centroids'...
        // but, always select something
        String selectColumnY = null;
        String defaultY = null;
        for (String name : newValue.getMeasurementNames()) {
            if (!name.toLowerCase().startsWith("centroid")) {
                if (selectColumnY == null) {
                    selectColumnY = name;
                    continue;
                } else {
                    break;
                }
            }
            if (defaultY == null) {
                defaultY = name;
            }
        }
        if (selectColumnY != null) {
            comboNameY.getSelectionModel().select(selectColumnY);
        }
    }


    private TitledPane createDisplayOptionsPane() {
        Spinner<Double> spinPointOpacity = new Spinner<>(
                0.05, 1.0, pointOpacity.get(), 0.05);
        spinPointOpacity.getValueFactory().valueProperty().bindBidirectional(pointOpacity.asObject());
        spinPointOpacity.setEditable(true);
        spinPointOpacity.setMinWidth(80);
        FXUtils.resetSpinnerNullToPrevious(spinPointOpacity);

        Spinner<Double> spinPointRadius = new Spinner<>(
                0.5, 20.0, pointRadius.get(), 0.25);
        spinPointRadius.getValueFactory().valueProperty().bindBidirectional(pointRadius.asObject());
        spinPointRadius.setEditable(true);
        spinPointRadius.setMinWidth(80);
        FXUtils.resetSpinnerNullToPrevious(spinPointRadius);

        CheckBox cbDrawGrid = new CheckBox(QuPathResources.getString("Charts.ScatterPlotDisplay.showGrid"));
        cbDrawGrid.setTooltip(new Tooltip(QuPathResources.getString("Charts.ScatterPlotDisplay.showGridDescription")));
        cbDrawGrid.selectedProperty().bindBidirectional(showGrid);
        cbDrawGrid.setMinWidth(CheckBox.USE_PREF_SIZE);

        CheckBox cbDrawAxes = new CheckBox(QuPathResources.getString("Charts.ScatterPlotDisplay.showAxes"));
        cbDrawAxes.setTooltip(new Tooltip(QuPathResources.getString("Charts.ScatterPlotDisplay.showAxesDescription")));
        cbDrawAxes.selectedProperty().bindBidirectional(showAxes);
        cbDrawAxes.setMinWidth(CheckBox.USE_PREF_SIZE);

        var pane = new GridPane();
        int row = 0;

        pane.addRow(
                row++,
                createLabelFor(
                        spinPointOpacity,
                        QuPathResources.getString("Charts.ScatterPlotDisplay.pointOpacity"),
                        QuPathResources.getString("Charts.ScatterPlotDisplay.pointOpacityDescription")
                ),
                spinPointOpacity
        );

        pane.addRow(
                row++,
                createLabelFor(
                        spinPointRadius,
                        QuPathResources.getString("Charts.ScatterPlotDisplay.pointRadius"),
                        QuPathResources.getString("Charts.ScatterPlotDisplay.pointRadiusDescription")
                ),
                spinPointRadius
        );
        pane.setHgap(5);
        pane.setVgap(5);
        pane.setAlignment(Pos.CENTER);
        pane.setMaxHeight(Double.MAX_VALUE);

        var boxCheckboxes = new VBox(
                cbDrawGrid,
                cbDrawAxes
        );
        boxCheckboxes.setAlignment(Pos.CENTER_LEFT);
        boxCheckboxes.setSpacing(5);

        var hbox = new HBox(
                pane,
                new Separator(Orientation.VERTICAL),
                boxCheckboxes
        );
        hbox.setSpacing(10);

        return new TitledPane(QuPathResources.getString("Charts.ScatterPlotDisplay.display"), hbox);
    }

    /**
     * Set the data to display in the plot from a table model.
     * <p>
     * This calls {@link setData(Collection, Function)} in addition to setting the x and y labels.
     *
     * @param pathObjects the objects to display
     * @param model the table model containing the measurements
     * @param yMeasurement the column to use for y values
     */
    public static void setDataFromTable(
            BoxplotChart<String, Number> boxplot,
            Collection<?> pathObjects,
            PathTableData<?> model,
            String yMeasurement) {

        // todo don't love these casts just for abstraction
        var pathModel = (PathTableData<PathObject>)model;
        var pathCollection = (Collection<PathObject>)pathObjects;

        setData(boxplot, pathCollection, p -> pathModel.getNumericValue(p, yMeasurement));
        boxplot.getYAxis().setLabel(yMeasurement);
    }

    /**
     * Set the data to display in the plot.
     * @param pathObjects the objects to display
     * @param yFun a function to extract the y value to plot
     */
    public static void setData(BoxplotChart<String,Number> boxplotChart,
                              Collection<? extends PathObject> pathObjects,
                              Function<PathObject, Number> yFun) {

        // Find the represented classes & sort them
        var newData = pathObjects
                .stream()
                .map(PathObject::getPathClass)
                .distinct()
                .sorted(Comparator.nullsFirst(PathClass::compareTo))
                .map(pc -> {
                    // create a series for each class so they appear nicely in the legend
                    return new XYChart.Series<>(
                            pc == null ? PathClass.NULL_CLASS.toString() : pc.toString(),
                            FXCollections.observableArrayList(pathObjects.stream()
                                    .filter(po -> po.getPathClass() == pc)
                                    .map(po -> new XYChart.Data<>(pc.toString(), yFun.apply(po), po))
                                    .toList())
                    );
                })
                .toList();

        boxplotChart.getData().setAll(newData);
    }


    private void initProperties() {
        pointOpacity.addListener((v, o, n) -> boxplot.setMarkerOpacity(n.doubleValue()));
        pointRadius.addListener((v, o, n) -> boxplot.setMarkerSize(n.doubleValue() * 2));

        boxplot.verticalGridLinesVisibleProperty().bindBidirectional(showGrid);
        boxplot.horizontalGridLinesVisibleProperty().bindBidirectional(showGrid);

        boxplot.getXAxis().tickLabelsVisibleProperty().bindBidirectional(showAxes);
        boxplot.getYAxis().tickLabelsVisibleProperty().bindBidirectional(showAxes);

    }

    private static Label createLabelFor(Node node, String text, String tooltip) {
        var label = new Label(text);
        label.setLabelFor(node);
        label.setMinWidth(Label.USE_PREF_SIZE);
        if (tooltip != null) {
            var tt = new Tooltip(tooltip);
            if (node instanceof Control control)
                control.setTooltip(tt);
            else
                Tooltip.install(node, tt);
            label.setTooltip(tt);
        }
        return label;
    }

    @Override
    public String getName() {
        return QuPathResources.getString("Measure.MeasurementTable.boxPlot");
    }

    @Override
    public Pane getPane() {
        return pane;
    }

    @Override
    public void requestRefresh() {
        var model = this.model.get();
        if (model == null || isUpdating) {
            return;
        }
        // Awkward - but SearchableComboBox tends to set values temporarily to null
        var y = comboNameY.getValue();
        if (y != null) {
            var items = model.getItems();
            setDataFromTable(boxplot, items, model, y);
        }
    }

    @Override
    public void setModel(PathTableData<?> model) {
        this.model.set(model);
    }

    @Override
    public void showPlot(String... columns) {

    }
}
