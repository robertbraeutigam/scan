package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.Event.ContainerKind;
import com.vanillasource.scan.types.codec2.Event.EndContainer;
import com.vanillasource.scan.types.codec2.Event.EndItem;
import com.vanillasource.scan.types.codec2.Event.IntegerScalar;
import com.vanillasource.scan.types.codec2.Event.StartContainer;
import com.vanillasource.scan.types.codec2.Event.StartItem;
import com.vanillasource.scan.types.codec2.EventSink;
import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueDecoder;
import com.vanillasource.scan.types.codec2.bit.FedBitSource;
import com.vanillasource.scan.types.codec2.type.Set;
import com.vanillasource.scan.types.codec2.type.UnsignedInteger;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class SetTests {

   private static final Type U8 = new UnsignedInteger(1);

   @Test
   public void emptySetEmitsOnlyContainerMarkers() {
      ValueDecoder dec = new Set(1, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x00); // count = 0
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.SET, 0L),
            new EndContainer(ContainerKind.SET)));
   }

   @Test
   public void singleItemSet() {
      ValueDecoder dec = new Set(1, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x01, 0x42 }, 0, 2);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.SET, 1L),
            new StartItem(),
            new IntegerScalar(0x42L, 1),
            new EndItem(),
            new EndContainer(ContainerKind.SET)));
   }

   @Test
   public void multipleItemsSet() {
      ValueDecoder dec = new Set(1, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x03, 0x10, 0x20, 0x30 }, 0, 4);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.SET, 3L),
            new StartItem(),
            new IntegerScalar(0x10L, 1),
            new EndItem(),
            new StartItem(),
            new IntegerScalar(0x20L, 1),
            new EndItem(),
            new StartItem(),
            new IntegerScalar(0x30L, 1),
            new EndItem(),
            new EndContainer(ContainerKind.SET)));
   }

   @Test
   public void emitsNothingUntilCountByteArrives() {
      ValueDecoder dec = new Set(1, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      assertFalse(dec.parse(bits, capture));
      assertEquals(capture.events, List.of());
   }

   @Test
   public void completesAcrossPartialFeeds() {
      ValueDecoder dec = new Set(1, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x01);
      assertFalse(dec.parse(bits, capture));

      bits.write(0x42);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.SET, 1L),
            new StartItem(),
            new IntegerScalar(0x42L, 1),
            new EndItem(),
            new EndContainer(ContainerKind.SET)));
   }

   @Test
   public void doesNotConsumeBytesPastEnd() {
      ValueDecoder dec = new Set(1, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x01, 0x42, (byte) 0xAB }, 0, 3);
      assertTrue(dec.parse(bits, capture));

      assertEquals(bits.availableBytes(), 1);
      assertEquals(bits.readUnsignedByte(), 0xAB);
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
