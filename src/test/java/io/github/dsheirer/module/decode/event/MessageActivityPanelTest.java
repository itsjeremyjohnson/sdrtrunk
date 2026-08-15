/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.event;

import io.github.dsheirer.filter.Filter;
import io.github.dsheirer.filter.FilterElement;
import io.github.dsheirer.filter.FilterSet;
import io.github.dsheirer.preference.NowPlayingPreference;
import io.github.dsheirer.preference.UserPreferences;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MessageActivityPanelTest
{
    @Test
    void savesAndRestoresNestedFilterElements()
    {
        UserPreferences preferences = new UserPreferences();
        MessageActivityPanel panel = new MessageActivityPanel(preferences);
        FilterElement<String> element = new FilterElement<>("Nested Element", false);
        TestFilter filter = new TestFilter("Nested Filter", element);
        FilterSet<Object> nested = new FilterSet<>("Nested Set");
        nested.addFilter(filter);
        FilterSet<Object> root = new FilterSet<>("Root Set");
        root.addFilter(nested);

        panel.saveFilterStates(root);
        element.setEnabled(true);
        panel.restoreFilterStates(root);

        assertFalse(element.isEnabled());
        preferences.getNowPlayingPreference().setFilterEnabled("Nested Filter.Nested Element", true);
    }

    @Test
    void persistsDistinctLongFilterNamesWithinPreferenceKeyLimit()
    {
        NowPlayingPreference preferences = new NowPlayingPreference();
        String commonPrefix = "Vendor-Motorola Messages.MOTOROLA GROUP REGROUP CHANNEL GRANT ";
        String first = commonPrefix + "ONE";
        String second = commonPrefix + "TWO";

        preferences.setFilterEnabled(first, false);
        preferences.setFilterEnabled(second, true);

        assertFalse(preferences.isFilterEnabled(first));
        assertEquals(true, preferences.isFilterEnabled(second));
    }

    @Test
    void initializesMessagesHistoryFromPreference()
    {
        UserPreferences preferences = new UserPreferences();
        NowPlayingPreference nowPlaying = preferences.getNowPlayingPreference();
        int previous = nowPlaying.getMessageHistorySize();

        try
        {
            nowPlaying.setMessageHistorySize(333);
            assertEquals(333, new MessageActivityPanel(preferences).getConfiguredHistorySize());
        }
        finally
        {
            nowPlaying.setMessageHistorySize(previous);
        }
    }

    private static class TestFilter extends Filter<Object,String>
    {
        private TestFilter(String name, FilterElement<String> element)
        {
            super(name);
            add(element);
        }

        @Override
        public Function<Object,String> getKeyExtractor()
        {
            return Object::toString;
        }
    }
}
