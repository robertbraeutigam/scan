package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitWriter;
import com.vanillasource.scan.types.codec.EncoderFrame;
import com.vanillasource.scan.types.codec.Type;

/** Holds a primitive {@link Type} and dispatches the user's value to its polymorphic write method. */
final class PrimitiveEncoderFrame implements EncoderFrame {
    private final Type type;

    PrimitiveEncoderFrame(Type type) {
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
}
