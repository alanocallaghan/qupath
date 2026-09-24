package qupath.lib.gui.plots.charts;

import com.sun.javafx.charts.Legend;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import javafx.animation.AnimationTimer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Axis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.ValueAxis;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Window;
import javafx.util.Pair;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;

public class CanvasScatterChart<X,Y> extends ScatterChart<X,Y> implements CanvasChart<X,Y> {
    private static final Logger logger = LoggerFactory.getLogger(CanvasScatterChart.class);

    // my best attempt at non-terrible non-clashing default colors. Could instead use something from ColorBrewer
    private static final List<Color> DEFAULT_COLORS = List.of(
            Color.FIREBRICK, Color.DODGERBLUE, Color.FORESTGREEN,
            Color.GOLDENROD, Color.DARKMAGENTA, Color.TEAL,
            Color.DEEPPINK, Color.CHOCOLATE, Color.SLATEBLUE,
            Color.DARKSLATEGRAY);
    private final Map<String, Color> colorMap = new HashMap<>();
    private final GeometryFactory gf;
    private final Quadtree tree;
    private int colorIdx = 0;
    private final Canvas canvas = new Canvas();
    private boolean redrawNeeded;

    // List of all objects to display - we retain this only so that we can shuffle reproducibly if the seed changes
    private final ObservableList<Data<X,Y>> allData = FXCollections.observableArrayList();
    // Shuffled objects - this is the main list we use, in preference to allData
    private final ObservableList<Data<X,Y>> shuffledData = FXCollections.observableArrayList();


    private final DoubleProperty markerSize = new SimpleDoubleProperty(2);
    private final DoubleProperty markerOpacity = new SimpleDoubleProperty(1);

    // todo implement subsampling
    private final BooleanProperty autorangeToFullData = new SimpleBooleanProperty(true);
    private final DoubleProperty maxPoints = new SimpleDoubleProperty(1);
    private final Random random = new Random(123);

    /**
     * The size of markers on this chart
     * @return the property corresponding to marker size
     */
    public DoubleProperty markerSizeProperty() {
        return markerSize;
    }

    public void setMarkerSize(double value) {
        if (value <= 0 || !Double.isFinite(value)) return;
        this.markerSize.set(value);
    }

    public double getMarkerSize() {
        return markerSize.get();
    }

    /**
     * The opacity of markers in this plot
     * @return the property corresponding to marker opacity
     */
    public DoubleProperty markerOpacityProperty() {
        return markerOpacity;
    }

    public void setMarkerOpacity(double value) {
        if (value <= 0 || value > 1 || !Double.isFinite(value)) return;
        this.markerOpacity.set(value);
    }

    public double getMarkerOpacity() {
        return markerOpacity.get();
    }

    // todo finish getters/setters + jdocs
    public BooleanProperty autorangeToFullDataProperty() {
        logger.warn("Currently autorangeToFullData does nothing on CanvasScatterChart.");
        return this.autorangeToFullData;
    }

    public void setMaxPoints(double value) {
        logger.warn("Currently maxPoints does nothing on CanvasScatterChart.");
        this.maxPoints.set(value);
    }

    public void setRngSeed(int value) {
        logger.warn("Currently RngSeed does nothing on CanvasScatterChart.");
        this.random.setSeed(value);
    }


    /**
     * Construct a CanvasScatterChart with the two axes and the defined color map.
     * @param xAxis the x-axis for this chart
     * @param yAxis the y-axis for this chart
     */
    public CanvasScatterChart(Axis<X> xAxis, Axis<Y> yAxis) {
        this(xAxis, yAxis, Map.of());
    }

