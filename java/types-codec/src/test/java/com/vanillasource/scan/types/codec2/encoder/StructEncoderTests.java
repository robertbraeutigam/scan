package com.vanillasource.scan.types.codec2.encoder;

import com.vanillasource.scan.types.codec2.BitSink;
import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.Event.EndField;
import com.vanillasource.scan.types.codec2.Event.IntegerScalar;
import com.vanillasource.scan.types.codec2.Event.StartField;
import com.vanillasource.scan.types.codec2.Event.UnitScalar;
import com.vanillasource.scan.types.codec2.EventSource;
import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueEncoder;
import com.vanillasource.scan.types.codec2.type.Struct;
import com.vanillasource.scan.types.codec2.type.Unit;
import com.vanillasource.scan.types.codec2.type.UnsignedInteger;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class StructEncoderTests {

   private static final Type U8 = new UnsignedInteger(1);
   private static final Type U16 = new UnsignedInteger(2);

   @Test
   public void emptyStructWritesNothingAndCompletes() {
      ValueEncoder enc = new Struct().createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of());
   }

   @Test
   public void singleFieldStruct() {
      ValueEncoder enc = new Struct(U8).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartField(0));
      events.put(new IntegerScalar(0x42L, 1));
      events.put(new EndField(0));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x42));
   }

   @Test
   public void multipleFieldsWriteInDeclarationOrder() {
      ValueEncoder enc = new Struct(U8, U16, U8).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartField(0));
      events.put(new IntegerScalar(0x10L, 1));
      events.put(new EndField(0));
      events.put(new StartField(1));
      events.put(new IntegerScalar(0x2030L, 1));
      events.put(new EndField(1));
      events.put(new StartField(2));
      events.put(new IntegerScalar(0x40L, 1));
      events.put(new EndField(2));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x10, 0x20, 0x30, 0x40));
   }

   @Test
   public void unitFieldWritesNoBytes() {
      ValueEncoder enc = new Struct(new Unit(), U8).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartField(0));
      events.put(new UnitScalar());
      events.put(new EndField(0));
      events.put(new StartField(1));
      events.put(new IntegerScalar(0x55L, 1));
      events.put(new EndField(1));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x55));
   }

   @Test
   public void completesAcrossPartialEventFeeds() {
      ValueEncoder enc = new Struct(U16, U8).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartField(0));
      assertFalse(enc.generate(events, sink));

      events.put(new IntegerScalar(0x1234L, 1));
      assertFalse(enc.generate(events, sink));

      events.put(new EndField(0));
      assertFalse(enc.generate(events, sink));

      events.put(new StartField(1));
      assertFalse(enc.generate(events, sink));

      events.put(new IntegerScalar(0x56L, 1));
      assertFalse(enc.generate(events, sink));

      events.put(new EndField(1));
      assertTrue(enc.generate(events, sink));

      assertEquals(sink.bytes, List.of(0x12, 0x34, 0x56));
   }

   @Test
   public void nestedStructs() {
      ValueEncoder enc = new Struct(
            new Struct(U8, U8),
            U16).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartField(0));
      events.put(new StartField(0));
      events.put(new IntegerScalar(0x10L, 1));
      events.put(new EndField(0));
      events.put(new StartField(1));
      events.put(new IntegerScalar(0x20L, 1));
      events.put(new EndField(1));
      events.put(new EndField(0));
      events.put(new StartField(1));
      events.put(new IntegerScalar(0x3040L, 1));
      events.put(new EndField(1));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x10, 0x20, 0x30, 0x40));
   }

   @Test
   public void doesNotConsumeEventsPastEnd() {
      ValueEncoder enc = new Struct(U8).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartField(0));
      events.put(new IntegerScalar(0x42L, 1));
      events.put(new EndField(0));
      events.put(new IntegerScalar(0x99L, 1)); // sentinel: must remain

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x42));
      assertEquals(events.availableEvents(), 1);
      assertTrue(events.read() instanceof IntegerScalar);
   }

   @Test
   public void waitsWhenSinkFillsMidStruct() {
      ValueEncoder enc = new Struct(U8, U8).createEncoder();
      EventQueue events = new EventQueue();
      ByteCollector sink = new ByteCollector();

      events.put(new StartField(0));
      events.put(new IntegerScalar(0x11L, 1));
      events.put(new EndField(0));
      events.put(new StartField(1));
      events.put(new IntegerScalar(0x22L, 1));
      events.put(new EndField(1));

      sink.capacity = 1;
      assertFalse(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x11));

      sink.capacity = Integer.MAX_VALUE;
      assertTrue(enc.generate(events, sink));
      assertEquals(sink.bytes, List.of(0x11, 0x22));
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
