package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitReader;
import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.DecodingEvent;
import com.vanillasource.scan.types.codec.DecodingEventHandler;

/** Reads a 32- or 64-bit IEEE 754 float. */
final class FloatingPointDecoderFrame implements DecoderFrame {
    private final int byteSize;

    FloatingPointDecoderFrame(int byteSize) {
        this.byteSize = byteSize;
    }

    @Override
    public Result step(BitReader bits, DecodingEventHandler events) {
        if (!bits.hasBytes(byteSize)) {
            return new Result.WaitForInput();
        }
        long bb = bits.readBigEndianBytes(byteSize);
        double v = (byteSize == 4)
                ? Float.intBitsToFloat((int) bb)
                : Double.longBitsToDouble(bb);
        events.onEvent(new DecodingEvent.FloatingPointScalar(v));
        return new Result.Done();
    }

    @Override
    public Result onChildCompleted(BitReader bits, DecodingEventHandler events) {
        throw new IllegalStateException("primitive frame has no children");
    }
}