    /**
     * Construct a CanvasScatterChart with the two axes and the defined color map.
     * @param xAxis the x-axis for this chart
     * @param yAxis the y-axis for this chart
     * @param colorMap the mapping from series names to colors (can be empty)
     */
    public CanvasScatterChart(Axis<X> xAxis, Axis<Y> yAxis, Map<String, Color> colorMap) {
        super(xAxis, yAxis);
        this.colorMap.putAll(colorMap);
        this.tree = new Quadtree();
        this.gf = new GeometryFactory();
        // unsure if this is idiomatic
        markerOpacity.subscribe(this::redraw);
        markerSize.subscribe(this::redraw);
        sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::showingProperty).subscribe(n -> {
            if (Boolean.TRUE.equals(n))
                timer.start();
            else
                timer.stop();;
        });
    }


    @Override
    public Optional<Data<X,Y>> findDataPoint(double x, double y, double tolerance) {
        // translate from canvas coords to data scale
        double width = getXAxis().getWidth();
        double height = getYAxis().getHeight();
        // we know they're valueAxes as there's only value or category for now...
        // but this assumption may not hold in future
        @SuppressWarnings("rawtypes") var vax = (ValueAxis)getXAxis();
        @SuppressWarnings("rawtypes") var vay = (ValueAxis)getYAxis();
        double rx = Math.abs(vax.getUpperBound() - vax.getLowerBound());
        double ry = Math.abs(vay.getUpperBound() - vay.getLowerBound());
        double xPerPix = rx / width;
        double yPerPix = ry / height;
        double tolX = tolerance * xPerPix;
        double tolY = tolerance * yPerPix;

        logger.debug("Querying tree at X: {}, Y: {}; tol X: {}, tol Y: {}", x, y, tolX, tolY);
        Envelope search = new Envelope(
                (double)getXAxis().getValueForDisplay(x - tolX),
                (double)getXAxis().getValueForDisplay(x + tolX),
                (double)getYAxis().getValueForDisplay(y - tolY),
                (double)getYAxis().getValueForDisplay(y + tolY)
        );

        List<Data<X,Y>> candidates = tree.query(search);
        logger.debug("{} candidates found", candidates.size());

        Data<X,Y> closestPoint = null;
        double minDistance = Double.MAX_VALUE;
        double maxDistance = markerSize.get() / 2;
        // these are actually the xy mouse coords
        Coordinate clickCoord = new Coordinate(x, y);
        for (Data<X,Y> candidate : candidates) {
            // candidate values are on data scale
            double distance = new Coordinate(
                    getXAxis().getDisplayPosition(candidate.getXValue()),
                    getYAxis().getDisplayPosition(candidate.getYValue())
            ).distance(clickCoord);
            if (distance <= minDistance && distance < maxDistance) {
                minDistance = distance;
                closestPoint = candidate;
            }
        }
        logger.debug("Minimum distance {}", minDistance);
        return Optional.ofNullable(closestPoint);
    }


    @Override
    protected void dataItemAdded(Series<X, Y> series, int itemIndex, Data<X, Y> item) {
        tree.insert(createEnvelope(item), item);
    }

    @Override
    protected void dataItemRemoved(Data<X, Y> item, Series<X, Y> series) {
        tree.remove(createEnvelope(item), item);
        removeDataItemFromDisplay(series, item);
    }

    @Override
    protected void dataItemChanged(Data<X, Y> item) {
        requestChartLayout();
    }

    @Override
    protected void seriesAdded(Series<X, Y> series, int seriesIndex) {
        for (int j = 0; j < series.getData().size(); j++) {
            var item = series.getData().get(j);
            dataItemAdded(series, j, item);
        }
    }

    @Override
    protected void seriesRemoved(Series<X, Y> series) {
        requestChartLayout();
    }

    private Envelope createEnvelope(Data<X, Y> item) {
        // cannot create tree with XY coords because axes may not be initiated
        // also keeping it on data scale means it does not need to be update
        Point p = gf.createPoint(new Coordinate(
                (Double) item.getXValue(),
                (Double) item.getYValue())
        );
        return p.getEnvelopeInternal();
    }

    @Override
    protected void layoutPlotChildren() {
        getPlotChildren().clear();
        getPlotChildren().add(canvas);

        // can't select parent of plot children (might be empty), but scenicView to the rescue
        Pane plotContent = (Pane) lookup(".chart-content");

        if (plotContent != null) {
            canvas.widthProperty().bind(plotContent.widthProperty());
            canvas.heightProperty().bind(plotContent.heightProperty());
            canvas.widthProperty().addListener((v, o, n) ->
                    redrawNeeded = true);
            canvas.heightProperty().addListener((v, o, n) ->
                    redrawNeeded = true);
        }

        redraw();
    }

    private void redraw() {
        // todo shuffle so that one class isn't drawn on the other
        var context = canvas.getGraphicsContext2D();
        context.setGlobalAlpha(markerOpacity.get());
        context.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        List<Pair<String, Data<X,Y>>> allPoints = new ArrayList<>();
        for (Series<X,Y> series : getData()) {
            for (Data<X, Y> elem: series.getData()) {
                var p = new Pair<>(series.getName(), elem);
                allPoints.add(p);
            }
        }
        Collections.shuffle(allPoints);
        double size = getMarkerSize();
        for (var pair: allPoints) {
            var color = getColor(pair.getKey());
            context.setFill(color);
            // fillOval uses bounding box coords
            context.fillOval(
                    getXAxis().getDisplayPosition(pair.getValue().getXValue()) - (size / 2),
                    getYAxis().getDisplayPosition(pair.getValue().getYValue()) - (size / 2),
                    size, size);

        }

    }

    @Override
    protected void updateLegend() {
        List<Legend.LegendItem> legendList = new ArrayList<>();
        if (getData() != null) {
            for (Series<X,Y> series : getData()) {
                legendList.add(createLegendItem(series));
            }
        }
        Legend legend = new Legend();
        legend.getItems().setAll(legendList);
        if (!legendList.isEmpty()) {
            setLegend(legend);
        } else {
            setLegend(null);
        }
    }

    Legend.LegendItem createLegendItem(Series<X,Y> series) {
        Legend.LegendItem legendItem = new Legend.LegendItem(series.getName());
        legendItem.setSymbol(new Circle(5, getColor(series.getName())));
        return legendItem;
    }

    private Color getColor(String name) {
        return colorMap.computeIfAbsent(name, _ -> getNextColor());
    }

    private Color getNextColor() {
        return DEFAULT_COLORS.get(colorIdx++);
    }

    public Canvas getCanvas() {
        return canvas;
    }


    private final AnimationTimer timer = new AnimationTimer() {

        @Override
        public void handle(long now) {
            handlePulse();
        }

    };

    private void handlePulse() {
        if (redrawNeeded) {
            redraw();
        }
        redrawNeeded = false;
        Region r;
    }


    /**
     * Set the data to display in the plot.
     * @param pathObjects the objects to display
     * @param xFun a function to extract the x value to plot
     * @param yFun a function to extract the y value to plot
     */
    public void setData(Collection<? extends PathObject> pathObjects,
                        Function<PathObject, X> xFun,
                        Function<PathObject, Y> yFun) {

        // Find the represented classes & sort them
        var newData = pathObjects
                .stream()
                .map(PathObject::getPathClass)
                .distinct()
                .sorted(Comparator.nullsFirst(PathClass::compareTo))
                .map(pc -> {
                    // create a series for each class so they appear nicely in the legend
                    return new Series<>(
                                    pc == null ? PathClass.NULL_CLASS.toString() : pc.toString(),
                                    FXCollections.observableArrayList(pathObjects.stream()
                                            .filter(po -> po.getPathClass() == pc)
                                            .map(po -> new Data<>(xFun.apply(po), yFun.apply(po), po))
                                            .toList())
                            );
                })
                .toList();

        updateLegend();

        this.getData().setAll(newData);
    }

}
