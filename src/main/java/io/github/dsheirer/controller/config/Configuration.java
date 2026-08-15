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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

@JacksonXmlRootElement(localName = "configuration")
@JsonPropertyOrder({"serializedUnknownProperties"})
public abstract class Configuration
{
    private Map<String, Object> mUnknownProperties;
    private Set<String> mUnknownAttributeNames;

    public Configuration()
    {
    }

    public void setUnknownProperty(String key, Object value)
    {
        setUnknownProperty(key, new UnknownXmlPropertyValue(value, false));
    }

    @JsonAnySetter
    public void setUnknownProperty(String key, UnknownXmlPropertyValue unknownValue)
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
            List<Object> values = new ArrayList<>();
            values.add(existing);
            values.add(value);
            mUnknownProperties.put(key, values);
        }
    }

    @JsonIgnore
    public Map<String, Object> getUnknownProperties()
    {
        return mUnknownProperties;
    }

    @JsonAnyGetter
    @JsonSerialize(keyUsing = UnknownXmlPropertyNameSerializer.class)
    public Map<String, Object> getSerializedUnknownProperties()
    {
        if(mUnknownProperties == null)
        {
            return null;
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        mUnknownProperties.forEach((key, value) -> properties.put(
                mUnknownAttributeNames != null && mUnknownAttributeNames.contains(key) ?
                        UnknownXmlPropertyNameSerializer.ATTRIBUTE_PREFIX + key : key, value));
        return properties;
    }

    public void copyUnknownPropertiesFrom(Configuration configuration)
    {
        if(configuration != null && configuration.mUnknownProperties != null)
        {
            mUnknownProperties = new LinkedHashMap<>();
            configuration.mUnknownProperties.forEach((key, value) -> mUnknownProperties.put(key,
                    value instanceof List<?> list ? new ArrayList<>(list) : value));
            if(configuration.mUnknownAttributeNames != null)
            {
                mUnknownAttributeNames = new HashSet<>(configuration.mUnknownAttributeNames);
            }
        }
    }

    @JsonDeserialize(using = UnknownXmlPropertyDeserializer.class)
    public record UnknownXmlPropertyValue(Object value, boolean attribute) {}

    public static class UnknownXmlPropertyDeserializer extends JsonDeserializer<UnknownXmlPropertyValue>
    {
        @Override
        public UnknownXmlPropertyValue deserialize(JsonParser parser, DeserializationContext context) throws IOException
        {
            boolean attribute = isAttribute(parser);
            Object value = parser.getCodec().treeToValue(parser.readValueAsTree(), Object.class);
            return new UnknownXmlPropertyValue(value, attribute);
        }

        private boolean isAttribute(JsonParser parser)
        {
            if(parser instanceof FromXmlParser xmlParser)
            {
                XMLStreamReader reader = xmlParser.getStaxReader();
                if(reader.getEventType() == XMLStreamConstants.START_ELEMENT)
                {
                    try
                    {
                        String propertyName = parser.currentName();
                        for(int index = 0; index < reader.getAttributeCount(); index++)
                        {
                            if(reader.getAttributeLocalName(index).equals(propertyName))
                            {
                                return true;
                            }
                        }
                    }
                    catch(IOException ignored)
                    {
                        return false;
                    }
                }
            }

            return false;
        }
    }

    public static class UnknownXmlPropertyNameSerializer extends JsonSerializer<Object>
    {
        public static final String ATTRIBUTE_PREFIX = "__sdrtrunk_xml_attribute__:";

        @Override
        public void serialize(Object value, JsonGenerator generator,
                              com.fasterxml.jackson.databind.SerializerProvider serializers) throws IOException
        {
            String propertyName = (String)value;
            boolean attribute = propertyName.startsWith(ATTRIBUTE_PREFIX);
            if(generator instanceof ToXmlGenerator xmlGenerator)
            {
                xmlGenerator.setNextIsAttribute(attribute);
            }
            generator.writeFieldName(attribute ? propertyName.substring(ATTRIBUTE_PREFIX.length()) : propertyName);
        }
    }
}
