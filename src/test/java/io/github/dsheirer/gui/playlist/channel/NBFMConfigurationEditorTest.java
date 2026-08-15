/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.gui.playlist.channel;

import io.github.dsheirer.module.decode.config.ChannelToneFilter;
import java.util.List;
import javafx.scene.control.SpinnerValueFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class NBFMConfigurationEditorTest
{
    @Test
    void commitsTypedSpinnerTextBeforeSavingConfiguration()
    {
        SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory =
            new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 2000, 0);

        NBFMConfigurationEditor.commitEditorText(valueFactory, "250");

        assertEquals(250, valueFactory.getValue());
    }

    @Test
    void preservesAdditionalToneFiltersWhenEditingFirstFilter()
    {
        ChannelToneFilter original = new ChannelToneFilter(ChannelToneFilter.ToneType.CTCSS, "TONE_XZ", "first");
        ChannelToneFilter preserved = new ChannelToneFilter(ChannelToneFilter.ToneType.DCS, "N023", "second");
        ChannelToneFilter edited = new ChannelToneFilter(ChannelToneFilter.ToneType.CTCSS, "TONE_1Z", "");

        List<ChannelToneFilter> merged = NBFMConfigurationEditor.mergeToneFilters(
            List.of(original, preserved), edited);

        assertEquals(2, merged.size());
        assertSame(edited, merged.getFirst());
        assertSame(preserved, merged.get(1));
    }
}
