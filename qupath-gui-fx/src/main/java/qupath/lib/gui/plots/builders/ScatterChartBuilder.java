package qupath.lib.gui.plots.builders;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javafx.scene.chart.Axis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.plots.charts.CanvasChart;
import qupath.lib.gui.plots.charts.CanvasScatterChart;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Builder for creating scatter charts.
 */
public class ScatterChartBuilder extends Charts.XYNumberChartBuilder<ScatterChartBuilder, ScatterChart<Number, Number>> {

    private static final Logger logger = LoggerFactory.getLogger(ScatterChartBuilder.class);

    private Integer DEFAULT_MAX_DATAPOINTS = 10_000;
    private Integer maxDatapoints;
    private Random rnd = new Random();
    private boolean useCanvas = true;

    ScatterChartBuilder() {
    }

    /**
     * Choose the maximum number of supported datapoints per series.
     * Scattercharts are rather 'heavyweight', and including many thousands of datapoints can cause
     * severe performance issues due to high processing and memory requirements.
     * <p>
     * By default, datapoints will be randomly subsampled to a 'manageable number' where necessary,
     * which can be customized with this setting.
     *
     * @param max the maximum number of data points to show per series
     * @return this builder
     * @see #unlimitedDatapoints()
     */
    public ScatterChartBuilder limitDatapoints(int max) {
        this.maxDatapoints = max;
        return this;
    }

    /**
     * Show all datapoints, without subsampling, even when this may cause performance issues.
     * Use with caution.
     *
     * @return this builder
     * @see #limitDatapoints(int)
     */
    public ScatterChartBuilder unlimitedDatapoints() {
        maxDatapoints = -1;
        return this;
    }

    @Override
    protected String getDefaultWindowTitle() {
        return QuPathResources.getString("Charts.scatterChart");
    }

    /**
     * Set the random number generator.
     *
     * @param rnd A random number generator
     * @return A modified builder.
     */
    public ScatterChartBuilder random(Random rnd) {
        this.rnd = rnd;
        return this;
    }

    public ScatterChartBuilder useCanvas(boolean value) {
        this.useCanvas = value;
        return this;
    }

    /**
     * Plot centroids for the specified objects using a fixed pixel calibration.
     *
     * @param pathObjects the objects to plot
     * @param cal         the pixel calibration used to convert the centroids into other units
     * @return this builder
     */
    public <T> ScatterChartBuilder centroids(Collection<? extends PathObject> pathObjects, PixelCalibration cal) {
        xLabel("x (" + cal.getPixelWidthUnit() + ")");
        yLabel("y (" + cal.getPixelHeightUnit() + ")");
        return addSeries(
                null,
                pathObjects,
                (PathObject p) -> PathObjectTools.getROI(p, true).getCentroidX() * cal.getPixelWidth().doubleValue(),
                (PathObject p) -> -PathObjectTools.getROI(p, true).getCentroidY() * cal.getPixelHeight().doubleValue());
    }

    /**
     * Plot centroids for the specified objects in pixel units.
     *
     * @param pathObjects the objects to plot.
     * @return this builder
     */
    public ScatterChartBuilder centroids(Collection<? extends PathObject> pathObjects) {
        var cal = imageData == null ? PixelCalibration.getDefaultInstance() : imageData.getServer().getPixelCalibration();
        return centroids(pathObjects, cal);
    }

    /**
     * Plot two measurements against one another for the specified objects.
     *
     * @param pathObjects  the objects to plot
     * @param xMeasurement the measurement to extract from each object's measurement list for the x location
     * @param yMeasurement the measurement to extract from each object's measurement list for the y location
     * @return this builder
     */
    public ScatterChartBuilder measurements(Collection<? extends PathObject> pathObjects, String xMeasurement, String yMeasurement) {
        return measurements(pathObjects, xMeasurement, yMeasurement, true);
    }

