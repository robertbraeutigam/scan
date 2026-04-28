package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.Constructor;
import com.vanillasource.scan.types.codec.Event.ContainerKind;
import com.vanillasource.scan.types.codec.Event.EndContainer;
import com.vanillasource.scan.types.codec.Event.EndItem;
import com.vanillasource.scan.types.codec.Event.StartContainer;
import com.vanillasource.scan.types.codec.Event.StartItem;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.bit.BufferedBitSink;
import com.vanillasource.scan.types.codec.type.Set;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public final class SetEncoderTests {

   @Test
   public void emptyBitmaskFourMembers() {
      ValueEncoder enc = new Set(4).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      events.put(new StartContainer(ContainerKind.SET, 0L));
      events.put(new EndContainer(ContainerKind.SET));

      assertTrue(enc.generate(events, sink));
      sink.closeBits();
      assertEquals(sink.toBytes(), new byte[] { 0x00 });
   }

   @Test
   public void firstTwoOfFourMembersSet() {
      ValueEncoder enc = new Set(4).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      events.put(new StartContainer(ContainerKind.SET, 2L));
      events.put(new StartItem());
      events.put(new Constructor(0));
      events.put(new EndItem());
      events.put(new StartItem());
      events.put(new Constructor(1));
      events.put(new EndItem());
      events.put(new EndContainer(ContainerKind.SET));

      assertTrue(enc.generate(events, sink));
      sink.closeBits();
      assertEquals(sink.toBytes(), new byte[] { (byte) 0xC0 });
   }

   @Test
   public void lastTwoOfFourMembersSet() {
      ValueEncoder enc = new Set(4).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      events.put(new StartContainer(ContainerKind.SET, 2L));
      events.put(new StartItem());
      events.put(new Constructor(2));
      events.put(new EndItem());
      events.put(new StartItem());
      events.put(new Constructor(3));
      events.put(new EndItem());
      events.put(new EndContainer(ContainerKind.SET));

      assertTrue(enc.generate(events, sink));
      sink.closeBits();
      assertEquals(sink.toBytes(), new byte[] { 0x30 });
   }

   @Test
   public void allFourMembersSet() {
      ValueEncoder enc = new Set(4).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      events.put(new StartContainer(ContainerKind.SET, 4L));
      for (int i = 0; i < 4; i++) {
         events.put(new StartItem());
         events.put(new Constructor(i));
         events.put(new EndItem());
      }
      events.put(new EndContainer(ContainerKind.SET));

      assertTrue(enc.generate(events, sink));
      sink.closeBits();
      assertEquals(sink.toBytes(), new byte[] { (byte) 0xF0 });
   }

   @Test
   public void multiByteBitmaskTenMembers() {
      ValueEncoder enc = new Set(10).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      events.put(new StartContainer(ContainerKind.SET, 4L));
      for (int i : new int[] { 0, 7, 8, 9 }) {
         events.put(new StartItem());
         events.put(new Constructor(i));
         events.put(new EndItem());
      }
      events.put(new EndContainer(ContainerKind.SET));

      assertTrue(enc.generate(events, sink));
      sink.closeBits();
      // bit 0 of stream = ctor 0 = 1, bits 1..6 = ctors 1..6 = 0, bit 7 = ctor 7 = 1
      // → byte 0 = 1000_0001 = 0x81
      // bit 8 = ctor 8 = 1, bit 9 = ctor 9 = 1, rest = 0
      // → byte 1 = 1100_0000 = 0xC0
      assertEquals(sink.toBytes(), new byte[] { (byte) 0x81, (byte) 0xC0 });
   }

   @Test
   public void writesNothingUntilStartContainerArrives() {
      ValueEncoder enc = new Set(4).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.size(), 0);
   }

   @Test
   public void invalidConstructorIndexThrows() {
      ValueEncoder enc = new Set(4).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

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
}
