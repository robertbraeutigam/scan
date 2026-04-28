package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.UnitScalar;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.bit.FedBitSource;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class UnitDecoderTests {

   @Test
   public void completesImmediatelyOnEmptyInput() {
      UnitDecoder decoder = new UnitDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      assertTrue(decoder.parse(bits, capture));
      assertEquals(capture.events, List.of(new UnitScalar()));
   }

   @Test
   public void emitsExactlyOneUnitScalarEvent() {
      UnitDecoder decoder = new UnitDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      decoder.parse(bits, capture);

      assertEquals(capture.events.size(), 1);
      assertTrue(capture.events.get(0) instanceof UnitScalar);
   }

   @Test
   public void doesNotConsumeAnyAvailableBytes() {
      UnitDecoder decoder = new UnitDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x11, 0x22, 0x33 }, 0, 3);
      assertTrue(decoder.parse(bits, capture));

      assertEquals(bits.availableBytes(), 3);
   }

   @Test
   public void waitsWhenSinkHasNoCapacity() {
      UnitDecoder decoder = new UnitDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();
      capture.capacity = 0;

      assertFalse(decoder.parse(bits, capture));
      assertEquals(capture.events.size(), 0);

      capture.capacity = 1;
      assertTrue(decoder.parse(bits, capture));
      assertEquals(capture.events, List.of(new UnitScalar()));
   }

   private static final class EventCapture implements EventSink {
      final List<Event> events = new ArrayList<>();
      int capacity = Integer.MAX_VALUE;

      @Override
      public int writableEvents() {
         return capacity;
      }

      @Override
      public void put(Event event) {
         events.add(event);
      }
   }
}
