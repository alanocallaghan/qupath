package qupath.lib.gui.charts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import qupath.lib.color.ColorMaps;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.objects.PathObject;

public class ColorScales {

    public static final ColorScale SERIES_NAME = new AbstractColorScale() {
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
        public Color getColor(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
            return colorMap.computeIfAbsent(series.getName(), _ -> getNextColor());
        }

        @Override
        public String getCategory(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
            return series.getName();
        }

    };

    static abstract class ContinuousColorScale implements ColorScale {

		protected final NumberAxis colorAxis;
		protected final ColorMaps.ColorMap colorMap;
		private final String name;
		private final Node legend;

		ContinuousColorScale(String name, ColorMaps.ColorMap colorMap) {
			this.name = name;
			this.colorAxis = new NumberAxis();
			colorAxis.setLabel(name);
			this.colorMap = colorMap;
			this.legend = createLegendItem();
		}

        @Override
        public Node getLegendItem(XYChart.Series<?,?> series, XYChart.Data<?,?> data) {
            return this.legend;
        }

		private Node createLegendItem() {
			HBox legendContainer = new HBox(); // todo probably need to handle different orientations...
			legendContainer.setAlignment(Pos.TOP_CENTER);
			legendContainer.setSpacing(5);
			int width = 100;
			int height = 20;

			Rectangle scaleRect = new Rectangle(width, height);
			List<Stop> stops = createStops(colorMap, colorAxis.getLowerBound(), colorAxis.getUpperBound(), 100);
			var grad = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, stops);
			scaleRect.setFill(grad);

			scaleRect.widthProperty().bind(colorAxis.widthProperty());

            return new VBox(scaleRect, colorAxis);
		}

		static List<Stop> createStops(ColorMaps.ColorMap colorMap, double min, double max, int nStep) {
			double range = Math.abs(max - min);
			double step = 1 / (((double)nStep) - 1);
			List<Stop> stops = new ArrayList<>();
			for (double current = 0; current <= 1; current+=step) {
				double cVal = min + (range * current);
				var color = ColorToolsFX.getCachedColor(colorMap.getColor(cVal, min, max));
				stops.add(new Stop(current, color));
			}
			return stops;
		}

	}



    public static ColorScale createMeasurementColorScale(String measurement) {
		return new ContinuousColorScale(measurement, ColorMaps.getColorMaps().get("Viridis")) {
			private double min = Double.MAX_VALUE;
			private double max = -Double.MAX_VALUE;

			private double getMeasurement(XYChart.Data<?, ?> data) {
				return ((PathObject)data.getExtraValue()).getMeasurements().get(measurement).doubleValue();
			}

			@Override
			public Color getColor(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
				double val = getMeasurement(data);
				min = Math.min(min, val);
				max = Math.max(max, val);
//				colorAxis.setLowerBound(min);
//				colorAxis.setUpperBound(max);
				// todo would be nice to set nice ticks without this and setting upper/lower directly
				colorAxis.invalidateRange(List.of(min, max));
				return ColorToolsFX.getCachedColor(
						colorMap.getColor(val, colorAxis.getLowerBound(), colorAxis.getUpperBound())
				);
			}

			@Override
			public String getCategory(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
				// should be able to return anything as it won't get used...
				return "";
			}
		};
	}

	static abstract class AbstractColorScale implements ColorScale {

		private final HashMap<String, Node> legendItems = new HashMap<>();

		@Override
		public Node getLegendItem(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
			var category = getCategory(series, data);
			return legendItems.computeIfAbsent(category, cat -> {
				HBox box = new HBox();
				box.setAlignment(Pos.CENTER);
				box.setSpacing(5);
				Label label = new Label(category);
				var color = getColor(series, data);
				Node symbol = new Circle(5, color);
				symbol.getStyleClass().setAll("chart-legend-item-symbol");
				box.getChildren().addAll(symbol, label);
				return box;
			});
		}
	}
}
