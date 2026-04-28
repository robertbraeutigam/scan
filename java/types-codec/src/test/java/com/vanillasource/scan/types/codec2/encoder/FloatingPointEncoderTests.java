package com.vanillasource.scan.types.codec2.encoder;

import com.vanillasource.scan.types.codec2.BitSink;
import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.Event.FloatingPointScalar;
import com.vanillasource.scan.types.codec2.EventSource;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public final class FloatingPointEncoderTests {

   // ---- construction ----

   @Test
   public void rejectsByteSizeOtherThanFourOrEight() {
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointEncoder(0));
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointEncoder(1));
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointEncoder(2));
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointEncoder(3));
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointEncoder(5));
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointEncoder(7));
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointEncoder(16));
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointEncoder(-1));
   }

   // ---- 32-bit single-call ----

   @Test
   public void encodes32BitZero() {
      assertEncodes32(0.0f, new int[] { 0, 0, 0, 0 });
   }

   @Test
   public void encodes32BitNegativeZero() {
      assertEncodes32(-0.0f, new int[] { 0x80, 0, 0, 0 });
   }

   @Test
   public void encodes32BitOne() {
      assertEncodes32(1.0f, new int[] { 0x3F, 0x80, 0, 0 });
   }

   @Test
   public void encodes32BitNegativeOne() {
      assertEncodes32(-1.0f, new int[] { 0xBF, 0x80, 0, 0 });
   }

   @Test
   public void encodes32BitPositiveInfinity() {
      assertEncodes32(Float.POSITIVE_INFINITY, new int[] { 0x7F, 0x80, 0, 0 });
   }

   @Test
   public void encodes32BitNegativeInfinity() {
      assertEncodes32(Float.NEGATIVE_INFINITY, new int[] { 0xFF, 0x80, 0, 0 });
   }

   @Test
   public void encodes32BitMaxValue() {
      assertEncodes32(Float.MAX_VALUE, new int[] { 0x7F, 0x7F, 0xFF, 0xFF });
   }

   @Test
   public void encodes32BitNaNPreservesBitPattern() {
      int nanBits = 0x7FC00001;
      float nanValue = Float.intBitsToFloat(nanBits);

      FloatingPointEncoder enc = new FloatingPointEncoder(4);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new FloatingPointScalar(nanValue));
      assertTrue(enc.generate(events, sink));

      assertEquals(sink.bytes.size(), 4);
      int reconstructed = (sink.bytes.get(0) << 24)
            | (sink.bytes.get(1) << 16)
            | (sink.bytes.get(2) << 8)
            | sink.bytes.get(3);
      assertEquals(reconstructed, nanBits);
   }

   // ---- 64-bit single-call ----

   @Test
   public void encodes64BitZero() {
      assertEncodes64(0.0, new int[] { 0, 0, 0, 0, 0, 0, 0, 0 });
   }

   @Test
   public void encodes64BitOne() {
      assertEncodes64(1.0, new int[] { 0x3F, 0xF0, 0, 0, 0, 0, 0, 0 });
   }

   @Test
   public void encodes64BitNegativeOne() {
      assertEncodes64(-1.0, new int[] { 0xBF, 0xF0, 0, 0, 0, 0, 0, 0 });
   }

   @Test
   public void encodes64BitPositiveInfinity() {
      assertEncodes64(Double.POSITIVE_INFINITY,
            new int[] { 0x7F, 0xF0, 0, 0, 0, 0, 0, 0 });
   }

   @Test
   public void encodes64BitNegativeInfinity() {
      assertEncodes64(Double.NEGATIVE_INFINITY,
            new int[] { 0xFF, 0xF0, 0, 0, 0, 0, 0, 0 });
   }

   @Test
   public void encodes64BitPi() {
      long bits = Double.doubleToRawLongBits(Math.PI);
      assertEncodes64(Math.PI, longToBigEndian(bits));
   }

   @Test
   public void encodes64BitNaNPreservesBitPattern() {
      long nanBits = 0x7FF8000000000001L;
      double nanValue = Double.longBitsToDouble(nanBits);

      FloatingPointEncoder enc = new FloatingPointEncoder(8);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new FloatingPointScalar(nanValue));
      assertTrue(enc.generate(events, sink));

      assertEquals(sink.bytes.size(), 8);
      long reconstructed = 0;
      for (int i = 0; i < 8; i++) {
         reconstructed = (reconstructed << 8) | sink.bytes.get(i);
      }
      assertEquals(reconstructed, nanBits);
   }

   // ---- partial flushes ----

   @Test
   public void waitsWhenNoEventAvailable() {
      FloatingPointEncoder enc = new FloatingPointEncoder(4);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes.size(), 0);
   }

   @Test
   public void completes32BitAcrossSingleByteCapacity() {
      FloatingPointEncoder enc = new FloatingPointEncoder(4);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();
      sink.capacity = 1;

      events.put(new FloatingPointScalar(1.0f));

      assertFalse(enc.generate(events, sink));
      sink.capacity = 1;
      assertFalse(enc.generate(events, sink));
      sink.capacity = 1;
      assertFalse(enc.generate(events, sink));
      sink.capacity = 1;
      assertTrue(enc.generate(events, sink));

      assertEquals(sink.bytes, List.of(0x3F, 0x80, 0x00, 0x00));
   }

   @Test
   public void completes64BitAcrossSingleByteCapacity() {
      FloatingPointEncoder enc = new FloatingPointEncoder(8);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new FloatingPointScalar(1.0));

      for (int i = 0; i < 7; i++) {
         sink.capacity = 1;
         assertFalse(enc.generate(events, sink),
               "should still be writing after byte " + i);
      }
      sink.capacity = 1;
      assertTrue(enc.generate(events, sink));

      assertEquals(sink.bytes, List.of(0x3F, 0xF0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00));
   }

   @Test
   public void waitsWhenSinkHasNoCapacityAfterReadingEvent() {
      FloatingPointEncoder enc = new FloatingPointEncoder(4);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();
      sink.capacity = 0;

      events.put(new FloatingPointScalar(1.0f));

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes.size(), 0);

      sink.capacity = Integer.MAX_VALUE;
      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x3F, 0x80, 0x00, 0x00));
   }

   // ---- helpers ----

   private static void assertEncodes32(float value, int[] expected) {
      FloatingPointEncoder enc = new FloatingPointEncoder(4);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new FloatingPointScalar(value));
      assertTrue(enc.generate(events, sink));

      List<Integer> exp = new ArrayList<>(expected.length);
      for (int b : expected) {
         exp.add(b);
      }
      assertEquals(sink.bytes, exp);
   }

   private static void assertEncodes64(double value, int[] expected) {
      FloatingPointEncoder enc = new FloatingPointEncoder(8);
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new FloatingPointScalar(value));
      assertTrue(enc.generate(events, sink));

      List<Integer> exp = new ArrayList<>(expected.length);
      for (int b : expected) {
         exp.add(b);
      }
      assertEquals(sink.bytes, exp);
   }

   private static int[] longToBigEndian(long v) {
      int[] out = new int[8];
      for (int i = 7; i >= 0; i--) {
         out[i] = (int) (v & 0xFF);
         v >>>= 8;
      }
      return out;
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
