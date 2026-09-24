package qupath.lib.gui.plots.display;

import javafx.scene.layout.Pane;
import qupath.lib.gui.measure.PathTableData;

/**
 * Wrapping charts for displaying data about PathObject measurements and classes, and similar objects.
 */
public interface PlotDisplay {

    /**
     * Return the name of this type of plot, e.g., "Box plot", "Scatter plot", "Histogram"
     * @return the type of plot
     */
    String getName();

    /**
     * Get the primary pane used to display the plot
     * @return the pane
     */
    Pane getPane();

    /**
     * Request the plot be updated with current settings
     */
    void requestRefresh();

    /**
     * Update the data model underlying the data
     * @param model the data model
     */
    void setModel(PathTableData<?> model);

    /**
     * Update plot for specified data columns.
     * @param columns the names of the columns to show
     */
    void plotColumns(String... columns);
}
