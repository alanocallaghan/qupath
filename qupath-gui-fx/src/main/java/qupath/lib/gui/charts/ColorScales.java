package qupath.lib.gui.charts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import qupath.lib.color.ColorMaps;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.objects.PathObject;

public class ColorScales {

	/**
	 * Color points based on the series they belong to, labelling them using the series name.
	 * This is more-or-less equivalent to the native way that Charts work.
	 */
    public static ColorScale colorBySeriesName() {
		return new AbstractColorScale() {
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
				int i = colorIdx++;
				var palette = getDefaultColors();
				if (i < palette.size()) {
					return palette.get(i);
				}
				int n = i - palette.size();
				double hue = (n * 137.5) % 360;
				return Color.hsb(hue, 0.6, 0.7);
			}


			@Override
			public Color getColor(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
				return colorMap.computeIfAbsent(series.getName(), _ -> getNextColor());
			}

		};
	}

	/**
	 * Color points based on a continuous measurement.
	 */
    public static abstract class ContinuousColorScale implements ColorScale {

		protected final NumberAxis colorAxis;
		protected final ColorMaps.ColorMap colorMap;
		protected final String name;
		protected Node legend;
		private Side side;
		private Region scaleRect;
		private DoubleProperty upperBound = new SimpleDoubleProperty(Double.NEGATIVE_INFINITY);
		private DoubleProperty lowerBound = new SimpleDoubleProperty(Double.POSITIVE_INFINITY);

		ContinuousColorScale(String name, ColorMaps.ColorMap colorMap, Side side) {
			this.colorAxis = new NumberAxis();
			this.name = name;
			colorAxis.setLabel(this.name);
			colorAxis.setForceZeroInRange(false);
			colorAxis.setMinorTickVisible(false);
			colorAxis.setAnimated(false);

			this.colorMap = colorMap;
			this.side = side;
			this.legend = createLegendItem();
		}

		public void setLowerBound(double lowerBound) {
			this.lowerBound.set(lowerBound);
		}

		public double getLowerBound() {
			return this.lowerBound.getValue();
		}

		public void setUpperBound(double upperBound) {
			this.upperBound.set(upperBound);
		}

		public double getUpperBound() {
			return this.upperBound.get();
		}

		public void setAutoRanging(boolean autoRanging) {
			colorAxis.setAutoRanging(autoRanging);
		}

		public boolean isAutoRanging() {
			return colorAxis.isAutoRanging();
		}

		// todo tick setter and builder

        @Override
        public Node getLegendItem(XYChart.Series<?,?> series, XYChart.Data<?,?> data) {
			return this.legend;
        }

		private Node createLegendItem() {
			scaleRect = new Region();
			remakeFill();

			Pane container;
			if (side.isVertical()) {
				var box = new HBox();
				if (side == Side.LEFT) {
					box.getChildren().addAll(colorAxis, scaleRect);
				} else {
					box.getChildren().addAll(scaleRect, colorAxis);
				}
				scaleRect.setPrefWidth(20); // todo non-fixed
				colorAxis.setSide(side);
				container = box;
				container.setPadding(new Insets(20, 5, 20, 5));
			} else {
				var box = new VBox();
				if (side == Side.TOP) {
					box.getChildren().addAll(colorAxis, scaleRect);
				} else {
					box.getChildren().addAll(scaleRect, colorAxis);
				}
				scaleRect.setPrefHeight(20); // todo as above
				colorAxis.setSide(side);
				container = box;
				container.setPadding(new Insets(5, 25, 5, 25));
			}
			container.getStyleClass().add("chart-legend");
            return container;
		}

		@Override
		public void setSide(Side side) {
			this.side = side;
			this.legend = createLegendItem();
		}

		protected void remakeFill() {
			List<Stop> stops = createStops(colorMap, colorAxis.getLowerBound(), colorAxis.getUpperBound(), 100);
			LinearGradient grad;
			if (side.isVertical()) {
				grad = new LinearGradient(0, 1, 0, 0, true, CycleMethod.NO_CYCLE, stops);
			} else {
				grad = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, stops);
			}
			scaleRect.setBackground(new Background(new BackgroundFill(grad, null, null)));
		}

		static List<Stop> createStops(ColorMaps.ColorMap colorMap, double min, double max, int nStep) {
			double range = Math.abs(max - min);
			double step = 1.0 / (nStep - 1);
			List<Stop> stops = new ArrayList<>();
			for (double current = 0; current <= 1; current+=step) {
				double cVal = min + (range * current);
				var color = ColorToolsFX.getCachedColor(colorMap.getColor(cVal, min, max));
				stops.add(new Stop(current, color));
			}
			return stops;
		}
	}


	public static class MeasurementColorScale extends ContinuousColorScale {
		MeasurementColorScale(String name, ColorMaps.ColorMap colorMap, Side side) {
			super(name, colorMap, side);
		}

		@Override
		public void prepare(List<? extends XYChart.Series<?, ?>> seriesList) {
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			for (var series: seriesList) {
				for (var data : series.getData()) {
					double val = getMeasurement(data);
					if (!Double.isFinite(val) || Double.isNaN(val)) {
						continue;
					}
					min = Math.min(min, val);
					max = Math.max(max, val);
				}
			}
			if (min == Double.POSITIVE_INFINITY && max == Double.NEGATIVE_INFINITY) {
				return;
			}
			// first set the bounds directly so that we can calculate colors
			setLowerBound(Math.min(min, getLowerBound()));
			setUpperBound(Math.max(max, getUpperBound()));
			colorAxis.setLowerBound(getLowerBound());
			colorAxis.setUpperBound(getUpperBound());

			// then call invalidateRange so we hopefully get the nice auto-ranging from jfx (if set)
			colorAxis.invalidateRange(List.of());
			remakeFill();
		}

		private double getMeasurement(XYChart.Data<?, ?> data) {
			if (data.getExtraValue() instanceof PathObject pathObject) {
				return pathObject.getMeasurementList().get(name);
			} else {
				return Double.NaN;
			}
		}

		@Override
		public Color getColor(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
			double val = getMeasurement(data);
			if (!Double.isFinite(val) || Double.isNaN(val)) {
				// todo parameterize
				return Color.TRANSPARENT;
			}
			return ColorToolsFX.getCachedColor(
					colorMap.getColor(val, colorAxis.getLowerBound(), colorAxis.getUpperBound())
			);
		}
	}


	public static ColorScale createMeasurementColorScale(String measurement, ColorMaps.ColorMap colorMap) {
		return createMeasurementColorScale(measurement, colorMap, Side.BOTTOM);
	}

	/**
	 * Color points based on a PathObject measurement field
	 * @param measurement the measurement
	 * @param colorMap the color map to be used
	 * @return a color scale
	 */
    public static ColorScale createMeasurementColorScale(String measurement, ColorMaps.ColorMap colorMap, Side side) {
		return new MeasurementColorScale(measurement, colorMap, side);
	}

	/**
	 * An abstract class that provides a shared legend item for each category in the data,
	 * whether that is provided by series name or otherwise.
	 */
	public static abstract class AbstractColorScale implements ColorScale {

		private final Map<String, Node> legendItems = new HashMap<>();
		protected Side side; // todo use

		@Override
		public void setSide(Side legendSide) {
			this.side = legendSide;
		}

		@Override
		public void prepare(List<? extends XYChart.Series<?, ?>> seriesList) {}

		@Override
		public Node getLegendItem(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
			var category = getCategory(series, data);
			return legendItems.computeIfAbsent(category, cat -> {
				HBox box = new HBox();
				box.setAlignment(Pos.CENTER);
				box.setSpacing(5);
				Label label = new Label(cat);
				var color = getColor(series, data);
				Node symbol = new Circle(5, color);
				symbol.getStyleClass().setAll("chart-legend-item-symbol");
				box.getChildren().addAll(symbol, label);
				return box;
			});
		}

		protected String getCategory(XYChart.Series<?, ?> series, XYChart.Data<?, ?> data) {
			return series.getName();
		}
	}
}
