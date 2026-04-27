package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitReader;
import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.DecodingEvent;
import com.vanillasource.scan.types.codec.DecodingEventHandler;

/** Reads a union discriminator and replaces itself with the constructor's fields. */
final class UnionDecoderFrame implements DecoderFrame {
    private final Union union;

    UnionDecoderFrame(Union union) {
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
        return new Result.Replace(new FieldsDecoderFrame(ctor.fields()));
    }

    @Override
    public Result onChildCompleted(BitReader bits, DecodingEventHandler events) {
        throw new IllegalStateException("union frame is replaced before any child runs");
    }
}
