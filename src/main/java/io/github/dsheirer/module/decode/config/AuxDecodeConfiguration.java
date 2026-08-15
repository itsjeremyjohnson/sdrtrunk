/*
 *
 *  * ******************************************************************************
 *  * Copyright (C) 2014-2020 Dennis Sheirer
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *  * *****************************************************************************
 *
 *
 */
package io.github.dsheirer.module.decode.config;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import io.github.dsheirer.controller.config.Configuration;
import io.github.dsheirer.module.decode.DecoderType;

import java.util.ArrayList;
import java.util.List;

@JsonSubTypes.Type(value = AuxDecodeConfiguration.class, name = "auxDecodeConfiguration")
public class AuxDecodeConfiguration extends Configuration
{
    private List<DecoderType> mAuxDecoders = new ArrayList<>();
    private List<String> mAuxDecoderValues = new ArrayList<>();

    public AuxDecodeConfiguration()
    {
    }

    @JsonIgnore
    public List<DecoderType> getAuxDecoders()
    {
        return mAuxDecoders;
    }

    @JsonIgnore
    public void setAuxDecoders(List<DecoderType> decoders)
    {
        mAuxDecoders = decoders == null ? new ArrayList<>() : new ArrayList<>(decoders);
        mAuxDecoderValues = new ArrayList<>(mAuxDecoders.stream().map(DecoderType::name).toList());
    }

    @JacksonXmlProperty(isAttribute = false, localName = "aux_decoder")
    @JsonGetter("aux_decoder")
    public List<String> getAuxDecoderValues()
    {
        return mAuxDecoderValues;
    }

    @JsonSetter("aux_decoder")
    public void setAuxDecoderValues(List<String> values)
    {
        mAuxDecoderValues = values == null ? new ArrayList<>() : new ArrayList<>(values);
        mAuxDecoders = new ArrayList<>();

        for(String value : mAuxDecoderValues)
        {
            if(value == null)
            {
                continue;
            }

            try
            {
                mAuxDecoders.add(DecoderType.valueOf(value));
            }
            catch(IllegalArgumentException ignored)
            {
                // Preserve future decoder values for round-trip, but omit unsupported runtime decoders.
            }
        }
    }

    public void addAuxDecoder(DecoderType decoder)
    {
        mAuxDecoders.add(decoder);
        mAuxDecoderValues.add(decoder.name());
    }

    public void removeAuxDecoder(DecoderType decoder)
    {
        mAuxDecoders.remove(decoder);
        mAuxDecoderValues.remove(decoder.name());
    }

    public void clearAuxDecoders()
    {
        mAuxDecoders.clear();
        mAuxDecoderValues.removeIf(value -> {
            if(value == null)
            {
                return true;
            }

            try
            {
                DecoderType.valueOf(value);
                return true;
            }
            catch(IllegalArgumentException ignored)
            {
                return false;
            }
        });
    }
}
