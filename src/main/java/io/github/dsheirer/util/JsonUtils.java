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
package io.github.dsheirer.util;

/**
 * Utility methods for JSON serialization.
 */
public class JsonUtils
{
    private JsonUtils()
    {
    }

    /**
     * Escapes a string for use as a JSON string value.
     *
     * @param value to escape
     * @return escaped value, or an empty string when the value is null
     */
    public static String escape(String value)
    {
        if(value == null)
        {
            return "";
        }

        StringBuilder escaped = new StringBuilder(value.length());

        for(int index = 0; index < value.length(); index++)
        {
            char character = value.charAt(index);

            switch(character)
            {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default ->
                {
                    if(character < 0x20)
                    {
                        escaped.append(String.format("\\u%04x", (int)character));
                    }
                    else
                    {
                        escaped.append(character);
                    }
                }
            }
        }

        return escaped.toString();
    }
}
