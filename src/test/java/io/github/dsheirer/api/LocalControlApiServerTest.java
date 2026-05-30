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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalControlApiServerTest
{
    private LocalControlApiServer mServer;
    private final HttpClient mClient = HttpClient.newHttpClient();

    @AfterEach
    void tearDown()
    {
        if(mServer != null)
        {
            mServer.stop();
        }
    }

    @Test
    void statusReturnsJsonWithoutSecrets() throws Exception
    {
        mServer = startServer("super-secret-token");

        HttpResponse<String> response = send("/api/v1/status", "super-secret-token");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"ok\""));
        assertTrue(response.body().contains("\"apiEnabled\":true"));
        assertTrue(response.body().contains("\"readOnly\":true"));
        assertFalse(response.body().contains("super-secret-token"));
    }

    @Test
    void configuredTokenIsRequired() throws Exception
    {
        mServer = startServer("super-secret-token");

        assertEquals(401, send("/api/v1/status", null).statusCode());
        assertEquals(401, send("/api/v1/status", "wrong-token").statusCode());
        assertEquals(200, send("/api/v1/status", "super-secret-token").statusCode());
    }

    @Test
    void openApiDocumentIsServed() throws Exception
    {
        mServer = startServer(null);

        HttpResponse<String> response = send("/api/v1/openapi.yaml", null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("openapi: 3.0.3"));
        assertTrue(response.body().contains("/api/v1/status"));
    }

    private LocalControlApiServer startServer(String token) throws IOException
    {
        LocalControlApiConfig config = new LocalControlApiConfig(true, "127.0.0.1", 0, token);
        LocalControlApiServer server = new LocalControlApiServer(config, () -> "sdrtrunk test");
        server.start();
        return server;
    }

    private HttpResponse<String> send(String path, String token) throws IOException, InterruptedException
    {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + mServer.getPort() + path))
            .GET();

        if(token != null)
        {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        return mClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
