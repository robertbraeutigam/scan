package com.vanillasource.scan.types.codec;

import java.util.List;

/**
 * One stack entry of a {@link ValueDecoder}'s walk through a {@link Type} tree.
 * {@code step} drains the buffered bits/bytes as far as the current position
 * allows; {@code onChildCompleted} cascades from a finished child up through
 * its enclosing aggregates.
 */
sealed interface DecoderFrame {

    /**
     * Try to make progress with the bits currently available. The frame must end
     * its turn by either: pushing a child (new top runs next), popping itself and
     * notifying its parent (via {@link DecoderContext#childCompleted()}), or
     * calling {@link DecoderContext#waitForInput()} when it cannot proceed.
     */
    void step(DecoderContext ctx);

    /**
     * Called when a child frame has just finished. Returns {@code true} if this
     * frame is now also complete (the cascade pops it and notifies its parent).
     */
    boolean onChildCompleted(DecoderContext ctx);

    /** Walks a list of struct or constructor fields, emitting Start/EndField around each. */
    final class FieldsFrame implements DecoderFrame {
        private final List<Type.Field> fields;
        private int fieldIndex;

        FieldsFrame(List<Type.Field> fields) {
            this.fields = fields;
        }

        @Override
        public void step(DecoderContext ctx) {
            ctx.emit(new DecodingEvent.StartField(fieldIndex));
            fields.get(fieldIndex).type().startDecode(ctx);
        }

        @Override
        public boolean onChildCompleted(DecoderContext ctx) {
            ctx.emit(new DecodingEvent.EndField(fieldIndex));
            fieldIndex++;
            return fieldIndex >= fields.size();
        }
    }

    /** Reads a union discriminator and replaces itself with the constructor's fields. */
    final class UnionFrame implements DecoderFrame {
        private final Type.Union union;

        UnionFrame(Type.Union union) {
            this.union = union;
        }

        @Override
        public void step(DecoderContext ctx) {
            int n = union.constructors().size();
            int k = union.discriminatorBits();
            int j;
            if (k == 0) {
                j = 0;
            } else {
                if (!ctx.bits().hasBits(k)) {
                    ctx.waitForInput();
                    return;
                }
                j = (int) ctx.bits().readBits(k);
                if (j >= n) {
                    throw new IllegalStateException(
                            "invalid discriminator " + j + " for union with " + n + " constructors");
                }
            }
            ctx.emit(new DecodingEvent.Constructor(j));
            ctx.pop();
            Type.Constructor ctor = union.constructors().get(j);
            if (ctor.fields().isEmpty()) {
                ctx.childCompleted();
            } else {
                ctx.push(new FieldsFrame(ctor.fields()));
            }
        }

        @Override
        public boolean onChildCompleted(DecoderContext ctx) {
            throw new IllegalStateException("union frame is popped before any child runs");
        }
    }

    /**
     * Drives an {@link Type.Array}: closes the bit byte, emits StartContainer, reads
     * the count (fixed or varint), then either chunk-emits raw bytes for fixed-byte
     * primitive items or descends per-item with Start/EndItem markers.
     */
    final class ArrayFrame implements DecoderFrame {
        private final Type.Array array;
        private boolean entered;
        private boolean countRead;
        private boolean primitiveItems;
        private int itemByteSize;
        private int declaredCount;
        private int itemsCompleted;
        private boolean itemStarted;
        private long bytesRemaining;
        private VarIntDecoder countDecoder;

        ArrayFrame(Type.Array array) {
            this.array = array;
        }

        @Override
        public void step(DecoderContext ctx) {
            BitReader bits = ctx.bits();
            if (!entered) {
                bits.closeBitByte();
                ctx.emit(new DecodingEvent.StartContainer(DecodingEvent.ContainerKind.ARRAY));
                entered = true;
                java.util.OptionalInt fixed = array.element().fixedPrimitiveByteSize();
                primitiveItems = fixed.isPresent();
                if (primitiveItems) {
                    itemByteSize = fixed.getAsInt();
                }
            }
            if (!countRead && !readCount(bits, ctx)) {
                return;
            }
            if (primitiveItems) {
                stepPrimitiveItems(bits, ctx);
            } else {
                stepComplexItems(ctx);
            }
        }

        private boolean readCount(BitReader bits, DecoderContext ctx) {
            SizeConstraint sc = array.size();
            if (sc.isFixed()) {
                declaredCount = sc.lowerBound();
                countRead = true;
            } else {
                if (countDecoder == null) {
                    countDecoder = new VarIntDecoder(sc.countVarintSize());
                }
                while (bits.available() > 0) {
                    if (countDecoder.feed(bits.readOneByte())) {
                        declaredCount = (int) (countDecoder.value() + sc.lowerBound());
                        countRead = true;
                        break;
                    }
                }
                if (!countRead) {
                    ctx.waitForInput();
                    return false;
                }
            }
            if (primitiveItems) {
                bytesRemaining = (long) declaredCount * itemByteSize;
            }
            return true;
        }

