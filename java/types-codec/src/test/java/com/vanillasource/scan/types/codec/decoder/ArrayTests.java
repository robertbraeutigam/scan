package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.ContainerKind;
import com.vanillasource.scan.types.codec.Event.Constructor;
import com.vanillasource.scan.types.codec.Event.EndContainer;
import com.vanillasource.scan.types.codec.Event.EndItem;
import com.vanillasource.scan.types.codec.Event.IntegerScalar;
import com.vanillasource.scan.types.codec.Event.StartContainer;
import com.vanillasource.scan.types.codec.Event.StartItem;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.bit.FedBitSource;
import com.vanillasource.scan.types.codec.type.Array;
import com.vanillasource.scan.types.codec.type.Unit;
import com.vanillasource.scan.types.codec.type.Union;
import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class ArrayTests {

   private static final Type U16 = new UnsignedInteger(2);

   @Test
   public void emptyArrayEmitsOnlyContainerMarkers() {
      ValueDecoder dec = new Array(0, 0xFF, U16).createDecoder();
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
      ValueDecoder dec = new Array(0, 0xFF, U16).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x01, 0x00, 0x42 }, 0, 3);
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
      ValueDecoder dec = new Array(0, 0xFF, U16).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x03, 0x00, 0x10, 0x00, 0x20, 0x00, 0x30 }, 0, 7);
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
      ValueDecoder dec = new Array(0, 0xFF, U16).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      assertFalse(dec.parse(bits, capture));

      // StartContainer carries the count; without the count byte, no events fire.
      assertEquals(capture.events, List.of());
   }

   @Test
   public void completesAcrossPartialFeeds() {
      ValueDecoder dec = new Array(0, 0xFF, U16).createDecoder();
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
      Type unit = new Unit();
      // Range chosen so the count VLI uses 3+ bytes (cap >= 22 bits) — that
      // makes value 200 encode via the "clear high bit" termination path
      // ([0x81, 0x48]) rather than terminating-by-maxBytes at 1 byte.
      ValueDecoder dec = new Array(0, 100_000, unit).createDecoder();
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
      ValueDecoder dec = new Array(0, 0xFF, U16).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x02, 0x00, 0x10, 0x00, 0x20 }, 0, 5);
      assertTrue(dec.parse(bits, capture));

      // Exactly two IntegerScalar events — for the items only, not the count.
      long integerEvents = capture.events.stream()
            .filter(e -> e instanceof IntegerScalar)
            .count();
      assertEquals(integerEvents, 2L);
   }

   @Test
   public void nestedArrays() {
      ValueDecoder dec = new Array(0, 0xFF, new Array(0, 0xFF, U16)).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      // outer count = 2, inner1 = [0x10, 0x20] (U16), inner2 = [0x30] (U16)
      bits.write(new byte[] { 0x02, 0x02, 0x00, 0x10, 0x00, 0x20, 0x01, 0x00, 0x30 }, 0, 9);
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
      ValueDecoder dec = new Array(0, 0xFF, U16).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x01, 0x00, 0x42, (byte) 0xAB }, 0, 4);
      assertTrue(dec.parse(bits, capture));

      assertEquals(bits.availableBytes(), 1);
      assertEquals(bits.readUnsignedByte(), 0xAB);
   }

   @Test
   public void waitsWhenSinkFillsMidArray() {
      ValueDecoder dec = new Array(0, 0xFF, U16).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x02, 0x00, 0x10, 0x00, 0x20 }, 0, 5);
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

   @Test
   public void fixedLengthArrayReadsNoCount() {
      // min == max → no count on the wire; decoder reads exactly 3 items.
      ValueDecoder dec = new Array(3, 3, U16).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x00, 0x10, 0x00, 0x20, 0x00, 0x30 }, 0, 6);
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

   /**
    * Round-trip of {@code Array(Union(True | False))} — verifies the decoder
    * unpacks a 1-bit-per-item bit stream the same way the encoder packed it.
    */
   @Test
   public void arrayOfBooleansUnpacksFromBitStream() {
      Type bool = new Union(List.of(List.of(), List.of()));
      ValueDecoder dec = new Array(0, 0xFF, bool).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      // 16 booleans alternating True/False: 0x10 0x55 0x55
      bits.write(new byte[] { 0x10, 0x55, 0x55 }, 0, 3);
      assertTrue(dec.parse(bits, capture));

      List<Event> expected = new ArrayList<>();
      expected.add(new StartContainer(ContainerKind.ARRAY, 16L));
      for (int i = 0; i < 16; i++) {
         expected.add(new StartItem());
         expected.add(new Constructor(i % 2));
         expected.add(new EndItem());
      }
      expected.add(new EndContainer(ContainerKind.ARRAY));
      assertEquals(capture.events, expected);
   }

   @Test
   public void hundredBooleansUnpackFromThirteenPayloadBytes() {
      Type bool = new Union(List.of(List.of(), List.of()));
      ValueDecoder dec = new Array(0, 0xFF, bool).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      // count=100, then 12 × 0xFF + 0xF0 (4 ones MSB + 4 zero pad)
      byte[] wire = new byte[14];
      wire[0] = 0x64;
      for (int i = 1; i <= 12; i++) {
         wire[i] = (byte) 0xFF;
      }
      wire[13] = (byte) 0xF0;
      bits.write(wire, 0, wire.length);

      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events.get(0), new StartContainer(ContainerKind.ARRAY, 100L));
      // All items are False (constructor index 1).
      long falseCount = capture.events.stream()
            .filter(e -> e instanceof Constructor c && c.index() == 1)
            .count();
      assertEquals(falseCount, 100L);
   }

   @Test
   public void nonZeroLowerBoundIsAddedBackToCount() {
      // min = 5, max = 10. Wire count = actual - min, so 0x02 → 5 + 2 = 7 items.
      ValueDecoder dec = new Array(5, 10, U16).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] {
            0x02,
            0x00, 0x10, 0x00, 0x20, 0x00, 0x30, 0x00, 0x40, 0x00, 0x50, 0x00, 0x60, 0x00, 0x70
      }, 0, 15);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events.get(0), new StartContainer(ContainerKind.ARRAY, 7L));
      long itemCount = capture.events.stream().filter(e -> e instanceof IntegerScalar).count();
      assertEquals(itemCount, 7L);
   }

   private static final class EventCapture implements EventSink {
      final List<Event> events = new ArrayList<>();
      int capacity = Integer.MAX_VALUE;

      @Override
      public int writableEvents() {
         return capacity;
      }

      @Override
      public void write(Event event) {
         if (capacity != Integer.MAX_VALUE) {
            capacity--;
         }
         events.add(event);
      }
   }
}
