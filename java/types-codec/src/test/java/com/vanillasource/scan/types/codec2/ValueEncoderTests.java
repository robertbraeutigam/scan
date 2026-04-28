package com.vanillasource.scan.types.codec2;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class ValueEncoderTests {

   @Test
   public void runsBothInOrderWhenBothCompleteImmediately() {
      ValueEncoder a = writeByte(0x11);
      ValueEncoder b = writeByte(0x22);
      ByteCollector sink = new ByteCollector();

      assertTrue(a.andThen(b).generate(new EmptyEventSource(), sink));

      assertEquals(sink.bytes, List.of(0x11, 0x22));
   }

   @Test
   public void fallsThroughToSecondWhenFirstCompletesInSameCall() {
      int[] callIndex = { 0 };
      List<Integer> firstCalls = new ArrayList<>();
      List<Integer> secondCalls = new ArrayList<>();

      ValueEncoder first = (events, sink) -> { firstCalls.add(callIndex[0]); return true; };
      ValueEncoder second = (events, sink) -> { secondCalls.add(callIndex[0]); return true; };

      callIndex[0] = 1;
      assertTrue(first.andThen(second).generate(new EmptyEventSource(), new ByteCollector()));

      assertEquals(firstCalls, List.of(1));
      assertEquals(secondCalls, List.of(1));
   }

   @Test
   public void doesNotInvokeSecondWhileFirstNotDone() {
      MultiStepEncoder first = new MultiStepEncoder(2);
      CountingEncoder second = new CountingEncoder();
      ByteCollector sink = new ByteCollector();

      ValueEncoder seq = first.andThen(second);

      assertFalse(seq.generate(new EmptyEventSource(), sink));
      assertEquals(second.calls, 0);
   }

   @Test
   public void resumesAtSecondAfterFirstCompletes() {
      MultiStepEncoder first = new MultiStepEncoder(2);
      CountingEncoder second = new CountingEncoder();
      ByteCollector sink = new ByteCollector();

      ValueEncoder seq = first.andThen(second);

      assertFalse(seq.generate(new EmptyEventSource(), sink));    // first step 1
      assertEquals(second.calls, 0);
      assertTrue(seq.generate(new EmptyEventSource(), sink));     // first step 2 + second
      assertEquals(second.calls, 1);
   }

   @Test
   public void doesNotRestartFirstAcrossCalls() {
      CountingEncoder first = new CountingEncoder();
      MultiStepEncoder second = new MultiStepEncoder(3);
      ByteCollector sink = new ByteCollector();

      ValueEncoder seq = first.andThen(second);

      assertFalse(seq.generate(new EmptyEventSource(), sink)); // first done, second step 1
      assertFalse(seq.generate(new EmptyEventSource(), sink)); // second step 2
      assertTrue(seq.generate(new EmptyEventSource(), sink));  // second step 3

      assertEquals(first.calls, 1);
   }

   @Test
   public void propagatesSinkBackpressureFromFirst() {
      ValueEncoder a = writeByte(0x11);
      ValueEncoder b = writeByte(0x22);
      ByteCollector sink = new ByteCollector();
      sink.capacity = 0;

      ValueEncoder seq = a.andThen(b);

      assertFalse(seq.generate(new EmptyEventSource(), sink));
      assertEquals(sink.bytes, List.of());

      sink.capacity = Integer.MAX_VALUE;
      assertTrue(seq.generate(new EmptyEventSource(), sink));
      assertEquals(sink.bytes, List.of(0x11, 0x22));
   }

   @Test
   public void propagatesSinkBackpressureFromSecond() {
      ValueEncoder a = writeByte(0x11);
      ValueEncoder b = writeByte(0x22);
      ByteCollector sink = new ByteCollector();
      sink.capacity = 1; // room for first byte only

      ValueEncoder seq = a.andThen(b);

      assertFalse(seq.generate(new EmptyEventSource(), sink));
      assertEquals(sink.bytes, List.of(0x11));

      sink.capacity = Integer.MAX_VALUE;
      assertTrue(seq.generate(new EmptyEventSource(), sink));
      assertEquals(sink.bytes, List.of(0x11, 0x22));
   }

   @Test
   public void chainsMoreThanTwoEncoders() {
      ValueEncoder a = writeByte(0x11);
      ValueEncoder b = writeByte(0x22);
      ValueEncoder c = writeByte(0x33);
      ByteCollector sink = new ByteCollector();

      assertTrue(a.andThen(b).andThen(c).generate(new EmptyEventSource(), sink));

      assertEquals(sink.bytes, List.of(0x11, 0x22, 0x33));
   }

   private static ValueEncoder writeByte(int value) {
      return (events, sink) -> {
         if (sink.writableBytes() <= 0) {
            return false;
         }
         sink.writeUnsignedByte(value);
         return true;
      };
   }

   private static final class CountingEncoder implements ValueEncoder {
      int calls = 0;

      @Override
      public boolean generate(EventSource events, BitSink sink) {
         calls++;
         return true;
      }
   }

   private static final class MultiStepEncoder implements ValueEncoder {
      int remaining;

      MultiStepEncoder(int steps) { this.remaining = steps; }

      @Override
      public boolean generate(EventSource events, BitSink sink) {
         remaining--;
         return remaining <= 0;
      }
   }

   private static final class EmptyEventSource implements EventSource {
      @Override
      public int availableEvents() {
         return 0;
      }

      @Override
      public Event read() {
         throw new IllegalStateException("no events available");
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
