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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ****************************************************************************
 */
package io.github.dsheirer.source.tuner.hydrasdr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HydraSdrTunerConfigurationTest
{
    @Test
    void migratesLegacyGainProperties() throws Exception
    {
        String json = "{\"type\":\"hydraSdrTunerConfiguration\",\"gain\":\"SENSITIVITY_10\"," +
            "\"IFGain\":7,\"LNAGain\":8," +
            "\"mixerGain\":4,\"LNAAGC\":true,\"mixerAGC\":true}";

        HydraSdrTunerConfiguration configuration = new ObjectMapper()
            .readValue(json, HydraSdrTunerConfiguration.class);

        assertEquals(1, configuration.getGainMode());
        assertEquals(10, configuration.getSensitivityGain());
        assertEquals(7, configuration.getVgaGain());
        assertEquals(8, configuration.getLnaGain());
        assertEquals(4, configuration.getMixerGain());
        assertTrue(configuration.isLnaAgc());
        assertTrue(configuration.isMixerAgc());
    }

    @Test
    void persistsExtendedCustomGainStages() throws Exception
    {
        String json = "{\"type\":\"hydraSdrTunerConfiguration\",\"rfGain\":12,\"filterGain\":7," +
            "\"rfAgc\":true,\"filterAgc\":false}";
        ObjectMapper mapper = new ObjectMapper();
        HydraSdrTunerConfiguration configuration = mapper.readValue(json, HydraSdrTunerConfiguration.class);

        assertEquals(12, configuration.getRfGain());
        assertEquals(7, configuration.getFilterGain());
        assertTrue(configuration.isRfAgc());
        assertFalse(configuration.isFilterAgc());

        String persisted = mapper.writeValueAsString(configuration);
        HydraSdrTunerConfiguration restored = mapper.readValue(persisted, HydraSdrTunerConfiguration.class);
        assertEquals(12, restored.getRfGain());
        assertEquals(7, restored.getFilterGain());
        assertTrue(restored.isRfAgc());
        assertFalse(restored.isFilterAgc());
    }
}
