package qupath.lib.gui.charts;

import javafx.scene.Node;
import javafx.scene.chart.XYChart;
import javafx.scene.paint.Color;

public interface ColorScale {
    Color getColor(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data);

    String getCategory(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data);

    // todo document weird requirements of singleton for category and for continuous
    Node getLegendItem(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data);
}
