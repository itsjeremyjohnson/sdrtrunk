/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.gui.playlist.alias.identifier;

import io.github.dsheirer.alias.id.ctcss.Ctcss;
import io.github.dsheirer.module.decode.ctcss.CTCSSCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CtcssEditorTest
{
    @Test
    void invalidItemHasNoSelection()
    {
        Ctcss valid = new Ctcss();
        valid.setCTCSSCode(CTCSSCode.TONE_1Z);
        assertEquals(CTCSSCode.TONE_1Z, CtcssEditor.selectionFor(valid));
        assertNull(CtcssEditor.selectionFor(new Ctcss()));
    }
}
