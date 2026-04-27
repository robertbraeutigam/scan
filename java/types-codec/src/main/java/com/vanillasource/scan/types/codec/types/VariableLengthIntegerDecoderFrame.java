package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitReader;
import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.DecodingEvent;
import com.vanillasource.scan.types.codec.DecodingEventHandler;

/** Reads a variable-length integer via a stateful {@link VarIntDecoder}. */
final class VariableLengthIntegerDecoderFrame implements DecoderFrame {
    private final VarIntDecoder varint;

    VariableLengthIntegerDecoderFrame(int maxBytes) {
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
}
