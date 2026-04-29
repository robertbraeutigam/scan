package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.Chunk;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.bit.FedBitSource;
import com.vanillasource.scan.types.codec.type.Array;
import com.vanillasource.scan.types.codec.type.ByteArray;
import com.vanillasource.scan.types.codec.type.ByteStream;
import com.vanillasource.scan.types.codec.type.FloatingPoint;
import com.vanillasource.scan.types.codec.type.Set;
import com.vanillasource.scan.types.codec.type.SignedInteger;
import com.vanillasource.scan.types.codec.type.Stream;
import com.vanillasource.scan.types.codec.type.Struct;
import com.vanillasource.scan.types.codec.type.Union;
import com.vanillasource.scan.types.codec.type.Unit;
import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import com.vanillasource.scan.types.codec.type.VariableLengthInteger;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * Iteration 7: every type's bulk decode is reproduced byte-by-byte. The same
 * input bytes routed through 1-byte feeds must emit the same logical event
 * sequence as a single bulk feed. This is what makes the push design sound —
 * fragmentation across {@code feed()} calls cannot change semantics.
 *
 * <p>Chunk granularity is the one exception. ByteArray/ByteStream emit
 * opportunistic chunks of {@code min(availableBytes, remaining)}, so the
 * sliced run produces many small chunks while the bulk run produces one. We
 * compare after merging adjacent {@code Chunk} events — the byte payload is
 * what's semantically meaningful.
 */
public final class PartialFeedRobustnessTests {

   private static final Type U8 = new UnsignedInteger(1);
   private static final Type U16 = new UnsignedInteger(2);
   private static final Type U32 = new UnsignedInteger(4);
   private static final Type U64 = new UnsignedInteger(8);
   private static final Type S8 = new SignedInteger(1);
   private static final Type S16 = new SignedInteger(2);
   private static final Type S32 = new SignedInteger(4);
   private static final Type S64 = new SignedInteger(8);

   // ---- primitives ----

   @Test
   public void unsigned8() {
      assertPartialFeedMatchesBulk(U8, new byte[] { 0x42 });
   }

   @Test
   public void unsigned16() {
      assertPartialFeedMatchesBulk(U16, new byte[] { 0x12, 0x34 });
   }

   @Test
   public void unsigned32() {
      assertPartialFeedMatchesBulk(U32, new byte[] { 0x12, 0x34, 0x56, 0x78 });
   }

