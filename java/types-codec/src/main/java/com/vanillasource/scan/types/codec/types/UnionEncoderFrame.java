package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitWriter;
import com.vanillasource.scan.types.codec.EncoderFrame;

import java.util.List;

/** Awaits a {@code writeConstructor} call; on receipt writes the discriminator and runs the chosen constructor's fields inline. */
final class UnionEncoderFrame implements EncoderFrame {
    private final Union union;
    private EncoderFrame currentChild;

    UnionEncoderFrame(Union union) {
        this.union = union;
    }

    @Override
    public String describe() {
        if (currentChild != null) {
            return currentChild.describe();
        }
        return "union with " + union.constructors().size() + " constructors";
    }

    @Override
    public Result writeConstructor(BitWriter bits, int index) {
        if (currentChild != null) {
            return afterChild(currentChild.writeConstructor(bits, index));
        }
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
        currentChild = new FieldsEncoderFrame(ctor.fields());
        return afterChild(currentChild.onEntered(bits));
    }

    @Override
    public Result writeInteger(BitWriter bits, long value) {
        if (currentChild == null) {
            return EncoderFrame.super.writeInteger(bits, value);
        }
        return afterChild(currentChild.writeInteger(bits, value));
    }

    @Override
    public Result writeFloat(BitWriter bits, double value) {
        if (currentChild == null) {
            return EncoderFrame.super.writeFloat(bits, value);
        }
        return afterChild(currentChild.writeFloat(bits, value));
    }

    @Override
    public Result startArray(BitWriter bits, int count) {
        if (currentChild == null) {
            return EncoderFrame.super.startArray(bits, count);
        }
        return afterChild(currentChild.startArray(bits, count));
    }

    private Result afterChild(Result r) {
        if (r instanceof Result.Done) {
            currentChild = null;
        }
        return r;
    }
}
