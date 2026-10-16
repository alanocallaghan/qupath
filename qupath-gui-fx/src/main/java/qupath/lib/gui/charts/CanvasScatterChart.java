package qupath.lib.gui.charts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import javafx.animation.AnimationTimer;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Axis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.ValueAxis;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
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

public class CanvasScatterChart<X,Y> extends ScatterChart<X,Y> implements CanvasChart<X,Y> {
    private static final Logger logger = LoggerFactory.getLogger(CanvasScatterChart.class);

    private final GeometryFactory gf;
    private final Quadtree tree;
    private final Canvas canvas = new Canvas();
    private boolean redrawNeeded;

    private final Map<Series<X,Y>, Boolean> seriesVisible = new HashMap<>();

    private final DoubleProperty markerRadius = new SimpleDoubleProperty(2);
    private final DoubleProperty markerOpacity = new SimpleDoubleProperty(1);

    private final IntegerProperty randomSeed = new SimpleIntegerProperty(123);

    private ObjectProperty<ColorScale<?>> colorScaleObjectProperty = new SimpleObjectProperty<>(ColorScale.SERIES_NAME);

    public void setColorScale(ColorScale<?> colorScale) {
        this.colorScaleObjectProperty.set(colorScale);
    }


    /**
     * Construct a CanvasScatterChart with the two axes and the defined color map.
     * @param xAxis the x-axis for this chart
     * @param yAxis the y-axis for this chart
     */
    public CanvasScatterChart(Axis<X> xAxis, Axis<Y> yAxis) {
        super(xAxis, yAxis);
        this.tree = new Quadtree();
        this.gf = new GeometryFactory();
        initProperties();

        getPlotChildren().add(canvas);
    }

    private void initProperties() {
        // unsure if this is idiomatic
        markerOpacity.subscribe(this::redraw);
        markerOpacity.addListener((v, o, n) -> {
            if (n.doubleValue() <= 0 || n.doubleValue() > 1 || !Double.isFinite(n.doubleValue())) markerOpacity.set(o.doubleValue());
        });
        markerRadius.subscribe(this::redraw);
        markerRadius.addListener((v, o, n) -> {
            if (n.doubleValue() <= 0 || !Double.isFinite(n.doubleValue())) markerRadius.set(o.doubleValue());
        });
        sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::showingProperty).subscribe(n -> {
            if (Boolean.TRUE.equals(n))
                timer.start();
            else
                timer.stop();;
        });

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

        @SuppressWarnings("unchecked") List<Data<X,Y>> candidates = (List<Data<X,Y>>)tree.query(search);
        logger.debug("{} candidates found", candidates.size());
        Data<X,Y> closestPoint = null;
        double minDistance = Double.MAX_VALUE;
        double maxDistance = getMarkerRadius();
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

    /**
     * The radius of markers on this chart
     * @return the property corresponding to marker radius
     */
    public DoubleProperty markerRadiusProperty() {
        return markerRadius;
    }

    /**
     * Get the current marker radius
     * @return the marker radius
     */
    public double getMarkerRadius() {
        return markerRadius.get();
    }

    /**
     * Set the marker size
     * @param value the new value
     */
    public void setMarkerRadius(double value) {
        this.markerRadius.set(value);
    }

    /**
     * The opacity of markers in this plot
     * @return the property corresponding to marker opacity
     */
    public DoubleProperty markerOpacityProperty() {
        return markerOpacity;
    }

    /**
     * Get the current marker opacity
     * @return the marker opacity in [0, 1)
     */
    public double getMarkerOpacity() {
        return markerOpacity.get();
    }

    /**
     * Set the opacity used to draw markers
     * @param value the new value in [0, 1)
     */
    public void setMarkerOpacity(double value) {
        this.markerOpacity.set(value);
    }
    
    /**
     * The random seed property
     * @return the property
     */
    public IntegerProperty randomSeedProperty() {
        return randomSeed;
    }

    /**
     * Set the random seed
     * @return the current seed
     */
    public int getRandomSeed() {
        return randomSeed.get();
    }

    @Override
    public Canvas getCanvas() {
        return canvas;
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
        // also keeping it on data scale means it does not need to be updated
        Point p = gf.createPoint(new Coordinate(
                (Double) item.getXValue(),
                (Double) item.getYValue())
        );
        return p.getEnvelopeInternal();
    }

    @Override
    protected void layoutPlotChildren() {
        redraw();
    }

    private void redraw() {
        var context = canvas.getGraphicsContext2D();
        context.setGlobalAlpha(markerOpacity.get());
        context.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        double rad = getMarkerRadius();
        for (Series<X,Y> series : getData()) {
            if (seriesVisible.computeIfAbsent(series, _ -> true)) {
                for (var point : series.getData()) {
                    var color = colorScaleObjectProperty.get().getColor(series, point);
                    context.setFill(color);
                    // fillOval uses bounding box coords
                    context.fillOval(
                            getXAxis().getDisplayPosition(point.getXValue()) - rad,
                            getYAxis().getDisplayPosition(point.getYValue()) - rad,
                            rad * 2, rad * 2);
                }
            }
        }

    }

    @Override
    protected void updateLegend() {
        List<Node> legendList = new ArrayList<>();
        HBox container = new HBox(); // todo probably need to handle different orientations...
        container.setAlignment(Pos.CENTER);
        container.setSpacing(5);
        Set<Pair<String, Color>> categories = new HashSet<>();
        if (getData() != null) {
            for (Series<X,Y> series : getData()) {
                for (var point : series.getData()) {
                    categories.add(
                            new Pair<>(
                                    colorScaleObjectProperty.get().getCategoryName(series, point),
                                    colorScaleObjectProperty.get().getColor(series, point)
                            )
                    );
                }
            }
        }
        for (var category : categories) {
            HBox box = new HBox();
            box.setAlignment(Pos.CENTER);
            box.setSpacing(5);
            Label label = new Label(category.getKey());
            Node symbol = new Circle(5, category.getValue());
            symbol.getStyleClass().setAll("chart-legend-item-symbol");
            box.getChildren().addAll(symbol, label);
            legendList.add(box);
        }

        container.getChildren().setAll(legendList);
        setLegend(container);
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
            updateLegend();
        }
        redrawNeeded = false;
    }

}
