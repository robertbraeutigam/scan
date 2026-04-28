package com.vanillasource.scan.types.codec;

public interface ValueEncoder {
   /**
    * Try to consume events from the given source and write bytes to the given sink.
    * Returns {@code false} when neither side can make further progress —
    * either no events are available or the sink has no room — and {@code true}
    * exactly once when this encoder has consumed its full sequence of events
    * and emitted its bytes. Calling the encoder after it completed is undefined.
    */
   boolean generate(EventSource events, BitSink sink);

   /**
    * Sequence: run {@code this} until it completes, then run {@code other}. The composite
    * completes when {@code other} completes; if {@code this} completes within a single
    * {@code generate} call, control falls through to {@code other} immediately.
    */
   default ValueEncoder andThen(ValueEncoder other) {
      ValueEncoder self = this;
      return new ValueEncoder() {
         private boolean firstDone = false;

         @Override
         public boolean generate(EventSource events, BitSink sink) {
            if (!firstDone) {
               if (!self.generate(events, sink)) {
                  return false;
               }
               firstDone = true;
            }
            return other.generate(events, sink);
         }
      };
   }
}
