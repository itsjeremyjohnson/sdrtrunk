/*
 * *****************************************************************************
 * Copyright (C) 2014-2024 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */

package io.github.dsheirer.gui.preference.application;

import io.github.dsheirer.api.LocalControlApiConfig;
import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.preference.application.ApplicationPreference;
import io.github.dsheirer.properties.SystemProperties;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.controlsfx.control.ToggleSwitch;


/**
 * Preference settings for application
 */
public class ApplicationPreferenceEditor extends HBox
{
    private ApplicationPreference mApplicationPreference;
    private GridPane mEditorPane;
    private Label mAutoStartTimeoutLabel;
    private Spinner<Integer> mTimeoutSpinner;
    private ToggleSwitch mAutomaticDiagnosticMonitoringToggle;
    private ToggleSwitch mLocalControlApiEnabledToggle;
    private TextField mLocalControlApiHostField;
    private Spinner<Integer> mLocalControlApiPortSpinner;

    /**
     * Constructs an instance
     * @param userPreferences for obtaining reference to preference.
     */
    public ApplicationPreferenceEditor(UserPreferences userPreferences)
    {
        mApplicationPreference = userPreferences.getApplicationPreference();
        setMaxWidth(Double.MAX_VALUE);

        VBox vbox = new VBox();
        vbox.setMaxHeight(Double.MAX_VALUE);
        vbox.setMaxWidth(Double.MAX_VALUE);
        vbox.getChildren().add(getEditorPane());
        HBox.setHgrow(vbox, Priority.ALWAYS);
        getChildren().add(vbox);
    }

    private GridPane getEditorPane()
    {
        if(mEditorPane == null)
        {
            int row = 0;
            mEditorPane = new GridPane();
            mEditorPane.setMaxWidth(Double.MAX_VALUE);
            mEditorPane.setVgap(10);
            mEditorPane.setHgap(3);
            mEditorPane.setPadding(new Insets(10, 10, 10, 10));

            Label monitoringLabel = new Label("Application Health and Diagnostic Monitoring.");
            mEditorPane.add(monitoringLabel, 0, row, 2, 1);
            GridPane.setHalignment(getAutomaticDiagnosticMonitoringToggle(), HPos.RIGHT);
            mEditorPane.add(getAutomaticDiagnosticMonitoringToggle(), 0, ++row);
            mEditorPane.add(new Label("Enable Diagnostic Monitoring"), 1, row, 2, 1);

            Separator separator = new Separator(Orientation.HORIZONTAL);
            GridPane.setHgrow(separator, Priority.ALWAYS);
            mEditorPane.add(separator, 0, ++row, 3, 1);

            Label apiLabel = new Label("Local Control API. Changes take effect after restarting sdrtrunk. Configure the token with -D" +
                LocalControlApiConfig.PROPERTY_TOKEN + " or " + LocalControlApiConfig.ENV_TOKEN + ".");
            apiLabel.setWrapText(true);
            mEditorPane.add(apiLabel, 0, ++row, 2, 1);
            GridPane.setHalignment(getLocalControlApiEnabledToggle(), HPos.RIGHT);
            mEditorPane.add(getLocalControlApiEnabledToggle(), 0, ++row);
            mEditorPane.add(new Label("Enable Local Control API"), 1, row, 2, 1);
            mEditorPane.add(new Label("API Host"), 0, ++row);
            mEditorPane.add(getLocalControlApiHostField(), 1, row);
            mEditorPane.add(new Label("API Port"), 0, ++row);
            mEditorPane.add(getLocalControlApiPortSpinner(), 1, row);

            Separator apiSeparator = new Separator(Orientation.HORIZONTAL);
            GridPane.setHgrow(apiSeparator, Priority.ALWAYS);
            mEditorPane.add(apiSeparator, 0, ++row, 3, 1);

            mEditorPane.add(getAutoStartTimeoutLabel(), 0, ++row, 2, 1);
            GridPane.setHalignment(getTimeoutSpinner(), HPos.RIGHT);
            mEditorPane.add(getTimeoutSpinner(), 0, ++row);
            mEditorPane.add(new Label("seconds"), 1, row);

            ColumnConstraints c1 = new ColumnConstraints();
            c1.setPercentWidth(30);
            ColumnConstraints c2 = new ColumnConstraints();
            c2.setHgrow(Priority.ALWAYS);
            mEditorPane.getColumnConstraints().addAll(c1, c2);
        }

        return mEditorPane;
    }

