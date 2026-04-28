package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.Constructor;
import com.vanillasource.scan.types.codec.Event.EndField;
import com.vanillasource.scan.types.codec.Event.IntegerScalar;
import com.vanillasource.scan.types.codec.Event.StartField;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.bit.FedBitSource;
import com.vanillasource.scan.types.codec.type.Struct;
import com.vanillasource.scan.types.codec.type.Union;
import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public final class UnionTests {

   private static final Type U8 = new UnsignedInteger(1);

   @Test
   public void singleConstructorWritesNoDiscriminator() {
      // Single-constructor union: 0-bit discriminator. No bytes consumed for it.
      ValueDecoder dec = new Union(List.of(List.of(U8))).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x42);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new Constructor(0),
            new StartField(0),
            new IntegerScalar(0x42L, 1),
            new EndField(0)));
   }

   @Test
   public void singleConstructorWithNoFields() {
      ValueDecoder dec = new Union(List.of(List.of())).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(new Constructor(0)));
   }

   @Test
   public void twoConstructorsOneBitDiscriminatorPicksFirst() {
      // Boolean-shaped: True | False. 1-bit discriminator. 0 → first constructor.
      ValueDecoder dec = new Union(List.of(List.of(), List.of())).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x00); // 0b00000000 → top bit is 0 → constructor 0
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(new Constructor(0)));
   }

   @Test
   public void twoConstructorsOneBitDiscriminatorPicksSecond() {
      ValueDecoder dec = new Union(List.of(List.of(), List.of())).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x80); // 0b10000000 → top bit is 1 → constructor 1
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(new Constructor(1)));
   }

   @Test
   public void twoConstructorsWithFieldsBitStateCarriesIntoBody() {
      // Constructor 0: U8.   Constructor 1: U8.
      // 1-bit discriminator + 7 bits unused in same byte, then byte-aligned U8.
      ValueDecoder dec = new Union(List.of(List.of(U8), List.of(U8))).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      // bit byte: 0b1_0000000 (disc=1, 7 unused), then 0xAB
      bits.write(new byte[] { (byte) 0x80, (byte) 0xAB }, 0, 2);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new Constructor(1),
            new StartField(0),
            new IntegerScalar(0xABL, 1),
            new EndField(0)));
   }

   @Test
   public void threeConstructorsTwoBitDiscriminator() {
      // 3 constructors → ceil(log2 3) = 2-bit discriminator.
      ValueDecoder dec = new Union(List.of(
            List.of(),
            List.of(U8),
            List.of(U8, U8))).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      // disc = 0b10 in top 2 bits → 0xC0 = 0b11000000 → wait, 0x80 = 0b10...
      // 0b10 in top 2 bits → byte = 0b10_000000 = 0x80; then 2 fields of U8 each.
      bits.write(new byte[] { (byte) 0x80, 0x10, 0x20 }, 0, 3);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new Constructor(2),
            new StartField(0),
            new IntegerScalar(0x10L, 1),
            new EndField(0),
            new StartField(1),
            new IntegerScalar(0x20L, 1),
            new EndField(1)));
   }

   @Test
   public void fourConstructorsTwoBitDiscriminator() {
      // 4 constructors → 2-bit discriminator. Test all four.
      Type union = new Union(List.of(
            List.of(),
            List.of(),
            List.of(),
            List.of()));
      for (int i = 0; i < 4; i++) {
         ValueDecoder dec = union.createDecoder();
         FedBitSource bits = new FedBitSource();
         EventCapture capture = new EventCapture();

         bits.write(i << 6); // top 2 bits = i
         assertTrue(dec.parse(bits, capture));
         assertEquals(capture.events, List.of(new Constructor(i)));
      }
   }

   @Test
   public void waitsForDiscriminatorByte() {
      ValueDecoder dec = new Union(List.of(List.of(), List.of())).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      assertFalse(dec.parse(bits, capture));
      assertEquals(capture.events, List.of());

      bits.write(0x80);
      assertTrue(dec.parse(bits, capture));
   }

   @Test
   public void waitsWhenSinkFullOnDiscriminator() {
      ValueDecoder dec = new Union(List.of(List.of(U8), List.of(U8))).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { (byte) 0x80, (byte) 0xAB }, 0, 2);
      capture.capacity = 0;
      assertFalse(dec.parse(bits, capture));
      assertEquals(capture.events.size(), 0);

      capture.capacity = Integer.MAX_VALUE;
      assertTrue(dec.parse(bits, capture));
      assertEquals(capture.events, List.of(
            new Constructor(1),
            new StartField(0),
            new IntegerScalar(0xABL, 1),
            new EndField(0)));
   }

   @Test
   public void invalidDiscriminatorThrows() {
      // 3 constructors → 2-bit discriminator, valid values 0..2. 3 is invalid.
      ValueDecoder dec = new Union(List.of(
            List.of(),
            List.of(),
            List.of())).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0xC0); // top 2 bits = 0b11 = 3 → out of range
      assertThrows(IllegalStateException.class, () -> dec.parse(bits, capture));
   }

   @Test
   public void unionInsideStructBitStatePassesThrough() {
      // Struct(Union(True | False), U8): 1-bit disc shares byte with subsequent U8?
      // Actually no — bit state carries within struct, so the disc and U8 share a byte.
      // But this test uses a U8 (byte-aligned), so disc takes 1 bit + 7 zeros, then U8.
      Type unionType = new Union(List.of(List.of(), List.of()));
      ValueDecoder dec = new Struct(unionType, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      // Byte 0: disc=1 + zeros, Byte 1: U8 = 0xAB
      bits.write(new byte[] { (byte) 0x80, (byte) 0xAB }, 0, 2);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartField(0),
            new Constructor(1),
            new EndField(0),
            new StartField(1),
            new IntegerScalar(0xABL, 1),
            new EndField(1)));
   }

   private static final class EventCapture implements EventSink {
      final List<Event> events = new ArrayList<>();
      int capacity = Integer.MAX_VALUE;

      @Override
      public int writableEvents() {
         return capacity;
      }

      @Override
      public void put(Event event) {
         if (capacity != Integer.MAX_VALUE) {
            capacity--;
         }
         events.add(event);
      }
   }
}
