package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.EndField;
import com.vanillasource.scan.types.codec.Event.IntegerScalar;
import com.vanillasource.scan.types.codec.Event.StartField;
import com.vanillasource.scan.types.codec.Event.UnitScalar;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.bit.FedBitSource;
import com.vanillasource.scan.types.codec.type.Struct;
import com.vanillasource.scan.types.codec.type.Unit;
import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class StructTests {

   private static final Type U8 = new UnsignedInteger(1);
   private static final Type U16 = new UnsignedInteger(2);

   @Test
   public void emptyStructEmitsNothingAndCompletes() {
      ValueDecoder dec = new Struct().createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      assertTrue(dec.parse(bits, capture));
      assertEquals(capture.events, List.of());
   }

   @Test
   public void singleFieldStruct() {
      ValueDecoder dec = new Struct(U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x42);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartField(0),
            new IntegerScalar(0x42L, 1),
            new EndField(0)));
   }

   @Test
   public void multipleFieldsEmitInDeclarationOrder() {
      ValueDecoder dec = new Struct(U8, U16, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x10, 0x20, 0x30, 0x40 }, 0, 4);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartField(0),
            new IntegerScalar(0x10L, 1),
            new EndField(0),
            new StartField(1),
            new IntegerScalar(0x2030L, 1),
            new EndField(1),
            new StartField(2),
            new IntegerScalar(0x40L, 1),
            new EndField(2)));
   }

   @Test
   public void unitFieldEmitsUnitScalarBetweenStartAndEnd() {
      ValueDecoder dec = new Struct(new Unit(), U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x55);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartField(0),
            new UnitScalar(),
            new EndField(0),
            new StartField(1),
            new IntegerScalar(0x55L, 1),
            new EndField(1)));
   }

   @Test
   public void completesAcrossPartialFeeds() {
      ValueDecoder dec = new Struct(U16, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      assertFalse(dec.parse(bits, capture));

      bits.write(0x12);
      assertFalse(dec.parse(bits, capture));

      bits.write(0x34);
      assertFalse(dec.parse(bits, capture));

      bits.write(0x56);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartField(0),
            new IntegerScalar(0x1234L, 1),
            new EndField(0),
            new StartField(1),
            new IntegerScalar(0x56L, 1),
            new EndField(1)));
   }

   @Test
   public void waitsWhenSinkFillsMidStruct() {
      ValueDecoder dec = new Struct(U8, U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x11, 0x22 }, 0, 2);
      capture.capacity = 3; // StartField, IntegerScalar, EndField — then full

      assertFalse(dec.parse(bits, capture));
      assertEquals(capture.events.size(), 3);

      capture.capacity = Integer.MAX_VALUE;
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartField(0),
            new IntegerScalar(0x11L, 1),
            new EndField(0),
            new StartField(1),
            new IntegerScalar(0x22L, 1),
            new EndField(1)));
   }

   @Test
   public void nestedStructs() {
      ValueDecoder dec = new Struct(
            new Struct(U8, U8),
            U16).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x10, 0x20, 0x30, 0x40 }, 0, 4);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartField(0),
            new StartField(0),
            new IntegerScalar(0x10L, 1),
            new EndField(0),
            new StartField(1),
            new IntegerScalar(0x20L, 1),
            new EndField(1),
            new EndField(0),
            new StartField(1),
            new IntegerScalar(0x3040L, 1),
            new EndField(1)));
   }

   @Test
   public void doesNotConsumeBytesPastEnd() {
      ValueDecoder dec = new Struct(U8).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x42, (byte) 0xAB }, 0, 2);
      assertTrue(dec.parse(bits, capture));

      assertEquals(bits.availableBytes(), 1);
      assertEquals(bits.readUnsignedByte(), 0xAB);
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
