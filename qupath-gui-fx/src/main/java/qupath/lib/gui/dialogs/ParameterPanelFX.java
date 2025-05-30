/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020, 2025 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui.dialogs;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Locale.Category;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import qupath.lib.common.GeneralTools;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.plugins.parameters.BooleanParameter;
import qupath.lib.plugins.parameters.ChoiceParameter;
import qupath.lib.plugins.parameters.DoubleParameter;
import qupath.lib.plugins.parameters.EmptyParameter;
import qupath.lib.plugins.parameters.IntParameter;
import qupath.lib.plugins.parameters.NumericParameter;
import qupath.lib.plugins.parameters.Parameter;
import qupath.lib.plugins.parameters.ParameterChangeListener;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.parameters.StringParameter;

/**
 * A panel for displaying a list of parameters suitably to aid with creating JavaFX GUIs.
 * 
 * @author Pete Bankhead
 *
 */
public class ParameterPanelFX {

	private final List<ParameterChangeListener> listeners = Collections.synchronizedList(new ArrayList<>());

	private static final Logger logger = LoggerFactory.getLogger(ParameterPanelFX.class);
	
	private static int DEFAULT_NUMERIC_TEXT_COLS = 8;
	
	private final GridPane pane;
	private final ParameterList params;
	private final Map<Parameter<?>, Node> map = new HashMap<>();

	/**
	 * Create a ParameterPanelFX.
	 * 
	 * @param params
	 */
	public ParameterPanelFX(final ParameterList params) {
		this(params, new GridPane());
	}
	
	/**
	 * Create a ParameterPanelFX using a specified GridPane.
	 * 
	 * @param params
	 * @param gridPane
	 */
	private ParameterPanelFX(final ParameterList params, final GridPane gridPane) {
		super();
		this.params = params;
		this.pane = gridPane == null ? new GridPane() : gridPane;
		initialize();
		pane.setVgap(4);
		pane.setHgap(4);
	}
	
	/**
	 * Get the {@link ParameterList} displayed in this panel.
	 * @return
	 */
	public ParameterList getParameters() {
		return params;
	}
	
