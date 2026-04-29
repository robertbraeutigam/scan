package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.FloatingPointScalar;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.bit.FedBitSource;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public final class FloatingPointDecoderTests {

   // ---- construction ----

   @Test
   public void rejectsByteSizeOtherThanFourOrEight() {
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointDecoder(0));
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointDecoder(1));
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointDecoder(2));
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointDecoder(3));
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointDecoder(5));
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointDecoder(7));
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointDecoder(16));
      assertThrows(IllegalArgumentException.class, () -> new FloatingPointDecoder(-1));
   }

   // ---- 32-bit, single parse ----

   @Test
   public void decodes32BitZero() {
      assertDecodes32(0.0f, new byte[] { 0, 0, 0, 0 });
   }

   @Test
   public void decodes32BitNegativeZero() {
      assertDecodes32(-0.0f, new byte[] { (byte) 0x80, 0, 0, 0 });
   }

   @Test
   public void decodes32BitOne() {
      assertDecodes32(1.0f, new byte[] { 0x3F, (byte) 0x80, 0, 0 });
   }

   @Test
   public void decodes32BitNegativeOne() {
      assertDecodes32(-1.0f, new byte[] { (byte) 0xBF, (byte) 0x80, 0, 0 });
   }

   @Test
   public void decodes32BitPositiveInfinity() {
      assertDecodes32(Float.POSITIVE_INFINITY,
            new byte[] { 0x7F, (byte) 0x80, 0, 0 });
   }

   @Test
   public void decodes32BitNegativeInfinity() {
      assertDecodes32(Float.NEGATIVE_INFINITY,
            new byte[] { (byte) 0xFF, (byte) 0x80, 0, 0 });
   }

   @Test
   public void decodes32BitMaxValue() {
      assertDecodes32(Float.MAX_VALUE,
            new byte[] { 0x7F, 0x7F, (byte) 0xFF, (byte) 0xFF });
   }

   @Test
   public void decodes32BitNaNPreservesBitPattern() {
      // Quiet NaN with payload bit
      int nanBits = 0x7FC00001;
      byte[] in = new byte[] {
            (byte) ((nanBits >>> 24) & 0xFF),
            (byte) ((nanBits >>> 16) & 0xFF),
            (byte) ((nanBits >>> 8) & 0xFF),
            (byte) (nanBits & 0xFF) };
      FloatingPointDecoder decoder = new FloatingPointDecoder(4);
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(in, 0, 4);
      assertTrue(decoder.parse(bits, capture));
      FloatingPointScalar event = (FloatingPointScalar) capture.events.get(0);
      assertEquals(Float.floatToRawIntBits((float) event.value()), nanBits);
   }

   // ---- 64-bit, single parse ----

   @Test
   public void decodes64BitZero() {
      assertDecodes64(0.0, new byte[8]);
   }

   @Test
   public void decodes64BitOne() {
      assertDecodes64(1.0,
            new byte[] { 0x3F, (byte) 0xF0, 0, 0, 0, 0, 0, 0 });
   }

   @Test
   public void decodes64BitNegativeOne() {
      assertDecodes64(-1.0,
            new byte[] { (byte) 0xBF, (byte) 0xF0, 0, 0, 0, 0, 0, 0 });
   }

   @Test
   public void decodes64BitPi() {
      long bits = Double.doubleToRawLongBits(Math.PI);
      byte[] in = longToBigEndian(bits);
      assertDecodes64(Math.PI, in);
   }

   @Test
   public void decodes64BitPositiveInfinity() {
      assertDecodes64(Double.POSITIVE_INFINITY,
            new byte[] { 0x7F, (byte) 0xF0, 0, 0, 0, 0, 0, 0 });
   }

   @Test
   public void decodes64BitNegativeInfinity() {
      assertDecodes64(Double.NEGATIVE_INFINITY,
            new byte[] { (byte) 0xFF, (byte) 0xF0, 0, 0, 0, 0, 0, 0 });
   }

   @Test
   public void decodes64BitMaxValue() {
      long bits = Double.doubleToRawLongBits(Double.MAX_VALUE);
      assertDecodes64(Double.MAX_VALUE, longToBigEndian(bits));
   }

   @Test
   public void decodes64BitMinPositive() {
      long bits = Double.doubleToRawLongBits(Double.MIN_VALUE);
      assertDecodes64(Double.MIN_VALUE, longToBigEndian(bits));
   }

   @Test
   public void decodes64BitNaNPreservesBitPattern() {
      long nanBits = 0x7FF8000000000001L;
      byte[] in = longToBigEndian(nanBits);
      FloatingPointDecoder decoder = new FloatingPointDecoder(8);
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(in, 0, 8);
      assertTrue(decoder.parse(bits, capture));
      FloatingPointScalar event = (FloatingPointScalar) capture.events.get(0);
      assertEquals(Double.doubleToRawLongBits(event.value()), nanBits);
   }

   // ---- partial feeds ----

   @Test
   public void waitsForInputWhenNoBytesAvailable() {
      FloatingPointDecoder decoder = new FloatingPointDecoder(4);
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      assertFalse(decoder.parse(bits, capture));
      assertEquals(capture.events.size(), 0);
   }

   @Test
   public void completes32BitAcrossSingleByteFeeds() {
      // 1.0f = 0x3F800000
      FloatingPointDecoder decoder = new FloatingPointDecoder(4);
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x3F);
      assertFalse(decoder.parse(bits, capture));
      bits.write(0x80);
      assertFalse(decoder.parse(bits, capture));
      bits.write(0x00);
      assertFalse(decoder.parse(bits, capture));
      bits.write(0x00);
      assertTrue(decoder.parse(bits, capture));

      assertEquals(capture.events, List.of(new FloatingPointScalar(1.0f)));
   }

   @Test
   public void completes64BitAcrossSingleByteFeeds() {
      // 1.0 = 0x3FF0000000000000
      FloatingPointDecoder decoder = new FloatingPointDecoder(8);
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      int[] bytes = { 0x3F, 0xF0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
      for (int i = 0; i < 7; i++) {
         bits.write(bytes[i]);
         assertFalse(decoder.parse(bits, capture),
               "should still be waiting after byte " + i);
      }
      bits.write(bytes[7]);
      assertTrue(decoder.parse(bits, capture));

      assertEquals(capture.events, List.of(new FloatingPointScalar(1.0)));
   }

   @Test
   public void parseDoesNotEmitUntilLastByteArrives() {
      FloatingPointDecoder decoder = new FloatingPointDecoder(4);
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x3F, (byte) 0x80, 0x00 }, 0, 3);
      assertFalse(decoder.parse(bits, capture));
      assertEquals(capture.events.size(), 0);

      bits.write(0x00);
      assertTrue(decoder.parse(bits, capture));
      assertEquals(capture.events, List.of(new FloatingPointScalar(1.0f)));
   }

   @Test
   public void multiByteFeedConsumedInOneParse() {
      FloatingPointDecoder decoder = new FloatingPointDecoder(8);
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x3F, (byte) 0xF0, 0, 0, 0, 0, 0, 0 }, 0, 8);
      assertTrue(decoder.parse(bits, capture));

      assertEquals(capture.events, List.of(new FloatingPointScalar(1.0)));
   }

   // ---- helpers ----

   private static void assertDecodes32(float expected, byte[] input) {
      FloatingPointDecoder decoder = new FloatingPointDecoder(4);
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(input, 0, input.length);
      assertTrue(decoder.parse(bits, capture));
      FloatingPointScalar event = (FloatingPointScalar) capture.events.get(0);
      assertEquals(Float.floatToRawIntBits((float) event.value()),
            Float.floatToRawIntBits(expected));
   }

   private static void assertDecodes64(double expected, byte[] input) {
      FloatingPointDecoder decoder = new FloatingPointDecoder(8);
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(input, 0, input.length);
      assertTrue(decoder.parse(bits, capture));
      FloatingPointScalar event = (FloatingPointScalar) capture.events.get(0);
      assertEquals(Double.doubleToRawLongBits(event.value()),
            Double.doubleToRawLongBits(expected));
   }

   private static byte[] longToBigEndian(long v) {
      byte[] out = new byte[8];
      for (int i = 7; i >= 0; i--) {
         out[i] = (byte) (v & 0xFF);
         v >>>= 8;
      }
      return out;
   }

   private static final class EventCapture implements EventSink {
      final List<Event> events = new ArrayList<>();
      int capacity = Integer.MAX_VALUE;

      @Override
      public int writableEvents() {
         return capacity;
      }

      @Override
      public void write(Event event) {
         events.add(event);
      }
   }
}
