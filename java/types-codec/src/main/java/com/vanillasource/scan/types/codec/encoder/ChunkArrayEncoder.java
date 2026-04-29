package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;

/**
 * Chunked array encode path: the {@code Array}-with-1-byte-primitive-element
 * branch of {@link com.vanillasource.scan.types.codec.type.Array}'s dispatch.
 * Composes the array's phases via {@link ValueEncoder#andThen}: consume
 * {@code StartContainer(ARRAY, count)} (validating the count against
 * {@code min} and the fixed-length contract), write the count (omitted when
 * {@code countVarintBytes == 0}, otherwise {@code count - min} as a
 * {@code VariableLengthInteger(countVarintBytes)}), drain {@code Chunk} events
 * whose total byte length must equal the declared count straight to the sink,
 * then consume {@code EndContainer(ARRAY)}.
 */
public final class ChunkArrayEncoder implements ValueEncoder {
    private final ValueEncoder pipeline;

    public ChunkArrayEncoder(int min, int countVarintBytes) {
        long[] countHolder = new long[1];
        pipeline = captureStartContainer(countHolder, min, countVarintBytes)
                .andThen(writeCount(min, countVarintBytes, countHolder))
                .andThen(drainChunks(countHolder))
                .andThen(ValueEncoder.skipEvent());
    }

    @Override
    public boolean generate(EventSource events, BitSink sink) {
        return pipeline.generate(events, sink);
    }

    private static ValueEncoder captureStartContainer(long[] target, int min, int countVarintBytes) {
        return (events, sink) -> {
            if (events.availableEvents() <= 0) {
                return false;
            }
            long count = ((Event.StartContainer) events.read()).count();
            if (count < min) {
                throw new IllegalArgumentException(
                        "count " + count + " is below min " + min);
            }
            if (countVarintBytes == 0 && count != min) {
                throw new IllegalArgumentException(
                        "fixed-length array requires count == " + min + ", got " + count);
            }
            target[0] = count;
            return true;
        };
    }

    private static ValueEncoder writeCount(int min, int countVarintBytes, long[] countHolder) {
        if (countVarintBytes == 0) {
            return (events, sink) -> true;
        }
        return new ValueEncoder() {
            private ValueEncoder vli;
            private EventSource fixedSource;

            @Override
            public boolean generate(EventSource events, BitSink sink) {
                if (vli == null) {
                    vli = new VariableLengthIntegerEncoder(countVarintBytes);
                    long delta = countHolder[0] - min;
                    fixedSource = singletonSource(
                            new Event.IntegerScalar(delta, delta == 0 ? 0 : 1));
                }
                return vli.generate(fixedSource, sink);
            }
        };
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

    private static ValueEncoder drainChunks(long[] countHolder) {
        return new ValueEncoder() {
            private long remaining = -1;
            private byte[] pendingChunk;
            private int pendingOffset = 0;

            @Override
            public boolean generate(EventSource events, BitSink sink) {
                if (remaining < 0) {
                    remaining = countHolder[0];
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
                return true;
            }
        };
    }
}
