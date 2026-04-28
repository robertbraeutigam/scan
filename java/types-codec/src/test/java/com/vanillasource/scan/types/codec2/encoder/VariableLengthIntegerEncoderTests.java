package com.vanillasource.scan.types.codec2.encoder;

import com.vanillasource.scan.types.codec2.BitSink;
import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.Event.IntegerScalar;
import com.vanillasource.scan.types.codec2.EventSource;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public final class VariableLengthIntegerEncoderTests {

   // ---- construction ----

   @Test
   public void rejectsMaxBytesBelowOne() {
      assertThrows(IllegalArgumentException.class,
            () -> new VariableLengthIntegerEncoder(0));
      assertThrows(IllegalArgumentException.class,
            () -> new VariableLengthIntegerEncoder(-1));
   }

   @Test
   public void rejectsMaxBytesAboveEight() {
      assertThrows(IllegalArgumentException.class,
            () -> new VariableLengthIntegerEncoder(9));
      assertThrows(IllegalArgumentException.class,
            () -> new VariableLengthIntegerEncoder(100));
   }

   // ---- maxBytes=1: single byte uses all 8 bits ----

   @Test
   public void maxN1ZeroByte() {
      assertEncodes(1, 0L, new int[] { 0x00 });
   }

   @Test
   public void maxN1FullEightBitsWhenLast() {
      assertEncodes(1, 0xFFL, new int[] { 0xFF });
   }

   @Test
   public void maxN1MidValue() {
      assertEncodes(1, 0x42L, new int[] { 0x42 });
   }

   // ---- termination via clear high bit ----

   @Test
   public void singleByteValueTerminatesViaClearHighBit() {
      assertEncodes(4, 127L, new int[] { 0x7F });
   }

   @Test
   public void zeroEncodesAsSingleZeroByte() {
      assertEncodes(4, 0L, new int[] { 0x00 });
   }

   // ---- 7-bit boundary, two-byte cases ----

   @Test
   public void twoByteValueOneTwentyEight() {
      // 128 spans 8 bits → 2 bytes with 7-bit trailer: 0x81 0x00
      assertEncodes(4, 128L, new int[] { 0x81, 0x00 });
   }

   @Test
   public void twoByteMaxWithMaxN2UsesEightBitTrailer() {
      // maxN=2, second byte uses 8 bits → 7+8 = 15 bits payload.
      assertEncodes(2, (1L << 15) - 1, new int[] { 0xFF, 0xFF });
   }

   @Test
   public void twoByteValueWithMaxN3UsesSevenBitTrailer() {
      // maxN=3, terminating on second byte → 7+7=14 bits.
      assertEncodes(3, (1L << 14) - 1, new int[] { 0xFF, 0x7F });
   }

   // ---- maxN reached: trailer uses all 8 bits ----

   @Test
   public void maxN3FullyUsedWith8BitTrailer() {
      // maxN=3 fully used → 7+7+8 = 22 bits payload.
      assertEncodes(3, (1L << 22) - 1, new int[] { 0xFF, 0xFF, 0xFF });
   }

   @Test
   public void maxN8FullyUsedWith8BitTrailer() {
      // maxN=8: 7*7 + 8 = 57 bits payload, max value = 2^57 - 1.
      int[] expected = new int[8];
      for (int i = 0; i < 8; i++) {
         expected[i] = 0xFF;
      }
      assertEncodes(8, (1L << 57) - 1, expected);
   }

   // ---- chooses minimum byte count ----

   @Test
   public void usesMinimumByteCountForSmallValues() {
      // value=1 with maxN=8 still encodes as one byte.
      assertEncodes(8, 1L, new int[] { 0x01 });
   }

   @Test
   public void choosesTwoBytesAtSevenBitBoundary() {
      // value=128 needs 8 bits; with maxN=2 the second byte uses all 8 bits, so the
      // top byte holds bit 8 only (with continuation): 0x80 0x80.
      assertEncodes(2, 128L, new int[] { 0x80, 0x80 });
   }

   // ---- partial flushes ----

   @Test
   public void waitsWhenNoEventAvailable() {
      VariableLengthIntegerEncoder enc = new VariableLengthIntegerEncoder(4);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes.size(), 0);
   }

   @Test
   public void completesAcrossSingleByteCapacity() {
      VariableLengthIntegerEncoder enc = new VariableLengthIntegerEncoder(3);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();
      sink.capacity = 1;

      events.put(new IntegerScalar((1L << 22) - 1, 1));

      assertFalse(enc.generate(events, sink));
      sink.capacity = 1;
      assertFalse(enc.generate(events, sink));
      sink.capacity = 1;
      assertTrue(enc.generate(events, sink));

      assertEquals(sink.bytes, List.of(0xFF, 0xFF, 0xFF));
   }

   @Test
   public void waitsWhenSinkHasNoCapacityAfterReadingEvent() {
      VariableLengthIntegerEncoder enc = new VariableLengthIntegerEncoder(4);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();
      sink.capacity = 0;

      events.put(new IntegerScalar(128L, 1));

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes.size(), 0);

      sink.capacity = Integer.MAX_VALUE;
      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x81, 0x00));
   }

   // ---- helpers ----

   private static void assertEncodes(int maxBytes, long value, int[] expected) {
      VariableLengthIntegerEncoder enc = new VariableLengthIntegerEncoder(maxBytes);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new IntegerScalar(value, value == 0 ? 0 : 1));
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
