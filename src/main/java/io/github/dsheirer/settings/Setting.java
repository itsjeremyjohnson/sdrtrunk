/*
 * *********************************************************************************************************************
 * sdrtrunk
 * Copyright (C) 2014-2017 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by  the Free Software Foundation, either version 3 of the License, or  (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful,  but WITHOUT ANY WARRANTY; without even the implied
 * warranty of  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License  along with this program.
 * If not, see <http://www.gnu.org/licenses/>
 * *********************************************************************************************************************
 */
package io.github.dsheirer.settings;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import io.github.dsheirer.controller.config.Configuration;
import io.github.dsheirer.map.DefaultIcon;
import io.github.dsheirer.map.MapIcon;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include= JsonTypeInfo.As.PROPERTY, property="type")
@JsonSubTypes({
    @JsonSubTypes.Type(value=ColorSetting.class, name="colorSetting"),
    @JsonSubTypes.Type(value=DefaultIcon.class, name="defaultIcon"),
    @JsonSubTypes.Type(value=FileSetting.class, name="fileSetting"),
    @JsonSubTypes.Type(value=MapIcon.class, name="mapIcon"),
    @JsonSubTypes.Type(value=MapViewSetting.class, name="mapViewSetting"),
})
@JacksonXmlRootElement( localName = "setting" )
@JsonPropertyOrder({"serializedUnknownProperties"})
public abstract class Setting
{
    protected String mName;

    public Setting()
    {
    }

    public Setting(String name)
    {
        mName = name;
    }

    @JacksonXmlProperty(isAttribute = true, localName = "name")
    public String getName()
    {
        return mName;
    }

    public void setName(String name)
    {
        mName = name;
    }

    @JsonIgnore
    public abstract SettingType getType();

    private Map<String, Object> mUnknownProperties;
    private Set<String> mUnknownAttributeNames;

    public void setUnknownProperty(String key, Object value)
    {
        setUnknownProperty(key, new Configuration.UnknownXmlPropertyValue(value, false));
    }

    @JsonAnySetter
    public void setUnknownProperty(String key, Configuration.UnknownXmlPropertyValue unknownValue)
    {
        Object value = unknownValue.value();
        if(unknownValue.attribute())
        {
            if(mUnknownAttributeNames == null)
            {
                mUnknownAttributeNames = new HashSet<>();
            }
            mUnknownAttributeNames.add(key);
        }
        if(mUnknownProperties == null)
        {
            mUnknownProperties = new LinkedHashMap<>();
        }
        Object existing = mUnknownProperties.get(key);
        if(existing == null)
        {
            mUnknownProperties.put(key, value);
        }
        else if(existing instanceof List<?> list)
        {
            List<Object> values = new ArrayList<>(list);
            values.add(value);
            mUnknownProperties.put(key, values);
        }
        else
        {
            mUnknownProperties.put(key, new ArrayList<>(List.of(existing, value)));
        }
    }

    @JsonIgnore
    public Map<String, Object> getUnknownProperties()
    {
        return mUnknownProperties;
    }

    @JsonAnyGetter
    @JsonSerialize(keyUsing = Configuration.UnknownXmlPropertyNameSerializer.class)
    public Map<String, Object> getSerializedUnknownProperties()
    {
        if(mUnknownProperties == null)
        {
            return null;
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        mUnknownProperties.forEach((key, value) -> properties.put(
                mUnknownAttributeNames != null && mUnknownAttributeNames.contains(key) ?
                        Configuration.UnknownXmlPropertyNameSerializer.ATTRIBUTE_PREFIX + key : key, value));
        return properties;
    }
}
