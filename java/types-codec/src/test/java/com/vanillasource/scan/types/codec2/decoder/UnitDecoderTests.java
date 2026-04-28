package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.DecodingEvent;
import com.vanillasource.scan.types.codec2.DecodingEvent.UnitScalar;
import com.vanillasource.scan.types.codec2.DecodingEventHandler;
import com.vanillasource.scan.types.codec2.bit.FedBitReader;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public final class UnitDecoderTests {

   @Test
   public void completesImmediatelyOnEmptyInput() {
      UnitDecoder decoder = new UnitDecoder();
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      assertTrue(decoder.parse(bits, capture));
      assertEquals(capture.events, List.of(new UnitScalar()));
   }

   @Test
   public void emitsExactlyOneUnitScalarEvent() {
      UnitDecoder decoder = new UnitDecoder();
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      decoder.parse(bits, capture);

      assertEquals(capture.events.size(), 1);
      assertTrue(capture.events.get(0) instanceof UnitScalar);
   }

   @Test
   public void doesNotConsumeAnyAvailableBytes() {
      UnitDecoder decoder = new UnitDecoder();
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x11, 0x22, 0x33 }, 0, 3);
      assertTrue(decoder.parse(bits, capture));

      assertEquals(bits.availableBytes(), 3);
   }

   private static final class EventCapture implements DecodingEventHandler {
      final List<DecodingEvent> events = new ArrayList<>();

      @Override
      public void onEvent(DecodingEvent event) {
         events.add(event);
      }
   }
}
