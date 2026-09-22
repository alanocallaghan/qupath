package qupath.lib.gui.plots.charts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BoxPlotChart<X, Y> extends XYChart<X, Y> {
    private final boolean drawAllPoints;
    private final CategoryAxis categoryAxis;
    private final ValueAxis valueAxis;
    private final Orientation orientation;
    private final Random random = new Random(42);
    private static final Logger logger = LoggerFactory.getLogger(BoxPlotChart.class);
    private Function<Data<X, Y>, String> getCategory;
    private Function<Data<X, Y>, Number> getNumeric;

    /**
     * Constructs a XYChart given the two axes. The initial content for the chart
     * plot background and plot area that includes vertical and horizontal grid
     * lines and fills, are added.
     *
     * @param xAxis X Axis for this XY chart
     * @param yAxis Y Axis for this XY chart
     */
    public BoxPlotChart(Axis<X> xAxis, Axis<Y> yAxis) {
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
    public BoxPlotChart(Axis<X> xAxis, Axis<Y> yAxis, boolean drawAllPoints) {
        super(xAxis, yAxis);
        this.drawAllPoints = drawAllPoints;
        if (!((xAxis instanceof CategoryAxis && yAxis instanceof ValueAxis) || (yAxis instanceof CategoryAxis && xAxis instanceof ValueAxis))) {
            throw new IllegalArgumentException("Illegal axis types: must supply one Category and one Value axis");
        }
        if (xAxis instanceof CategoryAxis) {
            categoryAxis = (CategoryAxis) xAxis;
            valueAxis = (ValueAxis) yAxis;
            orientation = Orientation.HORIZONTAL;
            getCategory = d -> (String) d.getXValue();
            getNumeric = d -> (Number)d.getYValue();
        } else {
            categoryAxis = (CategoryAxis) yAxis;
            valueAxis = (ValueAxis) xAxis;
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
        // todo update boxes for this series
        if (item.getNode() == null) {
            Node node = createPoint(item);
            item.setNode(node);
            getPlotChildren().add(item.getNode());
            node.getStyleClass().setAll("chart-symbol", "series" + getData().indexOf(series), "data" + itemIndex);
        }
        // todo animations?
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
        // todo update boxes for this series
        // todo animations
        removeDataItemFromDisplay(series, item);
        requestChartLayout();
    }

    @Override
    protected void dataItemChanged(Data<X, Y> item) {
        // todo animations; update boxes
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
        // todo update boxes
        requestChartLayout();
    }

    @Override
    protected void seriesRemoved(Series<X, Y> series) {
        requestChartLayout();
    }

    @Override
    protected void layoutPlotChildren() {
        Map<String, List<Data<X,Y>>> valuesByCategory = new LinkedHashMap<>();
        for (var series : getData()) {
            for (var data : series.getData()) {
                valuesByCategory
                        .computeIfAbsent(getCategory.apply(data), (_) -> new ArrayList<>())
                        .add(data);
            }
        }
        getPlotChildren().clear();
        for (var entry: valuesByCategory.entrySet()) {
            String category = entry.getKey();
            List<Data<X,Y>> datas = entry.getValue();
            List<Number> values = datas.stream().map(getNumeric).toList();
            double[] doubles = values.stream().mapToDouble(Number::doubleValue).toArray();

            // sort for the sake of binary search; percentile could cope with unsorted
            Arrays.sort(doubles);
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

            // todo if multiple series, need to dodge the boxes and adjust width
            double catPos = categoryAxis.getDisplayPosition(category);
            Group box = makeBox(
                    catPos,
                    valueAxis.getDisplayPosition(lowWhisk),
                    valueAxis.getDisplayPosition(lq),
                    valueAxis.getDisplayPosition(median),
                    valueAxis.getDisplayPosition(uq),
                    valueAxis.getDisplayPosition(upWhisk),
                    categoryAxis.getCategorySpacing() * 0.75
            );

            getPlotChildren().add(box);
            Group containerGroup = new Group();
            getPlotChildren().add(containerGroup);

            for (var data: datas) {
                double value = getNumeric.apply(data).doubleValue();
                double valPos = valueAxis.getDisplayPosition(value);
                var j = jitter();
                double x = orientation == Orientation.VERTICAL ?  valPos: catPos + j;
                double y = orientation == Orientation.VERTICAL ? catPos + j: valPos;
                var node = data.getNode();
                // nudge points based on point size (i.e., don't centre them on the topleft of the point).
                double halfWidth = node.getBoundsInLocal().getWidth() / 2;
                double halfHeight = node.getBoundsInLocal().getHeight() / 2;
                node.setLayoutX(x - halfWidth);
                node.setLayoutY(y - halfHeight);
                if (!drawAllPoints) {
                    if ((value > lowWhisk) && (value < upWhisk)) {
                        node.setVisible(false);
                    }
                }
                containerGroup.getChildren().add(node);
            }

        }
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

    private Rectangle makeRect(double catPos, double lowPos, double highPos, double width) {
        if (orientation == Orientation.VERTICAL) {
            return new Rectangle(lowPos, catPos - (width/2), Math.abs(highPos  - lowPos), width);
        } else {
            double minY = Math.min(lowPos, highPos);
            double height = Math.abs(lowPos - highPos);
            return new Rectangle(catPos - (width/2), minY, width, height);
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
