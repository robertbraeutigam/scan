package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.ContainerKind;
import com.vanillasource.scan.types.codec.Event.Constructor;
import com.vanillasource.scan.types.codec.Event.EndContainer;
import com.vanillasource.scan.types.codec.Event.EndItem;
import com.vanillasource.scan.types.codec.Event.IntegerScalar;
import com.vanillasource.scan.types.codec.Event.StartContainer;
import com.vanillasource.scan.types.codec.Event.StartItem;
import com.vanillasource.scan.types.codec.Event.UnitScalar;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.bit.BufferedBitSink;
import com.vanillasource.scan.types.codec.type.Array;
import com.vanillasource.scan.types.codec.type.Unit;
import com.vanillasource.scan.types.codec.type.Union;
import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public final class ArrayEncoderTests {

   private static final Type U16 = new UnsignedInteger(2);

   @Test
   public void emptyArrayWritesOnlyCount() {
      ValueEncoder enc = new Array(0, 0xFF, U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.ARRAY, 0L));
      events.put(new EndContainer(ContainerKind.ARRAY));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x00));
   }

   @Test
   public void singleItemArray() {
      ValueEncoder enc = new Array(0, 0xFF, U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.ARRAY, 1L));
      events.put(new StartItem());
      events.put(new IntegerScalar(0x42L, 1));
      events.put(new EndItem());
      events.put(new EndContainer(ContainerKind.ARRAY));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x01, 0x00, 0x42));
   }

   @Test
   public void multipleItemsArray() {
      ValueEncoder enc = new Array(0, 0xFF, U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.ARRAY, 3L));
      events.put(new StartItem());
      events.put(new IntegerScalar(0x10L, 1));
      events.put(new EndItem());
      events.put(new StartItem());
      events.put(new IntegerScalar(0x20L, 1));
      events.put(new EndItem());
      events.put(new StartItem());
      events.put(new IntegerScalar(0x30L, 1));
      events.put(new EndItem());
      events.put(new EndContainer(ContainerKind.ARRAY));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x03, 0x00, 0x10, 0x00, 0x20, 0x00, 0x30));
   }

   @Test
   public void writesNothingUntilStartContainerArrives() {
      ValueEncoder enc = new Array(0, 0xFF, U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes.size(), 0);
   }

   @Test
   public void completesAcrossPartialEventFeeds() {
      ValueEncoder enc = new Array(0, 0xFF, U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.ARRAY, 1L));
      assertFalse(enc.generate(events, sink));

      events.put(new StartItem());
      assertFalse(enc.generate(events, sink));

      events.put(new IntegerScalar(0x1234L, 1));
      assertFalse(enc.generate(events, sink));

      events.put(new EndItem());
      assertFalse(enc.generate(events, sink));

      events.put(new EndContainer(ContainerKind.ARRAY));
      assertTrue(enc.generate(events, sink));

      assertEquals(sink.bytes, List.of(0x01, 0x12, 0x34));
   }

   @Test
   public void multiByteVliCount() {
      // count=200 with maxBytes=4: 0x81 0x48 (terminating via clear high bit).
      Type unit = new Unit();
      // Range chosen so the count VLI uses 3+ bytes — count 200 then encodes
      // via the "clear high bit" termination path ([0x81, 0x48]).
      ValueEncoder enc = new Array(0, 100_000, unit).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.ARRAY, 200L));
      for (int i = 0; i < 200; i++) {
         events.put(new StartItem());
         events.put(new UnitScalar());
         events.put(new EndItem());
      }
      events.put(new EndContainer(ContainerKind.ARRAY));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x81, 0x48));
   }

   @Test
   public void nestedArrays() {
      ValueEncoder enc = new Array(0, 0xFF, new Array(0, 0xFF, U16)).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.ARRAY, 2L));
      events.put(new StartItem());
      events.put(new StartContainer(ContainerKind.ARRAY, 2L));
      events.put(new StartItem());
      events.put(new IntegerScalar(0x10L, 1));
      events.put(new EndItem());
      events.put(new StartItem());
      events.put(new IntegerScalar(0x20L, 1));
      events.put(new EndItem());
      events.put(new EndContainer(ContainerKind.ARRAY));
      events.put(new EndItem());
      events.put(new StartItem());
      events.put(new StartContainer(ContainerKind.ARRAY, 1L));
      events.put(new StartItem());
      events.put(new IntegerScalar(0x30L, 1));
      events.put(new EndItem());
      events.put(new EndContainer(ContainerKind.ARRAY));
      events.put(new EndItem());
      events.put(new EndContainer(ContainerKind.ARRAY));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x02, 0x02, 0x00, 0x10, 0x00, 0x20, 0x01, 0x00, 0x30));
   }

   @Test
   public void doesNotConsumeEventsPastEndContainer() {
      ValueEncoder enc = new Array(0, 0xFF, U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.ARRAY, 1L));
      events.put(new StartItem());
      events.put(new IntegerScalar(0x42L, 1));
      events.put(new EndItem());
      events.put(new EndContainer(ContainerKind.ARRAY));
      events.put(new IntegerScalar(0x99L, 1)); // sentinel: must remain

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x01, 0x00, 0x42));
      assertEquals(events.availableEvents(), 1);
      assertTrue(events.read() instanceof IntegerScalar);
   }

   @Test
   public void waitsWhenSinkFillsMidArray() {
      ValueEncoder enc = new Array(0, 0xFF, U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.ARRAY, 2L));
      events.put(new StartItem());
      events.put(new IntegerScalar(0x10L, 1));
      events.put(new EndItem());
      events.put(new StartItem());
      events.put(new IntegerScalar(0x20L, 1));
      events.put(new EndItem());
      events.put(new EndContainer(ContainerKind.ARRAY));

      sink.capacity = 3; // count + first U16 item
      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x02, 0x00, 0x10));

      sink.capacity = Integer.MAX_VALUE;
      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x02, 0x00, 0x10, 0x00, 0x20));
   }

   @Test
   public void fixedLengthArrayWritesNoCount() {
      // min == max → no count is written; only the items appear on the wire.
      ValueEncoder enc = new Array(3, 3, U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.ARRAY, 3L));
      events.put(new StartItem());
      events.put(new IntegerScalar(0x10L, 1));
      events.put(new EndItem());
      events.put(new StartItem());
      events.put(new IntegerScalar(0x20L, 1));
      events.put(new EndItem());
      events.put(new StartItem());
      events.put(new IntegerScalar(0x30L, 1));
      events.put(new EndItem());
      events.put(new EndContainer(ContainerKind.ARRAY));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x00, 0x10, 0x00, 0x20, 0x00, 0x30));
   }

   @Test
   public void nonZeroLowerBoundIsSubtractedFromCount() {
      // min = 5, max = 10. count = 7 → wire byte = 7 - 5 = 2.
      ValueEncoder enc = new Array(5, 10, U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.ARRAY, 7L));
      for (int i = 0; i < 7; i++) {
         events.put(new StartItem());
         events.put(new IntegerScalar(i, 1));
         events.put(new EndItem());
      }
      events.put(new EndContainer(ContainerKind.ARRAY));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes.get(0).intValue(), 0x02);
      assertEquals(sink.bytes.size(), 1 + 7 * 2);
   }

   @Test
   public void countBelowMinThrows() {
      ValueEncoder enc = new Array(2, 5, U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.ARRAY, 1L));

      assertThrows(IllegalArgumentException.class, () -> enc.generate(events, sink));
   }

   @Test
   public void fixedLengthRejectsMismatchedCount() {
      ValueEncoder enc = new Array(3, 3, U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.ARRAY, 4L));

      assertThrows(IllegalArgumentException.class, () -> enc.generate(events, sink));
   }

   /**
    * 16 booleans (Union(True | False)) — discriminator is 1 bit per item, so the
    * 16 items must pack into exactly 2 wire bytes (no padding) per TYPES.md
    * §"Bit-State Lifetime": bit state carries across array items.
    */
   @Test
   public void arrayOfBooleansPacksOneBitPerItem() {
      Type bool = new Union(List.of(List.of(), List.of()));
      ValueEncoder enc = new Array(0, 0xFF, bool).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      // Alternating True (ctor 0 → bit 0) and False (ctor 1 → bit 1).
      // Bits MSB-first: 0,1,0,1,0,1,0,1 | 0,1,0,1,0,1,0,1 → 0x55 0x55.
      events.put(new StartContainer(ContainerKind.ARRAY, 16L));
      for (int i = 0; i < 16; i++) {
         events.put(new StartItem());
         events.put(new Constructor(i % 2));
         events.put(new EndItem());
      }
      events.put(new EndContainer(ContainerKind.ARRAY));

      assertTrue(enc.generate(events, sink));
      sink.closeBits();
      // count VLI(1) = 0x10, then 16 bits of payload = 2 bytes.
      assertEquals(sink.toBytes(), new byte[] { 0x10, 0x55, 0x55 });
   }

   @Test
   public void arrayOfBooleansLastByteHasZeroPadding() {
      // 13 booleans all False (ctor 1 → bit 1). 13 bits = 1 full byte + 5 bits;
      // the trailing 3 slots of the second byte stay 0.
      Type bool = new Union(List.of(List.of(), List.of()));
      ValueEncoder enc = new Array(0, 0xFF, bool).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      events.put(new StartContainer(ContainerKind.ARRAY, 13L));
      for (int i = 0; i < 13; i++) {
         events.put(new StartItem());
         events.put(new Constructor(1));
         events.put(new EndItem());
      }
      events.put(new EndContainer(ContainerKind.ARRAY));

      assertTrue(enc.generate(events, sink));
      sink.closeBits();
      // 0x0D = 13, 0xFF = 8 ones, 0xF8 = 11111000 (5 ones + 3 zero pad).
      assertEquals(sink.toBytes(), new byte[] { 0x0D, (byte) 0xFF, (byte) 0xF8 });
   }

   @Test
   public void hundredBooleansPackInto13Bytes() {
      // 100 × 1 bit = 100 bits = 12 full bytes + 4 bits → 13 bytes payload.
      // All False (ctor 1 → bit 1) so we see actual 1-bits in the bytes,
      // with the last byte being 0xF0 (4 ones MSB + 4 zero pad).
      Type bool = new Union(List.of(List.of(), List.of()));
      ValueEncoder enc = new Array(0, 0xFF, bool).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      events.put(new StartContainer(ContainerKind.ARRAY, 100L));
      for (int i = 0; i < 100; i++) {
         events.put(new StartItem());
         events.put(new Constructor(1));
         events.put(new EndItem());
      }
      events.put(new EndContainer(ContainerKind.ARRAY));

      assertTrue(enc.generate(events, sink));
      sink.closeBits();
      byte[] expected = new byte[14];
      expected[0] = 0x64; // count = 100
      for (int i = 1; i <= 12; i++) {
         expected[i] = (byte) 0xFF;
      }
      expected[13] = (byte) 0xF0;
      assertEquals(sink.toBytes(), expected);
   }

   private static final class EventQueue implements EventSource {
      private final java.util.Deque<Event> queue = new java.util.ArrayDeque<>();

      void put(Event event) {
         queue.addLast(event);
      }

      @Override
      public int availableEvents() {
         return queue.size();
      }

      @Override
      public Event read() {
         return queue.pollFirst();
      }
   }

   private static final class ByteCollector implements BitSink {
      final List<Integer> bytes = new ArrayList<>();
      int capacity = Integer.MAX_VALUE;

      @Override
      public int writableBytes() {
         return capacity;
      }

      @Override
      public int write(byte[] buf, int off, int len) {
         int n = Math.min(len, capacity);
         for (int i = 0; i < n; i++) {
            bytes.add(buf[off + i] & 0xFF);
         }
         if (capacity != Integer.MAX_VALUE) {
            capacity -= n;
         }
         return n;
      }

      @Override
      public int writeUnsignedByte(int unsignedByte) {
         if (capacity <= 0) {
            return 0;
         }
         bytes.add(unsignedByte & 0xFF);
         if (capacity != Integer.MAX_VALUE) {
            capacity--;
         }
         return 1;
      }

      @Override
      public int writableBits() {
         return writableBytes() * 8;
      }

      @Override
      public int writeBits(int bits, int count) {
         throw new UnsupportedOperationException();
      }
   }
}
