package qupath.lib.gui.plots.charts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.AccessibleRole;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.ValueAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BoxplotChart<X, Y> extends XYChart<X, Y> {
    private static final Logger logger = LoggerFactory.getLogger(BoxplotChart.class);
    protected final Orientation orientation;
    private final Random random = new Random(42);
    protected final CategoryAxis categoryAxis;
    protected final ValueAxis<Number> valueAxis;
    protected Function<Data<X, Y>, String> getCategory;
    protected Function<Data<X, Y>, Number> getNumeric;
    protected final BooleanProperty drawAllPoints = new SimpleBooleanProperty(false); // todo property?
    private final DoubleProperty markerSize = new SimpleDoubleProperty(2);
    private final DoubleProperty markerOpacity = new SimpleDoubleProperty(1);
    private final Map<Data<X,Y>, Double> jitterValues = new HashMap<>();

    protected double getJitterValue(Data<X,Y> data) {
        return jitterValues.computeIfAbsent(data, (_) -> jitter());
    }

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
     * Whether to draw all points, or only outliers
     * @return the corresponding boolean property
     */
    public BooleanProperty drawAllPointsProperty() {
        return drawAllPoints;
    }

    public void setDrawAllPoints(boolean value) {
        this.drawAllPoints.set(value);
    }

    public boolean getDrawAllPoints() {
        return drawAllPoints.get();
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

    /**
     * Constructs a XYChart given the two axes. The initial content for the chart
     * plot background and plot area that includes vertical and horizontal grid
     * lines and fills, are added.
     *
     * @param xAxis X Axis for this XY chart
     * @param yAxis Y Axis for this XY chart
     */
    public BoxplotChart(Axis<X> xAxis, Axis<Y> yAxis) {
        this(xAxis, yAxis, true);
    }

    /**
     * Constructs a XYChart given the two axes. The initial content for the chart
     * plot background and plot area that includes vertical and horizontal grid
     * lines and fills, are added.
     *
     * @param xAxis X Axis for this XY chart
     * @param yAxis Y Axis for this XY chart
     * @param drawAllPoints whether to draw all points, or only the outliers (outside 1.5*IQR)
     */
    public BoxplotChart(Axis<X> xAxis, Axis<Y> yAxis, boolean drawAllPoints) {
        super(xAxis, yAxis);
        setDrawAllPoints(drawAllPoints);
        if (!((xAxis instanceof CategoryAxis && yAxis instanceof ValueAxis) || (yAxis instanceof CategoryAxis && xAxis instanceof ValueAxis))) {
            throw new IllegalArgumentException("Illegal axis types: must supply one Category and one Value axis");
        }
        if (xAxis instanceof CategoryAxis) {
            categoryAxis = (CategoryAxis) xAxis;
            valueAxis = (ValueAxis<Number>) yAxis;
            orientation = Orientation.HORIZONTAL;
            getCategory = d -> (String) d.getXValue();
            getNumeric = d -> (Number)d.getYValue();
        } else {
            categoryAxis = (CategoryAxis) yAxis;
            valueAxis = (ValueAxis<Number>) xAxis;
            orientation = Orientation.VERTICAL;
            getNumeric = d -> (Number)d.getXValue();
            getCategory = d -> (String) d.getYValue();
        }

        if (getData() == null) {
            setData(FXCollections.observableArrayList());
        }
    }

    @Override
    protected void dataItemAdded(Series<X, Y> series, int itemIndex, Data<X, Y> item) {
        if (item.getNode() == null) {
            Node node = createPoint(item);
            item.setNode(node);
            getPlotChildren().add(item.getNode());
            node.getStyleClass().setAll("chart-symbol", "series" + getData().indexOf(series), "data" + itemIndex);
        }
        requestChartLayout();
    }

    private Node createPoint(Data<X, Y> item) {
        var symbol = new StackPane();
        symbol.setAccessibleRole(AccessibleRole.TEXT);
        symbol.setAccessibleRoleDescription("Point");
        symbol.setFocusTraversable(false);
        return symbol;
    }

    @Override
    protected void dataItemRemoved(Data<X, Y> item, Series<X, Y> series) {
        removeDataItemFromDisplay(series, item);
        requestChartLayout();
    }

    @Override
    protected void dataItemChanged(Data<X, Y> item) {
        item.setNode(createPoint(item));
        getPlotChildren().add(item.getNode());
        requestChartLayout();
    }

    @Override
    protected void seriesAdded(Series<X, Y> series, int seriesIndex) {
        for (int j = 0; j < series.getData().size(); j++) {
            Data<X, Y> item = series.getData().get(j);
            dataItemAdded(series, j, item);
        }
        requestChartLayout();
    }

    @Override
    protected void seriesChanged(ListChangeListener.Change<? extends Series> c) {
        requestChartLayout();
    }

    @Override
    protected void seriesRemoved(Series<X, Y> series) {
        requestChartLayout();
    }


    @Override
    protected void layoutPlotChildren() {
        random.setSeed(42); // todo probably parameterise this
        Map<String, List<Data<X, Y>>> valuesByCategory = collectValuesByCategory();
        resetPlotChildren();
        for (var entry: valuesByCategory.entrySet()) {
            String category = entry.getKey();
            List<Data<X,Y>> datas = entry.getValue();
            var boxParams = calculateBoxParams(datas);

            // todo if multiple series, need to dodge the boxes and adjust width
            double catPos = categoryAxis.getDisplayPosition(category);
            drawBox(boxParams, catPos);
            for (var data: datas) {
                drawPoint(data, catPos, boxParams);
            }
        }
    }

    protected void resetPlotChildren() {
        getPlotChildren().clear();
    }

    protected void drawBox(BoxParams boxParams, double catPos) {
        Group box = makeBox(
                catPos,
                valueAxis.getDisplayPosition(boxParams.lowWhisk),
                valueAxis.getDisplayPosition(boxParams.lowQuartile),
                valueAxis.getDisplayPosition(boxParams.median),
                valueAxis.getDisplayPosition(boxParams.upQuartile),
                valueAxis.getDisplayPosition(boxParams.upWhisk),
                categoryAxis.getCategorySpacing() * 0.75
        );
        getPlotChildren().add(box);
    }

    protected void drawPoint(Data<X, Y> data, double catPos, BoxParams boxParams) {
        Group containerGroup = new Group();
        getPlotChildren().add(containerGroup);

        double value = getNumeric.apply(data).doubleValue();
        double valPos = valueAxis.getDisplayPosition(value);
        var node = data.getNode();

        if (!getDrawAllPoints()) {
            if ((value > boxParams.lowWhisk) && (value < boxParams.upWhisk)) {
                node.setVisible(false);
                return;
            }
        }

        var j = getJitterValue(data);
        double x = orientation == Orientation.VERTICAL ?  valPos: catPos + j;
        double y = orientation == Orientation.VERTICAL ? catPos + j: valPos;
        // nudge points based on point size (i.e., don't centre them on the topleft of the point).
        double halfWidth = node.getBoundsInLocal().getWidth() / 2;
        double halfHeight = node.getBoundsInLocal().getHeight() / 2;
        node.setLayoutX(x - halfWidth);
        node.setLayoutY(y - halfHeight);
        containerGroup.getChildren().add(node);
    }

    protected record BoxParams(double lowWhisk, double lowQuartile, double median, double upQuartile, double upWhisk) {}

    protected BoxParams calculateBoxParams(List<Data<X, Y>> datas) {
        // sort for the sake of binary search; percentile could cope with unsorted
        double[] doubles = datas.stream().map(getNumeric)
                .mapToDouble(Number::doubleValue)
                .sorted()
                .toArray();

        Percentile percentile = new Percentile();
        percentile.setData(doubles);

        // basic quantities
        double lq = percentile.evaluate(25);
        double median = percentile.evaluate(50);
        double uq = percentile.evaluate(75);
        double iqr = uq - lq;
        // traditional boxplot whiskers are 1.5 * IQR
        double lf = lq - (1.5 * iqr);
        double uf = uq + (1.5 * iqr);
        double lowWhisk = searchForWhisker(doubles, lf);
        double upWhisk = searchForWhisker(doubles, uf);

        return new BoxParams(lowWhisk, lq, median, uq, upWhisk);
    }

    // todo this should in future handle series, I think
    protected @NonNull Map<String, List<Data<X, Y>>> collectValuesByCategory() {
        Map<String, List<Data<X,Y>>> valuesByCategory = new LinkedHashMap<>();
        for (var series : getData()) {
            for (var data : series.getData()) {
                valuesByCategory
                        .computeIfAbsent(getCategory.apply(data), (_) -> new ArrayList<>())
                        .add(data);
            }
        }
        return valuesByCategory;
    }

    private static double searchForWhisker(double[] doubles, double lf) {
        int idx = Arrays.binarySearch(doubles, lf);
        // binary search returns -(low + 1) if not found
        if (idx < 0) {
            idx = Math.clamp(-(int) idx - 1, 0, doubles.length - 1);
        }
        return doubles[idx];
    }

    // todo control jitter width + seed
    private double jitter() {
        double spacing = categoryAxis.getCategorySpacing() / 6;
        return random.nextDouble(-spacing, spacing);
    }

    private Line makeWhisker(double catPos, double startPos, double endPos) {
        if (orientation == Orientation.VERTICAL) {
            return new Line(startPos, catPos, endPos, catPos);
        } else {
            return new Line(catPos, startPos, catPos, endPos);
        }
    }

    private Line makeLine(double catPos, double valPos, double width) {
        if (orientation == Orientation.VERTICAL) {
            return new Line(valPos, catPos - (width/2), valPos,catPos + (width/2));
        } else {
            return new Line(catPos - (width/2), valPos, catPos + (width/2), valPos);
        }
    }

    private Rectangle makeRect(double catPos, double lowPos, double highPos, double boxSize) {
        if (orientation == Orientation.VERTICAL) {
            return new Rectangle(lowPos, catPos - (boxSize / 2), Math.abs(highPos  - lowPos), boxSize);
        } else {
            double minY = Math.min(lowPos, highPos);
            double height = Math.abs(lowPos - highPos);
            return new Rectangle(catPos - (boxSize / 2), minY, boxSize, height);
        }
    }


    private Group makeBox(double categoryPosition, double lowWhisk, double lq, double median, double uq, double upWhisk, double width) {
        Line lowWhisker = makeWhisker(categoryPosition, lowWhisk, lq);
        Line lowCap = makeLine(categoryPosition, lowWhisk, width / 2);

        Line medLine = makeLine(categoryPosition, median, width);

        Line highWhisker = makeWhisker(categoryPosition, upWhisk, uq);
        Line highCap = makeLine(categoryPosition, upWhisk, width / 2);
        // todo fill by category somehow? or is grey fine
        Rectangle rect = makeRect(categoryPosition, lq, uq, width);
        rect.setFill(Color.TRANSPARENT);
        rect.setStroke(Color.GRAY);

        Group group = new Group();
        group.getChildren().addAll(
                rect,
                lowWhisker,
                lowCap,
                highWhisker,
                highCap,
                medLine
        );
        return group;
    }

    @Override
    protected void updateAxisRange() {
        var data = getData();
        if (data == null || data.isEmpty()) {
            return;
        }
        Set<String> categories = new LinkedHashSet<>();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Series<X,Y> series: data) {
            for (Data<X,Y> item: series.getData()) {
                double val = getNumeric.apply(item).doubleValue();
                min = Math.min(min, val);
                max = Math.max(max, val);
                categories.add(getCategory.apply(item));
            }
        }
        if (categoryAxis != null && !categories.isEmpty()) {
            categoryAxis.setCategories(FXCollections.observableArrayList(categories));
        }
        if (valueAxis != null && valueAxis.isAutoRanging() && min <= max) {
            valueAxis.invalidateRange(List.of(min, max));
        }
    }
}
