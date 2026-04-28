package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.BitReader;
import com.vanillasource.scan.types.codec2.DecodingEvent;
import com.vanillasource.scan.types.codec2.DecodingEventHandler;
import com.vanillasource.scan.types.codec2.ValueDecoder;

/**
 * Reads an unsigned variable-length integer. Each non-final byte has its high bit set
 * and contributes 7 value bits; the final byte contributes 8 value bits and is final
 * either because its high bit is clear or because {@code maxBytes} bytes have been read.
 */
public final class VariableLengthIntegerDecoder implements ValueDecoder {
    private final int maxBytes;
    private int bytesRead = 0;
    private long accumulator = 0;

    VariableLengthIntegerDecoder(int maxBytes) {
        if (maxBytes < 1 || maxBytes > 8) {
            throw new IllegalArgumentException("maxBytes must be 1..8: " + maxBytes);
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public boolean parse(BitReader bits, DecodingEventHandler handler) {
        while (bits.availableBytes() > 0) {
            int b = bits.readUnsignedByte();
            bytesRead++;
            if (bytesRead == maxBytes) {
                accumulator = (accumulator << 8) | (b & 0xFF);
                emit(handler);
                return true;
            }
            if ((b & 0x80) == 0) {
                accumulator = (accumulator << 7) | (b & 0xFF);
                emit(handler);
                return true;
            }
            accumulator = (accumulator << 7) | (b & 0x7F);
        }
        return false;
    }

    private void emit(DecodingEventHandler handler) {
        int sign = accumulator == 0 ? 0 : 1;
        handler.onEvent(new DecodingEvent.IntegerScalar(accumulator, sign));
    }
}
