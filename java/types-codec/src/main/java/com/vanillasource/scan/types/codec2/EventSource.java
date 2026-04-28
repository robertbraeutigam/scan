package com.vanillasource.scan.types.codec2;

public interface EventSource {
    /**
     * @return The number of events that can be read.
     */
    int availableEvents();

    /**
     * Read an event.
     */
    Event read();
}
