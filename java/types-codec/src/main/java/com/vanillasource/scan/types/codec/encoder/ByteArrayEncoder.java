package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;

/**
 * Consumes {@code StartContainer(BYTE_ARRAY, count)}, writes the count VLI
 * (omitted when {@code countVarintBytes == 0}), then accepts {@code Chunk}
 * events whose total byte length must equal the declared count, finally
 * consumes {@code EndContainer(BYTE_ARRAY)}. Bytes from each chunk are written
 * straight through to the sink; partial sink space is honoured by buffering
 * the in-flight chunk's offset.
 */
public final class ByteArrayEncoder implements ValueEncoder {
    private final int min;
    private final int countVarintBytes;
    private boolean startConsumed = false;
    private long count = -1;
    private long remaining = -1;
    private VariableLengthIntegerEncoder vli;
    private EventSource vliSource;
    private boolean countWritten = false;
    private byte[] pendingChunk;
    private int pendingOffset = 0;
    private boolean endConsumed = false;

    public ByteArrayEncoder(int min, int countVarintBytes) {
        this.min = min;
        this.countVarintBytes = countVarintBytes;
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        if (!startConsumed) {
            if (events.availableEvents() <= 0) {
                return false;
            }
            Event.StartContainer sc = (Event.StartContainer) events.read();
            count = sc.count();
            if (count < min) {
                throw new IllegalArgumentException(
                        "count " + count + " is below min " + min);
            }
            if (countVarintBytes == 0 && count != min) {
                throw new IllegalArgumentException(
                        "fixed-length ByteArray requires count == " + min + ", got " + count);
            }
            remaining = count;
            startConsumed = true;
        }
        if (!countWritten) {
            if (countVarintBytes == 0) {
                countWritten = true;
            } else {
                if (vli == null) {
                    vli = new VariableLengthIntegerEncoder(countVarintBytes);
                    long delta = count - min;
                    vliSource = singletonSource(
                            new Event.IntegerScalar(delta, delta == 0 ? 0 : 1));
                }
                if (!vli.generate(vliSource, sink)) {
                    return false;
                }
                countWritten = true;
            }
        }
        while (remaining > 0) {
            if (pendingChunk == null) {
                if (events.availableEvents() <= 0) {
                    return false;
                }
                Event.Chunk chunk = (Event.Chunk) events.read();
                if (chunk.bytes().length > remaining) {
                    throw new IllegalArgumentException(
                            "chunk of " + chunk.bytes().length
                                    + " bytes overflows remaining " + remaining);
                }
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
            remaining -= pendingChunk.length;
            pendingChunk = null;
        }
        if (!endConsumed) {
            if (events.availableEvents() <= 0) {
                return false;
            }
            events.read(); // EndContainer
            endConsumed = true;
        }
        return true;
    }

    private static EventSource singletonSource(Event event) {
        return new EventSource() {
            private int remaining = 1;

            @Override
            public int availableEvents() {
                return remaining;
            }

            @Override
            public Event read() {
                if (remaining == 0) {
                    throw new IllegalStateException("singleton source exhausted");
                }
                remaining--;
                return event;
            }
        };
    }
}
