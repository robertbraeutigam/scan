package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.BitReader;
import com.vanillasource.scan.types.codec2.DecodingEvent;
import com.vanillasource.scan.types.codec2.DecodingEventHandler;
import com.vanillasource.scan.types.codec2.ValueDecoder;

/** Reads a fixed-width signed or unsigned big-endian integer, sign-extending when signed. */
public final class IntegerDecoder implements ValueDecoder {
    private final int byteSize;
    private final boolean signed;
    private int remainingBytes;
    private long currentValue = 0;

    public IntegerDecoder(int byteSize, boolean signed) {
        this.byteSize = byteSize;
        this.remainingBytes = byteSize;
        this.signed = signed;
    }

    @Override
    public boolean parse(BitReader bits, DecodingEventHandler handler) {
        while (remainingBytes > 0 && bits.availableBytes() > 0) {
            currentValue = (currentValue << 8) | bits.readUnsignedByte();
            remainingBytes--;
        }
        if (remainingBytes > 0) {
            return false;
        }
        if (signed && byteSize < 8) {
            long signBit = 1L << (byteSize * 8 - 1);
            if ((currentValue & signBit) != 0) {
                currentValue |= -1L << (byteSize * 8);
            }
        }
        int sign;
        if (signed) {
            sign = Long.signum(currentValue);
        } else {
            sign = currentValue == 0 ? 0 : 1;
        }
        handler.onEvent(new DecodingEvent.IntegerScalar(currentValue, sign));
        return true;
    }
}
