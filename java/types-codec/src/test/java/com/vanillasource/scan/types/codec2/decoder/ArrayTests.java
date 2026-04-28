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
import com.vanillasource.scan.types.codec2.type.Array;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class ArrayTests {

   private static final Type U8 = () -> new IntegerDecoder(1, false);
   private static final Type U16 = () -> new IntegerDecoder(2, false);

   @Test
   public void emptyArrayEmitsOnlyContainerMarkers() {
      ValueDecoder dec = new Array(1, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x00); // count = 0
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.ARRAY, 0L),
            new EndContainer(ContainerKind.ARRAY)));
   }

   @Test
   public void singleItemArray() {
      ValueDecoder dec = new Array(1, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x01, 0x42 }, 0, 2);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.ARRAY, 1L),
            new StartItem(),
            new IntegerScalar(0x42L, 1),
            new EndItem(),
            new EndContainer(ContainerKind.ARRAY)));
   }

   @Test
   public void multipleItemsArray() {
      ValueDecoder dec = new Array(1, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x03, 0x10, 0x20, 0x30 }, 0, 4);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.ARRAY, 3L),
            new StartItem(),
            new IntegerScalar(0x10L, 1),
            new EndItem(),
            new StartItem(),
            new IntegerScalar(0x20L, 1),
            new EndItem(),
            new StartItem(),
            new IntegerScalar(0x30L, 1),
            new EndItem(),
            new EndContainer(ContainerKind.ARRAY)));
   }

   @Test
   public void emitsNothingUntilCountByteArrives() {
      ValueDecoder dec = new Array(1, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      assertFalse(dec.parse(bits, capture));

      // StartContainer carries the count; without the count byte, no events fire.
      assertEquals(capture.events, List.of());
   }

   @Test
   public void completesAcrossPartialFeeds() {
      ValueDecoder dec = new Array(1, U16).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x01); // count = 1
      assertFalse(dec.parse(bits, capture));

      bits.write(0x12); // first byte of 2-byte int
      assertFalse(dec.parse(bits, capture));

      bits.write(0x34); // second byte
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.ARRAY, 1L),
            new StartItem(),
            new IntegerScalar(0x1234L, 1),
            new EndItem(),
            new EndContainer(ContainerKind.ARRAY)));
   }

   @Test
   public void multiByteVliCount() {
      // count = 200 with maxBytes=4, terminating via clear high bit: 0x81 0x48
      //   byte1: 0x81 (continuation, low 7 bits = 0x01)
      //   byte2: 0x48 (clear high bit, low 7 bits = 0x48)
      //   accumulator = (1 << 7) | 0x48 = 200
      // Use a Unit item type so we don't have to hand-write 200 bytes of payload.
      Type unit = UnitDecoder::new;
      ValueDecoder dec = new Array(4, unit).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { (byte) 0x81, 0x48 }, 0, 2);
      assertTrue(dec.parse(bits, capture));

      // 1 StartContainer + 200*(StartItem + UnitScalar + EndItem) + 1 EndContainer
      assertEquals(capture.events.size(), 1 + 200 * 3 + 1);
      assertEquals(capture.events.get(0), new StartContainer(ContainerKind.ARRAY, 200L));
      assertEquals(capture.events.get(capture.events.size() - 1), new EndContainer(ContainerKind.ARRAY));
   }

   @Test
   public void doesNotEmitCountAsIntegerScalar() {
      ValueDecoder dec = new Array(1, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x02, 0x10, 0x20 }, 0, 3);
      assertTrue(dec.parse(bits, capture));

      // Exactly two IntegerScalar events — for the items only, not the count.
      long integerEvents = capture.events.stream()
            .filter(e -> e instanceof IntegerScalar)
            .count();
      assertEquals(integerEvents, 2L);
   }

   @Test
   public void nestedArrays() {
      ValueDecoder dec = new Array(1, new Array(1, U8)).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      // outer count = 2, inner1 = [0x10, 0x20], inner2 = [0x30]
      bits.write(new byte[] { 0x02, 0x02, 0x10, 0x20, 0x01, 0x30 }, 0, 6);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.ARRAY, 2L),
            new StartItem(),
            new StartContainer(ContainerKind.ARRAY, 2L),
            new StartItem(),
            new IntegerScalar(0x10L, 1),
            new EndItem(),
            new StartItem(),
            new IntegerScalar(0x20L, 1),
            new EndItem(),
            new EndContainer(ContainerKind.ARRAY),
            new EndItem(),
            new StartItem(),
            new StartContainer(ContainerKind.ARRAY, 1L),
            new StartItem(),
            new IntegerScalar(0x30L, 1),
            new EndItem(),
            new EndContainer(ContainerKind.ARRAY),
            new EndItem(),
            new EndContainer(ContainerKind.ARRAY)));
   }

   @Test
   public void doesNotConsumeBytesPastEnd() {
      ValueDecoder dec = new Array(1, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x01, 0x42, (byte) 0xAB }, 0, 3);
      assertTrue(dec.parse(bits, capture));

      assertEquals(bits.availableBytes(), 1);
      assertEquals(bits.readUnsignedByte(), 0xAB);
   }

   @Test
   public void waitsWhenSinkFillsMidArray() {
      ValueDecoder dec = new Array(1, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x02, 0x10, 0x20 }, 0, 3);
      capture.capacity = 3; // StartContainer, StartItem, IntegerScalar — then full

      assertFalse(dec.parse(bits, capture));
      assertEquals(capture.events.size(), 3);

      capture.capacity = Integer.MAX_VALUE;
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.ARRAY, 2L),
            new StartItem(),
            new IntegerScalar(0x10L, 1),
            new EndItem(),
            new StartItem(),
            new IntegerScalar(0x20L, 1),
            new EndItem(),
            new EndContainer(ContainerKind.ARRAY)));
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
