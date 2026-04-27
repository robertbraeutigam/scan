package com.vanillasource.scan.types.codec;

import java.util.List;

/**
 * One stack entry of a {@link ValueEncoder}'s walk through a {@link Type} tree.
 * Each user write call dispatches to the top frame's matching method; default
 * implementations throw with a frame-specific description so callers learn what
 * was expected at that position.
 */
sealed interface EncoderFrame {

    /** Human-readable position description for type-mismatch error messages. */
    String describe();

    default void writeInteger(EncoderContext ctx, long value) {
        throw new IllegalStateException("expected " + describe() + ", not an integer");
    }

    default void writeFloat(EncoderContext ctx, double value) {
        throw new IllegalStateException("expected " + describe() + ", not a float");
    }

    default void writeConstructor(EncoderContext ctx, int index) {
        throw new IllegalStateException("expected " + describe() + ", not a constructor");
    }

    default void startArray(EncoderContext ctx, int count) {
        throw new IllegalStateException("expected " + describe() + ", not an array");
    }

    /**
     * Called when a child frame has just finished. Returns {@code true} if this
     * frame is now also complete (the cascade pops it and notifies its parent).
     * If the frame stays on the stack, it must descend into its next child before
     * returning so the new top is ready for the user's next write.
     */
    boolean onChildCompleted(EncoderContext ctx);

    /** Holds a primitive {@link Type} and dispatches the user's value to its polymorphic write method. */
    final class PrimitiveFrame implements EncoderFrame {
        private final Type type;

        PrimitiveFrame(Type type) {
            this.type = type;
        }

        @Override
        public String describe() {
            return "primitive " + type;
        }

        @Override
        public void writeInteger(EncoderContext ctx, long value) {
            type.writeIntegerValue(ctx.bits(), value);
            ctx.pop();
            ctx.childCompleted();
        }

        @Override
        public void writeFloat(EncoderContext ctx, double value) {
            type.writeFloatValue(ctx.bits(), value);
            ctx.pop();
            ctx.childCompleted();
        }

        @Override
        public boolean onChildCompleted(EncoderContext ctx) {
            throw new IllegalStateException("primitive frame has no children");
        }
    }

    /** Walks a list of struct or constructor fields, eagerly descending into the next field after each child completes. */
    final class FieldsFrame implements EncoderFrame {
        private final List<Type.Field> fields;
        private int fieldIndex;

        FieldsFrame(List<Type.Field> fields) {
            this.fields = fields;
        }

        @Override
        public String describe() {
            return "field " + fieldIndex + " of " + fields.size();
        }

        /** Called after the frame is pushed to position the encoder at the first field's first leaf. */
        void enter(EncoderContext ctx) {
            fields.get(fieldIndex).type().startEncode(ctx);
        }

        @Override
        public boolean onChildCompleted(EncoderContext ctx) {
            fieldIndex++;
            if (fieldIndex >= fields.size()) {
                return true;
            }
            enter(ctx);
            return false;
        }
    }

    /** Awaits a {@code writeConstructor} call; on receipt writes the discriminator and replaces itself with the constructor's fields. */
    final class UnionFrame implements EncoderFrame {
        private final Type.Union union;

        UnionFrame(Type.Union union) {
            this.union = union;
        }

        @Override
        public String describe() {
            return "union with " + union.constructors().size() + " constructors";
        }

        @Override
        public void writeConstructor(EncoderContext ctx, int index) {
            List<Type.Constructor> ctors = union.constructors();
            if (index < 0 || index >= ctors.size()) {
                throw new IllegalArgumentException(
                        "constructor index " + index + " out of range [0," + ctors.size() + ")");
            }
            int k = union.discriminatorBits();
            if (k > 0) {
                ctx.bits().writeBits(k, index);
            }
            ctx.pop();
            Type.Constructor ctor = ctors.get(index);
            if (ctor.fields().isEmpty()) {
                ctx.childCompleted();
            } else {
                FieldsFrame frame = new FieldsFrame(ctor.fields());
                ctx.push(frame);
                frame.enter(ctx);
            }
        }

        @Override
        public boolean onChildCompleted(EncoderContext ctx) {
            throw new IllegalStateException("union frame is popped before any child runs");
        }
    }

    /** Awaits a {@code startArray} call; once declared, descends into one element at a time until the count is reached. */
    final class ArrayFrame implements EncoderFrame {
        private final Type.Array array;
        private boolean declared;
        private int declaredCount;
        private int itemsCompleted;

        ArrayFrame(Type.Array array) {
            this.array = array;
        }

        @Override
        public String describe() {
            return declared
                    ? ("array item " + itemsCompleted + " of " + declaredCount)
                    : "array (count not yet declared)";
        }

        @Override
        public void startArray(EncoderContext ctx, int count) {
            if (declared) {
                throw new IllegalStateException("array count already declared");
            }
            SizeConstraint sc = array.size();
            sc.validate(count);
            ctx.bits().closeBitByte();
            if (!sc.isFixed()) {
                long encoded = (long) count - sc.lowerBound();
                new Type.VariableLengthInteger(sc.countVarintSize()).writeIntegerValue(ctx.bits(), encoded);
            }
            declaredCount = count;
            declared = true;
            if (count == 0) {
                ctx.bits().closeBitByte();
                ctx.pop();
                ctx.childCompleted();
            } else {
                array.element().startEncode(ctx);
            }
        }

        @Override
        public boolean onChildCompleted(EncoderContext ctx) {
            itemsCompleted++;
            ctx.bits().closeBitByte();
            if (itemsCompleted >= declaredCount) {
                return true;
            }
            array.element().startEncode(ctx);
            return false;
        }
    }
}
