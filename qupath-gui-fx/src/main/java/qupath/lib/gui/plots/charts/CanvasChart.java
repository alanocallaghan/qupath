package qupath.lib.gui.plots.charts;

import java.util.Optional;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.XYChart;

public interface CanvasChart<X, Y> {
    // note that canvas could be an ObjectProperty

    /**
     * Get the canvas used to render points
     * @return the canvas
     */
    Canvas getCanvas();

    /**
     * Find the datapoint closest to plot area x,y coordinates
     * @param x the pixel x-coordinate
     * @param y the pixel y-coordinate
     * @return the nearest point that contains the input point
     */
    Optional<XYChart.Data<X, Y>> findDataPoint(double x, double y, double tolerance);

}
