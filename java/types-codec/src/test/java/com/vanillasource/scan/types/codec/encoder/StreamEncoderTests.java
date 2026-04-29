package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.BitSink;
import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.EndItem;
import com.vanillasource.scan.types.codec.Event.IntegerScalar;
import com.vanillasource.scan.types.codec.Event.StartItem;
import com.vanillasource.scan.types.codec.Event.StartStream;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.type.Stream;
import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public final class StreamEncoderTests {

   private static final Type U16 = new UnsignedInteger(2);

   @Test
   public void writesNothingForEmptyStream() {
      ValueEncoder enc = new Stream(U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartStream());

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of());
   }

   @Test
   public void writesItemsAsEventsArrive() {
      ValueEncoder enc = new Stream(U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartStream());
      events.put(new StartItem());
      events.put(new IntegerScalar(0x10L, 1));
      events.put(new EndItem());
      events.put(new StartItem());
      events.put(new IntegerScalar(0x20L, 1));
      events.put(new EndItem());

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x00, 0x10, 0x00, 0x20));
   }

   @Test
   public void neverCompletesEvenWhenIdle() {
      ValueEncoder enc = new Stream(U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartStream());
      events.put(new StartItem());
      events.put(new IntegerScalar(0x10L, 1));
      events.put(new EndItem());

      assertFalse(enc.generate(events, sink));
      assertFalse(enc.generate(events, sink));
      assertFalse(enc.generate(events, sink));
   }

   @Test
   public void completesAcrossPartialEventFeeds() {
      ValueEncoder enc = new Stream(U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartStream());
      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of());

      events.put(new StartItem());
      assertFalse(enc.generate(events, sink));

      events.put(new IntegerScalar(0x1234L, 1));
      assertFalse(enc.generate(events, sink));

      events.put(new EndItem());
      assertFalse(enc.generate(events, sink));

      assertEquals(sink.bytes, List.of(0x12, 0x34));
   }

   @Test
   public void waitsWhenSinkFillsMidStream() {
      ValueEncoder enc = new Stream(U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartStream());
      events.put(new StartItem());
      events.put(new IntegerScalar(0x10L, 1));
      events.put(new EndItem());
      events.put(new StartItem());
      events.put(new IntegerScalar(0x20L, 1));
      events.put(new EndItem());

      sink.capacity = 2;
      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x00, 0x10));

      sink.capacity = Integer.MAX_VALUE;
      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x00, 0x10, 0x00, 0x20));
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
