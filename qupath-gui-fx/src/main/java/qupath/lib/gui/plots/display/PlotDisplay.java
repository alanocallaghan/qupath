package qupath.lib.gui.plots.display;

import javafx.scene.layout.Pane;
import qupath.lib.gui.measure.PathTableData;

/**
 * Wrapping charts for displaying data about PathObject measurements and classes.
 */
public interface PlotDisplay {

    String getName();

    Pane getPane();

    void requestRefresh();

    void setModel(PathTableData<?> model);

    /**
     * Show a plot for specified data columns.
     * @param columns the names of the columns to show
     */
    void showPlot(String... columns);
}
