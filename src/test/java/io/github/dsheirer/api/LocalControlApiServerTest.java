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
import io.github.dsheirer.api.model.ApiAuditRecord;
import io.github.dsheirer.api.model.ApiBroadcastConfiguration;
import io.github.dsheirer.api.model.ApiChannel;
import io.github.dsheirer.api.model.ApiControlResult;
import io.github.dsheirer.api.model.ApiModelResponse;
import io.github.dsheirer.api.model.ApiRuntimeSummary;
import io.github.dsheirer.api.model.ApiTuner;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
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

        HttpResponse<String> response = send("GET", "/api/v1/status", "super-secret-token");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"ok\""));
        assertTrue(response.body().contains("\"apiEnabled\":true"));
        assertTrue(response.body().contains("\"readOnly\":false"));
        assertFalse(response.body().contains("super-secret-token"));
    }

    @Test
    void configuredTokenIsRequired() throws Exception
    {
        mServer = startServer("super-secret-token");

        assertEquals(401, send("GET", "/api/v1/status", null).statusCode());
        assertEquals(401, send("GET", "/api/v1/status", "wrong-token").statusCode());
        assertEquals(200, send("GET", "/api/v1/status", "super-secret-token").statusCode());
    }

    @Test
    void openApiDocumentIsServed() throws Exception
    {
        mServer = startServer(null);

        HttpResponse<String> response = send("GET", "/api/v1/openapi.yaml", null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("openapi: 3.0.3"));
        assertTrue(response.body().contains("/api/v1/status"));
    }

    @Test
    void channelStartDryRunReturnsPlanWithoutMutationOrAudit() throws Exception
    {
        FakeModelProvider provider = new FakeModelProvider();
        mServer = startServer(null, provider);

        HttpResponse<String> response = send("POST", "/api/v1/channels/channel-1/start?dry_run=true", null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"channelId\":\"channel-1\""));
        assertTrue(response.body().contains("\"action\":\"start\""));
        assertTrue(response.body().contains("\"dryRun\":true"));
        assertTrue(response.body().contains("\"wouldChange\":true"));
        assertFalse(provider.mutated);
        assertEquals(0, provider.getAuditRecords(100, 0).getTotal());
    }

    @Test
    void channelStopApplyMutatesAndWritesAuditRecord() throws Exception
    {
        FakeModelProvider provider = new FakeModelProvider();
        provider.processing = true;
        mServer = startServer(null, provider);

        HttpResponse<String> response = send("POST", "/api/v1/channels/channel-1/stop?dry_run=false", null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"dryRun\":false"));
        assertTrue(response.body().contains("\"result\":\"applied\""));
        assertFalse(provider.processing);
        assertTrue(provider.mutated);

        HttpResponse<String> audit = send("GET", "/api/v1/audit", null);
        assertEquals(200, audit.statusCode());
        assertTrue(audit.body().contains("\"endpoint\":\"/api/v1/channels/channel-1/stop\""));
        assertTrue(audit.body().contains("\"dryRun\":false"));
        assertTrue(audit.body().contains("\"result\":\"applied\""));
    }

    @Test
    void channelMutationValidationFailureReturnsBadRequest() throws Exception
    {
        FakeModelProvider provider = new FakeModelProvider();
        mServer = startServer(null, provider);

        HttpResponse<String> response = send("POST", "/api/v1/channels/missing/start?dry_run=false", null);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("channel_not_found"));
        assertFalse(provider.mutated);
    }

    private LocalControlApiServer startServer(String token) throws IOException
    {
        return startServer(token, EmptyLocalControlApiModelProvider.INSTANCE);
    }

    private LocalControlApiServer startServer(String token, LocalControlApiModelProvider provider) throws IOException
    {
        LocalControlApiConfig config = new LocalControlApiConfig(true, "127.0.0.1", 0, token);
        LocalControlApiServer server = new LocalControlApiServer(config, () -> "sdrtrunk test", provider);
        server.start();
        return server;
    }

    private HttpResponse<String> send(String method, String path, String token) throws IOException, InterruptedException
    {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + mServer.getPort() + path));

        if("POST".equals(method))
        {
            requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
        }
        else
        {
            requestBuilder.GET();
        }

        if(token != null)
        {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        return mClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static class FakeModelProvider implements LocalControlApiModelProvider
    {
        private boolean processing;
        private boolean mutated;
        private final List<ApiAuditRecord> auditRecords = new ArrayList<>();

        @Override
        public ApiModelResponse<ApiChannel> getChannels(int limit, int offset)
        {
            return ApiModelResponse.of(List.of(), 0, limit, offset);
        }

        @Override
        public ApiModelResponse<ApiAliasList> getAliases(int limit, int offset)
        {
            return ApiModelResponse.of(List.of(), 0, limit, offset);
        }

        @Override
        public ApiModelResponse<ApiTuner> getTuners(int limit, int offset)
        {
            return ApiModelResponse.of(List.of(), 0, limit, offset);
        }

        @Override
        public ApiModelResponse<ApiBroadcastConfiguration> getBroadcasts(int limit, int offset)
        {
            return ApiModelResponse.of(List.of(), 0, limit, offset);
        }

        @Override
        public ApiRuntimeSummary getRuntimeSummary()
        {
            return new ApiRuntimeSummary(1, processing ? 1 : 0, 0, 0, 0);
        }

        @Override
        public ApiModelResponse<ApiAuditRecord> getAuditRecords(int limit, int offset)
        {
            return ApiModelResponse.of(auditRecords, auditRecords.size(), limit, offset);
        }

        @Override
        public ApiControlResult controlChannel(String channelId, String action, boolean dryRun, String endpoint)
        {
            if(!"channel-1".equals(channelId))
            {
                throw new IllegalArgumentException("channel_not_found: " + channelId);
            }

            boolean targetProcessing = "start".equals(action);
            boolean wouldChange = processing != targetProcessing;
            String result = dryRun ? "dry_run" : "applied";

            if(!dryRun)
            {
                processing = targetProcessing;
                mutated = true;
                auditRecords.add(new ApiAuditRecord(endpoint, "local-api", action, channelId, dryRun, result,
                    List.of("processing")));
            }

            return new ApiControlResult(channelId, action, dryRun, wouldChange, result,
                List.of(wouldChange ? "processing would change" : "channel already in requested state"));
        }
    }
}
