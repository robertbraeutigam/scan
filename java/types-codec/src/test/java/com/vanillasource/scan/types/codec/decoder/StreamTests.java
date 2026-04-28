package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.EndItem;
import com.vanillasource.scan.types.codec.Event.IntegerScalar;
import com.vanillasource.scan.types.codec.Event.StartItem;
import com.vanillasource.scan.types.codec.Event.StartStream;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.bit.FedBitSource;
import com.vanillasource.scan.types.codec.type.Stream;
import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public final class StreamTests {

   private static final Type U8 = new UnsignedInteger(1);
   private static final Type U16 = new UnsignedInteger(2);

   @Test
   public void emitsStartStreamEvenWithNoBytes() {
      ValueDecoder dec = new Stream(U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      assertFalse(dec.parse(bits, capture));
      assertEquals(capture.events, List.of(new StartStream()));
   }

   @Test
   public void emitsItemsAsBytesArrive() {
      ValueDecoder dec = new Stream(U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x10, 0x20, 0x30 }, 0, 3);
      assertFalse(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartStream(),
            new StartItem(),
            new IntegerScalar(0x10L, 1),
            new EndItem(),
            new StartItem(),
            new IntegerScalar(0x20L, 1),
            new EndItem(),
            new StartItem(),
            new IntegerScalar(0x30L, 1),
            new EndItem()));
   }

   @Test
   public void neverCompletesEvenWhenIdle() {
      ValueDecoder dec = new Stream(U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x10);
      assertFalse(dec.parse(bits, capture));
      // Drained — but stream never reports done.
      assertFalse(dec.parse(bits, capture));
      assertFalse(dec.parse(bits, capture));
   }

   @Test
   public void completesAcrossPartialFeedsForMultiByteItems() {
      ValueDecoder dec = new Stream(U16).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x12);
      assertFalse(dec.parse(bits, capture));
      // StartStream + StartItem (waiting for second byte of U16)
      assertEquals(capture.events, List.of(new StartStream(), new StartItem()));

      bits.write(0x34);
      assertFalse(dec.parse(bits, capture));
      assertEquals(capture.events, List.of(
            new StartStream(),
            new StartItem(),
            new IntegerScalar(0x1234L, 1),
            new EndItem()));
   }

   @Test
   public void waitsWhenSinkFillsMidStream() {
      ValueDecoder dec = new Stream(U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x10, 0x20 }, 0, 2);
      capture.capacity = 3; // StartStream + StartItem + IntegerScalar — then full
      assertFalse(dec.parse(bits, capture));
      assertEquals(capture.events.size(), 3);

      capture.capacity = Integer.MAX_VALUE;
      assertFalse(dec.parse(bits, capture));
      assertEquals(capture.events, List.of(
            new StartStream(),
            new StartItem(),
            new IntegerScalar(0x10L, 1),
            new EndItem(),
            new StartItem(),
            new IntegerScalar(0x20L, 1),
            new EndItem()));
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
         if (capacity != Integer.MAX_VALUE) {
            capacity--;
         }
         events.add(event);
      }
   }
}
