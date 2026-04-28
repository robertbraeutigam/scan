package com.vanillasource.scan.types.codec2.encoder;

import com.vanillasource.scan.types.codec2.BitSink;
import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.Event.ContainerKind;
import com.vanillasource.scan.types.codec2.Event.EndContainer;
import com.vanillasource.scan.types.codec2.Event.EndItem;
import com.vanillasource.scan.types.codec2.Event.IntegerScalar;
import com.vanillasource.scan.types.codec2.Event.StartContainer;
import com.vanillasource.scan.types.codec2.Event.StartItem;
import com.vanillasource.scan.types.codec2.Event.UnitScalar;
import com.vanillasource.scan.types.codec2.EventSource;
import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueEncoder;
import com.vanillasource.scan.types.codec2.type.Array;
import com.vanillasource.scan.types.codec2.type.Unit;
import com.vanillasource.scan.types.codec2.type.UnsignedInteger;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class ArrayEncoderTests {

   private static final Type U8 = new UnsignedInteger(1);
   private static final Type U16 = new UnsignedInteger(2);

   @Test
   public void emptyArrayWritesOnlyCount() {
      ValueEncoder enc = new Array(1, U8).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.ARRAY, 0L));
      events.put(new EndContainer(ContainerKind.ARRAY));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x00));
   }

   @Test
   public void singleItemArray() {
      ValueEncoder enc = new Array(1, U8).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.ARRAY, 1L));
      events.put(new StartItem());
      events.put(new IntegerScalar(0x42L, 1));
      events.put(new EndItem());
      events.put(new EndContainer(ContainerKind.ARRAY));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x01, 0x42));
   }

   @Test
   public void multipleItemsArray() {
      ValueEncoder enc = new Array(1, U8).createEncoder();
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
      assertEquals(sink.bytes, List.of(0x03, 0x10, 0x20, 0x30));
   }

   @Test
   public void writesNothingUntilStartContainerArrives() {
      ValueEncoder enc = new Array(1, U8).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes.size(), 0);
   }

   @Test
   public void completesAcrossPartialEventFeeds() {
      ValueEncoder enc = new Array(1, U16).createEncoder();
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
      ValueEncoder enc = new Array(4, unit).createEncoder();
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
      ValueEncoder enc = new Array(1, new Array(1, U8)).createEncoder();
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
      assertEquals(sink.bytes, List.of(0x02, 0x02, 0x10, 0x20, 0x01, 0x30));
   }

   @Test
   public void doesNotConsumeEventsPastEndContainer() {
      ValueEncoder enc = new Array(1, U8).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.ARRAY, 1L));
      events.put(new StartItem());
      events.put(new IntegerScalar(0x42L, 1));
      events.put(new EndItem());
      events.put(new EndContainer(ContainerKind.ARRAY));
      events.put(new IntegerScalar(0x99L, 1)); // sentinel: must remain

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x01, 0x42));
      assertEquals(events.availableEvents(), 1);
      assertTrue(events.read() instanceof IntegerScalar);
   }

   @Test
   public void waitsWhenSinkFillsMidArray() {
      ValueEncoder enc = new Array(1, U8).createEncoder();
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

      sink.capacity = 2; // count + first item
      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x02, 0x10));

      sink.capacity = Integer.MAX_VALUE;
      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x02, 0x10, 0x20));
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
