package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.ValueDecoder;

/**
 * Composes the byte-stream's phases via {@link ValueDecoder#andThen}: emit
 * {@code StartStream} once on entry, then drain available bytes as
 * {@code Chunk(bytes)} events for as long as bytes are present and the sink
 * has room. The drain phase never completes — a byte stream has no terminator
 * on the wire, so completion is declared externally by the transport.
 */
public final class ByteStreamDecoder implements ValueDecoder {
    private final ValueDecoder pipeline = emitStartStream().andThen(drainBytes());

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        return pipeline.parse(bits, sink);
    }

    private static ValueDecoder emitStartStream() {
        return (bits, sink) -> {
            if (sink.writableEvents() <= 0) {
                return false;
            }
            sink.write(new Event.StartStream());
            return true;
        };
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
