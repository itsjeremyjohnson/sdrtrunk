/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
 * *****************************************************************************
 */
package io.github.dsheirer.module.decode.p25.phase1;

import io.github.dsheirer.sample.complex.IQImbalanceCorrector;
import io.github.dsheirer.sample.complex.NoiseBlanker;
import io.github.dsheirer.source.SourceEvent;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class P25P1DecoderRetuneTest
{
    @Test
    void correctionRetuneResetsC4fmAdaptiveFilters() throws Exception
    {
        assertRetuneResetsAdaptiveFilters(new P25P1DecoderC4FM());
    }

    @Test
    void correctionRetuneResetsLsmAdaptiveFilters() throws Exception
    {
        assertRetuneResetsAdaptiveFilters(new P25P1DecoderLSM());
    }

    private static void assertRetuneResetsAdaptiveFilters(Object decoder) throws Exception
    {
        IQImbalanceCorrector corrector = field(decoder, "mIQImbalanceCorrector", IQImbalanceCorrector.class);
        NoiseBlanker blanker = field(decoder, "mNoiseBlanker", NoiseBlanker.class);
        corrector.correct(new float[]{1.0f}, new float[]{0.5f});
        blanker.process(new float[]{1.0f}, new float[]{1.0f});

        if(decoder instanceof P25P1DecoderC4FM c4fm)
        {
            c4fm.getSourceEventListener().receive(SourceEvent.frequencyCorrectionChange(1));
        }
        else if(decoder instanceof P25P1DecoderLSM lsm)
        {
            lsm.getSourceEventListener().receive(SourceEvent.frequencyCorrectionChange(1));
        }

        assertTrue(corrector.toString().contains("samples=0"));
        assertTrue(blanker.toString().contains("blanked=0/0"));
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }
}
