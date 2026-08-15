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

package io.github.dsheirer.source.tuner.hydrasdr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HydraSdrTunerConfigurationTest
{
    private final ObjectMapper mMapper = new ObjectMapper();

    @Test
    void migratesLegacyLinearityGain() throws Exception
    {
        HydraSdrTunerConfiguration configuration = mMapper.readValue(
            "{\"type\":\"hydraSdrTunerConfiguration\",\"gain\":\"LINEARITY_18\"}", HydraSdrTunerConfiguration.class);

        assertEquals(0, configuration.getGainMode());
        assertEquals(18, configuration.getLinearityGain());
    }

    @Test
    void migratesLegacySensitivityAndIfGain() throws Exception
    {
        HydraSdrTunerConfiguration configuration = mMapper.readValue(
            "{\"type\":\"hydraSdrTunerConfiguration\",\"gain\":\"SENSITIVITY_17\",\"ifGain\":12}", HydraSdrTunerConfiguration.class);

        assertEquals(1, configuration.getGainMode());
        assertEquals(17, configuration.getSensitivityGain());
        assertEquals(12, configuration.getVgaGain());
    }

    @Test
    void persistsRfAndFilterCustomGainSettings() throws Exception
    {
        HydraSdrTunerConfiguration configuration = mMapper.readValue(
            "{\"type\":\"hydraSdrTunerConfiguration\",\"rfGain\":7,\"filterGain\":11," +
                "\"rfAgc\":true,\"filterAgc\":true}",
            HydraSdrTunerConfiguration.class);

        assertEquals(7, configuration.getRfGain());
        assertEquals(11, configuration.getFilterGain());
        assertTrue(configuration.isRfAgc());
        assertTrue(configuration.isFilterAgc());

        String serialized = mMapper.writeValueAsString(configuration);
        assertTrue(serialized.contains("\"rfGain\":7"));
        assertTrue(serialized.contains("\"filterGain\":11"));
        assertTrue(serialized.contains("\"rfAgc\":true"));
        assertTrue(serialized.contains("\"filterAgc\":true"));
    }

    @Test
    void persistsZeroPresetGainsAndRfPort() throws Exception
    {
        HydraSdrTunerConfiguration configuration = mMapper.readValue(
            "{\"type\":\"hydraSdrTunerConfiguration\",\"linearityGain\":0," +
                "\"sensitivityGain\":0,\"rfPort\":2}", HydraSdrTunerConfiguration.class);

        assertEquals(0, configuration.getLinearityGain());
        assertEquals(0, configuration.getSensitivityGain());
        assertEquals(2, configuration.getRfPort());

        String serialized = mMapper.writeValueAsString(configuration);
        assertTrue(serialized.contains("\"linearityGain\":0"));
        assertTrue(serialized.contains("\"sensitivityGain\":0"));
        assertTrue(serialized.contains("\"rfPort\":2"));
    }

    @Test
    void migratesLegacyCustomGainAndDoesNotReserializeLegacyFields() throws Exception
    {
        HydraSdrTunerConfiguration configuration = mMapper.readValue(
            "{\"type\":\"hydraSdrTunerConfiguration\",\"gain\":\"CUSTOM\",\"if_gain\":6,\"lnagain\":9,\"mixerGain\":7," +
                "\"lnaagc\":true,\"mixerAGC\":true}", HydraSdrTunerConfiguration.class);

        assertEquals(2, configuration.getGainMode());
        assertEquals(6, configuration.getVgaGain());
        assertEquals(9, configuration.getLnaGain());
        assertEquals(7, configuration.getMixerGain());
        assertTrue(configuration.isLnaAgc());
        assertTrue(configuration.isMixerAgc());

        String serialized = mMapper.writeValueAsString(configuration);
        assertFalse(serialized.contains("\"gain\""));
        assertFalse(serialized.contains("ifGain"));
        assertFalse(serialized.contains("if_gain"));
    }
}
