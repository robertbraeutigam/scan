package com.vanillasource.scan.types.codec;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

/**
 * Writes a single value of a given root {@link Type} to a byte array, in lockstep
 * with the type's structure (TYPES.md §"Values Binary Representation"). Iteration 3
 * supports primitive root types only.
 *
 * <p>For {@link Type.Unit} the encoder is "complete" at construction — the wire
 * form is zero bytes and the caller may call {@link #toByteArray()} immediately
 * without invoking any write method.
 */
public final class ValueEncoder {
    private final Type rootType;
    private final ByteArrayOutputStream out;
    private boolean complete;

    public ValueEncoder(Type rootType) {
        this.rootType = Objects.requireNonNull(rootType, "rootType");
        this.out = new ByteArrayOutputStream();
        this.complete = (rootType instanceof Type.Unit);
    }

    public void writeInteger(long value) {
        ensureWritable();
        if (rootType instanceof Type.UnsignedInteger u) {
            writeUnsignedBigEndian(value, u.byteSize(), false);
        } else if (rootType instanceof Type.SignedInteger s) {
            writeSignedBigEndian(value, s.byteSize());
        } else if (rootType instanceof Type.VariableLengthInteger v) {
            writeVarInt(value, v.maxBytes());
        } else {
            throw new IllegalStateException("expected integer type at this position, got " + rootType);
        }
        complete = true;
    }

    public void writeFloat(double value) {
        ensureWritable();
        if (!(rootType instanceof Type.FloatingPoint f)) {
            throw new IllegalStateException("expected FloatingPoint type at this position, got " + rootType);
        }
        if (f.byteSize() == 4) {
            int bits = Float.floatToRawIntBits((float) value);
            writeUnsignedBigEndian(bits & 0xFFFFFFFFL, 4, true);
        } else {
            long bits = Double.doubleToRawLongBits(value);
            writeUnsignedBigEndian(bits, 8, true);
        }
        complete = true;
    }

    public boolean isComplete() {
        return complete;
    }

    public byte[] toByteArray() {
        if (!complete) {
            throw new IllegalStateException("encoder is incomplete");
        }
        return out.toByteArray();
    }

    private void ensureWritable() {
        if (complete) {
            throw new IllegalStateException("encoder is complete");
        }
    }

    private void writeUnsignedBigEndian(long value, int byteSize, boolean skipRangeCheck) {
        if (!skipRangeCheck && byteSize < 8) {
            long max = (1L << (byteSize * 8)) - 1;
            if (value < 0 || value > max) {
                throw new IllegalArgumentException(
                        "value " + value + " does not fit in " + byteSize + " unsigned bytes");
            }
        }
        for (int i = byteSize - 1; i >= 0; i--) {
            out.write((int) ((value >>> (i * 8)) & 0xFF));
        }
    }

    private void writeSignedBigEndian(long value, int byteSize) {
        if (byteSize < 8) {
            long max = (1L << (byteSize * 8 - 1)) - 1;
            long min = -(1L << (byteSize * 8 - 1));
            if (value < min || value > max) {
                throw new IllegalArgumentException(
                        "value " + value + " does not fit in " + byteSize + " signed bytes");
            }
        }
        for (int i = byteSize - 1; i >= 0; i--) {
            out.write((int) ((value >>> (i * 8)) & 0xFF));
        }
    }

    private void writeVarInt(long value, int maxN) {
        if (value < 0) {
            throw new IllegalArgumentException("VariableLengthInteger value must be non-negative: " + value);
        }
        int bitsNeeded = (value == 0) ? 1 : (64 - Long.numberOfLeadingZeros(value));
        int k = -1;
        for (int trial = 1; trial <= maxN; trial++) {
            int capacity = (trial < maxN) ? (7 * trial) : (7 * (trial - 1) + 8);
            if (capacity >= bitsNeeded) {
                k = trial;
                break;
            }
        }
        if (k < 0) {
            throw new IllegalArgumentException(
                    "value " + value + " does not fit in VariableLengthInteger(" + maxN + ")");
        }
        int totalBits = (k < maxN) ? (7 * k) : (7 * (k - 1) + 8);
        for (int i = 0; i < k; i++) {
            int groupBits = (i < k - 1) ? 7 : ((k < maxN) ? 7 : 8);
            int bitsAfter = totalBits - 7 * i - groupBits;
            int groupValue = (int) ((value >>> bitsAfter) & ((1L << groupBits) - 1));
            int byteValue = (i < k - 1) ? (0x80 | groupValue) : groupValue;
            out.write(byteValue);
        }
    }
}
