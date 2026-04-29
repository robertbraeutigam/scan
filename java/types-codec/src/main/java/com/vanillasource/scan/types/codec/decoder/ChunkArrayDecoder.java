package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.ContainerKind;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.ValueDecoder;

/**
 * Chunked array decode path: the {@code Array}-with-1-byte-primitive-element
 * branch of {@link com.vanillasource.scan.types.codec.type.Array}'s dispatch.
 * Composes the array's phases via {@link ValueDecoder#andThen}: read (or skip)
 * the count, emit {@code StartContainer(ARRAY, count)}, drain the declared
 * payload as opportunistically-sized {@code Chunk(bytes)} events, then emit
 * {@code EndContainer(ARRAY)}. Batched (chunked) delivery is the
 * implementation choice for runs of 1-byte-primitive elements; see the spec's
 * <i>Incremental Consumption</i>.
 */
public final class ChunkArrayDecoder implements ValueDecoder {
    private final ValueDecoder pipeline;

    public ChunkArrayDecoder(int min, int countVarintBytes) {
        long[] countHolder = new long[1];
        pipeline = captureCount(min, countVarintBytes, countHolder)
                .andThen(ValueDecoder.writeEvent(() -> new Event.StartContainer(ContainerKind.ARRAY, countHolder[0])))
                .andThen(drainChunks(countHolder))
                .andThen(ValueDecoder.writeEvent(new Event.EndContainer(ContainerKind.ARRAY)));
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        return pipeline.parse(bits, sink);
    }

    private static ValueDecoder captureCount(int min, int countVarintBytes, long[] target) {
        if (countVarintBytes == 0) {
            return (bits, sink) -> {
                target[0] = min;
                return true;
            };
        }
        ValueDecoder vli = new VariableLengthIntegerDecoder(countVarintBytes);
        EventSink capture = new EventSink() {
            @Override
            public int writableEvents() {
                return Integer.MAX_VALUE;
            }

            @Override
            public void write(Event event) {
                if (event instanceof Event.IntegerScalar is) {
                    target[0] = is.value() + min;
                }
            }
        };
        return (bits, sink) -> vli.parse(bits, capture);
    }

    private static ValueDecoder drainChunks(long[] countHolder) {
        return new ValueDecoder() {
            private long remaining = -1;

            @Override
            public boolean parse(BitSource bits, EventSink sink) {
                if (remaining < 0) {
                    remaining = countHolder[0];
                }
                while (remaining > 0) {
                    if (sink.writableEvents() <= 0) {
                        return false;
                    }
                    int avail = bits.availableBytes();
                    if (avail <= 0) {
                        return false;
                    }
                    int take = (int) Math.min(avail, remaining);
                    byte[] chunk = new byte[take];
                    int read = bits.readBytes(chunk, 0, take);
                    if (read <= 0) {
                        return false;
                    }
                    if (read < take) {
                        byte[] shrunk = new byte[read];
                        System.arraycopy(chunk, 0, shrunk, 0, read);
                        chunk = shrunk;
                    }
                    sink.write(new Event.Chunk(chunk));
                    remaining -= read;
                }
                return true;
            }
        };
    }
}
