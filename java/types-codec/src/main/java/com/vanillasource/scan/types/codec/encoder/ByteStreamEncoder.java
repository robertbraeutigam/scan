package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;

/**
 * Composes the byte-stream's phases via {@link ValueEncoder#andThen}: consume a
 * single {@code StartStream} event on entry, then write the bytes from each
 * subsequent {@code Chunk} event for as long as events are available and the
 * sink has room. The drain phase never completes — a byte stream has no
 * terminator on the wire, so completion is declared externally by the
 * transport.
 */
public final class ByteStreamEncoder implements ValueEncoder {
    private final ValueEncoder pipeline = consumeStartStream().andThen(drainChunks());

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        return pipeline.generate(events, sink);
    }

    private static ValueEncoder consumeStartStream() {
        return (events, sink) -> {
            if (events.availableEvents() <= 0) {
                return false;
            }
            events.read();
            return true;
        };
    }

    private static ValueEncoder drainChunks() {
        return new ValueEncoder() {
            private byte[] pendingChunk;
            private int pendingOffset = 0;

            @Override
            public boolean generate(EventSource events, BitSink sink) {
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
        };
    }
}
