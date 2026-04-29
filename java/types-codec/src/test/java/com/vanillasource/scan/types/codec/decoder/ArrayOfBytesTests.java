package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.Chunk;
import com.vanillasource.scan.types.codec.Event.ContainerKind;
import com.vanillasource.scan.types.codec.Event.EndContainer;
import com.vanillasource.scan.types.codec.Event.StartContainer;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.bit.FedBitSource;
import com.vanillasource.scan.types.codec.type.Array;
import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Verifies the chunk-emitting decode path that {@link Array} dispatches to
 * when its element type is a 1-byte primitive (per TYPES.md
 * §"Incremental Consumption"). Bytes flow as {@code Chunk} events between the
 * usual {@code StartContainer}/{@code EndContainer} markers, never as
 * per-item {@code StartItem}/{@code EndItem} pairs.
 */
public final class ArrayOfBytesTests {

   private static final UnsignedInteger BYTE = new UnsignedInteger(1);

   @Test
   public void emptyArrayOfBytesEmitsOnlyContainerMarkers() {
      ValueDecoder dec = new Array(0, 0xFF, BYTE).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x00); // count = 0
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.ARRAY, 0L),
            new EndContainer(ContainerKind.ARRAY)));
   }

   @Test
   public void singleChunkWhenAllBytesPresent() {
      ValueDecoder dec = new Array(0, 0xFF, BYTE).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x03, 0x10, 0x20, 0x30 }, 0, 4);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.ARRAY, 3L),
            new Chunk(new byte[] { 0x10, 0x20, 0x30 }),
            new EndContainer(ContainerKind.ARRAY)));
   }

   @Test
   public void splitsAcrossPartialFeeds() {
      ValueDecoder dec = new Array(0, 0xFF, BYTE).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x04); // count = 4
      assertFalse(dec.parse(bits, capture));
      assertEquals(capture.events, List.of(new StartContainer(ContainerKind.ARRAY, 4L)));

      bits.write(new byte[] { 0x10, 0x20 }, 0, 2);
      assertFalse(dec.parse(bits, capture));
      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.ARRAY, 4L),
            new Chunk(new byte[] { 0x10, 0x20 })));

      bits.write(new byte[] { 0x30, 0x40 }, 0, 2);
      assertTrue(dec.parse(bits, capture));
      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.ARRAY, 4L),
            new Chunk(new byte[] { 0x10, 0x20 }),
            new Chunk(new byte[] { 0x30, 0x40 }),
            new EndContainer(ContainerKind.ARRAY)));
   }

   @Test
   public void doesNotConsumeBytesPastDeclaredCount() {
      ValueDecoder dec = new Array(0, 0xFF, BYTE).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x02, 0x10, 0x20, (byte) 0xAB }, 0, 4);
      assertTrue(dec.parse(bits, capture));

      // Trailing 0xAB is left for the next consumer.
      assertEquals(bits.availableBytes(), 1);
      assertEquals(bits.readUnsignedByte(), 0xAB);
   }

   @Test
   public void fixedLengthReadsNoCount() {
      // min == max → no count on the wire; decoder reads exactly 4 payload bytes.
      ValueDecoder dec = new Array(4, 4, BYTE).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x01, 0x02, 0x03, 0x04 }, 0, 4);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.ARRAY, 4L),
            new Chunk(new byte[] { 0x01, 0x02, 0x03, 0x04 }),
            new EndContainer(ContainerKind.ARRAY)));
   }

   @Test
   public void nonZeroLowerBoundIsAddedBackToCount() {
      // min = 5, max = 10 → wire count = actual - 5; 0x02 → 7 bytes.
      ValueDecoder dec = new Array(5, 10, BYTE).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x02, 0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70 }, 0, 8);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events.get(0), new StartContainer(ContainerKind.ARRAY, 7L));
      // Total bytes across all Chunk events sums to 7.
      int totalBytes = capture.events.stream()
            .filter(e -> e instanceof Chunk)
            .mapToInt(e -> ((Chunk) e).bytes().length)
            .sum();
      assertEquals(totalBytes, 7);
   }

   @Test
   public void waitsWhenSinkFillsMidStream() {
      ValueDecoder dec = new Array(0, 0xFF, BYTE).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x03, 0x10, 0x20, 0x30 }, 0, 4);
      capture.capacity = 1; // StartContainer only — then full
      assertFalse(dec.parse(bits, capture));
      assertEquals(capture.events, List.of(new StartContainer(ContainerKind.ARRAY, 3L)));

      capture.capacity = Integer.MAX_VALUE;
      assertTrue(dec.parse(bits, capture));
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
         if (capacity != Integer.MAX_VALUE) {
            capacity--;
         }
         events.add(event);
      }
   }
}
