package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitReader;
import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.DecodingEvent;
import com.vanillasource.scan.types.codec.DecodingEventHandler;

/** Reads a fixed-width signed big-endian integer with sign extension. */
final class SignedIntegerDecoderFrame implements DecoderFrame {
    private final int byteSize;

    SignedIntegerDecoderFrame(int byteSize) {
        this.byteSize = byteSize;
    }

    @Override
    public Result step(BitReader bits, DecodingEventHandler events) {
        if (!bits.hasBytes(byteSize)) {
            return new Result.WaitForInput();
        }
        long unsigned = bits.readBigEndianBytes(byteSize);
        if (byteSize < 8) {
            long signBit = 1L << (byteSize * 8 - 1);
            if ((unsigned & signBit) != 0) {
                unsigned |= -1L << (byteSize * 8);
            }
        }
        events.onEvent(new DecodingEvent.IntegerScalar(unsigned));
        return new Result.Done();
    }
}
