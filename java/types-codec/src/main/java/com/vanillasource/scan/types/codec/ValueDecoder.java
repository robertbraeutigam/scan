package com.vanillasource.scan.types.codec;

import java.util.Objects;

/**
 * Push-style decoder. Constructed with a root {@link Type} and a
 * {@link DecodingEventHandler}; {@link #feed} accepts bytes as they arrive on the
 * wire and emits events as decoding progresses. Iteration 3 supports primitive
 * root types only.
 *
 * <p>For {@link Type.Unit} the {@link DecodingEvent.UnitScalar} event is fired
 * during construction since the wire form is zero bytes.
 */
public final class ValueDecoder {
    private final Type rootType;
    private final DecodingEventHandler handler;
    private final ByteAccumulator buffer;
    private boolean complete;

    private long varintAccumulator;
    private int varintBytesRead;

    public ValueDecoder(Type rootType, DecodingEventHandler handler) {
        this.rootType = Objects.requireNonNull(rootType, "rootType");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.buffer = new ByteAccumulator();
        this.complete = false;
        this.varintAccumulator = 0L;
        this.varintBytesRead = 0;
        tryProgress();
    }

    public void feed(byte[] bytes) {
        feed(bytes, 0, bytes.length);
    }

    public void feed(byte[] bytes, int offset, int length) {
        if (complete) {
            throw new IllegalStateException("decoder is complete; no more bytes accepted");
        }
        Objects.requireNonNull(bytes, "bytes");
        buffer.append(bytes, offset, length);
        tryProgress();
    }

    public boolean isComplete() {
        return complete;
    }

    private void tryProgress() {
        if (complete) {
            return;
        }
        if (rootType instanceof Type.Unit) {
            emit(new DecodingEvent.UnitScalar());
            return;
        }
        if (rootType instanceof Type.UnsignedInteger u) {
            int n = u.byteSize();
            if (buffer.available() >= n) {
                long value = readUnsignedBigEndian(n);
                emit(new DecodingEvent.IntegerScalar(value));
            }
            return;
        }
        if (rootType instanceof Type.SignedInteger s) {
            int n = s.byteSize();
            if (buffer.available() >= n) {
                long value = readSignedBigEndian(n);
                emit(new DecodingEvent.IntegerScalar(value));
            }
            return;
        }
        if (rootType instanceof Type.FloatingPoint f) {
            int n = f.byteSize();
            if (buffer.available() >= n) {
                long bits = readUnsignedBigEndian(n);
                double value = (n == 4)
                        ? Float.intBitsToFloat((int) bits)
                        : Double.longBitsToDouble(bits);
                emit(new DecodingEvent.FloatingPointScalar(value));
            }
            return;
        }
        if (rootType instanceof Type.VariableLengthInteger v) {
            int maxN = v.maxBytes();
            while (!complete && buffer.available() > 0) {
                int b = buffer.readOne() & 0xFF;
                varintBytesRead++;
                if (varintBytesRead == maxN) {
                    varintAccumulator = (varintAccumulator << 8) | b;
                    emit(new DecodingEvent.IntegerScalar(varintAccumulator));
                } else if ((b & 0x80) == 0) {
                    varintAccumulator = (varintAccumulator << 7) | b;
                    emit(new DecodingEvent.IntegerScalar(varintAccumulator));
                } else {
                    varintAccumulator = (varintAccumulator << 7) | (b & 0x7F);
                }
            }
            return;
        }
        throw new UnsupportedOperationException("type not supported in this iteration: " + rootType);
    }

    private void emit(DecodingEvent event) {
        complete = true;
        handler.onEvent(event);
    }

    private long readUnsignedBigEndian(int n) {
        long result = 0L;
        for (int i = 0; i < n; i++) {
            result = (result << 8) | (buffer.readOne() & 0xFF);
        }
        return result;
    }

    private long readSignedBigEndian(int n) {
        long unsigned = readUnsignedBigEndian(n);
        if (n < 8) {
            long signBit = 1L << (n * 8 - 1);
            if ((unsigned & signBit) != 0) {
                unsigned |= -1L << (n * 8);
            }
        }
        return unsigned;
    }
}
