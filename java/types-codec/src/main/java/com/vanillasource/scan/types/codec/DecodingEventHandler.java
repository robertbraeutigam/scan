package com.vanillasource.scan.types.codec;

/**
 * Receiver of decoding events from a {@link ValueDecoder}. Single-method functional
 * interface; consumers dispatch on event variants with {@code instanceof}.
 */
@FunctionalInterface
public interface DecodingEventHandler {
    void onEvent(DecodingEvent event);
}
