package qupath.lib.gui.plots.charts;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.animation.AnimationTimer;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Axis;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Window;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CanvasBoxplotChart<X,Y> extends BoxplotChart<X,Y> implements CanvasChart<X,Y> {
    private static final Logger logger = LoggerFactory.getLogger(CanvasBoxplotChart.class);
    private final Canvas canvas = new Canvas();
    private boolean redrawNeeded;

    // hackery abounds: use a single private node to mark data points being drawn or not for the findObject method
    private final Node node = new Circle();

    public CanvasBoxplotChart(Axis<X> xAxis, Axis<Y> yAxis, boolean showAllPoints) {
        super(xAxis, yAxis, showAllPoints);
        sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::showingProperty).subscribe(n -> {
            if (Boolean.TRUE.equals(n))
                timer.start();
            else
                timer.stop();;
        });
    }

    @Override
    protected void dataItemAdded(Series<X, Y> series, int itemIndex, Data<X, Y> item) {
        requestChartLayout();
    }

    @Override
    protected void resetPlotChildren() {
        getPlotChildren().clear();
        Pane plotContent = (Pane) lookup(".chart-content");
        if (plotContent != null) {
            canvas.widthProperty().bind(plotContent.widthProperty());
            canvas.heightProperty().bind(plotContent.heightProperty());
            getPlotChildren().add(canvas);
            canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        }
    }

    @Override
    protected void drawPoint(Data<X, Y> data, double catPos, BoxParams boxParams) {

        double value = getNumeric.apply(data).doubleValue();
        double valPos = valueAxis.getDisplayPosition(value);
        if (!getDrawAllPoints()) {
            if ((value > boxParams.lowWhisk()) && (value < boxParams.upWhisk())) {
                return;
            }
        }
        data.setNode(node);

        var j = getJitterValue(data);
        double x = orientation == Orientation.VERTICAL ?  valPos: catPos + j;
        double y = orientation == Orientation.VERTICAL ? catPos + j: valPos;
        // nudge points based on point size (i.e., don't centre them on the topleft of the point).
        var context = canvas.getGraphicsContext2D();
        context.setFill(Color.BLACK);
        context.setGlobalAlpha(getMarkerOpacity());
        context.fillOval(
                x - (getMarkerSize() / 2),
                y - (getMarkerSize() / 2),
                getMarkerSize(), getMarkerSize()
        );
    }

    private final AnimationTimer timer = new AnimationTimer() {

        @Override
        public void handle(long now) {
            handlePulse();
        }

    };

    private void handlePulse() {
        if (redrawNeeded) {
            layoutPlotChildren();
        }
        redrawNeeded = false;
    }


    @Override
    public Optional<Data<X,Y>> findDataPoint(double x, double y, double tolerance) {
        String category = categoryAxis.getValueForDisplay(orientation == Orientation.HORIZONTAL ? x : y);
        double value = valueAxis.getValueForDisplay(orientation == Orientation.HORIZONTAL ? y : x).doubleValue();

        List<Data<X,Y>> candidates = new ArrayList<>();
        for (var series: getData()) {
            for (var item: series.getData()) {
                // only drawn items have non-null nodes, and they all share one...
                if (item.getNode() == null) {
                    continue;
                }
                if (getCategory.apply(item).equals(category)) {
                    double itemValue = getNumeric.apply(item).doubleValue();
                    if (itemValue < (value + getMarkerSize()) && itemValue > (value - getMarkerSize())) {
                        candidates.add(item);
                    }
                }
            }
        }
        logger.debug("{} candidates found", candidates.size());

        Data<X,Y> closestPoint = null;
        double minDistance = Double.MAX_VALUE;
        double maxDistance = getMarkerSize() / 2;

        Coordinate clickCoord = new Coordinate(x, y);
        for (Data<X,Y> candidate : candidates) {
            // candidate values are on data scale
            double cx = getXAxis().getDisplayPosition(candidate.getXValue());
            double cy = getYAxis().getDisplayPosition(candidate.getYValue());
            if (orientation == Orientation.HORIZONTAL) {
                cx += getJitterValue(candidate);
            } else {
                cy += getJitterValue(candidate);
            }
            Coordinate candidateCoord = new Coordinate(cx, cy);
            double distance = candidateCoord.distance(clickCoord);
            logger.debug("Distance from {} to {}: {}", clickCoord, candidateCoord, distance);
            if (distance <= minDistance && distance < maxDistance) {
                minDistance = distance;
                closestPoint = candidate;
            }
        }
        logger.debug("Min distance found {}", minDistance);
        return Optional.ofNullable(closestPoint);
    }

    public Canvas getCanvas() {
        return this.canvas;
    }
}
