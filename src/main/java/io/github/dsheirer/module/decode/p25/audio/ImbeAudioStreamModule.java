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
package io.github.dsheirer.module.decode.p25.audio;

import io.github.dsheirer.audio.squelch.ISquelchStateListener;
import io.github.dsheirer.audio.squelch.SquelchState;
import io.github.dsheirer.audio.squelch.SquelchStateEvent;
import io.github.dsheirer.identifier.Identifier;
import io.github.dsheirer.identifier.IdentifierUpdateListener;
import io.github.dsheirer.identifier.IdentifierUpdateNotification;
import io.github.dsheirer.identifier.MutableIdentifierCollection;
import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.message.IMessageListener;
import io.github.dsheirer.module.Module;
import io.github.dsheirer.module.decode.p25.phase1.ImbeStreamManager;
import io.github.dsheirer.module.decode.p25.phase1.message.hdu.HDUMessage;
import io.github.dsheirer.module.decode.p25.phase1.message.ldu.LDU1Message;
import io.github.dsheirer.module.decode.p25.phase1.message.ldu.LDU2Message;
import io.github.dsheirer.module.decode.p25.phase1.message.ldu.LDUMessage;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.util.JsonUtils;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Module that intercepts unencrypted P25 Phase 1 LDU voice messages and streams each raw
 * IMBE frame to connected TCP clients via ImbeStreamManager. Encrypted voice frames are suppressed.
 *
 * Three NDJSON message types are emitted per call:
 *
 *   call_start — when squelch opens; carries talkgroup, from-unit, and a unique callId
 *   frame      — one per 18-byte IMBE frame (~9 per LDU, ~20 ms each)
 *   call_end   — when squelch closes; carries total frame count for the call
 *
 * This module is wired into the P25P1 channel pipeline by DecoderFactory alongside
 * (not instead of) the normal P25P1AudioModule.  Both run in parallel: SDRTrunk
 * continues recording MP3 files as usual while this module simultaneously streams
 * raw frames to any connected TCP clients.
 */
