package com.vanillasource.scan.types.codec;

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
}
