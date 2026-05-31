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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedded local HTTP API for safe, scriptable sdrtrunk inspection and future control operations.
 */
public class LocalControlApiServer
{
    private final static Logger mLog = LoggerFactory.getLogger(LocalControlApiServer.class);
    private static final String STATUS_PATH = "/api/v1/status";
    private static final String OPENAPI_PATH = "/api/v1/openapi.yaml";
    private static final String OPENAPI_RESOURCE = "/openapi/local-control-api.yaml";
    private static final String CHANNELS_PATH = "/api/v1/channels";
    private static final String ALIASES_PATH = "/api/v1/aliases";
    private static final String TUNERS_PATH = "/api/v1/tuners";
    private static final String BROADCASTS_PATH = "/api/v1/broadcasts";
    private static final String RUNTIME_PATH = "/api/v1/runtime";
    private static final String AUDIT_PATH = "/api/v1/audit";
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final LocalControlApiConfig mConfig;
    private final Supplier<String> mApplicationNameSupplier;
    private final ObjectMapper mObjectMapper = new ObjectMapper();
    private volatile LocalControlApiModelProvider mModelProvider;
    private final Instant mStartedAt = Instant.now();
    private HttpServer mServer;
    private ExecutorService mExecutor;

    public LocalControlApiServer(LocalControlApiConfig config, Supplier<String> applicationNameSupplier)
    {
        this(config, applicationNameSupplier, EmptyLocalControlApiModelProvider.INSTANCE);
    }

    public LocalControlApiServer(LocalControlApiConfig config, Supplier<String> applicationNameSupplier,
                                 LocalControlApiModelProvider modelProvider)
    {
        mConfig = config;
        mApplicationNameSupplier = applicationNameSupplier;
        mModelProvider = modelProvider != null ? modelProvider : EmptyLocalControlApiModelProvider.INSTANCE;
    }

    public void setModelProvider(LocalControlApiModelProvider modelProvider)
    {
        mModelProvider = modelProvider != null ? modelProvider : EmptyLocalControlApiModelProvider.INSTANCE;
    }

