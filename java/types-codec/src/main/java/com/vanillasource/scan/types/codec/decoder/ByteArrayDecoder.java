package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.BitSource;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.ContainerKind;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.ValueDecoder;

/**
 * Reads (or skips) the count, emits {@code StartContainer(ARRAY, count)},
 * then drains the byte payload as a sequence of {@code Chunk(bytes)} events
 * sized opportunistically — at each call, one chunk is emitted of
 * {@code min(availableBytes, remainingPayload)}. After all bytes are read,
 * emits {@code EndContainer(ARRAY)}. The chunked-form delivery (rather than
 * StartItem/EndItem pairs) follows from the element type being a 1-byte
 * primitive; see the spec's <i>Per-Type Event Sequences</i>.
 */
public final class ByteArrayDecoder implements ValueDecoder {
    private final int min;
    private final int countVarintBytes;
    private VariableLengthIntegerDecoder vli;
    private long count = -1;
    private long remaining = -1;
    private boolean startEmitted = false;
    private boolean endEmitted = false;

    public ByteArrayDecoder(int min, int countVarintBytes) {
        this.min = min;
        this.countVarintBytes = countVarintBytes;
    }

    @Override
    public boolean parse(BitSource bits, EventSink sink) {
        if (count < 0) {
            if (countVarintBytes == 0) {
                count = min;
                remaining = count;
            } else {
                if (vli == null) {
                    vli = new VariableLengthIntegerDecoder(countVarintBytes);
                }
                LongCapture capture = new LongCapture();
                if (!vli.parse(bits, capture)) {
                    return false;
                }
                count = capture.value + min;
                remaining = count;
            }
        }
        if (!startEmitted) {
            if (sink.writableEvents() <= 0) {
                return false;
            }
            sink.put(new Event.StartContainer(ContainerKind.ARRAY, count));
            startEmitted = true;
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
            sink.put(new Event.Chunk(chunk));
            remaining -= read;
        }
        if (!endEmitted) {
            if (sink.writableEvents() <= 0) {
                return false;
            }
            sink.put(new Event.EndContainer(ContainerKind.ARRAY));
            endEmitted = true;
        }
        return true;
    }

    private static final class LongCapture implements EventSink {
        long value;

        @Override
        public int writableEvents() {
            return Integer.MAX_VALUE;
        }

        @Override
        public void put(Event event) {
            if (event instanceof Event.IntegerScalar is) {
                value = is.value();
            }
        }
    }
}