    public ScatterChartBuilder measurements(Collection<? extends PathObject> pathObjects, String xMeasurement, String yMeasurement, boolean seriesByClass) {
        xLabel(xMeasurement);
        yLabel(yMeasurement);
        if (seriesByClass) {
            var pathClasses = pathObjects.stream().map(PathObject::getPathClass).distinct().toList();
            for (var pathClass : pathClasses) {
                addSeries(
                        pathClass == null ? PathClass.NULL_CLASS.toString() : pathClass.toString(),
                        pathObjects.stream().filter(po -> po.getPathClass() == pathClass).toList(),
                        (PathObject p) -> p.getMeasurementList().get(xMeasurement),
                        (PathObject p) -> p.getMeasurementList().get(yMeasurement)
                );
            }
            return this;
        } else {
            return addSeries(
                    null,
                    pathObjects,
                    (PathObject p) -> p.getMeasurementList().get(xMeasurement),
                    (PathObject p) -> p.getMeasurementList().get(yMeasurement));
        }
    }


    /**
     * Plot metadata values against each other
     * @param projectImageEntries the image entries to collect the metadata from
     * @param xMeasurement the name of the metadata value to plot on the x-axis
     * @param yMeasurement the name of the metadata value to plot on the y-axis
     */
    public ScatterChartBuilder metadata(Collection<? extends ProjectImageEntry<BufferedImage>> projectImageEntries, String xMeasurement, String yMeasurement) {
        xLabel(xMeasurement);
        yLabel(yMeasurement);
        var filteredEntries = projectImageEntries.stream()
                .filter(e -> e.getMetadata().get(xMeasurement) != null && e.getMetadata().get(yMeasurement) != null)
                .toList();
        BiFunction<ProjectImageEntry<BufferedImage>, String, Double> getMetadata = (it, metadata) -> {
            String yVal = it.getMetadata().get(metadata);
            if (yVal == null) {
                return Double.NaN;
            }
            return Double.valueOf(yVal);
        };
        Double[] x = filteredEntries.stream()
                .map(pie -> getMetadata.apply(pie, xMeasurement))
                .toArray(Double[]::new);
        Double[] y = filteredEntries.stream()
                .map(pie -> getMetadata.apply(pie, xMeasurement))
                .toArray(Double[]::new);
        return addSeries(
                Charts.XYChartBuilder.createSeries(null,
                        x,
                        y,
                        filteredEntries
                )
        );
    }

