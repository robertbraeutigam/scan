package com.vanillasource.scan.types.codec;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Push-style decoder. Constructed with a root {@link Type} and a
 * {@link DecodingEventHandler}; {@link #feed} accepts bytes as they arrive on the
 * wire and emits events as decoding progresses. Iteration 4 supports primitives,
 * {@link Type.Struct}, and {@link Type.Union}; aggregates and streams arrive in
 * later iterations.
 *
 * <p>State persists across feeds: a half-read varint, a half-emitted struct, and a
 * partially-consumed bit byte all survive a return from {@code feed}. Bytes are
 * consumed only as full events become available, so calling {@code feed} with a
 * single byte at a time produces the same event sequence as one bulk feed.
 */
public final class ValueDecoder {
    private final Type rootType;
    private final DecodingEventHandler handler;
    private final ByteAccumulator buffer;
    private final Deque<Frame> stack;
    private boolean complete;

    private boolean activeBit;
    private int activeBitByte;
    private int bitsUsed;

    public ValueDecoder(Type rootType, DecodingEventHandler handler) {
        this.rootType = Objects.requireNonNull(rootType, "rootType");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.buffer = new ByteAccumulator();
        this.stack = new ArrayDeque<>();
        descendInto(rootType);
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

    private void descendInto(Type t) {
        if (t instanceof Type.Unit) {
            handler.onEvent(new DecodingEvent.UnitScalar());
            childCompleted();
            return;
        }
        if (t instanceof Type.Struct s) {
            if (s.fields().isEmpty()) {
                childCompleted();
                return;
            }
            stack.push(new FieldsFrame(s.fields()));
            return;
        }
        if (t instanceof Type.Union u) {
            stack.push(new UnionFrame(u));
            return;
        }
        stack.push(new PrimitiveFrame(t));
    }

    private void childCompleted() {
        while (!stack.isEmpty()) {
            Frame top = stack.peek();
            if (top instanceof FieldsFrame f) {
                handler.onEvent(new DecodingEvent.EndField(f.fieldIndex));
                f.startEmitted = false;
                f.fieldIndex++;
                if (f.fieldIndex < f.fields.size()) {
                    return;
                }
                stack.pop();
                continue;
            }
            throw new IllegalStateException("unexpected frame at child-complete: " + top);
        }
        complete = true;
    }

    private void tryProgress() {
        outer:
        while (!complete && !stack.isEmpty()) {
            Frame top = stack.peek();

            if (top instanceof FieldsFrame f) {
                if (!f.startEmitted) {
                    handler.onEvent(new DecodingEvent.StartField(f.fieldIndex));
                    f.startEmitted = true;
                    descendInto(f.fields.get(f.fieldIndex).type());
                    continue;
                }
                return;
            }

            if (top instanceof UnionFrame u) {
                int n = u.union.constructors().size();
                int k = ValueEncoder.ceilLog2(n);
                int j;
                if (k == 0) {
                    j = 0;
                } else {
                    if (!hasBits(k)) {
                        return;
                    }
                    j = (int) readBits(k);
                    if (j >= n) {
                        throw new IllegalStateException(
                                "invalid discriminator " + j + " for union with " + n + " constructors");
                    }
                }
                handler.onEvent(new DecodingEvent.Constructor(j));
                stack.pop();
                Type.Constructor ctor = u.union.constructors().get(j);
                if (ctor.fields().isEmpty()) {
                    childCompleted();
                } else {
                    stack.push(new FieldsFrame(ctor.fields()));
                }
                continue;
            }

            if (top instanceof PrimitiveFrame p) {
                if (!tryReadPrimitive(p)) {
                    return;
                }
                stack.pop();
                childCompleted();
                continue;
            }

            throw new IllegalStateException("unknown frame: " + top);
        }
    }

    private boolean tryReadPrimitive(PrimitiveFrame p) {
        if (p.type instanceof Type.UnsignedInteger u) {
            int n = u.byteSize();
            if (buffer.available() < n) {
                return false;
            }
            handler.onEvent(new DecodingEvent.IntegerScalar(readUnsignedBigEndian(n)));
            return true;
        }
        if (p.type instanceof Type.SignedInteger s) {
            int n = s.byteSize();
            if (buffer.available() < n) {
                return false;
            }
            handler.onEvent(new DecodingEvent.IntegerScalar(readSignedBigEndian(n)));
            return true;
        }
        if (p.type instanceof Type.FloatingPoint f) {
            int n = f.byteSize();
            if (buffer.available() < n) {
                return false;
            }
            long bb = readUnsignedBigEndian(n);
            double v = (n == 4)
                    ? Float.intBitsToFloat((int) bb)
                    : Double.longBitsToDouble(bb);
            handler.onEvent(new DecodingEvent.FloatingPointScalar(v));
            return true;
        }
        if (p.type instanceof Type.VariableLengthInteger v) {
            int maxN = v.maxBytes();
            while (buffer.available() > 0) {
                int b = buffer.readOne() & 0xFF;
                p.varintBytesRead++;
                if (p.varintBytesRead == maxN) {
                    p.varintAccumulator = (p.varintAccumulator << 8) | b;
                    handler.onEvent(new DecodingEvent.IntegerScalar(p.varintAccumulator));
                    return true;
                } else if ((b & 0x80) == 0) {
                    p.varintAccumulator = (p.varintAccumulator << 7) | b;
                    handler.onEvent(new DecodingEvent.IntegerScalar(p.varintAccumulator));
                    return true;
                } else {
                    p.varintAccumulator = (p.varintAccumulator << 7) | (b & 0x7F);
                }
            }
            return false;
        }
        throw new IllegalStateException("unsupported primitive: " + p.type);
    }

    private boolean hasBits(int k) {
        int free = activeBit ? (8 - bitsUsed) : 0;
        if (free >= k) {
            return true;
        }
        int needed = (k - free + 7) / 8;
        return buffer.available() >= needed;
    }

    private long readBits(int k) {
        long result = 0L;
        int remaining = k;
        while (remaining > 0) {
            int free = activeBit ? (8 - bitsUsed) : 0;
            int groupSize;
            if (free > 0) {
                groupSize = Math.min(remaining, free);
            } else {
                activeBitByte = buffer.readOne() & 0xFF;
                bitsUsed = 0;
                activeBit = true;
                free = 8;
                groupSize = Math.min(remaining, free);
            }
            int shift = (8 - bitsUsed) - groupSize;
            long bitsValue = (activeBitByte >>> shift) & ((1L << groupSize) - 1);
            result = (result << groupSize) | bitsValue;
            bitsUsed += groupSize;
            if (bitsUsed == 8) {
                activeBit = false;
                bitsUsed = 0;
            }
            remaining -= groupSize;
        }
        return result;
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

    private sealed interface Frame {}

    private static final class PrimitiveFrame implements Frame {
        final Type type;
        long varintAccumulator;
        int varintBytesRead;

        PrimitiveFrame(Type type) {
            this.type = type;
        }
    }

    private static final class UnionFrame implements Frame {
        final Type.Union union;

        UnionFrame(Type.Union union) {
            this.union = union;
        }
    }

    private static final class FieldsFrame implements Frame {
        final List<Type.Field> fields;
        int fieldIndex;
        boolean startEmitted;

        FieldsFrame(List<Type.Field> fields) {
            this.fields = fields;
            this.fieldIndex = 0;
            this.startEmitted = false;
        }
    }
}
