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

import io.github.dsheirer.api.model.ApiAliasList;
import io.github.dsheirer.api.model.ApiBroadcastConfiguration;
import io.github.dsheirer.api.model.ApiChannel;
import io.github.dsheirer.api.model.ApiModelResponse;
import io.github.dsheirer.api.model.ApiRuntimeSummary;
import io.github.dsheirer.api.model.ApiTuner;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalControlApiReadModelEndpointTest
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
    void readModelEndpointsRequireConfiguredToken() throws Exception
    {
        mServer = startServer("phase-two-token", fixtureProvider());

        assertEquals(401, send("/api/v1/channels", null).statusCode());
        assertEquals(401, send("/api/v1/aliases", "wrong-token").statusCode());
        assertEquals(200, send("/api/v1/channels", "phase-two-token").statusCode());
    }

    @Test
    void channelsEndpointReturnsStableReadOnlyDtoEnvelope() throws Exception
    {
        mServer = startServer(null, fixtureProvider());

        HttpResponse<String> response = send("/api/v1/channels", null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"schemaVersion\":\"1.0\""));
        assertTrue(response.body().contains("\"generatedAt\":"));
        assertTrue(response.body().contains("\"items\":"));
        assertTrue(response.body().contains("\"name\":\"Law Dispatch\""));
        assertTrue(response.body().contains("\"autoStart\":true"));
        assertTrue(response.body().contains("\"processing\":false"));
        assertTrue(response.body().contains("\"decoderType\":\"P25_PHASE1\""));
    }

    @Test
    void aliasEndpointSupportsPaginationMetadata() throws Exception
    {
        mServer = startServer(null, fixtureProvider());

        HttpResponse<String> response = send("/api/v1/aliases?limit=1&offset=0", null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"limit\":1"));
        assertTrue(response.body().contains("\"offset\":0"));
        assertTrue(response.body().contains("\"total\":1"));
        assertTrue(response.body().contains("\"name\":\"Randolph County\""));
    }

    @Test
    void broadcastEndpointRedactsSecrets() throws Exception
    {
        mServer = startServer(null, fixtureProvider());

        HttpResponse<String> response = send("/api/v1/broadcasts", null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"secretRedacted\":true"));
        assertFalse(response.body().contains("stream-password"));
        assertFalse(response.body().contains("stream-token"));
    }

    @Test
    void tunersAndRuntimeEndpointsReturnReadOnlyState() throws Exception
    {
        mServer = startServer(null, fixtureProvider());

        HttpResponse<String> tuners = send("/api/v1/tuners", null);
        HttpResponse<String> runtime = send("/api/v1/runtime", null);

        assertEquals(200, tuners.statusCode());
        assertTrue(tuners.body().contains("\"id\":\"rtl-1\""));
        assertTrue(tuners.body().contains("\"available\":true"));
        assertEquals(200, runtime.statusCode());
        assertTrue(runtime.body().contains("\"configuredChannelCount\":1"));
        assertTrue(runtime.body().contains("\"activeChannelCount\":0"));
    }

    private LocalControlApiServer startServer(String token, LocalControlApiModelProvider provider) throws IOException
    {
        LocalControlApiConfig config = new LocalControlApiConfig(true, "127.0.0.1", 0, token);
        LocalControlApiServer server = new LocalControlApiServer(config, () -> "sdrtrunk test", provider);
        server.start();
        return server;
    }

    private LocalControlApiModelProvider fixtureProvider()
    {
        return new LocalControlApiModelProvider()
        {
            @Override
            public ApiModelResponse<ApiChannel> getChannels(int limit, int offset)
            {
                return ApiModelResponse.of(List.of(new ApiChannel(10, "Law Dispatch", "County", "North", "Public Safety",
                    true, 1, false, "STANDARD", "P25_PHASE1", List.of(155550000L))), 1, limit, offset);
            }

            @Override
            public ApiModelResponse<ApiAliasList> getAliases(int limit, int offset)
            {
                return ApiModelResponse.of(List.of(new ApiAliasList("Randolph County", 42)), 1, limit, offset);
            }

            @Override
            public ApiModelResponse<ApiTuner> getTuners(int limit, int offset)
            {
                return ApiModelResponse.of(List.of(new ApiTuner("rtl-1", "RTL2832", "AVAILABLE", true, true, null)), 1,
                    limit, offset);
            }

            @Override
            public ApiModelResponse<ApiBroadcastConfiguration> getBroadcasts(int limit, int offset)
            {
                return ApiModelResponse.of(List.of(new ApiBroadcastConfiguration("bcast-1", "Broadcastify", "Feed", true,
                    "CONNECTED", true)), 1, limit, offset);
            }

            @Override
            public ApiRuntimeSummary getRuntimeSummary()
            {
                return new ApiRuntimeSummary(1, 0, 1, 1, 0);
            }
        };
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
