package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.DecodingEvent;
import com.vanillasource.scan.types.codec2.DecodingEvent.IntegerScalar;
import com.vanillasource.scan.types.codec2.bit.FedBitReader;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class IntegerDecoderTests {

   // ---- unsigned, completes in a single parse() call ----

   @Test
   public void unsignedOneByteZero() {
      assertDecodes(1, false, new byte[] { 0x00 }, new IntegerScalar(0L, 0));
   }

   @Test
   public void unsignedOneByteMid() {
      assertDecodes(1, false, new byte[] { 0x42 }, new IntegerScalar(0x42L, 1));
   }

   @Test
   public void unsignedOneByteMax() {
      assertDecodes(1, false, new byte[] { (byte) 0xFF }, new IntegerScalar(0xFFL, 1));
   }

   @Test
   public void unsignedTwoByteBigEndian() {
      assertDecodes(2, false, new byte[] { 0x12, 0x34 }, new IntegerScalar(0x1234L, 1));
   }

   @Test
   public void unsignedTwoByteMax() {
      assertDecodes(2, false, new byte[] { (byte) 0xFF, (byte) 0xFF },
            new IntegerScalar(0xFFFFL, 1));
   }

   @Test
   public void unsignedFourByteMax() {
      assertDecodes(4, false,
            new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF },
            new IntegerScalar(0xFFFFFFFFL, 1));
   }

   @Test
   public void unsignedEightByteZero() {
      assertDecodes(8, false, new byte[8], new IntegerScalar(0L, 0));
   }

   @Test
   public void unsignedEightByteMax() {
      // All bits set: as a long this looks like -1, but conceptually it is 2^64 - 1 → sign = +1.
      byte[] in = new byte[] {
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
      assertDecodes(8, false, in, new IntegerScalar(-1L, 1));
   }

   @Test
   public void unsignedEightByteHighBitSetIsStillPositive() {
      // 0x8000_0000_0000_0000: long = Long.MIN_VALUE, conceptual = 2^63 → sign = +1.
      byte[] in = new byte[] {
            (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
      assertDecodes(8, false, in, new IntegerScalar(Long.MIN_VALUE, 1));
   }

   // ---- signed, completes in a single parse() call ----

   @Test
   public void signedOneByteZero() {
      assertDecodes(1, true, new byte[] { 0x00 }, new IntegerScalar(0L, 0));
   }

   @Test
   public void signedOneBytePositive() {
      assertDecodes(1, true, new byte[] { 0x2A }, new IntegerScalar(42L, 1));
   }

   @Test
   public void signedOneByteMinusOne() {
      assertDecodes(1, true, new byte[] { (byte) 0xFF }, new IntegerScalar(-1L, -1));
   }

   @Test
   public void signedOneByteMax() {
      assertDecodes(1, true, new byte[] { 0x7F },
            new IntegerScalar((long) Byte.MAX_VALUE, 1));
   }

   @Test
   public void signedOneByteMin() {
      assertDecodes(1, true, new byte[] { (byte) 0x80 },
            new IntegerScalar((long) Byte.MIN_VALUE, -1));
   }

   @Test
   public void signedTwoByteMax() {
      assertDecodes(2, true, new byte[] { 0x7F, (byte) 0xFF },
            new IntegerScalar((long) Short.MAX_VALUE, 1));
   }

   @Test
   public void signedTwoByteMin() {
      assertDecodes(2, true, new byte[] { (byte) 0x80, 0x00 },
            new IntegerScalar((long) Short.MIN_VALUE, -1));
   }

   @Test
   public void signedFourByteMax() {
      assertDecodes(4, true,
            new byte[] { 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF },
            new IntegerScalar((long) Integer.MAX_VALUE, 1));
   }

   @Test
   public void signedFourByteMin() {
      assertDecodes(4, true,
            new byte[] { (byte) 0x80, 0x00, 0x00, 0x00 },
            new IntegerScalar((long) Integer.MIN_VALUE, -1));
   }

   @Test
   public void signedEightByteMax() {
      assertDecodes(8, true,
            new byte[] { 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                  (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF },
            new IntegerScalar(Long.MAX_VALUE, 1));
   }

   @Test
   public void signedEightByteMin() {
      assertDecodes(8, true,
            new byte[] { (byte) 0x80, 0, 0, 0, 0, 0, 0, 0 },
            new IntegerScalar(Long.MIN_VALUE, -1));
   }

   @Test
   public void signedThreeByteSignExtendsNegative() {
      // 0xFFFFFE → as 3-byte two's complement = -2.
      assertDecodes(3, true, new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFE },
            new IntegerScalar(-2L, -1));
   }

   @Test
   public void signedThreeBytePositiveDoesNotSignExtend() {
      // 0x010203 as 3-byte signed positive.
      assertDecodes(3, true, new byte[] { 0x01, 0x02, 0x03 },
            new IntegerScalar(0x010203L, 1));
   }

   @Test
   public void signedThreeByteMinAndMax() {
      // Min for 3-byte signed is -2^23 = -8388608, encoded as 0x800000.
      assertDecodes(3, true, new byte[] { (byte) 0x80, 0x00, 0x00 },
            new IntegerScalar(-8_388_608L, -1));
      // Max for 3-byte signed is 2^23 - 1 = 8388607, encoded as 0x7FFFFF.
      assertDecodes(3, true, new byte[] { 0x7F, (byte) 0xFF, (byte) 0xFF },
            new IntegerScalar(8_388_607L, 1));
   }

   // ---- partial feeds ----

   @Test
   public void waitsForInputWhenNoBytesAvailable() {
      IntegerDecoder decoder = new IntegerDecoder(2, false);
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      assertFalse(decoder.parse(bits, capture));
      assertEquals(capture.events.size(), 0);
   }

   @Test
   public void completesAcrossMultipleSingleByteFeeds() {
      IntegerDecoder decoder = new IntegerDecoder(4, false);
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      bits.write(0x12);
      assertFalse(decoder.parse(bits, capture));
      bits.write(0x34);
      assertFalse(decoder.parse(bits, capture));
      bits.write(0x56);
      assertFalse(decoder.parse(bits, capture));
      bits.write(0x78);
      assertTrue(decoder.parse(bits, capture));

      assertEquals(capture.events, List.of(new IntegerScalar(0x12345678L, 1)));
   }

   @Test
   public void signExtensionUsesAllAccumulatedBytesAcrossFeeds() {
      // Feed sign byte first, then trailing bytes — sign-extension applies once
      // the full width is in.
      IntegerDecoder decoder = new IntegerDecoder(4, true);
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      bits.write(0xFF);
      assertFalse(decoder.parse(bits, capture));
      bits.write(0xFF);
      assertFalse(decoder.parse(bits, capture));
      bits.write(0xFF);
      assertFalse(decoder.parse(bits, capture));
      bits.write(0xFE);
      assertTrue(decoder.parse(bits, capture));

      assertEquals(capture.events, List.of(new IntegerScalar(-2L, -1)));
   }

   @Test
   public void parseDoesNotEmitUntilLastByteArrives() {
      IntegerDecoder decoder = new IntegerDecoder(2, true);
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      bits.write(0x7F);
      assertFalse(decoder.parse(bits, capture));
      assertEquals(capture.events.size(), 0);

      bits.write(0xFF);
      assertTrue(decoder.parse(bits, capture));
      assertEquals(capture.events, List.of(new IntegerScalar((long) Short.MAX_VALUE, 1)));
   }

   @Test
   public void multiByteFeedConsumedInOneParse() {
      IntegerDecoder decoder = new IntegerDecoder(4, true);
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { (byte) 0x80, 0x00, 0x00, 0x00 }, 0, 4);
      assertTrue(decoder.parse(bits, capture));
      assertEquals(capture.events,
            List.of(new IntegerScalar((long) Integer.MIN_VALUE, -1)));
   }

   // ---- zero-width edge case ----

   @Test
   public void zeroByteUnsignedEmitsZeroImmediately() {
      IntegerDecoder decoder = new IntegerDecoder(0, false);
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      assertTrue(decoder.parse(bits, capture));
      assertEquals(capture.events, List.of(new IntegerScalar(0L, 0)));
   }

   @Test
   public void zeroByteSignedEmitsZeroImmediately() {
      IntegerDecoder decoder = new IntegerDecoder(0, true);
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      assertTrue(decoder.parse(bits, capture));
      assertEquals(capture.events, List.of(new IntegerScalar(0L, 0)));
   }

   // ---- helpers ----

   private static void assertDecodes(int byteSize, boolean signed,
         byte[] input, IntegerScalar expected) {
      IntegerDecoder decoder = new IntegerDecoder(byteSize, signed);
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      bits.write(input, 0, input.length);
      assertTrue(decoder.parse(bits, capture),
            "decoder did not complete after feeding " + input.length + " bytes");
      assertEquals(capture.events, List.of(expected));
   }

   private static final class EventCapture
         implements com.vanillasource.scan.types.codec2.DecodingEventHandler {
      final List<DecodingEvent> events = new ArrayList<>();

      @Override
      public void onEvent(DecodingEvent event) {
         events.add(event);
      }
   }
}
