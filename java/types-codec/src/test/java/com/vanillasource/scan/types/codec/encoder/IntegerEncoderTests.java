package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.IntegerScalar;
import com.vanillasource.scan.types.codec.EventSource;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class IntegerEncoderTests {

   // ---- single-call completion ----

   @Test
   public void oneByteZero() {
      assertEncodes(1, new IntegerScalar(0L, 0), new int[] { 0x00 });
   }

   @Test
   public void oneByteMid() {
      assertEncodes(1, new IntegerScalar(0x42L, 1), new int[] { 0x42 });
   }

   @Test
   public void oneByteMax() {
      assertEncodes(1, new IntegerScalar(0xFFL, 1), new int[] { 0xFF });
   }

   @Test
   public void twoByteBigEndian() {
      assertEncodes(2, new IntegerScalar(0x1234L, 1), new int[] { 0x12, 0x34 });
   }

   @Test
   public void fourByteMax() {
      assertEncodes(4, new IntegerScalar(0xFFFFFFFFL, 1),
            new int[] { 0xFF, 0xFF, 0xFF, 0xFF });
   }

   @Test
   public void eightByteZero() {
      assertEncodes(8, new IntegerScalar(0L, 0),
            new int[] { 0, 0, 0, 0, 0, 0, 0, 0 });
   }

   @Test
   public void eightByteHighBitSet() {
      // 0x8000_0000_0000_0000: long = Long.MIN_VALUE conceptually 2^63.
      assertEncodes(8, new IntegerScalar(Long.MIN_VALUE, 1),
            new int[] { 0x80, 0, 0, 0, 0, 0, 0, 0 });
   }

   @Test
   public void eightByteAllOnes() {
      assertEncodes(8, new IntegerScalar(-1L, 1),
            new int[] { 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF });
   }

   // ---- signed values via two's complement ----

   @Test
   public void signedOneByteMinusOne() {
      assertEncodes(1, new IntegerScalar(-1L, -1), new int[] { 0xFF });
   }

   @Test
   public void signedTwoByteMin() {
      assertEncodes(2, new IntegerScalar((long) Short.MIN_VALUE, -1),
            new int[] { 0x80, 0x00 });
   }

   @Test
   public void signedFourByteMin() {
      assertEncodes(4, new IntegerScalar((long) Integer.MIN_VALUE, -1),
            new int[] { 0x80, 0, 0, 0 });
   }

   @Test
   public void signedThreeByteNegativeTwo() {
      // -2 as 3-byte two's complement = 0xFFFFFE
      assertEncodes(3, new IntegerScalar(-2L, -1), new int[] { 0xFF, 0xFF, 0xFE });
   }

   @Test
   public void signedThreeBytePositive() {
      assertEncodes(3, new IntegerScalar(0x010203L, 1), new int[] { 0x01, 0x02, 0x03 });
   }

   // ---- partial flushes ----

   @Test
   public void waitsWhenNoEventAvailable() {
      IntegerEncoder enc = new IntegerEncoder(2);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes.size(), 0);
   }

   @Test
   public void completesAcrossSingleByteCapacity() {
      IntegerEncoder enc = new IntegerEncoder(4);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();
      sink.capacity = 1;

      events.put(new IntegerScalar(0x12345678L, 1));

      assertFalse(enc.generate(events, sink));
      sink.capacity = 1;
      assertFalse(enc.generate(events, sink));
      sink.capacity = 1;
      assertFalse(enc.generate(events, sink));
      sink.capacity = 1;
      assertTrue(enc.generate(events, sink));

      assertEquals(sink.bytes, List.of(0x12, 0x34, 0x56, 0x78));
   }

   @Test
   public void waitsWhenSinkHasNoCapacityAfterReadingEvent() {
      IntegerEncoder enc = new IntegerEncoder(2);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();
      sink.capacity = 0;

      events.put(new IntegerScalar(0x1234L, 1));

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes.size(), 0);

      sink.capacity = Integer.MAX_VALUE;
      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x12, 0x34));
   }

   // ---- zero-width edge case ----

   @Test
   public void zeroByteCompletesAfterReadingEvent() {
      IntegerEncoder enc = new IntegerEncoder(0);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new IntegerScalar(0L, 0));
      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes.size(), 0);
   }

   // ---- helpers ----

   private static void assertEncodes(int byteSize, IntegerScalar scalar, int[] expected) {
      IntegerEncoder enc = new IntegerEncoder(byteSize);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(scalar);
      assertTrue(enc.generate(events, sink));

      List<Integer> exp = new ArrayList<>(expected.length);
      for (int e : expected) {
         exp.add(e);
      }
      assertEquals(sink.bytes, exp);
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
