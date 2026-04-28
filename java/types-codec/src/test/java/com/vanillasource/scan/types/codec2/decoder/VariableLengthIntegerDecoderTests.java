package com.vanillasource.scan.types.codec2.decoder;

import com.vanillasource.scan.types.codec2.DecodingEvent;
import com.vanillasource.scan.types.codec2.DecodingEvent.IntegerScalar;
import com.vanillasource.scan.types.codec2.DecodingEventHandler;
import com.vanillasource.scan.types.codec2.bit.FedBitReader;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public final class VariableLengthIntegerDecoderTests {

   // ---- construction ----

   @Test
   public void rejectsMaxBytesBelowOne() {
      assertThrows(IllegalArgumentException.class,
            () -> new VariableLengthIntegerDecoder(0));
      assertThrows(IllegalArgumentException.class,
            () -> new VariableLengthIntegerDecoder(-1));
   }

   @Test
   public void rejectsMaxBytesAboveEight() {
      assertThrows(IllegalArgumentException.class,
            () -> new VariableLengthIntegerDecoder(9));
      assertThrows(IllegalArgumentException.class,
            () -> new VariableLengthIntegerDecoder(100));
   }

   // ---- maxBytes=1: a plain unsigned byte ----

   @Test
   public void maxN1ZeroByte() {
      assertDecodes(1, new byte[] { 0x00 }, 0L, 0);
   }

   @Test
   public void maxN1ReadsFullEightBitsWhenLast() {
      // With maxBytes=1, the first (and last) byte uses all 8 bits, even with high bit set.
      assertDecodes(1, new byte[] { (byte) 0xFF }, 0xFFL, 1);
   }

   @Test
   public void maxN1MidValue() {
      assertDecodes(1, new byte[] { 0x42 }, 0x42L, 1);
   }

   // ---- single-byte termination via clear high bit ----

   @Test
   public void singleByteTerminatesViaClearHighBit() {
      // 0x7F: high bit clear, value = 0x7F (127)
      assertDecodes(4, new byte[] { 0x7F }, 127L, 1);
   }

   @Test
   public void singleByteZeroTerminates() {
      assertDecodes(4, new byte[] { 0x00 }, 0L, 0);
   }

   // ---- 7-bit boundary, two-byte cases ----

   @Test
   public void twoByteValueOneTwentyEight() {
      // 128 = 0b1000_0000_0000_0001 over 7-bit groups → 0x81 0x00
      assertDecodes(4, new byte[] { (byte) 0x81, 0x00 }, 128L, 1);
   }

   @Test
   public void twoByteMaxWithMaxN2UsesEightBitTrailer() {
      // maxBytes=2, second byte uses all 8 bits → 7 + 8 = 15 bits.
      // Max 15-bit value = 0x7FFF; encoding: 0xFF 0xFF.
      assertDecodes(2, new byte[] { (byte) 0xFF, (byte) 0xFF },
            (1L << 15) - 1, 1);
   }

   @Test
   public void twoByteValueWithMaxN3UsesSevenBitTrailer() {
      // maxBytes=3, terminating on second byte (high bit clear) → 7+7=14 bits.
      // Encode 16383 = 0x3FFF: 0xFF 0x7F  (first: 0x80 | 0x7F=0xFF; second: 0x7F).
      assertDecodes(3, new byte[] { (byte) 0xFF, 0x7F }, (1L << 14) - 1, 1);
   }

   // ---- maxN reached: trailer uses all 8 bits ----

   @Test
   public void maxN3FullyUsedWith8BitTrailer() {
      // maxBytes=3 fully used → 7+7+8 = 22 bits of payload.
      // Max value = 2^22 - 1 = 0x3FFFFF
      // Bytes: 0xFF 0xFF 0xFF (first two have continuation; the third is terminal by maxN).
      assertDecodes(3, new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF },
            (1L << 22) - 1, 1);
   }

   @Test
   public void maxN8FullyUsedWith8BitTrailer() {
      // maxBytes=8: 7*7 + 8 = 57 bits payload.
      // Encoding 2^57 - 1 = 0x01FF_FFFF_FFFF_FFFF.
      byte[] in = new byte[8];
      for (int i = 0; i < 7; i++) {
         in[i] = (byte) 0xFF;
      }
      in[7] = (byte) 0xFF;
      assertDecodes(8, in, (1L << 57) - 1, 1);
   }

   // ---- partial feeds ----

   @Test
   public void waitsForInputWhenNoBytesAvailable() {
      VariableLengthIntegerDecoder decoder = new VariableLengthIntegerDecoder(4);
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      assertFalse(decoder.parse(bits, capture));
      assertEquals(capture.events.size(), 0);
   }

   @Test
   public void doesNotEmitUntilTerminatorByteArrives() {
      // Encode 128 as 0x81 0x00; first byte has continuation set.
      VariableLengthIntegerDecoder decoder = new VariableLengthIntegerDecoder(3);
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      bits.write(0x81);
      assertFalse(decoder.parse(bits, capture));
      assertEquals(capture.events.size(), 0);

      bits.write(0x00);
      assertTrue(decoder.parse(bits, capture));
      assertEquals(capture.events, List.of(new IntegerScalar(128L, 1)));
   }

   @Test
   public void completesAcrossMultipleSingleByteFeedsUsingMaxN() {
      // maxBytes=3, all three bytes have continuation bit set; termination via maxN.
      VariableLengthIntegerDecoder decoder = new VariableLengthIntegerDecoder(3);
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      bits.write(0xFF);
      assertFalse(decoder.parse(bits, capture));
      bits.write(0xFF);
      assertFalse(decoder.parse(bits, capture));
      bits.write(0xFF);
      assertTrue(decoder.parse(bits, capture));

      assertEquals(capture.events, List.of(new IntegerScalar((1L << 22) - 1, 1)));
   }

   @Test
   public void multiByteFeedConsumedInOneParse() {
      VariableLengthIntegerDecoder decoder = new VariableLengthIntegerDecoder(4);
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      // Encode 128 = 0x81 0x00.
      bits.write(new byte[] { (byte) 0x81, 0x00 }, 0, 2);
      assertTrue(decoder.parse(bits, capture));

      assertEquals(capture.events, List.of(new IntegerScalar(128L, 1)));
   }

   @Test
   public void doesNotConsumeBytesPastTerminator() {
      // First byte (0x05) is a complete value. Second byte (0xAB) must remain.
      VariableLengthIntegerDecoder decoder = new VariableLengthIntegerDecoder(4);
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x05, (byte) 0xAB }, 0, 2);
      assertTrue(decoder.parse(bits, capture));

      assertEquals(capture.events, List.of(new IntegerScalar(5L, 1)));
      assertEquals(bits.availableBytes(), 1);
      assertEquals(bits.readUnsignedByte(), 0xAB);
   }

   // ---- sign reporting ----

   @Test
   public void signIsZeroForZero() {
      assertDecodes(4, new byte[] { 0x00 }, 0L, 0);
   }

   @Test
   public void signIsOneForAnyPositive() {
      assertDecodes(4, new byte[] { 0x01 }, 1L, 1);
   }

   // ---- helpers ----

   private static void assertDecodes(int maxBytes, byte[] input, long expectedValue, int expectedSign) {
      VariableLengthIntegerDecoder decoder = new VariableLengthIntegerDecoder(maxBytes);
      FedBitReader bits = new FedBitReader();
      EventCapture capture = new EventCapture();

      bits.write(input, 0, input.length);
      assertTrue(decoder.parse(bits, capture),
            "decoder did not complete after feeding " + input.length + " bytes");
      assertEquals(capture.events,
            List.of(new IntegerScalar(expectedValue, expectedSign)));
   }

   private static final class EventCapture implements DecodingEventHandler {
      final List<DecodingEvent> events = new ArrayList<>();

      @Override
      public void onEvent(DecodingEvent event) {
         events.add(event);
      }
   }
}
