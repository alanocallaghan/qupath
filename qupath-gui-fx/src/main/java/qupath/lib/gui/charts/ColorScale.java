package qupath.lib.gui.charts;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.chart.XYChart;
import javafx.scene.paint.Color;

public interface ColorScale<T> {
    default Color getColor(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
        return getColor(getCategory(series, data));
    }

    Color getColor(T value);

    T getCategory(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data);

    String getCategoryName(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data);

    static final ColorScale<String> SERIES_NAME = new ColorScale<String>() {
        private int colorIdx = 0;
        private final Map<String, Color> colorMap = new HashMap<>();

        // my best attempt at non-terrible non-clashing default colors. Could instead use something from ColorBrewer
        /**
         * A default color palette when colors aren't supplied
         * @return An immutable list of pre-defined colors.
         */
        List<Color> getDefaultColors() {
            return List.of(
                    Color.FIREBRICK, Color.DODGERBLUE, Color.FORESTGREEN,
                    Color.GOLDENROD, Color.DARKMAGENTA, Color.TEAL,
                    Color.DEEPPINK, Color.CHOCOLATE, Color.SLATEBLUE,
                    Color.DARKSLATEGRAY);
        }

        private Color getNextColor() {
            return getDefaultColors().get(colorIdx++);
        }

        @Override
        public Color getColor(String value) {
            return colorMap.computeIfAbsent(value, _ -> getNextColor());
        }

        @Override
        public String getCategory(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
            return series.getName();
        }

        @Override
        public String getCategoryName(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
            return series.getName();
        }
    };

}
