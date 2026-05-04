package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.Chunk;
import com.vanillasource.scan.types.codec.Event.ContainerKind;
import com.vanillasource.scan.types.codec.Event.EndContainer;
import com.vanillasource.scan.types.codec.Event.StartContainer;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.bit.OutputStreamBitSink;
import com.vanillasource.scan.types.codec.type.Array;
import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Verifies the chunk-consuming encode path that {@link Array} dispatches to
 * when its element type is a 1-byte primitive (per TYPES.md
 * §"Incremental Consumption"). The encoder accepts {@code Chunk} events
 * between {@code StartContainer}/{@code EndContainer} and writes their bytes
 * straight to the wire after the count prefix.
 */
public final class ArrayOfBytesEncoderTests {

   private static final UnsignedInteger BYTE = new UnsignedInteger(1);

   @Test
   public void writesEmptyArrayOfBytesAsZeroCount() {
      ValueEncoder enc = new Array(0, 0xFF, BYTE).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.ARRAY, 0L),
            new EndContainer(ContainerKind.ARRAY)));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      OutputStreamBitSink sink = new OutputStreamBitSink(out);

      assertTrue(enc.generate(events, sink));
      assertEquals(out.toByteArray(), new byte[] { 0x00 });
   }

   @Test
   public void writesCountThenBytesForSingleChunk() {
      ValueEncoder enc = new Array(0, 0xFF, BYTE).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.ARRAY, 3L),
            new Chunk(new byte[] { 0x10, 0x20, 0x30 }),
            new EndContainer(ContainerKind.ARRAY)));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      OutputStreamBitSink sink = new OutputStreamBitSink(out);

      assertTrue(enc.generate(events, sink));
      assertEquals(out.toByteArray(), new byte[] { 0x03, 0x10, 0x20, 0x30 });
   }

   @Test
   public void writesMultipleChunksAsContiguousBytes() {
      ValueEncoder enc = new Array(0, 0xFF, BYTE).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.ARRAY, 5L),
            new Chunk(new byte[] { 0x10, 0x20 }),
            new Chunk(new byte[] { 0x30 }),
            new Chunk(new byte[] { 0x40, 0x50 }),
            new EndContainer(ContainerKind.ARRAY)));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      OutputStreamBitSink sink = new OutputStreamBitSink(out);

      assertTrue(enc.generate(events, sink));
      assertEquals(out.toByteArray(), new byte[] { 0x05, 0x10, 0x20, 0x30, 0x40, 0x50 });
   }

   @Test
   public void fixedLengthOmitsCount() {
      ValueEncoder enc = new Array(4, 4, BYTE).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.ARRAY, 4L),
            new Chunk(new byte[] { 0x01, 0x02, 0x03, 0x04 }),
            new EndContainer(ContainerKind.ARRAY)));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      OutputStreamBitSink sink = new OutputStreamBitSink(out);

      assertTrue(enc.generate(events, sink));
      assertEquals(out.toByteArray(), new byte[] { 0x01, 0x02, 0x03, 0x04 });
   }

   @Test
   public void nonZeroLowerBoundSubtractedFromWireCount() {
      // min = 5, max = 10 → wire count = actual - 5; for 7 actual, wire = 0x02.
      ValueEncoder enc = new Array(5, 10, BYTE).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.ARRAY, 7L),
            new Chunk(new byte[] { 0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70 }),
            new EndContainer(ContainerKind.ARRAY)));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      OutputStreamBitSink sink = new OutputStreamBitSink(out);

      assertTrue(enc.generate(events, sink));
      assertEquals(out.toByteArray(), new byte[] {
            0x02, 0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70 });
   }

   @Test
   public void countBelowMinIsRejected() {
      ValueEncoder enc = new Array(2, 10, BYTE).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.ARRAY, 1L)));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      OutputStreamBitSink sink = new OutputStreamBitSink(out);

      expectThrows(IllegalArgumentException.class, () -> enc.generate(events, sink));
   }

   @Test
   public void chunkOverflowingDeclaredCountIsRejected() {
      ValueEncoder enc = new Array(0, 0xFF, BYTE).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.ARRAY, 2L),
            new Chunk(new byte[] { 0x10, 0x20, 0x30 })));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      OutputStreamBitSink sink = new OutputStreamBitSink(out);

      expectThrows(IllegalArgumentException.class, () -> enc.generate(events, sink));
   }

   @Test
   public void fixedLengthRejectsMismatchedCount() {
      ValueEncoder enc = new Array(4, 4, BYTE).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.ARRAY, 5L)));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      OutputStreamBitSink sink = new OutputStreamBitSink(out);

      expectThrows(IllegalArgumentException.class, () -> enc.generate(events, sink));
   }

   private static final class ListEventSource implements EventSource {
      private final List<Event> events;
      private int index = 0;

      ListEventSource(List<Event> events) {
         this.events = events;
      }

      @Override
      public int availableEvents() {
         return events.size() - index;
      }

      @Override
      public Event read() {
         return events.get(index++);
      }
   }
}
