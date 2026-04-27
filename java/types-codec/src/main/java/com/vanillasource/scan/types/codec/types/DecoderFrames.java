package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitReader;
import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.DecodingEvent;
import com.vanillasource.scan.types.codec.DecodingEventHandler;
import com.vanillasource.scan.types.codec.SizeConstraint;
import com.vanillasource.scan.types.codec.VarIntDecoder;

import java.util.List;
import java.util.OptionalInt;

/** Concrete {@link DecoderFrame} implementations for the type variants in this package. */
final class DecoderFrames {
    private DecoderFrames() {}

    /** One-shot frame for {@link Unit}: emits {@link DecodingEvent.UnitScalar} and completes. */
    static final class UnitFrame implements DecoderFrame {
        @Override
        public Result step(BitReader bits, DecodingEventHandler events) {
            events.onEvent(new DecodingEvent.UnitScalar());
            return new Result.Done();
        }

        @Override
        public Result onChildCompleted(BitReader bits, DecodingEventHandler events) {
            throw new IllegalStateException("primitive frame has no children");
        }
    }

    /** Walks a list of struct or constructor fields, emitting Start/EndField around each. */
    static final class FieldsFrame implements DecoderFrame {
        private final List<Field> fields;
        private int fieldIndex;

        FieldsFrame(List<Field> fields) {
            this.fields = fields;
        }

        @Override
        public Result step(BitReader bits, DecodingEventHandler events) {
            return advanceToNextField(events);
        }

        @Override
        public Result onChildCompleted(BitReader bits, DecodingEventHandler events) {
            events.onEvent(new DecodingEvent.EndField(fieldIndex));
            fieldIndex++;
            return advanceToNextField(events);
        }

        private Result advanceToNextField(DecodingEventHandler events) {
            while (fieldIndex < fields.size()) {
                events.onEvent(new DecodingEvent.StartField(fieldIndex));
                DecoderFrame childFrame = fields.get(fieldIndex).type().createDecodeFrame();
                if (childFrame != null) {
                    return new Result.Push(childFrame);
                }
                events.onEvent(new DecodingEvent.EndField(fieldIndex));
                fieldIndex++;
            }
            return new Result.Done();
        }
    }

    /** Reads a union discriminator and replaces itself with the constructor's fields. */
    static final class UnionFrame implements DecoderFrame {
        private final Union union;

        UnionFrame(Union union) {
            this.union = union;
        }

        @Override
        public Result step(BitReader bits, DecodingEventHandler events) {
            int n = union.constructors().size();
            int k = union.discriminatorBits();
            int j;
            if (k == 0) {
                j = 0;
            } else {
                if (!bits.hasBits(k)) {
                    return new Result.WaitForInput();
                }
                j = (int) bits.readBits(k);
                if (j >= n) {
                    throw new IllegalStateException(
                            "invalid discriminator " + j + " for union with " + n + " constructors");
                }
            }
            events.onEvent(new DecodingEvent.Constructor(j));
            Constructor ctor = union.constructors().get(j);
            if (ctor.fields().isEmpty()) {
                return new Result.Done();
            }
            return new Result.Replace(new FieldsFrame(ctor.fields()));
        }

        @Override
        public Result onChildCompleted(BitReader bits, DecodingEventHandler events) {
            throw new IllegalStateException("union frame is replaced before any child runs");
        }
    }

    /**
     * Drives an {@link Array}: closes the bit byte, emits StartContainer, reads the
     * count (fixed or varint), then either chunk-emits raw bytes for fixed-byte
     * primitive items or descends per-item with Start/EndItem markers.
     */
    static final class ArrayFrame implements DecoderFrame {
        private final Array array;
        private boolean entered;
        private boolean countRead;
        private boolean primitiveItems;
        private int itemByteSize;
        private int declaredCount;
        private int itemsCompleted;
        private long bytesRemaining;
        private VarIntDecoder countDecoder;

        ArrayFrame(Array array) {
            this.array = array;
        }

        @Override
        public Result step(BitReader bits, DecodingEventHandler events) {
            if (!entered) {
                bits.closeBitByte();
                events.onEvent(new DecodingEvent.StartContainer(DecodingEvent.ContainerKind.ARRAY));
                entered = true;
                OptionalInt fixed = array.element().fixedPrimitiveByteSize();
                primitiveItems = fixed.isPresent();
                if (primitiveItems) {
                    itemByteSize = fixed.getAsInt();
                }
            }
            if (!countRead) {
                if (!readCount(bits)) {
                    return new Result.WaitForInput();
                }
            }
            if (primitiveItems) {
                return stepPrimitiveItems(bits, events);
            }
            return advanceToNextItem(bits, events);
        }

        @Override
        public Result onChildCompleted(BitReader bits, DecodingEventHandler events) {
            events.onEvent(new DecodingEvent.EndItem());
            itemsCompleted++;
            bits.closeBitByte();
            return advanceToNextItem(bits, events);
        }

