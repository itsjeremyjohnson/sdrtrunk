/*******************************************************************************
 *     SDR Trunk 
 *     Copyright (C) 2014 Dennis Sheirer
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>
 ******************************************************************************/
package io.github.dsheirer.controller.config;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JacksonXmlRootElement(localName = "configuration")
public abstract class Configuration
{
    private Map<String, Object> mUnknownProperties;

    public Configuration()
    {
    }

    @JsonAnySetter
    public void setUnknownProperty(String key, Object value)
    {
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
            List<Object> values = new ArrayList<>();
            values.add(existing);
            values.add(value);
            mUnknownProperties.put(key, values);
        }
    }

    @JsonAnyGetter
    public Map<String, Object> getUnknownProperties()
    {
        return mUnknownProperties;
    }

    public void copyUnknownPropertiesFrom(Configuration configuration)
    {
        if(configuration != null && configuration.mUnknownProperties != null)
        {
            mUnknownProperties = new LinkedHashMap<>();
            configuration.mUnknownProperties.forEach((key, value) -> mUnknownProperties.put(key,
                    value instanceof List<?> list ? new ArrayList<>(list) : value));
        }
    }
}