    @Override
    protected void updateChart(ScatterChart<Number, Number> chart) {
        super.updateChart(chart);
        chart.getData().setAll(getSeries());
        // element in chart_base.css disables different point shapes for different series
        chart.getStylesheets()
                .add(
                        Objects.requireNonNull(
                                getClass().getClassLoader().getResource("css/charts/chart_base.css")).toExternalForm()
                );

        // if it's a canvaschart, we have to let it do the majic of finding data points for us
        if (chart instanceof CanvasChart) {
            CanvasChart<Number, Number> canvasChart = (CanvasChart<Number, Number>) chart;
            canvasChart.getCanvas().addEventHandler(MouseEvent.ANY, e -> {
                if (e.getEventType() == MouseEvent.MOUSE_CLICKED) {
                    double pixelTolerance = markerSize * 1.5; // todo figure this out

                    var item = canvasChart.findDataPoint(e.getX(), e.getY(), pixelTolerance);
                    item.ifPresent((data) ->
                            tryToSelect(
                                (PathObject) data.getExtraValue(),
                                e.isShiftDown(),
                                e.getClickCount() == 2));
                }
            });
        } else {
            // otherwise if we have a hierarchy, and PathObjects, make the plot live

            // set point style for legends to all be the same div2 because setting radius not width/height
            String baseStyle = String.format("-fx-background-radius: %fpx; -fx-padding: %fpx;", this.markerSize/2, this.markerSize/2);
            // counter for the default CHART_COLOR stuff below
            int n = 1;
            for (var s : getSeries()) {
                // if series names are available use them to set the default colors for the legend
                if (s.getName() != null) {
                    var pathClass = PathClass.fromString(s.getName());
                    if (pathClass == null) {
                        pathClass = PathClass.NULL_CLASS;
                    }
                    var color = pathClass.getColor();
                    chart.setStyle(chart.getStyle() + " CHART_COLOR_" + (n++) + ": " +
                            String.format("rgba(%d,%d,%d,%.2f);",
                                    ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color), markerOpacity)
                    );
                }

                for (var d : s.getData()) {
                    var extra = d.getExtraValue();
                    var node = d.getNode();
                    if (extra instanceof PathObject && node != null) {
                        if (node instanceof StackPane) {
                            node.setStyle(baseStyle);
                            node.addEventHandler(MouseEvent.ANY, e -> {
                                if (e.getEventType() == MouseEvent.MOUSE_CLICKED)
                                    tryToSelect((PathObject) extra, e.isShiftDown(), e.getClickCount() == 2);
                                else if (e.getEventType() == MouseEvent.MOUSE_ENTERED)
                                    node.setStyle(baseStyle +
                                            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
                                else if (e.getEventType() == MouseEvent.MOUSE_EXITED)
                                    node.setStyle(baseStyle);
                            });
                        }
                    } else //noinspection rawtypes
                        if (extra instanceof ProjectImageEntry pie && node != null) {
                        node.setStyle(baseStyle);
                        node.addEventHandler(MouseEvent.ANY, e -> {
                            if (e.getEventType() == MouseEvent.MOUSE_CLICKED)
                                Charts.tryToOpen((ProjectImageEntry<BufferedImage>) pie);
                            else if (e.getEventType() == MouseEvent.MOUSE_ENTERED)
                                node.setStyle(baseStyle + ";"
                                        + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.5), 4, 0, 1, 1);");
                            else if (e.getEventType() == MouseEvent.MOUSE_EXITED)
                                node.setStyle(baseStyle);
                        });
                    }
                }
            }
        }
    }

    @Override
    protected ScatterChart<Number, Number> createNewChart(Axis<Number> xAxis, Axis<Number> yAxis) {
        if (useCanvas) {
            var cmap = this.getSeries().stream()
                    .map(XYChart.Series::getName).map(PathClass::fromString)
                    .collect(Collectors.toMap(PathClass::toString, ColorToolsFX::getPathClassColor));
            var chart = new CanvasScatterChart<>(xAxis, yAxis, cmap);
            chart.setMarkerOpacity(this.markerOpacity);
            chart.setMarkerSize(this.markerSize);
            return chart;
        } else {
            return new ScatterChart<>(xAxis, yAxis);
        }
    }

    /**
     * Try to select an object if possible (e.g. because a user clicked on it).
     *
     * @param pathObject     the object to select
     * @param addToSelection if true, add to an existing selection; if false, reset any current selection
     * @param centerObject   if true, try to center it in a viewer (if possible)
     */
    private void tryToSelect(PathObject pathObject, boolean addToSelection, boolean centerObject) {
        Charts.tryToSelectObject(pathObject, viewer, imageData, addToSelection, centerObject);
    }

    @Override
    protected ScatterChartBuilder getThis() {
        return this;
    }

    @Override
    public ScatterChart<Number, Number> build() {
        subsampleSeries();
        return super.build();
    }

    /**
     * Perform data subsampling to ensure that each series contains <= maxDatapoints.
     */
    private void subsampleSeries() {
        // todo this is the laziest way but probably better to adapt in a smarter way
        if (useCanvas) {
            return;
        }
        int n = maxDatapoints == null ? DEFAULT_MAX_DATAPOINTS : maxDatapoints;
        for (var series : getSeries()) {
            List<XYChart.Data<Number, Number>> data = series.getData();
            if (data.size() > n) {
                logger.warn("Subsampling {} data points to {}", data.size(), n);
                var list = new ArrayList<>(data);
                Collections.shuffle(list, rnd);
                data = list.subList(0, n);
                series.getData().setAll(data);
            }
        }
    }
}