        private Result advanceToNextItem(BitReader bits, DecodingEventHandler events) {
            while (itemsCompleted < declaredCount) {
                events.onEvent(new DecodingEvent.StartItem());
                DecoderFrame childFrame = array.element().createDecodeFrame();
                if (childFrame != null) {
                    return new Result.Push(childFrame);
                }
                events.onEvent(new DecodingEvent.EndItem());
                itemsCompleted++;
            }
            bits.closeBitByte();
            events.onEvent(new DecodingEvent.EndContainer(DecodingEvent.ContainerKind.ARRAY));
            return new Result.Done();
        }

        private boolean readCount(BitReader bits) {
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
                    return false;
                }
            }
            if (primitiveItems) {
                bytesRemaining = (long) declaredCount * itemByteSize;
            }
            return true;
        }

        private Result stepPrimitiveItems(BitReader bits, DecodingEventHandler events) {
            if (bytesRemaining > 0) {
                if (bits.available() == 0) {
                    return new Result.WaitForInput();
                }
                int n = (int) Math.min(bits.available(), bytesRemaining);
                byte[] chunk = new byte[n];
                bits.readBytes(chunk, 0, n);
                events.onEvent(new DecodingEvent.Chunk(chunk));
                bytesRemaining -= n;
                if (bytesRemaining > 0) {
                    return new Result.WaitForInput();
                }
            }
            bits.closeBitByte();
            events.onEvent(new DecodingEvent.EndContainer(DecodingEvent.ContainerKind.ARRAY));
            return new Result.Done();
        }
    }

    /** Reads a fixed-width unsigned big-endian integer. */
    static final class UnsignedIntegerFrame implements DecoderFrame {
        private final int byteSize;

        UnsignedIntegerFrame(int byteSize) {
            this.byteSize = byteSize;
        }

        @Override
        public Result step(BitReader bits, DecodingEventHandler events) {
            if (!bits.hasBytes(byteSize)) {
                return new Result.WaitForInput();
            }
            events.onEvent(new DecodingEvent.IntegerScalar(readUnsignedBigEndian(bits, byteSize)));
            return new Result.Done();
        }

        @Override
        public Result onChildCompleted(BitReader bits, DecodingEventHandler events) {
            throw new IllegalStateException("primitive frame has no children");
        }
    }

    /** Reads a fixed-width signed big-endian integer with sign extension. */
    static final class SignedIntegerFrame implements DecoderFrame {
        private final int byteSize;

        SignedIntegerFrame(int byteSize) {
            this.byteSize = byteSize;
        }

        @Override
        public Result step(BitReader bits, DecodingEventHandler events) {
            if (!bits.hasBytes(byteSize)) {
                return new Result.WaitForInput();
            }
            long unsigned = readUnsignedBigEndian(bits, byteSize);
            if (byteSize < 8) {
                long signBit = 1L << (byteSize * 8 - 1);
                if ((unsigned & signBit) != 0) {
                    unsigned |= -1L << (byteSize * 8);
                }
            }
            events.onEvent(new DecodingEvent.IntegerScalar(unsigned));
            return new Result.Done();
        }

        @Override
        public Result onChildCompleted(BitReader bits, DecodingEventHandler events) {
            throw new IllegalStateException("primitive frame has no children");
        }
    }

    /** Reads a 32- or 64-bit IEEE 754 float. */
    static final class FloatingPointFrame implements DecoderFrame {
        private final int byteSize;

        FloatingPointFrame(int byteSize) {
            this.byteSize = byteSize;
        }

        @Override
        public Result step(BitReader bits, DecodingEventHandler events) {
            if (!bits.hasBytes(byteSize)) {
                return new Result.WaitForInput();
            }
            long bb = readUnsignedBigEndian(bits, byteSize);
            double v = (byteSize == 4)
                    ? Float.intBitsToFloat((int) bb)
                    : Double.longBitsToDouble(bb);
            events.onEvent(new DecodingEvent.FloatingPointScalar(v));
            return new Result.Done();
        }

        @Override
        public Result onChildCompleted(BitReader bits, DecodingEventHandler events) {
            throw new IllegalStateException("primitive frame has no children");
        }
    }

    /** Reads a variable-length integer via a stateful {@link VarIntDecoder}. */
    static final class VariableLengthIntegerFrame implements DecoderFrame {
        private final VarIntDecoder varint;

        VariableLengthIntegerFrame(int maxBytes) {
            this.varint = new VarIntDecoder(maxBytes);
        }

        @Override
        public Result step(BitReader bits, DecodingEventHandler events) {
            while (bits.available() > 0) {
                if (varint.feed(bits.readOneByte())) {
                    events.onEvent(new DecodingEvent.IntegerScalar(varint.value()));
                    return new Result.Done();
                }
            }
            return new Result.WaitForInput();
        }

        @Override
        public Result onChildCompleted(BitReader bits, DecodingEventHandler events) {
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
