package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.ValueDecoder;

/**
 * Chunked stream decode path: the {@code Stream}-with-1-byte-primitive-element
 * branch of {@link com.vanillasource.scan.types.codec.type.Stream}'s dispatch.
 * Composes the stream's phases via {@link ValueDecoder#andThen}: emit
 * {@code StartStream} once on entry, then drain available bytes as
 * {@code Chunk(bytes)} events for as long as bytes are present and the sink
 * has room. The drain phase never completes — a byte stream has no terminator
 * on the wire, so completion is declared externally by the transport.
 */
public final class ChunkStreamDecoder implements ValueDecoder {
    private final ValueDecoder pipeline =
            ValueDecoder.writeEvent(new Event.StartStream()).andThen(drainBytes());

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        return pipeline.parse(bits, sink);
    }

    private static ValueDecoder drainBytes() {
        return (bits, sink) -> {
            while (true) {
                if (sink.writableEvents() <= 0) {
                    return false;
                }
                int avail = bits.availableBytes();
                if (avail <= 0) {
                    return false;
                }
                byte[] chunk = new byte[avail];
                int read = bits.readBytes(chunk, 0, avail);
                if (read <= 0) {
                    return false;
                }
                if (read < avail) {
                    byte[] shrunk = new byte[read];
                    System.arraycopy(chunk, 0, shrunk, 0, read);
                    chunk = shrunk;
                }
                sink.write(new Event.Chunk(chunk));
            }
        };
    }
}