    public synchronized void start() throws IOException
    {
        if(mServer != null)
        {
            return;
        }

        if(!mConfig.isEnabled())
        {
            mLog.info("Local control API disabled");
            return;
        }

        if(!mConfig.isLoopbackOnly())
        {
            throw new IOException("Local control API refused to bind non-loopback host: " + mConfig.getHost());
        }

        InetSocketAddress address = new InetSocketAddress(mConfig.getHost(), mConfig.getPort());
        mServer = HttpServer.create(address, 0);
        mServer.createContext(STATUS_PATH, this::handleStatus);
        mServer.createContext(OPENAPI_PATH, this::handleOpenApi);
        mServer.createContext(CHANNELS_PATH, this::handleChannels);
        mServer.createContext(ALIASES_PATH, exchange -> handleModelResponse(exchange,
            () -> mModelProvider.getAliases(getLimit(exchange), getOffset(exchange))));
        mServer.createContext(TUNERS_PATH, exchange -> handleModelResponse(exchange,
            () -> mModelProvider.getTuners(getLimit(exchange), getOffset(exchange))));
        mServer.createContext(BROADCASTS_PATH, exchange -> handleModelResponse(exchange,
            () -> mModelProvider.getBroadcasts(getLimit(exchange), getOffset(exchange))));
        mServer.createContext(RUNTIME_PATH, exchange -> handleModelResponse(exchange,
            () -> mModelProvider.getRuntimeSummary()));
        mServer.createContext(AUDIT_PATH, exchange -> handleModelResponse(exchange,
            () -> mModelProvider.getAuditRecords(getLimit(exchange), getOffset(exchange))));
        mExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "sdrtrunk-local-control-api");
            thread.setDaemon(true);
            return thread;
        });
        mServer.setExecutor(mExecutor);
        mServer.start();
        mLog.info("Local control API listening on http://" + mConfig.getHost() + ":" + getPort());
    }

    public synchronized void stop()
    {
        if(mServer != null)
        {
            mServer.stop(0);
            mServer = null;
        }

        if(mExecutor != null)
        {
            mExecutor.shutdownNow();
            mExecutor = null;
        }
    }

    public int getPort()
    {
        if(mServer == null)
        {
            return mConfig.getPort();
        }

        return mServer.getAddress().getPort();
    }

    private void handleStatus(HttpExchange exchange) throws IOException
    {
        if(!isGet(exchange))
        {
            send(exchange, 405, "application/json", "{\"error\":\"method_not_allowed\"}");
            return;
        }

        if(!authorized(exchange))
        {
            send(exchange, 401, "application/json", "{\"error\":\"unauthorized\"}");
            return;
        }

        long uptimeSeconds = Math.max(0, Instant.now().getEpochSecond() - mStartedAt.getEpochSecond());
        String json = "{" +
            "\"status\":\"ok\"," +
            "\"application\":\"" + escape(mApplicationNameSupplier.get()) + "\"," +
            "\"apiEnabled\":true," +
            "\"readOnly\":false," +
            "\"host\":\"" + escape(mConfig.getHost()) + "\"," +
            "\"port\":" + getPort() + "," +
            "\"tokenConfigured\":" + mConfig.hasToken() + "," +
            "\"startedAt\":\"" + mStartedAt + "\"," +
            "\"uptimeSeconds\":" + uptimeSeconds +
            "}";

        send(exchange, 200, "application/json", json);
    }

    private void handleOpenApi(HttpExchange exchange) throws IOException
    {
        if(!isGet(exchange))
        {
            send(exchange, 405, "application/json", "{\"error\":\"method_not_allowed\"}");
            return;
        }

        send(exchange, 200, "application/yaml", loadOpenApiDocument());
    }

    private void handleChannels(HttpExchange exchange) throws IOException
    {
        if(isGet(exchange))
        {
            handleModelResponse(exchange, () -> mModelProvider.getChannels(getLimit(exchange), getOffset(exchange)));
            return;
        }

        if(!isPost(exchange))
        {
            send(exchange, 405, "application/json", "{\"error\":\"method_not_allowed\"}");
            return;
        }

        if(!authorized(exchange))
        {
            send(exchange, 401, "application/json", "{\"error\":\"unauthorized\"}");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String prefix = CHANNELS_PATH + "/";

        if(!path.startsWith(prefix))
        {
            send(exchange, 404, "application/json", "{\"error\":\"not_found\"}");
            return;
        }

        String[] parts = path.substring(prefix.length()).split("/");

        if(parts.length != 2 || !("start".equals(parts[1]) || "stop".equals(parts[1])))
        {
            send(exchange, 404, "application/json", "{\"error\":\"not_found\"}");
            return;
        }

        String endpoint = prefix + parts[0] + "/" + parts[1];

        try
        {
            Object result = mModelProvider.controlChannel(parts[0], parts[1], getDryRun(exchange), endpoint);
            send(exchange, 200, "application/json", mObjectMapper.writeValueAsString(result));
        }
        catch(IllegalArgumentException iae)
        {
            send(exchange, 400, "application/json", "{\"error\":\"bad_request\",\"message\":\"" + escape(iae.getMessage()) + "\"}");
        }
    }

    private void handleModelResponse(HttpExchange exchange, ThrowingSupplier<Object> responseSupplier) throws IOException
    {
        if(!isGet(exchange))
        {
            send(exchange, 405, "application/json", "{\"error\":\"method_not_allowed\"}");
            return;
        }

        if(!authorized(exchange))
        {
            send(exchange, 401, "application/json", "{\"error\":\"unauthorized\"}");
            return;
        }

        try
        {
            send(exchange, 200, "application/json", mObjectMapper.writeValueAsString(responseSupplier.get()));
        }
        catch(IllegalArgumentException iae)
        {
            send(exchange, 400, "application/json", "{\"error\":\"bad_request\",\"message\":\"" + escape(iae.getMessage()) + "\"}");
        }
    }

    private int getLimit(HttpExchange exchange)
    {
        return Math.min(MAX_LIMIT, Math.max(1, getQueryInt(exchange, "limit", DEFAULT_LIMIT)));
    }

    private int getOffset(HttpExchange exchange)
    {
        return Math.max(0, getQueryInt(exchange, "offset", 0));
    }

    private boolean getDryRun(HttpExchange exchange)
    {
        String value = getQueryValue(exchange, "dry_run");

        if(value == null)
        {
            return true;
        }

        if("true".equalsIgnoreCase(value))
        {
            return true;
        }

        if("false".equalsIgnoreCase(value))
        {
            return false;
        }

        throw new IllegalArgumentException("Invalid boolean query parameter: dry_run");
    }

    private String getQueryValue(HttpExchange exchange, String name)
    {
        String query = exchange.getRequestURI().getRawQuery();

        if(query == null || query.isEmpty())
        {
            return null;
        }

        for(String parameter: query.split("&"))
        {
            String[] parts = parameter.split("=", 2);
            if(parts.length == 2 && name.equals(parts[0]))
            {
                return parts[1];
            }
        }

        return null;
    }

    private int getQueryInt(HttpExchange exchange, String name, int defaultValue)
    {
        String query = exchange.getRequestURI().getRawQuery();

        if(query == null || query.isEmpty())
        {
            return defaultValue;
        }

        for(String parameter: query.split("&"))
        {
            String[] parts = parameter.split("=", 2);
            if(parts.length == 2 && name.equals(parts[0]))
            {
                try
                {
                    return Integer.parseInt(parts[1]);
                }
                catch(NumberFormatException nfe)
                {
                    throw new IllegalArgumentException("Invalid integer query parameter: " + name);
                }
            }
        }

        return defaultValue;
    }

    private interface ThrowingSupplier<T>
    {
        T get() throws IOException;
    }

    private boolean isGet(HttpExchange exchange)
    {
        return "GET".equalsIgnoreCase(exchange.getRequestMethod());
    }

    private boolean isPost(HttpExchange exchange)
    {
        return "POST".equalsIgnoreCase(exchange.getRequestMethod());
    }

    private boolean authorized(HttpExchange exchange)
    {
        if(!mConfig.hasToken())
        {
            return true;
        }

        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if(authorization == null || !authorization.startsWith("Bearer "))
        {
            return false;
        }

        return mConfig.matchesToken(authorization.substring("Bearer ".length()));
    }

    private String loadOpenApiDocument() throws IOException
    {
        try(InputStream inputStream = LocalControlApiServer.class.getResourceAsStream(OPENAPI_RESOURCE))
        {
            if(inputStream == null)
            {
                return "openapi: 3.0.3\ninfo:\n  title: sdrtrunk Local Control API\n  version: 1.0.0\npaths:\n  /api/v1/status:\n    get:\n      summary: Local API status\n";
            }

            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void send(HttpExchange exchange, int statusCode, String contentType, String body) throws IOException
    {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try(OutputStream outputStream = exchange.getResponseBody())
        {
            outputStream.write(bytes);
        }
    }

    private String escape(String value)
    {
        if(value == null)
        {
            return "";
        }

        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
