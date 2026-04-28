package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;

/**
 * Consumes a single {@code StartContainer(BYTE_STREAM, _)} event on entry,
 * then writes the bytes from each subsequent {@code Chunk} event for as long
 * as events are available and the sink has room. Always returns {@code false}
 * from {@link #generate}: a byte stream has no terminator on the wire, so
 * completion is declared externally by the transport.
 */
public final class ByteStreamEncoder implements ValueEncoder {
    private boolean startConsumed = false;
    private byte[] pendingChunk;
    private int pendingOffset = 0;

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        if (!startConsumed) {
            if (events.availableEvents() <= 0) {
                return false;
            }
            events.read(); // StartContainer(BYTE_STREAM, _)
            startConsumed = true;
        }
        while (true) {
            if (pendingChunk == null) {
                if (events.availableEvents() <= 0) {
                    return false;
                }
                Event.Chunk chunk = (Event.Chunk) events.read();
                pendingChunk = chunk.bytes();
                pendingOffset = 0;
            }
            while (pendingOffset < pendingChunk.length) {
                if (sink.writableBytes() <= 0) {
                    return false;
                }
                int n = sink.write(pendingChunk, pendingOffset,
                        pendingChunk.length - pendingOffset);
                if (n <= 0) {
                    return false;
                }
                pendingOffset += n;
            }
            pendingChunk = null;
        }
    }
}