   @Test
   public void unsigned64() {
      assertPartialFeedMatchesBulk(U64,
            new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 });
   }

   @Test
   public void signed8Negative() {
      assertPartialFeedMatchesBulk(S8, new byte[] { (byte) 0xFF });
   }

   @Test
   public void signed16Min() {
      assertPartialFeedMatchesBulk(S16, new byte[] { (byte) 0x80, 0x00 });
   }

   @Test
   public void signed32Negative() {
      assertPartialFeedMatchesBulk(S32,
            new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFE });
   }

   @Test
   public void signed64Max() {
      assertPartialFeedMatchesBulk(S64, new byte[] {
            0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF });
   }

   @Test
   public void variableLengthIntegerSingleByte() {
      assertPartialFeedMatchesBulk(new VariableLengthInteger(4), new byte[] { 0x05 });
   }

   @Test
   public void variableLengthIntegerTwoBytes() {
      // 128 = 0x81 0x00 (continuation, then terminator).
      assertPartialFeedMatchesBulk(new VariableLengthInteger(4),
            new byte[] { (byte) 0x81, 0x00 });
   }

   @Test
   public void variableLengthIntegerMaxNTerminator() {
      // maxBytes=3 fully used: 0xFF 0xFF 0xFF — termination via maxN, not high bit.
      assertPartialFeedMatchesBulk(new VariableLengthInteger(3),
            new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF });
   }

   @Test
   public void floatingPoint32() {
      assertPartialFeedMatchesBulk(new FloatingPoint(4),
            new byte[] { 0x3F, (byte) 0x80, 0x00, 0x00 }); // 1.0f
   }

   @Test
   public void floatingPoint64() {
      assertPartialFeedMatchesBulk(new FloatingPoint(8), new byte[] {
            0x3F, (byte) 0xF0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }); // 1.0
   }

   @Test
   public void unitConsumesNoBytes() {
      assertPartialFeedMatchesBulk(new Unit(), new byte[] {});
   }

   // ---- struct ----

   @Test
   public void structOfMixedSizes() {
      assertPartialFeedMatchesBulk(new Struct(U8, U16, U8),
            new byte[] { 0x10, 0x20, 0x30, 0x40 });
   }

   @Test
   public void nestedStruct() {
      assertPartialFeedMatchesBulk(
            new Struct(new Struct(U8, U8), U16),
            new byte[] { 0x10, 0x20, 0x30, 0x40 });
   }

   @Test
   public void structWithUnitField() {
      assertPartialFeedMatchesBulk(new Struct(new Unit(), U8), new byte[] { 0x55 });
   }

   // ---- union ----

   @Test
   public void unionTwoConstructorsBitDiscThenByteAlignedField() {
      // 1-bit disc consumes byte 0, U8 field consumes byte 1.
      assertPartialFeedMatchesBulk(
            new Union(List.of(List.of(U8), List.of(U8))),
            new byte[] { (byte) 0x80, (byte) 0xAB });
   }

   @Test
   public void unionThreeConstructorsTwoBitDisc() {
      assertPartialFeedMatchesBulk(
            new Union(List.of(List.of(), List.of(U8), List.of(U8, U8))),
            new byte[] { (byte) 0x80, 0x10, 0x20 }); // disc=2 picks third
   }

   @Test
   public void unionInsideStructBitStateCarries() {
      Type bool = new Union(List.of(List.of(), List.of()));
      assertPartialFeedMatchesBulk(new Struct(bool, U8),
            new byte[] { (byte) 0x80, (byte) 0xAB });
   }

   // ---- array ----

   @Test
   public void arrayVariableLength() {
      assertPartialFeedMatchesBulk(new Array(0, 0xFF, U8),
            new byte[] { 0x03, 0x10, 0x20, 0x30 });
   }

   @Test
   public void arrayFixedLengthNoCount() {
      assertPartialFeedMatchesBulk(new Array(3, 3, U8),
            new byte[] { 0x10, 0x20, 0x30 });
   }

   @Test
   public void arrayWithMultiByteVliCount() {
      // Use a Unit item type so the payload is empty — count is the only multi-byte work.
      // count = 200 over a 100_000-cap range encodes as 0x81 0x48.
      assertPartialFeedMatchesBulk(new Array(0, 100_000, new Unit()),
            new byte[] { (byte) 0x81, 0x48 });
   }

   @Test
   public void arrayOfBitPackedBooleans() {
      // 16 booleans alternating True/False: count=0x10, payload=0x55 0x55.
      Type bool = new Union(List.of(List.of(), List.of()));
      assertPartialFeedMatchesBulk(new Array(0, 0xFF, bool),
            new byte[] { 0x10, 0x55, 0x55 });
   }

   @Test
   public void arrayWithNonZeroLowerBound() {
      // min=5, max=10 → wire count 0x02 means 7 items.
      assertPartialFeedMatchesBulk(new Array(5, 10, U8), new byte[] {
            0x02, 0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70 });
   }

   @Test
   public void nestedArrays() {
      assertPartialFeedMatchesBulk(
            new Array(0, 0xFF, new Array(0, 0xFF, U8)),
            new byte[] { 0x02, 0x02, 0x10, 0x20, 0x01, 0x30 });
   }

   // ---- set ----

   @Test
   public void setSingleByteBitmask() {
      assertPartialFeedMatchesBulk(new Set(4), new byte[] { (byte) 0xC0 });
   }

   @Test
   public void setMultiByteBitmask() {
      assertPartialFeedMatchesBulk(new Set(10),
            new byte[] { (byte) 0x81, (byte) 0xC0 });
   }

   // ---- stream ----

   @Test
   public void streamOfU8WithSeveralItems() {
      assertPartialFeedMatchesBulk(new Stream(U8),
            new byte[] { 0x10, 0x20, 0x30, 0x40 });
   }

   @Test
   public void streamOfU16SplitsItemsAcrossFeeds() {
      assertPartialFeedMatchesBulk(new Stream(U16),
            new byte[] { 0x12, 0x34, 0x56, 0x78 });
   }

   // ---- byte array & byte stream ----

   @Test
   public void byteArrayVariableLength() {
      assertPartialFeedMatchesBulk(new ByteArray(0, 0xFF),
            new byte[] { 0x03, 0x10, 0x20, 0x30 });
   }

   @Test
   public void byteArrayFixedLengthNoCount() {
      assertPartialFeedMatchesBulk(new ByteArray(4, 4),
            new byte[] { 0x01, 0x02, 0x03, 0x04 });
   }

   @Test
   public void byteStreamMultipleBytes() {
      assertPartialFeedMatchesBulk(new ByteStream(),
            new byte[] { 0x10, 0x20, 0x30, 0x40 });
   }

   // ---- helpers ----

   private static void assertPartialFeedMatchesBulk(Type type, byte[] input) {
      List<Event> bulk = mergeAdjacentChunks(decodeAllAtOnce(type, input));
      List<Event> sliced = mergeAdjacentChunks(decodeOneByteAtATime(type, input));
      assertEquals(sliced, bulk,
            "byte-by-byte feed produced a different event sequence than bulk feed");
   }

   private static List<Event> decodeAllAtOnce(Type type, byte[] input) {
      ValueDecoder dec = type.createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture cap = new EventCapture();
      if (input.length > 0) {
         bits.write(input, 0, input.length);
      }
      dec.parse(bits, cap);
      return cap.events;
   }

   private static List<Event> decodeOneByteAtATime(Type type, byte[] input) {
      ValueDecoder dec = type.createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture cap = new EventCapture();
      if (input.length == 0) {
         dec.parse(bits, cap);
         return cap.events;
      }
      for (byte b : input) {
         bits.write(b & 0xFF);
         dec.parse(bits, cap);
      }
      return cap.events;
   }

   /**
    * Collapses runs of adjacent {@link Chunk} events into a single chunk carrying
    * the concatenated bytes. Lets bulk-feed runs (one big chunk) compare equal to
    * sliced runs (many small chunks) without losing the byte-payload check.
    */
   private static List<Event> mergeAdjacentChunks(List<Event> events) {
      List<Event> result = new ArrayList<>();
      ByteArrayOutputStream pending = null;
      for (Event e : events) {
         if (e instanceof Chunk c) {
            if (pending == null) {
               pending = new ByteArrayOutputStream();
            }
            pending.writeBytes(c.bytes());
         } else {
            if (pending != null) {
               result.add(new Chunk(pending.toByteArray()));
               pending = null;
            }
            result.add(e);
         }
      }
      if (pending != null) {
         result.add(new Chunk(pending.toByteArray()));
      }
      return result;
   }

   private static final class EventCapture implements EventSink {
      final List<Event> events = new ArrayList<>();

      @Override
      public int writableEvents() {
         return Integer.MAX_VALUE;
      }

      @Override
      public void write(Event event) {
         events.add(event);
      }
   }
}
