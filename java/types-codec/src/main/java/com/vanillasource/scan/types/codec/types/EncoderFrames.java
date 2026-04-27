package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitWriter;
import com.vanillasource.scan.types.codec.EncoderFrame;
import com.vanillasource.scan.types.codec.SizeConstraint;
import com.vanillasource.scan.types.codec.Type;

import java.util.List;

/** Concrete {@link EncoderFrame} implementations for the type variants in this package. */
final class EncoderFrames {
    private EncoderFrames() {}

    /** Holds a primitive {@link Type} and dispatches the user's value to its polymorphic write method. */
    static final class PrimitiveFrame implements EncoderFrame {
        private final Type type;

        PrimitiveFrame(Type type) {
            this.type = type;
        }

        @Override
        public String describe() {
            return "primitive " + type;
        }

        @Override
        public Result writeInteger(BitWriter bits, long value) {
            type.writeIntegerValue(bits, value);
            return new Result.Done();
        }

        @Override
        public Result writeFloat(BitWriter bits, double value) {
            type.writeFloatValue(bits, value);
            return new Result.Done();
        }

        @Override
        public Result onChildCompleted(BitWriter bits) {
            throw new IllegalStateException("primitive frame has no children");
        }
    }

    /** Walks a list of struct or constructor fields, descending into each field's frame in turn. */
    static final class FieldsFrame implements EncoderFrame {
        private final List<Field> fields;
        private int fieldIndex;

        FieldsFrame(List<Field> fields) {
            this.fields = fields;
        }

        @Override
        public String describe() {
            return "field " + fieldIndex + " of " + fields.size();
        }

        @Override
        public Result onPushed(BitWriter bits) {
            return descendNextField();
        }

        @Override
        public Result onChildCompleted(BitWriter bits) {
            fieldIndex++;
            return descendNextField();
        }

        private Result descendNextField() {
            while (fieldIndex < fields.size()) {
                EncoderFrame childFrame = fields.get(fieldIndex).type().createEncodeFrame();
                if (childFrame != null) {
                    return new Result.Push(childFrame);
                }
                fieldIndex++;
            }
            return new Result.Done();
        }
    }

    /** Awaits a {@code writeConstructor} call; on receipt writes the discriminator and replaces itself with the constructor's fields. */
    static final class UnionFrame implements EncoderFrame {
        private final Union union;

        UnionFrame(Union union) {
            this.union = union;
        }

        @Override
        public String describe() {
            return "union with " + union.constructors().size() + " constructors";
        }

        @Override
        public Result writeConstructor(BitWriter bits, int index) {
            List<Constructor> ctors = union.constructors();
            if (index < 0 || index >= ctors.size()) {
                throw new IllegalArgumentException(
                        "constructor index " + index + " out of range [0," + ctors.size() + ")");
            }
            int k = union.discriminatorBits();
            if (k > 0) {
                bits.writeBits(k, index);
            }
            Constructor ctor = ctors.get(index);
            if (ctor.fields().isEmpty()) {
                return new Result.Done();
            }
            return new Result.Replace(new FieldsFrame(ctor.fields()));
        }

        @Override
        public Result onChildCompleted(BitWriter bits) {
            throw new IllegalStateException("union frame is replaced before any child runs");
        }
    }

    /** Awaits a {@code startArray} call; once declared, descends into one element at a time until the count is reached. */
    static final class ArrayFrame implements EncoderFrame {
        private final Array array;
        private boolean declared;
        private int declaredCount;
        private int itemsCompleted;

        ArrayFrame(Array array) {
            this.array = array;
        }

        @Override
        public String describe() {
            return declared
                    ? ("array item " + itemsCompleted + " of " + declaredCount)
                    : "array (count not yet declared)";
        }

        @Override
        public Result startArray(BitWriter bits, int count) {
            if (declared) {
                throw new IllegalStateException("array count already declared");
            }
            SizeConstraint sc = array.size();
            sc.validate(count);
            bits.closeBitByte();
            if (!sc.isFixed()) {
                long encoded = (long) count - sc.lowerBound();
                bits.writeVarInt(encoded, sc.countVarintSize());
            }
            declaredCount = count;
            declared = true;
            if (count == 0) {
                bits.closeBitByte();
                return new Result.Done();
            }
            EncoderFrame childFrame = array.element().createEncodeFrame();
            if (childFrame == null) {
                bits.closeBitByte();
                return new Result.Done();
            }
            return new Result.Push(childFrame);
        }

        @Override
        public Result onChildCompleted(BitWriter bits) {
            itemsCompleted++;
            bits.closeBitByte();
            if (itemsCompleted >= declaredCount) {
                return new Result.Done();
            }
            return new Result.Push(array.element().createEncodeFrame());
        }
    }
}
