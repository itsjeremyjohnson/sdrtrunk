/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.gui.playlist.alias.identifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.scene.control.SpinnerValueFactory;
import org.junit.jupiter.api.Test;

class NacEditorTest
{
    @Test
    void commitsTypedNacTextBeforeSave()
    {
        SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory =
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 4095, 0);

        NacEditor.commitEditorText(valueFactory, "291");

        assertEquals(291, valueFactory.getValue());
    }
}
