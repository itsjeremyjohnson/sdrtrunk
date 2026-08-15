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
package io.github.dsheirer.module.decode.p25.phase1;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton dual TCP server that broadcasts decoded event JSON and raw message JSON to connected clients.
 */
public class NetworkStreamManager
{
    private static final Logger mLog = LoggerFactory.getLogger(NetworkStreamManager.class);

    private static NetworkStreamManager sInstance;

    private final CopyOnWriteArrayList<ClientWriter> mEventClients = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ClientWriter> mRawClients = new CopyOnWriteArrayList<>();

    private NetworkStreamManager(ServerSocket eventServerSocket, ServerSocket rawServerSocket)
    {
        startAcceptLoop(eventServerSocket, mEventClients);
        startAcceptLoop(rawServerSocket, mRawClients);
    }

    /**
     * Returns the singleton instance, creating and starting it if necessary.
     * @param eventPort TCP port for decoded event stream
     * @param rawPort TCP port for raw message stream
     * @return singleton instance
     */
    public static synchronized NetworkStreamManager getInstance(int eventPort, int rawPort)
    {
        if(eventPort == rawPort)
        {
            throw new IllegalArgumentException("Event and raw stream ports must be distinct");
        }

        if(sInstance == null)
        {
            ServerSocket eventServerSocket = null;
            try
            {
                eventServerSocket = new ServerSocket(eventPort);
                ServerSocket rawServerSocket = new ServerSocket(rawPort);
                sInstance = new NetworkStreamManager(eventServerSocket, rawServerSocket);
            }
            catch(IOException e)
            {
                close(eventServerSocket);
                mLog.error("Failed to bind network stream TCP ports {} and {}: {}", eventPort, rawPort,
                    e.getMessage());
            }
        }
        return sInstance;
    }

    /**
     * Broadcasts a JSON string to all connected event stream clients.
     */
    public void broadcastEvent(String json)
    {
        broadcast(json, mEventClients);
    }

    /**
     * Broadcasts a JSON string to all connected raw stream clients.
     */
    public void broadcastRaw(String json)
    {
        broadcast(json, mRawClients);
    }

    private void broadcast(String json, CopyOnWriteArrayList<ClientWriter> clients)
    {
        Iterator<ClientWriter> it = clients.iterator();
        while(it.hasNext())
        {
            ClientWriter writer = it.next();
            if(!writer.isAlive())
            {
                clients.remove(writer);
            }
            else if(!writer.offer(json))
            {
                clients.remove(writer);
            }
        }
    }

    private static void close(ServerSocket serverSocket)
    {
        if(serverSocket != null)
        {
            try
            {
                serverSocket.close();
            }
            catch(IOException e)
            {
                //Ignore cleanup failure after bind failure.
            }
        }
    }

    private void startAcceptLoop(ServerSocket serverSocket, CopyOnWriteArrayList<ClientWriter> clients)
    {
        Thread.ofVirtual().start(() ->
        {
            int port = serverSocket.getLocalPort();
            try(serverSocket)
            {
                mLog.info("NetworkStreamManager listening on port {}", port);
                while(true)
                {
                    try
                    {
                        Socket socket = serverSocket.accept();
                        ClientWriter writer = new ClientWriter(socket);
                        clients.add(writer);
                        mLog.debug("Client connected on port {}: {}", port, socket.getRemoteSocketAddress());

                        // Monitor for client disconnect
                        Thread.ofVirtual().start(() ->
                        {
                            try
                            {
                                while(socket.getInputStream().read() != -1)
                                {
                                    // Drain client input until disconnect.
                                }
                            }
                            catch(IOException e)
                            {
                                // Socket failure also indicates disconnect.
                            }
                            finally
                            {
                                writer.close();
                            }
                        });
                    }
                    catch(IOException e)
                    {
                        mLog.warn("Error accepting client on port {}: {}", port, e.getMessage());
                    }
                }
            }
            catch(IOException e)
            {
                mLog.error("Failed to start TCP server on port {}: {}", port, e.getMessage());
            }
        });
    }

    /**
     * Configuration POJO for network stream settings.
     */
    public static class Config
    {
        public int eventPort = 9500;
        public int rawPort = 9501;
    }

    /**
     * Wraps a connected client socket with a non-blocking queue-based writer.
     */
    public static class ClientWriter
    {
        private final Socket mSocket;
        private final PrintWriter mWriter;
        private final ArrayBlockingQueue<String> mQueue;
        private final Thread mWriterThread;
        private volatile boolean mAlive = true;

        public ClientWriter(Socket socket) throws IOException
        {
            this(socket, 512, true);
        }

        ClientWriter(Socket socket, int queueCapacity, boolean startWriter) throws IOException
        {
            mSocket = socket;
            mWriter = new PrintWriter(socket.getOutputStream(), false);
            mQueue = new ArrayBlockingQueue<>(queueCapacity);

            mWriterThread = startWriter ? Thread.ofVirtual().start(() ->
            {
                while(mAlive)
                {
                    try
                    {
                        String line = mQueue.take();
                        mWriter.println(line);
                        mWriter.flush();
                        if(mWriter.checkError())
                        {
                            mAlive = false;
                        }
                    }
                    catch(InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                        mAlive = false;
                    }
                }
            }) : null;
        }

        /**
         * Offers a line to the write queue and disconnects if the queue is full.
         */
        public boolean offer(String json)
        {
            boolean accepted = mQueue.offer(json);
            if(!accepted)
            {
                close();
            }
            return accepted;
        }

        /**
         * Returns whether this client connection is still alive.
         */
        public boolean isAlive()
        {
            return mAlive;
        }

        /**
         * Closes this client connection.
         */
        public void close()
        {
            mAlive = false;
            if(mWriterThread != null)
            {
                mWriterThread.interrupt();
            }
            try
            {
                mSocket.close();
            }
            catch(IOException e)
            {
                // ignore
            }
        }
    }
}
