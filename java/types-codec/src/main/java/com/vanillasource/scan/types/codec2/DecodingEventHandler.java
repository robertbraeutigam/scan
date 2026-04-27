package com.vanillasource.scan.types.codec2;

import com.vanillasource.scan.types.codec.DecodingEvent;
import com.vanillasource.scan.types.codec.ValueDecoder;

/**
 * Receiver of decoding events from a {@link ValueDecoder}. Single-method functional
 * interface; consumers dispatch on event variants with {@code instanceof}.
 */
@FunctionalInterface
public interface DecodingEventHandler {
    void onEvent(DecodingEvent event);
}
