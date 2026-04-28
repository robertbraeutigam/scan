package com.vanillasource.scan.types.codec2.encoder;

import com.vanillasource.scan.types.codec2.BitSink;
import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.EventSource;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class UnitEncoderTests {

   @Test
   public void completesOnSingleUnitScalarEvent() {
      UnitEncoder encoder = new UnitEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new Event.UnitScalar());
      assertTrue(encoder.generate(events, sink));
      assertEquals(sink.bytes.size(), 0);
   }

   @Test
   public void writesNoBytes() {
      UnitEncoder encoder = new UnitEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new Event.UnitScalar());
      encoder.generate(events, sink);

      assertEquals(sink.bytes.size(), 0);
   }

   @Test
   public void waitsWhenNoEventAvailable() {
      UnitEncoder encoder = new UnitEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      assertFalse(encoder.generate(events, sink));

      events.put(new Event.UnitScalar());
      assertTrue(encoder.generate(events, sink));
   }

   @Test
   public void consumesExactlyOneEvent() {
      UnitEncoder encoder = new UnitEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new Event.UnitScalar());
      events.put(new Event.UnitScalar());
      assertTrue(encoder.generate(events, sink));

      assertEquals(events.availableEvents(), 1);
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
