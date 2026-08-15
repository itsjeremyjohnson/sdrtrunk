/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
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
package io.github.dsheirer.identifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dsheirer.identifier.configuration.SystemConfigurationIdentifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MutableIdentifierCollectionTest
{
    @Test
    void clearRemovesIdentifiersAndNotifiesListener()
    {
        MutableIdentifierCollection identifiers = new MutableIdentifierCollection();
        List<IdentifierUpdateNotification> notifications = new ArrayList<>();
        identifiers.setIdentifierUpdateListener(notifications::add);
        identifiers.update(SystemConfigurationIdentifier.create("system"));

        identifiers.clear();

        assertTrue(identifiers.getIdentifiers().isEmpty());
        assertEquals(IdentifierUpdateNotification.Operation.REMOVE, notifications.getLast().getOperation());
    }
}
