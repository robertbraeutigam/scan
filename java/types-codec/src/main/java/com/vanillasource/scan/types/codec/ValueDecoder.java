package com.vanillasource.scan.types.codec;

import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Push-style decoder exposed as an {@link OutputStream}: callers write the wire
 * bytes in, the decoder emits {@link DecodingEvent}s to its
 * {@link DecodingEventHandler} as decoding progresses. Iteration 4 supports
 * primitives, {@link Type.Struct}, and {@link Type.Union}; aggregates and streams
 * arrive in later iterations.
 *
 * <p>State persists across writes: a half-read varint, a half-emitted struct, and
 * a partially-consumed bit byte all survive a return from {@code write}. Bytes are
 * consumed only as full events become available, so writing a single byte at a
 * time produces the same event sequence as one bulk write.
 *
 * <p>{@link #close()} signals end-of-stream and throws if the value is truncated
 * (decoding has not reached the end of the root type).
 */
public final class ValueDecoder extends OutputStream {
    private final Type rootType;
    private final DecodingEventHandler handler;
    private final BitReader bits;
    private final Deque<Frame> stack;
    private boolean complete;

    public ValueDecoder(Type rootType, DecodingEventHandler handler) {
        this.rootType = Objects.requireNonNull(rootType, "rootType");
        this.handler = Objects.requireNonNull(handler, "handler");
        this.bits = new BitReader();
        this.stack = new ArrayDeque<>();
        descendInto(rootType);
        tryProgress();
    }

    @Override
    public void write(int b) {
        write(new byte[] { (byte) b }, 0, 1);
    }

    @Override
    public void write(byte[] bytes) {
        write(bytes, 0, bytes.length);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) {
        Objects.requireNonNull(bytes, "bytes");
        if (length == 0) {
            return;
        }
        if (complete) {
            throw new IllegalStateException("decoder is complete; no more bytes accepted");
        }
        bits.feed(bytes, offset, length);
        tryProgress();
    }

    @Override
    public void flush() {
        // Decoder has no internal output buffer; nothing to flush.
    }

    /**
     * Signals end-of-stream. Throws {@link IllegalStateException} if decoding has
     * not yet reached the end of the root value (the wire bytes were truncated).
     */
    @Override
    public void close() {
        if (!complete) {
            throw new IllegalStateException("decoder closed before value completed");
        }
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
        if (t instanceof Type.Array a) {
            TypeLayout.requireArrayElementSupported(a.element());
            stack.push(new ArrayFrame(a));
            return;
        }
        if (t instanceof Type.Set || t instanceof Type.Stream) {
            throw new UnsupportedOperationException(
                    "type not supported in iteration 5: " + t);
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
            if (top instanceof ArrayFrame af) {
                handler.onEvent(new DecodingEvent.EndItem());
                af.itemStarted = false;
                af.itemsCompleted++;
                bits.closeBitByte();
                return;
            }
            throw new IllegalStateException("unexpected frame at child-complete: " + top);
        }
        complete = true;
    }

    private void tryProgress() {
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
                int k = u.union.discriminatorBits();
                int j;
                if (k == 0) {
                    j = 0;
                } else {
                    if (!bits.hasBits(k)) {
                        return;
                    }
                    j = (int) bits.readBits(k);
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

            if (top instanceof ArrayFrame af) {
                if (!progressArray(af)) {
                    return;
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

    private boolean progressArray(ArrayFrame af) {
        if (!af.entered) {
            bits.closeBitByte();
            handler.onEvent(new DecodingEvent.StartContainer(DecodingEvent.ContainerKind.ARRAY));
            af.entered = true;
            af.primitiveItems = isPrimitiveItem(af.array.element());
            if (af.primitiveItems) {
                af.itemByteSize = primitiveByteSize(af.array.element());
            }
        }
        if (!af.countRead) {
            SizeConstraint sc = af.array.size();
            if (sc.isFixed()) {
                af.declaredCount = ((SizeConstraint.Range) sc).min();
                af.countRead = true;
            } else {
                if (af.countDecoder == null) {
                    af.countDecoder = new VarIntDecoder(sc.countVarintSize());
                }
                while (bits.available() > 0) {
                    if (af.countDecoder.feed(bits.readOneByte())) {
                        af.declaredCount = (int) (af.countDecoder.value() + sc.lowerBound());
                        af.countRead = true;
                        break;
                    }
                }
                if (!af.countRead) {
                    return false;
                }
            }
            if (af.primitiveItems) {
                af.bytesRemaining = (long) af.declaredCount * af.itemByteSize;
            }
        }
        if (af.primitiveItems) {
            if (af.bytesRemaining > 0) {
                if (bits.available() == 0) {
                    return false;
                }
                int n = (int) Math.min(bits.available(), af.bytesRemaining);
                byte[] chunk = new byte[n];
                bits.readBytes(chunk, 0, n);
                handler.onEvent(new DecodingEvent.Chunk(chunk));
                af.bytesRemaining -= n;
                if (af.bytesRemaining > 0) {
                    return false;
                }
            }
            bits.closeBitByte();
            handler.onEvent(new DecodingEvent.EndContainer(DecodingEvent.ContainerKind.ARRAY));
            stack.pop();
            childCompleted();
            return true;
        }
        if (af.itemsCompleted >= af.declaredCount) {
            bits.closeBitByte();
            handler.onEvent(new DecodingEvent.EndContainer(DecodingEvent.ContainerKind.ARRAY));
            stack.pop();
            childCompleted();
            return true;
        }
        if (!af.itemStarted) {
            handler.onEvent(new DecodingEvent.StartItem());
            af.itemStarted = true;
            descendInto(af.array.element());
            return true;
        }
        return false;
    }

    private static boolean isPrimitiveItem(Type t) {
        return t instanceof Type.Unit
                || t instanceof Type.UnsignedInteger
                || t instanceof Type.SignedInteger
                || t instanceof Type.FloatingPoint
                || t instanceof Type.VariableLengthInteger;
    }

    private static int primitiveByteSize(Type t) {
        if (t instanceof Type.Unit) {
            return 0;
        }
        if (t instanceof Type.UnsignedInteger u) {
            return u.byteSize();
        }
        if (t instanceof Type.SignedInteger s) {
            return s.byteSize();
        }
        if (t instanceof Type.FloatingPoint f) {
            return f.byteSize();
        }
        throw new IllegalStateException("not a fixed-byte primitive: " + t);
    }

    private boolean tryReadPrimitive(PrimitiveFrame p) {
        if (p.type instanceof Type.UnsignedInteger u) {
            int n = u.byteSize();
            if (!bits.hasBytes(n)) {
                return false;
            }
            handler.onEvent(new DecodingEvent.IntegerScalar(readUnsignedBigEndian(n)));
            return true;
        }
        if (p.type instanceof Type.SignedInteger s) {
            int n = s.byteSize();
            if (!bits.hasBytes(n)) {
                return false;
            }
            handler.onEvent(new DecodingEvent.IntegerScalar(readSignedBigEndian(n)));
            return true;
        }
        if (p.type instanceof Type.FloatingPoint f) {
            int n = f.byteSize();
            if (!bits.hasBytes(n)) {
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
            if (p.varint == null) {
                p.varint = new VarIntDecoder(v.maxBytes());
            }
            while (bits.available() > 0) {
                if (p.varint.feed(bits.readOneByte())) {
                    handler.onEvent(new DecodingEvent.IntegerScalar(p.varint.value()));
                    return true;
                }
            }
            return false;
        }
        throw new IllegalStateException("unsupported primitive: " + p.type);
    }

    private long readUnsignedBigEndian(int n) {
        long result = 0L;
        for (int i = 0; i < n; i++) {
            result = (result << 8) | bits.readOneByte();
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
        VarIntDecoder varint;

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

    private static final class ArrayFrame implements Frame {
        final Type.Array array;
        boolean entered;
        boolean countRead;
        boolean primitiveItems;
        int itemByteSize;
        int declaredCount;
        int itemsCompleted;
        boolean itemStarted;
        long bytesRemaining;
        VarIntDecoder countDecoder;

        ArrayFrame(Type.Array array) {
            this.array = array;
        }
    }
}
