package com.vanillasource.scan.types.codec2.encoder;

import com.vanillasource.scan.types.codec2.BitSink;
import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.Event.Constructor;
import com.vanillasource.scan.types.codec2.Event.ContainerKind;
import com.vanillasource.scan.types.codec2.Event.EndContainer;
import com.vanillasource.scan.types.codec2.Event.EndItem;
import com.vanillasource.scan.types.codec2.Event.StartContainer;
import com.vanillasource.scan.types.codec2.Event.StartItem;
import com.vanillasource.scan.types.codec2.EventSource;
import com.vanillasource.scan.types.codec2.ValueEncoder;
import com.vanillasource.scan.types.codec2.type.BitmaskSet;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public final class BitmaskSetEncoderTests {

   @Test
   public void emptyBitmaskFourMembers() {
      ValueEncoder enc = new BitmaskSet(4).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.SET, 0L));
      events.put(new EndContainer(ContainerKind.SET));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x00));
   }

   @Test
   public void firstTwoOfFourMembersSet() {
      ValueEncoder enc = new BitmaskSet(4).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.SET, 2L));
      events.put(new StartItem());
      events.put(new Constructor(0));
      events.put(new EndItem());
      events.put(new StartItem());
      events.put(new Constructor(1));
      events.put(new EndItem());
      events.put(new EndContainer(ContainerKind.SET));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0xC0));
   }

   @Test
   public void lastTwoOfFourMembersSet() {
      ValueEncoder enc = new BitmaskSet(4).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.SET, 2L));
      events.put(new StartItem());
      events.put(new Constructor(2));
      events.put(new EndItem());
      events.put(new StartItem());
      events.put(new Constructor(3));
      events.put(new EndItem());
      events.put(new EndContainer(ContainerKind.SET));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x30));
   }

   @Test
   public void allFourMembersSet() {
      ValueEncoder enc = new BitmaskSet(4).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.SET, 4L));
      for (int i = 0; i < 4; i++) {
         events.put(new StartItem());
         events.put(new Constructor(i));
         events.put(new EndItem());
      }
      events.put(new EndContainer(ContainerKind.SET));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0xF0));
   }

   @Test
   public void multiByteBitmaskTenMembers() {
      ValueEncoder enc = new BitmaskSet(10).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.SET, 4L));
      for (int i : new int[] { 0, 7, 8, 9 }) {
         events.put(new StartItem());
         events.put(new Constructor(i));
         events.put(new EndItem());
      }
      events.put(new EndContainer(ContainerKind.SET));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x81, 0xC0));
   }

   @Test
   public void writesNothingUntilStartContainerArrives() {
      ValueEncoder enc = new BitmaskSet(4).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes.size(), 0);
   }

   @Test
   public void invalidConstructorIndexThrows() {
      ValueEncoder enc = new BitmaskSet(4).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartContainer(ContainerKind.SET, 1L));
      events.put(new StartItem());
      events.put(new Constructor(4));
      events.put(new EndItem());
      events.put(new EndContainer(ContainerKind.SET));

      assertThrows(IllegalArgumentException.class, () -> enc.generate(events, sink));
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
