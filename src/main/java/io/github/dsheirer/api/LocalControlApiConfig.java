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

import io.github.dsheirer.properties.SystemProperties;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Configuration for the local sdrtrunk control API.
 *
 * The token is intentionally sourced from a JVM property or environment variable and is not persisted by default.
 */
public class LocalControlApiConfig
{
    public static final String PROPERTY_ENABLED = "sdrtrunk.api.enabled";
    public static final String PROPERTY_HOST = "sdrtrunk.api.host";
    public static final String PROPERTY_PORT = "sdrtrunk.api.port";
    public static final String PROPERTY_TOKEN = "sdrtrunk.api.token";
    public static final String ENV_TOKEN = "SDRTRUNK_API_TOKEN";
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 9997;

    private final boolean mEnabled;
    private final String mHost;
    private final int mPort;
    private final String mToken;

    public LocalControlApiConfig(boolean enabled, String host, int port, String token)
    {
        mEnabled = enabled;
        mHost = sanitizeHost(host);
        mPort = validatePort(port);
        mToken = sanitizeToken(token);
    }

    public static LocalControlApiConfig defaults()
    {
        return new LocalControlApiConfig(false, DEFAULT_HOST, DEFAULT_PORT, null);
    }

    public static LocalControlApiConfig from(SystemProperties properties)
    {
        String enabledOverride = System.getProperty(PROPERTY_ENABLED);
        boolean enabled = enabledOverride != null ? Boolean.parseBoolean(enabledOverride) :
            properties.get(PROPERTY_ENABLED, false);

        String host = System.getProperty(PROPERTY_HOST);
        if(host == null || host.isBlank())
        {
            host = properties.get(PROPERTY_HOST, DEFAULT_HOST);
        }

        int port;
        String portOverride = System.getProperty(PROPERTY_PORT);
        if(portOverride != null && !portOverride.isBlank())
        {
            try
            {
                port = Integer.parseInt(portOverride);
            }
            catch(NumberFormatException nfe)
            {
                port = DEFAULT_PORT;
            }
        }
        else
        {
            port = properties.get(PROPERTY_PORT, DEFAULT_PORT);
        }

        String token = System.getProperty(PROPERTY_TOKEN);
        if(token == null || token.isBlank())
        {
            token = System.getenv(ENV_TOKEN);
        }

        return new LocalControlApiConfig(enabled, host, port, token);
    }

    public boolean isEnabled()
    {
        return mEnabled;
    }

    public String getHost()
    {
        return mHost;
    }

    public int getPort()
    {
        return mPort;
    }

    public boolean hasToken()
    {
        return mToken != null && !mToken.isBlank();
    }

    boolean matchesToken(String token)
    {
        return hasToken() && mToken.equals(token);
    }

    public boolean isLoopbackOnly()
    {
        try
        {
            return InetAddress.getByName(mHost).isLoopbackAddress();
        }
        catch(UnknownHostException uhe)
        {
            return false;
        }
    }

    @Override
    public String toString()
    {
        return "LocalControlApiConfig{" +
            "enabled=" + mEnabled +
            ", host='" + mHost + '\'' +
            ", port=" + mPort +
            ", tokenConfigured=" + hasToken() +
            '}';
    }

    private static String sanitizeHost(String host)
    {
        if(host == null || host.isBlank())
        {
            return DEFAULT_HOST;
        }

        return host.trim();
    }

    private static int validatePort(int port)
    {
        if(port < 0 || port > 65535)
        {
            return DEFAULT_PORT;
        }

        return port;
    }

    private static String sanitizeToken(String token)
    {
        if(token == null || token.isBlank())
        {
            return null;
        }

        return token.trim();
    }
}
