package com.vanillasource.scan.types.codec;

/**
 * Bounded sink of {@link Event}s, the dual of {@link BitSource} on the byte side.
 * Producers (decoders, transducers) check {@link #writableEvents()} before each
 * {@link #write} and stop when capacity is exhausted. The orchestrator drains the
 * sink and re-invokes the producer when capacity returns; no callback or back-channel
 * lives below this interface.
 */
public interface EventSink {
    /**
     * @return Number of additional events the sink can accept right now. Producers
     *         must not call {@link #write} when this is zero.
     */
    int writableEvents();

    /**
     * Append one event. Caller must have checked {@link #writableEvents()} {@code > 0}.
     */
    void write(Event event);
}
