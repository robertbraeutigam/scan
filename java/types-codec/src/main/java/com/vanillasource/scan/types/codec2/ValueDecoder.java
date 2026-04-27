package com.vanillasource.scan.types.codec2;

public interface ValueDecoder {
   /**
    * Try to parse from the given bits reader and issue events if any come up.
    * @return True, iff the parser completed. Calling the parser after it completed
    * is undefined.
    */
    boolean parse(BitReader bits, DecodingEventHandler handler);

   default ValueDecoder followedBy(ValueDecoder other) {
      ValueDecoder self = this;
      return new ValueDecoder() {
         private boolean doneWithFirst = false;
         private boolean doneWithSecond = false;

         @Override
         public boolean parse(BitReader bits, DecodingEventHandler handler) {
            if (!doneWithFirst) {
               doneWithFirst = self.parse(bits, handler);
            } else if (!doneWithSecond) {
               doneWithSecond = other.parse(bits, handler);
            }
            return true;
         }
      };
   }
}