    private Label getAutoStartTimeoutLabel()
    {
        if(mAutoStartTimeoutLabel == null)
        {
            mAutoStartTimeoutLabel = new Label("Channel Auto-Start Timeout");
        }

        return mAutoStartTimeoutLabel;
    }

    /**
     * Spinner to select channel auto-start timeout value in range 0-30 seconds.
     * @return spinner
     */
    private Spinner<Integer> getTimeoutSpinner()
    {
        if(mTimeoutSpinner == null)
        {
            mTimeoutSpinner = new Spinner<>(0, 30, mApplicationPreference.getChannelAutoStartTimeout(), 1);
            mTimeoutSpinner.valueProperty().addListener((observable, oldValue, newValue) -> mApplicationPreference.setChannelAutoStartTimeout(newValue));
        }

        return mTimeoutSpinner;
    }

    /**
     * Toggle switch to enable/disable the local control API.
     */
    private ToggleSwitch getLocalControlApiEnabledToggle()
    {
        if(mLocalControlApiEnabledToggle == null)
        {
            SystemProperties properties = SystemProperties.getInstance();
            mLocalControlApiEnabledToggle = new ToggleSwitch();
            mLocalControlApiEnabledToggle.setSelected(properties.get(LocalControlApiConfig.PROPERTY_ENABLED, false));
            mLocalControlApiEnabledToggle.selectedProperty().addListener((observable, oldValue, enabled) ->
                properties.set(LocalControlApiConfig.PROPERTY_ENABLED, enabled));
        }

        return mLocalControlApiEnabledToggle;
    }

    /**
     * Text field to configure the local control API bind host.
     */
    private TextField getLocalControlApiHostField()
    {
        if(mLocalControlApiHostField == null)
        {
            SystemProperties properties = SystemProperties.getInstance();
            mLocalControlApiHostField = new TextField(properties.get(LocalControlApiConfig.PROPERTY_HOST,
                LocalControlApiConfig.DEFAULT_HOST));
            mLocalControlApiHostField.setPromptText(LocalControlApiConfig.DEFAULT_HOST);
            mLocalControlApiHostField.setOnAction(event -> setLocalControlApiHost());
            mLocalControlApiHostField.focusedProperty().addListener((observable, oldValue, focused) ->
            {
                if(!focused)
                {
                    setLocalControlApiHost();
                }
            });
        }

        return mLocalControlApiHostField;
    }

    /**
     * Spinner to configure the local control API port.
     */
    private Spinner<Integer> getLocalControlApiPortSpinner()
    {
        if(mLocalControlApiPortSpinner == null)
        {
            SystemProperties properties = SystemProperties.getInstance();
            mLocalControlApiPortSpinner = new Spinner<>(0, 65535,
                properties.get(LocalControlApiConfig.PROPERTY_PORT, LocalControlApiConfig.DEFAULT_PORT), 1);
            mLocalControlApiPortSpinner.setEditable(true);
            mLocalControlApiPortSpinner.valueProperty().addListener((observable, oldValue, port) ->
                properties.set(LocalControlApiConfig.PROPERTY_PORT, port));
        }

        return mLocalControlApiPortSpinner;
    }

    private void setLocalControlApiHost()
    {
        String host = getLocalControlApiHostField().getText();

        if(host == null || host.isBlank())
        {
            host = LocalControlApiConfig.DEFAULT_HOST;
            getLocalControlApiHostField().setText(host);
        }

        SystemProperties.getInstance().set(LocalControlApiConfig.PROPERTY_HOST, host.trim());
    }

    /**
     * Toggle switch to enable/disable automatic diagnostic monitoring.
     */
    private ToggleSwitch getAutomaticDiagnosticMonitoringToggle()
    {
        if(mAutomaticDiagnosticMonitoringToggle == null)
        {
            mAutomaticDiagnosticMonitoringToggle = new ToggleSwitch();
            mAutomaticDiagnosticMonitoringToggle.setSelected(mApplicationPreference.isAutomaticDiagnosticMonitoring());
            mAutomaticDiagnosticMonitoringToggle.selectedProperty().addListener((observable, oldValue, enabled) ->
                    mApplicationPreference.setAutomaticDiagnosticMonitoring(enabled));
        }

        return mAutomaticDiagnosticMonitoringToggle;
    }
}
