package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.Chunk;
import com.vanillasource.scan.types.codec.Event.ContainerKind;
import com.vanillasource.scan.types.codec.Event.EndContainer;
import com.vanillasource.scan.types.codec.Event.StartContainer;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.bit.BufferedBitSink;
import com.vanillasource.scan.types.codec.type.ByteArray;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public final class ByteArrayEncoderTests {

   @Test
   public void writesEmptyByteArrayAsZeroCount() {
      ValueEncoder enc = new ByteArray(0, 0xFF).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.BYTE_ARRAY, 0L),
            new EndContainer(ContainerKind.BYTE_ARRAY)));
      BufferedBitSink sink = new BufferedBitSink();

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.toBytes(), new byte[] { 0x00 });
   }

   @Test
   public void writesCountThenBytesForSingleChunk() {
      ValueEncoder enc = new ByteArray(0, 0xFF).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.BYTE_ARRAY, 3L),
            new Chunk(new byte[] { 0x10, 0x20, 0x30 }),
            new EndContainer(ContainerKind.BYTE_ARRAY)));
      BufferedBitSink sink = new BufferedBitSink();

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.toBytes(), new byte[] { 0x03, 0x10, 0x20, 0x30 });
   }

   @Test
   public void writesMultipleChunksAsContiguousBytes() {
      ValueEncoder enc = new ByteArray(0, 0xFF).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.BYTE_ARRAY, 5L),
            new Chunk(new byte[] { 0x10, 0x20 }),
            new Chunk(new byte[] { 0x30 }),
            new Chunk(new byte[] { 0x40, 0x50 }),
            new EndContainer(ContainerKind.BYTE_ARRAY)));
      BufferedBitSink sink = new BufferedBitSink();

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.toBytes(), new byte[] { 0x05, 0x10, 0x20, 0x30, 0x40, 0x50 });
   }

   @Test
   public void fixedLengthOmitsCount() {
      ValueEncoder enc = new ByteArray(4, 4).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.BYTE_ARRAY, 4L),
            new Chunk(new byte[] { 0x01, 0x02, 0x03, 0x04 }),
            new EndContainer(ContainerKind.BYTE_ARRAY)));
      BufferedBitSink sink = new BufferedBitSink();

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.toBytes(), new byte[] { 0x01, 0x02, 0x03, 0x04 });
   }

   @Test
   public void nonZeroLowerBoundSubtractedFromWireCount() {
      // min = 5, max = 10 → wire count = actual - 5; for 7 actual, wire = 0x02.
      ValueEncoder enc = new ByteArray(5, 10).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.BYTE_ARRAY, 7L),
            new Chunk(new byte[] { 0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70 }),
            new EndContainer(ContainerKind.BYTE_ARRAY)));
      BufferedBitSink sink = new BufferedBitSink();

      assertTrue(enc.generate(events, sink));
      assertEquals(sink.toBytes(), new byte[] {
            0x02, 0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70 });
   }

   @Test
   public void countBelowMinIsRejected() {
      ValueEncoder enc = new ByteArray(2, 10).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.BYTE_ARRAY, 1L)));
      BufferedBitSink sink = new BufferedBitSink();

      expectThrows(IllegalArgumentException.class, () -> enc.generate(events, sink));
   }

   @Test
   public void chunkOverflowingDeclaredCountIsRejected() {
      ValueEncoder enc = new ByteArray(0, 0xFF).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.BYTE_ARRAY, 2L),
            new Chunk(new byte[] { 0x10, 0x20, 0x30 })));
      BufferedBitSink sink = new BufferedBitSink();

      expectThrows(IllegalArgumentException.class, () -> enc.generate(events, sink));
   }

   @Test
   public void fixedLengthRejectsMismatchedCount() {
      ValueEncoder enc = new ByteArray(4, 4).createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartContainer(ContainerKind.BYTE_ARRAY, 5L)));
      BufferedBitSink sink = new BufferedBitSink();

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
