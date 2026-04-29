package com.vanillasource.scan.types.codec;

import java.util.function.Supplier;

public interface ValueDecoder {
   /**
    * Try to parse from the given bits reader and push events into the given sink.
    * Returns {@code false} when neither side can make further progress —
    * either input bytes are exhausted or the sink has no room — and {@code true}
    * exactly once when this decoder has emitted its full sequence of events.
    * Calling the parser after it completed is undefined.
    */
    boolean parse(BitSource bits, EventSink sink);

   /**
    * Sequence: run {@code this} until it completes, then run {@code other}. The composite
    * completes when {@code other} completes; if {@code this} completes within a single
    * {@code parse} call, control falls through to {@code other} immediately.
    */
   default ValueDecoder andThen(ValueDecoder other) {
      ValueDecoder self = this;
      return new ValueDecoder() {
         private boolean firstDone = false;

         @Override
         public boolean parse(BitSource bits, EventSink sink) {
            if (!firstDone) {
               if (!self.parse(bits, sink)) {
                  return false;
               }
               firstDone = true;
            }
            return other.parse(bits, sink);
         }
      };
   }

   /**
    * Bracket {@code this} between two structural events: emit {@code start},
    * run {@code this}, then emit {@code end}. If {@code this} never completes
    * (e.g. a {@code Stream}), {@code end} is never emitted.
    */
   default ValueDecoder between(Event start, Event end) {
      return writeEvent(start).andThen(this).andThen(writeEvent(end));
   }

   /**
    * Single-event producer: writes {@code event} to the sink (subject to
    * capacity) and completes.
    */
   static ValueDecoder writeEvent(Event event) {
      return (bits, sink) -> {
         if (sink.writableEvents() <= 0) {
            return false;
         }
         sink.write(event);
         return true;
      };
   }

   /**
    * Single-event producer whose payload is computed lazily — useful when the
    * event depends on state populated by an earlier pipeline stage.
    */
   static ValueDecoder writeEvent(Supplier<Event> eventFn) {
      return (bits, sink) -> {
         if (sink.writableEvents() <= 0) {
            return false;
         }
         sink.write(eventFn.get());
         return true;
      };
   }
}
