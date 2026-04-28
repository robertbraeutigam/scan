package com.vanillasource.scan.types.codec2;

public interface ValueEncoder {
    boolean generate(EventSource events, BitSink sink);
}
