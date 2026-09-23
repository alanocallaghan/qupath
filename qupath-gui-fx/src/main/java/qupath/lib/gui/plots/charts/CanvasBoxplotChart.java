package qupath.lib.gui.plots.charts;

import javafx.animation.AnimationTimer;
import javafx.geometry.Orientation;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Axis;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

public class CanvasBoxplotChart<X,Y> extends BoxplotChart<X,Y> {
    private final Canvas canvas = new Canvas();
    private boolean redrawNeeded;

    public CanvasBoxplotChart(Axis<X> xAxis, Axis<Y> yAxis, boolean showAllPoints) {
        super(xAxis, yAxis, showAllPoints);
        timer.start();
    }

    @Override
    protected void dataItemAdded(Series<X, Y> series, int itemIndex, Data<X, Y> item) {
        // todo update boxes for this series
        // todo spatial cache
        // todo animations?
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
        if (!drawAllPoints) {
            if ((value > boxParams.lowWhisk()) && (value < boxParams.upWhisk())) {
                return;
            }
        }

        var j = jitter();
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
}