public class ImbeAudioStreamModule extends Module
        implements IMessageListener, ISquelchStateListener, IdentifierUpdateListener
{
    private static final Logger mLog = LoggerFactory.getLogger(ImbeAudioStreamModule.class);
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Base64.Encoder BASE64 = Base64.getEncoder();

    private final String mSystemName;
    private final ImbeStreamManager mStreamManager;

    // Identifier collection — updated by the processing chain as the decoder
    // discovers talkgroup and unit IDs during the call.
    private final MutableIdentifierCollection mIdentifiers = new MutableIdentifierCollection(0);

    // Per-call state
    private volatile String  mCallId      = null;
    private final AtomicInteger mFrameSeq = new AtomicInteger(0);
    private final AtomicInteger mFrameCount = new AtomicInteger(0);
    private boolean mEncryptedCall;
    private boolean mEncryptedCallStateEstablished;
    private final List<LDUMessage> mCachedLduMessages = new ArrayList<>();

    // Squelch listener — inner class so the processing chain can wire it up
    private final SquelchStateListener mSquelchStateListener = new SquelchStateListener();

    // Message listener — inner class returned via getMessageListener()
    private final MessageReceiver mMessageReceiver = new MessageReceiver();

    /**
     * Constructs an instance.
     * @param systemName label for the system this channel belongs to (included in every JSON message)
     * @param streamManager singleton TCP server to broadcast frames to
     */
    public ImbeAudioStreamModule(String systemName, ImbeStreamManager streamManager)
    {
        mSystemName = systemName;
        mStreamManager = streamManager;
    }

    // ── Module lifecycle ──────────────────────────────────────────────────

    @Override public void start()  {}
    @Override public void stop()   { endCall(); }
    @Override public void reset()  { endCall(); }

    // ── IMessageListener ─────────────────────────────────────────────────

    @Override
    public Listener<IMessage> getMessageListener()
    {
        return mMessageReceiver;
    }

    // ── ISquelchStateListener ─────────────────────────────────────────────

    @Override
    public Listener<SquelchStateEvent> getSquelchStateListener()
    {
        return mSquelchStateListener;
    }

    // ── IdentifierUpdateListener ──────────────────────────────────────────

    @Override
    public Listener<IdentifierUpdateNotification> getIdentifierUpdateListener()
    {
        return notification ->
        {
            if(notification != null && notification.getIdentifier() != null)
            {
                mIdentifiers.receive(notification);
            }
        };
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    /**
     * Generates a short hex call ID from the current timestamp.
     */
    private static String newCallId()
    {
        return UUID.randomUUID().toString();
    }

    private String currentTalkgroup()
    {
        Identifier to = mIdentifiers.getToIdentifier();
        return to != null ? to.toString() : "";
    }

    private String currentFrom()
    {
        Identifier from = mIdentifiers.getFromIdentifier();
        return from != null ? from.toString() : "";
    }

    /**
     * Opens a new call: generates a callId, resets counters, and broadcasts call_start.
     */
    private synchronized void openCall(long timestamp)
    {
        if(mCallId != null)
        {
            return;
        }

        mCallId = newCallId();
        mFrameSeq.set(0);
        mFrameCount.set(0);

        String json = createCallStartJson(mCallId, mSystemName, currentTalkgroup(), currentFrom(), timestamp);
        mStreamManager.broadcastCallStart(mCallId, json);
    }

    static String createCallStartJson(String callId, String systemName, String talkgroup, String from, long timestamp)
    {
        String formattedTimestamp = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(TIMESTAMP_FMT);
        return "{\"type\":\"call_start\"" +
            ",\"callId\":\"" + callId + "\"" +
            ",\"system\":\"" + JsonUtils.escape(systemName) + "\"" +
            ",\"talkgroup\":\"" + JsonUtils.escape(talkgroup) + "\"" +
            ",\"from\":\"" + JsonUtils.escape(from) + "\"" +
            ",\"timestamp\":\"" + formattedTimestamp + "\"}";
    }

    /**
     * Closes the current call: broadcasts call_end and clears state.
     */
    private synchronized void endCall()
    {
        if(mCallId != null)
        {
            String closingCallId = mCallId;
            int totalFrames = mFrameCount.get();
            mCallId = null;

            String json = "{\"type\":\"call_end\"" +
                    ",\"callId\":\"" + closingCallId + "\"" +
                    ",\"system\":\"" + JsonUtils.escape(mSystemName) + "\"" +
                    ",\"talkgroup\":\"" + JsonUtils.escape(currentTalkgroup()) + "\"" +
                    ",\"frames\":" + totalFrames + "}";

            mStreamManager.broadcastCallEnd(closingCallId, json);
        }

        mIdentifiers.clear();
        mEncryptedCall = false;
        mEncryptedCallStateEstablished = false;
        mCachedLduMessages.clear();
    }

    /**
     * Processes one LDU message: opens a call if needed, then streams each of its
     * 9 IMBE frames as individual JSON lines.
     */
    private void processLdu(LDUMessage ldu)
    {
        // Ensure a call is open before streaming frames
        if(mCallId == null)
        {
            openCall(ldu.getTimestamp());
        }

        String callId    = mCallId;
        String talkgroup = JsonUtils.escape(currentTalkgroup());
        String from      = JsonUtils.escape(currentFrom());
        String system    = JsonUtils.escape(mSystemName);

        List<byte[]> frames = ldu.getIMBEFrames();
        for (byte[] frame : frames)
        {
            String imbeB64 = BASE64.encodeToString(frame);
            int seq = mFrameSeq.getAndIncrement();
            mFrameCount.incrementAndGet();

            String json = "{\"type\":\"frame\"" +
                    ",\"callId\":\"" + callId + "\"" +
                    ",\"system\":\"" + system + "\"" +
                    ",\"talkgroup\":\"" + talkgroup + "\"" +
                    ",\"from\":\"" + from + "\"" +
                    ",\"seq\":" + seq +
                    ",\"imbe\":\"" + imbeB64 + "\"}";

            mStreamManager.broadcast(json);
        }
    }

    // ── Inner classes ─────────────────────────────────────────────────────

    private class MessageReceiver implements Listener<IMessage>
    {
        @Override
        public void receive(IMessage message)
        {
            if(message instanceof HDUMessage hdu && hdu.isValid())
            {
                mEncryptedCallStateEstablished = true;
                mEncryptedCall = hdu.getHeaderData().isEncryptedAudio();
            }
            else if(mEncryptedCallStateEstablished && message instanceof LDUMessage ldu)
            {
                if(!mEncryptedCall)
                {
                    processLdu(ldu);
                }
            }
            else if(message instanceof LDU1Message ldu1)
            {
                mCachedLduMessages.add(ldu1);
            }
            else if(message instanceof LDU2Message ldu2)
            {
                if(ldu2.getEncryptionSyncParameters().isValid())
                {
                    mEncryptedCallStateEstablished = true;
                    mEncryptedCall = ldu2.getEncryptionSyncParameters().isEncryptedAudio();
                }

                if(mEncryptedCallStateEstablished)
                {
                    if(!mEncryptedCall)
                    {
                        mCachedLduMessages.forEach(ImbeAudioStreamModule.this::processLdu);
                        processLdu(ldu2);
                    }
                    mCachedLduMessages.clear();
                }
                else
                {
                    mCachedLduMessages.add(ldu2);
                }
            }
        }
    }

    private class SquelchStateListener implements Listener<SquelchStateEvent>
    {
        @Override
        public void receive(SquelchStateEvent event)
        {
            if (event.getSquelchState() == SquelchState.SQUELCH)
            {
                endCall();
            }
            // UNSQUELCH is handled implicitly: the first LDU frame calls openCall()
        }
    }
}