        private void stepPrimitiveItems(BitReader bits, DecoderContext ctx) {
            if (bytesRemaining > 0) {
                if (bits.available() == 0) {
                    ctx.waitForInput();
                    return;
                }
                int n = (int) Math.min(bits.available(), bytesRemaining);
                byte[] chunk = new byte[n];
                bits.readBytes(chunk, 0, n);
                ctx.emit(new DecodingEvent.Chunk(chunk));
                bytesRemaining -= n;
                if (bytesRemaining > 0) {
                    ctx.waitForInput();
                    return;
                }
            }
            bits.closeBitByte();
            ctx.emit(new DecodingEvent.EndContainer(DecodingEvent.ContainerKind.ARRAY));
            ctx.pop();
            ctx.childCompleted();
        }

        private void stepComplexItems(DecoderContext ctx) {
            if (itemsCompleted >= declaredCount) {
                ctx.bits().closeBitByte();
                ctx.emit(new DecodingEvent.EndContainer(DecodingEvent.ContainerKind.ARRAY));
                ctx.pop();
                ctx.childCompleted();
                return;
            }
            if (!itemStarted) {
                ctx.emit(new DecodingEvent.StartItem());
                itemStarted = true;
                array.element().startDecode(ctx);
            }
        }

        @Override
        public boolean onChildCompleted(DecoderContext ctx) {
            ctx.emit(new DecodingEvent.EndItem());
            itemStarted = false;
            itemsCompleted++;
            ctx.bits().closeBitByte();
            return false;
        }
    }

    /** Reads a fixed-width unsigned big-endian integer. */
    final class UnsignedIntegerFrame implements DecoderFrame {
        private final int byteSize;

        UnsignedIntegerFrame(int byteSize) {
            this.byteSize = byteSize;
        }

        @Override
        public void step(DecoderContext ctx) {
            BitReader bits = ctx.bits();
            if (!bits.hasBytes(byteSize)) {
                ctx.waitForInput();
                return;
            }
            ctx.emit(new DecodingEvent.IntegerScalar(readUnsignedBigEndian(bits, byteSize)));
            ctx.pop();
            ctx.childCompleted();
        }

        @Override
        public boolean onChildCompleted(DecoderContext ctx) {
            throw new IllegalStateException("primitive frame has no children");
        }
    }

    /** Reads a fixed-width signed big-endian integer with sign extension. */
    final class SignedIntegerFrame implements DecoderFrame {
        private final int byteSize;

        SignedIntegerFrame(int byteSize) {
            this.byteSize = byteSize;
        }

        @Override
        public void step(DecoderContext ctx) {
            BitReader bits = ctx.bits();
            if (!bits.hasBytes(byteSize)) {
                ctx.waitForInput();
                return;
            }
            long unsigned = readUnsignedBigEndian(bits, byteSize);
            if (byteSize < 8) {
                long signBit = 1L << (byteSize * 8 - 1);
                if ((unsigned & signBit) != 0) {
                    unsigned |= -1L << (byteSize * 8);
                }
            }
            ctx.emit(new DecodingEvent.IntegerScalar(unsigned));
            ctx.pop();
            ctx.childCompleted();
        }

        @Override
        public boolean onChildCompleted(DecoderContext ctx) {
            throw new IllegalStateException("primitive frame has no children");
        }
    }

    /** Reads a 32- or 64-bit IEEE 754 float. */
    final class FloatingPointFrame implements DecoderFrame {
        private final int byteSize;

        FloatingPointFrame(int byteSize) {
            this.byteSize = byteSize;
        }

        @Override
        public void step(DecoderContext ctx) {
            BitReader bits = ctx.bits();
            if (!bits.hasBytes(byteSize)) {
                ctx.waitForInput();
                return;
            }
            long bb = readUnsignedBigEndian(bits, byteSize);
            double v = (byteSize == 4)
                    ? Float.intBitsToFloat((int) bb)
                    : Double.longBitsToDouble(bb);
            ctx.emit(new DecodingEvent.FloatingPointScalar(v));
            ctx.pop();
            ctx.childCompleted();
        }

        @Override
        public boolean onChildCompleted(DecoderContext ctx) {
            throw new IllegalStateException("primitive frame has no children");
        }
    }

    /** Reads a {@link Type.VariableLengthInteger} via a stateful {@link VarIntDecoder}. */
    final class VariableLengthIntegerFrame implements DecoderFrame {
        private final VarIntDecoder varint;

        VariableLengthIntegerFrame(int maxBytes) {
            this.varint = new VarIntDecoder(maxBytes);
        }

        @Override
        public void step(DecoderContext ctx) {
            BitReader bits = ctx.bits();
            while (bits.available() > 0) {
                if (varint.feed(bits.readOneByte())) {
                    ctx.emit(new DecodingEvent.IntegerScalar(varint.value()));
                    ctx.pop();
                    ctx.childCompleted();
                    return;
                }
            }
            ctx.waitForInput();
        }

        @Override
        public boolean onChildCompleted(DecoderContext ctx) {
            throw new IllegalStateException("primitive frame has no children");
        }
    }

    private static long readUnsignedBigEndian(BitReader bits, int n) {
        long result = 0L;
        for (int i = 0; i < n; i++) {
            result = (result << 8) | bits.readOneByte();
        }
        return result;
    }
}
