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

    /**
     * Create a canvas boxplot with the specified XY-axes
     * @param xAxis the x-axis (string or numeric)
     * @param yAxis the y-axis (string or numeric)
     * @param showAllPoints whether to draw all points, or only outliers
     */
    public CanvasBoxplotChart(Axis<X> xAxis, Axis<Y> yAxis, boolean showAllPoints) {
        super(xAxis, yAxis, showAllPoints);
        sceneProperty().flatMap(Scene::windowProperty).flatMap(Window::showingProperty).subscribe(n -> {
            if (Boolean.TRUE.equals(n))
                timer.start();
            else
                timer.stop();
        });
    }

    @Override
    public Optional<Data<X,Y>> findDataPoint(double x, double y, double tolerance) {
        String category = categoryAxis.getValueForDisplay(getOrientation() == Orientation.HORIZONTAL ? x : y);
        double value = valueAxis.getValueForDisplay(getOrientation() == Orientation.HORIZONTAL ? y : x).doubleValue();

        List<Data<X,Y>> candidates = new ArrayList<>();
        for (var series: getData()) {
            for (var item: series.getData()) {
                // only drawn items have non-null nodes, and they all share one...
                if (item.getNode() == null) {
                    continue;
                }
                if (getCategory.apply(item).equals(category)) {
                    double itemValue = getNumeric.apply(item).doubleValue();
                    if (itemValue < (value + getMarkerRadius()) && itemValue > (value - getMarkerRadius())) {
                        candidates.add(item);
                    }
                }
            }
        }
        logger.debug("{} candidates found", candidates.size());

        Data<X,Y> closestPoint = null;
        double minDistance = Double.MAX_VALUE;
        double maxDistance = getMarkerRadius() / 2;

        Coordinate clickCoord = new Coordinate(x, y);
        for (Data<X,Y> candidate : candidates) {
            // candidate values are on data scale
            double cx = getXAxis().getDisplayPosition(candidate.getXValue());
            double cy = getYAxis().getDisplayPosition(candidate.getYValue());
            if (getOrientation() == Orientation.HORIZONTAL) {
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

    @Override
    public Canvas getCanvas() {
        return this.canvas;
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
        double x = getOrientation() == Orientation.VERTICAL ?  valPos: catPos + j;
        double y = getOrientation() == Orientation.VERTICAL ? catPos + j: valPos;
        var context = canvas.getGraphicsContext2D();
        context.setFill(Color.BLACK);
        context.setGlobalAlpha(getMarkerOpacity());
        // nudge points based on point radius (i.e., don't center them on the top left of the point).
        double rad = getMarkerRadius();
        context.fillOval(x - rad, y - rad, rad * 2, rad * 2);
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

}