	/**
	 * Get the {@link Pane} that can be displayed.
	 * @return
	 */
	public Pane getPane() {
		return pane;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void initialize() {
		for (Entry<String, Parameter<?>> entry : params.getParameters().entrySet()) {
			String key = entry.getKey();
			Parameter<?> p = entry.getValue();
			// Don't show hidden parameters
			if (p.isHidden())
				continue;
            switch (p) {
                case DoubleParameter doubleParameter -> addDoubleParameter(key, doubleParameter);
                case IntParameter intParameter -> addIntParameter(key, intParameter);
                case StringParameter stringParameter -> addStringParameter(key, stringParameter);
                case EmptyParameter emptyParameter -> addEmptyParameter(emptyParameter);
                case ChoiceParameter choiceParameter -> addChoiceParameter(key, choiceParameter);
                case BooleanParameter booleanParameter -> addBooleanParameter(key, booleanParameter);
                default -> {
					logger.warn("Unknown parameter type: {}", p.getClass().getName());
                }
            }
		}
	}
	
	/**
	 * Add a {@link ParameterChangeListener} to be notified as the user modifies parameters.
	 * @param listener
	 * @see #removeParameterChangeListener(ParameterChangeListener)
	 */
	public void addParameterChangeListener(ParameterChangeListener listener) {
		listeners.add(listener);
	}

	/**
	 * Remove a {@link ParameterChangeListener}.
	 * @param listener
	 * @see #addParameterChangeListener(ParameterChangeListener)
	 */
	public void removeParameterChangeListener(ParameterChangeListener listener) {
		listeners.remove(listener);
	}
	
	
	private String getKey(Parameter<?> param) {
		// This is a rather clumsy workaround as the keys are not currently stored here...
		for (Entry<String, Parameter<?>> entry : params.getParameters().entrySet()) {
			if (entry.getValue().equals(param))
				return entry.getKey();
		}
		return null;
	}
	
	
	private void fireParameterChangedEvent(Parameter<?> param, boolean isAdjusting) {
		String key = getKey(param);
		if (key != null) {
			for (ParameterChangeListener listener: listeners) {
				listener.parameterChanged(params, key, isAdjusting);
			}
		}
	}

	private void addBooleanParameter(String key, BooleanParameter param) {
		addCheckBoxParameter(key, param);
	}
	
	private void addDoubleParameter(String key, DoubleParameter param) {
		if (param.hasLowerAndUpperBounds())
			addSliderParameter(key, param);
		else
			addNumericTextField(key, param);
	}

	private void addIntParameter(String key, IntParameter param) {
		if (param.hasLowerAndUpperBounds())
			addSliderParameter(key, param);
		else
			addNumericTextField(key, param);
	}
	
	private void addNumericTextField(String key, NumericParameter<? extends Number> param) {
		TextField tf = getTextField(param, DEFAULT_NUMERIC_TEXT_COLS, key);
		if (param.getUnit() != null) {
			Pane panel = new HBox();
			panel.getChildren().add(tf);
			Label label = new Label(param.getUnit());
			label.setPadding(new Insets(0, 0, 0, 4));
			panel.getChildren().add(label);
			addParamComponent(param, param.getPrompt(), panel);
		} else {
			Pane panel = new Pane();
			panel.getChildren().add(tf);
			addParamComponent(param, param.getPrompt(), panel);
		}
	}

	private void addStringParameter(String key, StringParameter param) {
		addParamComponent(param, param.getPrompt(), getTextField(param, 25, key));
	}

	private void addEmptyParameter(EmptyParameter param) {
		Label label = new Label(param.getPrompt());
		if (param.isTitle()) {
			// Cannot change font weight for default font (at least on macOS...) - need to change the font that's used
			// Necessary to use CSS rather than setting the font to avoid a weird focus issue that could cause the text
			// size to change, e.g. see https://stackoverflow.com/questions/53603250/javafx-how-to-prevent-label-text-resize-during-scrollpane-focus
			label.setStyle("-fx-font-weight: bold;");
			if (!map.isEmpty())
				label.setPadding(new Insets(10, 0, 0, 0));
		}
		addParamComponent(param, null, label);
	}

	private void addChoiceParameter(String key, ChoiceParameter<Object> param) {
		ComboBox<Object> combo = new ComboBox<>();
		combo.getItems().setAll(param.getChoices());
		combo.getSelectionModel().select(param.getValueOrDefault());
		combo.setOnAction(e -> {
			if (param.setValue(combo.getSelectionModel().getSelectedItem()))
				fireParameterChangedEvent(param, false);
		});
		combo.setMaxWidth(Double.MAX_VALUE);
		if (key != null)
			combo.setId(key);
		addParamComponent(param, param.getPrompt(), combo);
	}
	
	private void addCheckBoxParameter(String key, BooleanParameter param) {
		CheckBox cb = new CheckBox(param.getPrompt());
		cb.setSelected(param.getValueOrDefault());
		cb.setMinWidth(CheckBox.USE_COMPUTED_SIZE);
		cb.setMaxWidth(Double.MAX_VALUE);
		cb.selectedProperty().addListener((v, o, n) -> {
			if (param.setValue(cb.isSelected()))
				fireParameterChangedEvent(param, false);
		});
		if (key != null)
			cb.setId(key);
		addParamComponent(param, null, cb);
	}
	
	private void addSliderParameter(String key, IntParameter param) {
		int min = (int)param.getLowerBound();
		int max = (int)(param.getUpperBound() + .5);
		Slider slider = new Slider(min, max, param.getValueOrDefault());
		TextField tf = new TextField();
		if (key != null)
			tf.setId(key);

		tf.setEditable(false);
		tf.setText(""+slider.getValue());
		tf.setPrefColumnCount(DEFAULT_NUMERIC_TEXT_COLS);
		ParameterSliderChangeListener listener = new ParameterSliderChangeListener(slider, param, tf);
		slider.valueProperty().addListener((v, o, n) -> listener.handleSliderUpdate());
		BorderPane panel = new BorderPane();
		panel.setCenter(slider);
		panel.setRight(tf);
		addParamComponent(param, param.getPrompt(), panel);
	}
	
	
	private void addSliderParameter(String key, DoubleParameter param) {
		final Slider slider = new Slider(param.getLowerBound(), param.getUpperBound(),  param.getValueOrDefault());
		TextField tf = new TextField();
		if (key != null)
			tf.setId(key);
		tf.setPrefColumnCount(DEFAULT_NUMERIC_TEXT_COLS);
		setTextFieldFromNumber(tf, param.getValueOrDefault(), param.getUnit());
		tf.setEditable(false);
		ParameterSliderChangeListener listener = new ParameterSliderChangeListener(slider, param, tf);
		slider.valueProperty().addListener((v, o, n) -> listener.handleSliderUpdate());
		BorderPane panel = new BorderPane();
		panel.setCenter(slider);
		panel.setRight(tf);
		addParamComponent(param, param.getPrompt(), panel);
	}
	
	
	protected static void setTextFieldFromNumber(TextField text, Number value, String unit) {
		String s;
		if (value == null)
			s = "";
		else  {
			if (value instanceof Long || value instanceof BigInteger)
				s = String.format("%d", value.longValue());
			else {
				// Try to use a sensible number of decimal places
				double v = value.doubleValue();
				double log10 = Math.round(Math.log10(v));
				int ndp = (int)Math.max(4, -log10 + 2);
				s = GeneralTools.formatNumber(v, ndp);
			}
			if (unit != null)
				s += (" " + unit);
		}
		// Only set the text if it's different - avoids some exceptions due to the complex interplay between listeners...
		if (!text.getText().equals(s))
			text.setText(s);
	}
	
	
	protected TextField getTextField(Parameter<?> param, int cols, String key) {
		TextField tf = new TextField();
		Object defaultVal = param.getValueOrDefault();
		if (defaultVal instanceof Number)
			tf.setText(NumberFormat.getInstance().format(defaultVal));
		else if (defaultVal != null)
			tf.setText(defaultVal.toString());
		
		if (cols > 0)
			tf.setPrefColumnCount(cols);

		if (key != null)
			tf.setId(key);

		tf.textProperty().addListener((v, o, n) -> {
			if (n != null && param.setStringLastValue(Locale.getDefault(Category.FORMAT), n)) {
				fireParameterChangedEvent(param, false);
			}
		});
		return tf;
	}
	
	private int currentRow = 0;
	
	private void addParamComponent(Parameter<?> parameter, String text, Node component) {
		
		map.put(parameter, component);
		String help = parameter.getHelpText();

		GridPaneUtils.setFillWidth(Boolean.TRUE, component);
		GridPaneUtils.setHGrowPriority(Priority.ALWAYS, component);

		if (text == null) {
			GridPaneUtils.addGridRow(pane, currentRow++, 0, help, component, component);
		} else {
			Label label = new Label(text);
			label.setMaxWidth(Double.MAX_VALUE);
			label.setMinWidth(Label.USE_PREF_SIZE);
			label.setLabelFor(component);
			GridPaneUtils.addGridRow(pane, currentRow++, 0, help, label, component);
		}
	}
	
	/**
	 * Returns true if a parameter exists with the given key and is enabled (and is therefore editable).
	 * @param key
	 * @return
	 */
	public boolean getParameterEnabled(String key) {
		return getParameterEnabled(params.getParameters().get(key));
	}
	
	/**
	 * Returns true if a parameter is enabled (and is therefore editable).
	 * @param param
	 * @return
	 */
	public boolean getParameterEnabled(Parameter<?> param) {
		Node comp = map.get(param);
		return comp != null && !comp.isDisabled();
	}
	
	/**
	 * Set the enabled status of a parameter by key, to determine if it can be edited.
	 * @param key
	 * @param enabled
	 */
	public void setParameterEnabled(String key, boolean enabled) {
		setParameterEnabled(params.getParameters().get(key), enabled);
	}
	
	/**
	 * Set the enabled status of a parameter, to determine if it can be edited.
	 * @param param
	 * @param enabled
	 */
	public void setParameterEnabled(Parameter<?> param, boolean enabled) {
		Node comp = map.get(param);
		if (comp != null)
			setEnabledRecursively(comp, enabled);
	}
	
	private static void setEnabledRecursively(Node comp, boolean enabled) {
		comp.setDisable(!enabled);
	}
	
	
	
	
	class ParameterSliderChangeListener {
		
		private Slider slider;
		private NumericParameter<?> param;
		private TextField text;
		
		private boolean sliderChanging = false;
		private boolean textChanging = false;
		
		public ParameterSliderChangeListener(Slider slider, NumericParameter<?> param, TextField text) {
			this.slider = slider;
			this.param = param;
			this.text = text;
			
			this.text.setEditable(true);
			this.text.textProperty().addListener((v, o, n) -> {
				handleTextUpdate();
			});
		}

		public void handleSliderUpdate() {
			if (textChanging)
				return;
			sliderChanging = true;
			double val = slider.getValue();
			if (param.setDoubleLastValue(val)) {
				if (text != null) {
					setTextFieldFromNumber(text, param.getValueOrDefault(), param.getUnit());
				}
				fireParameterChangedEvent(param, slider.isValueChanging());
			}
			sliderChanging = false;
		}
		
		void handleTextUpdate() {
			if (sliderChanging)
				return;
			String s = text.getText();
			if (s == null || s.trim().length() == 0)
				return;
			try {
				String unit = param.getUnit();
				if (unit != null)
					s = s.toLowerCase().replace(unit.toLowerCase(), "").trim();
				if (s.length() == 0)
					return;
				double val = NumberFormat.getInstance().parse(s).doubleValue();
				double previousValue = param.getValueOrDefault().doubleValue();
				if (Double.isNaN(val) || val == previousValue)
					return;
				
				textChanging = true;
				param.setDoubleLastValue(val);
				slider.setValue(val);
				fireParameterChangedEvent(param, slider.isValueChanging());
				textChanging = false;
			} catch (Exception e) {
				logger.debug("Cannot parse number from {} - will keep default of {}", s, param.getValueOrDefault());
			} finally {
				textChanging = false;
			};
		}
		
	}
	
	
	
	/**
	 * Set a numeric parameter value (either int or double).
	 * 
	 * The reason for using this method rather than setting the parameter value directly is that it ensures that
	 * any displayed components (text fields, sliders...) are updated accordingly.
	 * 
	 * @param key
	 * @param value
	 * @return
	 */
	public boolean setNumericParameterValue(final String key, Number value) {
		// Try to get a component to set
		Parameter<?> parameterOrig = params.getParameters().get(key);
		if (parameterOrig == null || !(parameterOrig instanceof NumericParameter)) {
			logger.warn("Unable to set parameter {} with value {} - no numeric parameter found with that key", key, value);
			return false;			
		}
		NumericParameter<?> parameter = (NumericParameter<?>)parameterOrig;
		Node component = map.get(parameter);
		// Occurs with hidden parameters
		if (component == null) {
			parameter.setDoubleLastValue(value.doubleValue());
			return true;
		}
		if (component instanceof Parent) {
			for (Node comp : ((Parent)component).getChildrenUnmodifiable()) {
				if (comp instanceof TextField) {
					// Only change the text if necessary
					TextField textField = (TextField)comp;
					setTextFieldFromNumber(textField, value, parameter.getUnit());
					return true;
				}
			}
		}
		logger.warn("Unable to set parameter {} with value {} - no component found", key, value);		
		return false;
	}
	
	
	
	/**
	 * Set the minimum and maximum value for a numeric parameter.
	 * 
	 * If the parameter is being displayed with a slider, the slider range will also be updated accordingly.
	 * 
	 * @param key
	 * @param minValue
	 * @param maxValue
	 * @return
	 */
	public boolean setNumericParameterValueRange(final String key, double minValue, double maxValue) {
		// Try to get a component to set
		Parameter<?> parameterOrig = params.getParameters().get(key);
		if (parameterOrig == null || !(parameterOrig instanceof NumericParameter)) {
			logger.warn("Unable to set range for {} - no numeric parameter found with that key", key);
			return false;			
		}
		NumericParameter<?> parameter = (NumericParameter<?>)parameterOrig;
		// Occurs with hidden parameters
		try {
			parameter.setRange(minValue, maxValue);
			Node component = map.get(parameter);
			if (component instanceof Parent) {
				for (Node comp : ((Parent)component).getChildrenUnmodifiable()) {
					if (comp instanceof Slider) {
						// Only change the text if necessary
						Slider slider = (Slider)comp;
							slider.setMin(minValue);
							slider.setMax(maxValue);
							return true;
					}
				}
			}
		} catch (Exception e) {
			logger.warn("Unable to set range for {}: {}", parameter, e.getLocalizedMessage());							
		}
		return false;
	}
	
	
	
	static void demoParameterPanel() {
		
		new JFXPanel();
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(ParameterPanelFX::demoParameterPanel);
			return;
		}
		
		Stage frame = new Stage();
		frame.setTitle("Testing parameter panel");
		int k = 0;
		final ParameterList params = new ParameterList().
				addTitleParameter("Parameter list").
				addEmptyParameter("Here is a list of parameters that I am testing out").
				addIntParameter(Integer.toString(k++), "Enter an int", 5, "px", "Unbounded int").
				addDoubleParameter(Integer.toString(k++), "Enter a double", 5.2, "microns", "Unbounded double").
				addDoubleParameter(Integer.toString(k++), "Enter a double in range", 5.2, null, 1, 10, "Bounded double").
				addIntParameter(Integer.toString(k++), "Enter an int in range", 5, null, 1, 10, "Bounded int").
				addStringParameter(Integer.toString(k++), "Enter a string", "Default here").
				addChoiceParameter(Integer.toString(k++), "Choose a choice", "Two", Arrays.asList("One", "Two", "Three"), "Simple choice").
				addChoiceParameter(Integer.toString(k++), "Choose a number choice", 2, Arrays.asList(1, 2, 3), "Numeric choice").
				addBooleanParameter(Integer.toString(k++), "Check me out", true);
		
		
		
		BorderPane borderPane = new BorderPane();
		ParameterPanelFX panel = new ParameterPanelFX(params);
		
		final TextArea textArea = new TextArea();
		for (Parameter<?> p : params.getParameters().values()) {
			textArea.setText(textArea.getText() + (p + "\n"));
		}
		panel.addParameterChangeListener(new ParameterChangeListener() {
			@Override
			public void parameterChanged(ParameterList params, String key, boolean isAdjusting) {
				textArea.setText("");
				for (Parameter<?> p : params.getParameters().values())
					textArea.setText(textArea.getText() + (p + "\n"));
			}
		});
		
		borderPane.setCenter(panel.getPane());
		borderPane.setBottom(textArea);
		
		frame.setScene(new Scene(borderPane));
		frame.show();
	}
	
	
}
