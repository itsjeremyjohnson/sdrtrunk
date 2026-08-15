/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.event;

import io.github.dsheirer.filter.Filter;
import io.github.dsheirer.filter.FilterElement;
import io.github.dsheirer.filter.FilterSet;
import io.github.dsheirer.preference.UserPreferences;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

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
