package com.vanillasource.scan.types.codec2.encoder;

import com.vanillasource.scan.types.codec2.Event;
import com.vanillasource.scan.types.codec2.Event.Constructor;
import com.vanillasource.scan.types.codec2.Event.EndField;
import com.vanillasource.scan.types.codec2.Event.IntegerScalar;
import com.vanillasource.scan.types.codec2.Event.StartField;
import com.vanillasource.scan.types.codec2.EventSource;
import com.vanillasource.scan.types.codec2.Type;
import com.vanillasource.scan.types.codec2.ValueEncoder;
import com.vanillasource.scan.types.codec2.bit.BufferedBitSink;
import com.vanillasource.scan.types.codec2.type.Struct;
import com.vanillasource.scan.types.codec2.type.Union;
import com.vanillasource.scan.types.codec2.type.UnsignedInteger;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public final class UnionEncoderTests {

   private static final Type U8 = new UnsignedInteger(1);

   @Test
   public void singleConstructorWritesNoDiscriminator() {
      ValueEncoder enc = new Union(List.of(List.of(U8))).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      events.put(new Constructor(0));
      events.put(new StartField(0));
      events.put(new IntegerScalar(0x42L, 1));
      events.put(new EndField(0));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.toBytes(), new byte[] { 0x42 });
   }

   @Test
   public void singleConstructorNoFields() {
      ValueEncoder enc = new Union(List.of(List.of())).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      events.put(new Constructor(0));

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.toBytes(), new byte[] {});
   }

   @Test
   public void twoConstructorsOneBitDiscriminatorPicksFirst() {
      ValueEncoder enc = new Union(List.of(List.of(), List.of())).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      events.put(new Constructor(0));

      assertTrue(enc.generate(events, sink));
      // disc=0 in top bit, 7 unused bits = 0
      assertEquals(sink.size(), 0); // bit byte still open

      sink.closeBits();
      assertEquals(sink.toBytes(), new byte[] { 0x00 });
   }

   @Test
   public void twoConstructorsOneBitDiscriminatorPicksSecond() {
      ValueEncoder enc = new Union(List.of(List.of(), List.of())).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      events.put(new Constructor(1));

      assertTrue(enc.generate(events, sink));
      sink.closeBits();
      assertEquals(sink.toBytes(), new byte[] { (byte) 0x80 });
   }

   @Test
   public void twoConstructorsWithFieldsBitStateCarriesIntoBody() {
      // Constructor 0: U8.   Constructor 1: U8.
      ValueEncoder enc = new Union(List.of(List.of(U8), List.of(U8))).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      events.put(new Constructor(1));
      events.put(new StartField(0));
      events.put(new IntegerScalar(0xABL, 1));
      events.put(new EndField(0));

      assertTrue(enc.generate(events, sink));
      sink.closeBits();
      // Byte 0: disc=1 in top bit, then 7 zeros. Byte 1: 0xAB.
      assertEquals(sink.toBytes(), new byte[] { (byte) 0x80, (byte) 0xAB });
   }

   @Test
   public void threeConstructorsTwoBitDiscriminator() {
      ValueEncoder enc = new Union(List.of(
            List.of(),
            List.of(U8),
            List.of(U8, U8))).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      events.put(new Constructor(2));
      events.put(new StartField(0));
      events.put(new IntegerScalar(0x10L, 1));
      events.put(new EndField(0));
      events.put(new StartField(1));
      events.put(new IntegerScalar(0x20L, 1));
      events.put(new EndField(1));

      assertTrue(enc.generate(events, sink));
      sink.closeBits();
      // disc=0b10 in top 2 bits, then bytes 0x10, 0x20.
      assertEquals(sink.toBytes(), new byte[] { (byte) 0x80, 0x10, 0x20 });
   }

   @Test
   public void fourConstructorsTwoBitDiscriminator() {
      Type unionType = new Union(List.of(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
      for (int i = 0; i < 4; i++) {
         ValueEncoder enc = unionType.createEncoder();
         EventQueue events = new EventQueue();
         BufferedBitSink sink = new BufferedBitSink();

         events.put(new Constructor(i));

         assertTrue(enc.generate(events, sink));
         sink.closeBits();
         assertEquals(sink.toBytes(), new byte[] { (byte) (i << 6) });
      }
   }

   @Test
   public void writesNothingUntilConstructorEvent() {
      ValueEncoder enc = new Union(List.of(List.of(), List.of())).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.size(), 0);
   }

   @Test
   public void unionInsideStructPacksDiscriminatorBeforeByteField() {
      // Struct(Union(True|False), U8): disc=1 bit + 7 zeros in byte 0, U8 in byte 1.
      Type unionType = new Union(List.of(List.of(), List.of()));
      ValueEncoder enc = new Struct(unionType, U8).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      events.put(new StartField(0));
      events.put(new Constructor(1));
      events.put(new EndField(0));
      events.put(new StartField(1));
      events.put(new IntegerScalar(0xABL, 1));
      events.put(new EndField(1));

      assertTrue(enc.generate(events, sink));
      sink.closeBits();
      assertEquals(sink.toBytes(), new byte[] { (byte) 0x80, (byte) 0xAB });
   }

   @Test
   public void invalidConstructorIndexThrows() {
      ValueEncoder enc = new Union(List.of(List.of(), List.of())).createEncoder();
      EventQueue events = new EventQueue();
      BufferedBitSink sink = new BufferedBitSink();

      events.put(new Constructor(2));

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
