/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.alias;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AliasFactoryTest
{
    @Test
    void shallowCopyPreservesAudioOutputDevice()
    {
        Alias original = new Alias("routed");
        original.setAudioOutputDevice("speakers");

        Alias copy = AliasFactory.shallowCopyOf(original);

        assertEquals("speakers", copy.getAudioOutputDevice());
    }
}
