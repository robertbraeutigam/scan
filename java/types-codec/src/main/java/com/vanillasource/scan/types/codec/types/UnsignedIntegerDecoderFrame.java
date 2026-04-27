package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitReader;
import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.DecodingEvent;
import com.vanillasource.scan.types.codec.DecodingEventHandler;

/** Reads a fixed-width unsigned big-endian integer. */
final class UnsignedIntegerDecoderFrame implements DecoderFrame {
    private final int byteSize;

    UnsignedIntegerDecoderFrame(int byteSize) {
        this.byteSize = byteSize;
    }

    @Override
    public Result step(BitReader bits, DecodingEventHandler events) {
        if (!bits.hasBytes(byteSize)) {
            return new Result.WaitForInput();
        }
        events.onEvent(new DecodingEvent.IntegerScalar(bits.readBigEndianBytes(byteSize)));
        return new Result.Done();
    }

    @Override
    public Result onChildCompleted(BitReader bits, DecodingEventHandler events) {
        throw new IllegalStateException("primitive frame has no children");
    }
}
