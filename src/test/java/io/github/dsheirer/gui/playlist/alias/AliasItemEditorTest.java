/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.gui.playlist.alias;

import javafx.beans.property.SimpleBooleanProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasItemEditorTest
{
    @Test
    void priorityRefreshPreservesUnsavedModifiedState()
    {
        SimpleBooleanProperty modified = new SimpleBooleanProperty(false);

        AliasItemEditor.restoreModifiedState(modified, true);

        assertTrue(modified.get());
    }
}
