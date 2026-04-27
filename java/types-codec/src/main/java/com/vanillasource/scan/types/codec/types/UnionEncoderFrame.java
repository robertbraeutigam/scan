package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitWriter;
import com.vanillasource.scan.types.codec.EncoderFrame;

import java.util.List;

/** Awaits a {@code writeConstructor} call; on receipt writes the discriminator and replaces itself with the constructor's fields. */
final class UnionEncoderFrame implements EncoderFrame {
    private final Union union;

    UnionEncoderFrame(Union union) {
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
        return new Result.Replace(new FieldsEncoderFrame(ctor.fields()));
    }

    @Override
    public Result onChildCompleted(BitWriter bits) {
        throw new IllegalStateException("union frame is replaced before any child runs");
    }
}
