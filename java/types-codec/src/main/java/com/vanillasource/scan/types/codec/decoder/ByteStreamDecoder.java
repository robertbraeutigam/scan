package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.ContainerKind;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.ValueDecoder;

/**
 * Emits {@code StartContainer(BYTE_STREAM, 0)} once on entry, then drains
 * available bytes as {@code Chunk(bytes)} events for as long as bytes are
 * present and the sink has room. Always returns {@code false} from
 * {@link #parse}: a byte stream has no terminator on the wire, so completion
 * is declared externally by the transport. The {@code count} field of the
 * Start event is meaningless for streams and is set to {@code 0}.
 */
public final class ByteStreamDecoder implements ValueDecoder {
    private boolean startEmitted = false;

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        if (!startEmitted) {
            if (sink.writableEvents() <= 0) {
                return false;
            }
            sink.put(new Event.StartContainer(ContainerKind.BYTE_STREAM, 0L));
            startEmitted = true;
        }
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
            sink.put(new Event.Chunk(chunk));
        }
    }
}
