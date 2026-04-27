package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitReader;
import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.DecodingEvent;
import com.vanillasource.scan.types.codec.DecodingEventHandler;

/** Reads a union discriminator, then runs the chosen constructor's fields inline. */
final class UnionDecoderFrame implements DecoderFrame {
    private final Union union;
    private boolean discriminatorRead;
    private DecoderFrame currentChild;

    UnionDecoderFrame(Union union) {
        this.union = union;
    }

    @Override
    public Result step(BitReader bits, DecodingEventHandler events) {
        if (!discriminatorRead) {
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
            discriminatorRead = true;
            Constructor ctor = union.constructors().get(j);
            if (ctor.fields().isEmpty()) {
                return new Result.Done();
            }
            currentChild = new FieldsDecoderFrame(ctor.fields());
        }
        return currentChild.step(bits, events);
    }
}
