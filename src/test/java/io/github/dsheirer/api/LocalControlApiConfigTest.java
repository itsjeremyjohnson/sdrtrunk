/*
 * *****************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
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
package io.github.dsheirer.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalControlApiConfigTest
{
    @Test
    void defaultsAreSafe()
    {
        LocalControlApiConfig config = LocalControlApiConfig.defaults();

        assertFalse(config.isEnabled());
        assertEquals("127.0.0.1", config.getHost());
        assertEquals(9997, config.getPort());
        assertFalse(config.hasToken());
        assertTrue(config.isLoopbackOnly());
    }

    @Test
    void tokenIsNotExposedInStringRepresentation()
    {
        LocalControlApiConfig config = new LocalControlApiConfig(true, "127.0.0.1", 0, "super-secret-token");

        assertTrue(config.hasToken());
        assertFalse(config.toString().contains("super-secret-token"));
        assertTrue(config.toString().contains("tokenConfigured=true"));
    }
}
