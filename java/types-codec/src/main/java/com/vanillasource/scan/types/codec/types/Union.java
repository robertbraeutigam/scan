package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.EncoderFrame;
import com.vanillasource.scan.types.codec.Type;

import java.util.List;
import java.util.OptionalInt;

public record Union(List<Constructor> constructors) implements Type {
    public Union {
        constructors = List.copyOf(constructors);
        if (constructors.isEmpty()) {
            throw new IllegalArgumentException("Union must have at least one constructor");
        }
    }

    @Override public boolean containsStream() {
        for (Constructor c : constructors) {
            if (Field.anyContainsStream(c.fields())) {
                return true;
            }
        }
        return false;
    }

    @Override public OptionalInt staticBitSize() {
        Integer ctorSize = null;
        for (Constructor c : constructors) {
            OptionalInt sub = Field.sumBitSizes(c.fields());
            if (sub.isEmpty()) {
                return OptionalInt.empty();
            }
            int s = sub.getAsInt();
            if (ctorSize == null) {
                ctorSize = s;
            } else if (ctorSize != s) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.of(discriminatorBits() + (ctorSize == null ? 0 : ctorSize));
    }

    /** Bits the encoder writes / decoder reads for this union's discriminator. */
    public int discriminatorBits() {
        int n = constructors.size();
        if (n <= 1) {
            return 0;
        }
        return 32 - Integer.numberOfLeadingZeros(n - 1);
    }

    @Override
    public DecoderFrame createDecodeFrame() {
        return new DecoderFrames.UnionFrame(this);
    }

    @Override
    public EncoderFrame createEncodeFrame() {
        return new EncoderFrames.UnionFrame(this);
    }
}
