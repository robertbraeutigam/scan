package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.Chunk;
import com.vanillasource.scan.types.codec.Event.StartStream;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.bit.FedBitSource;
import com.vanillasource.scan.types.codec.type.Stream;
import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/**
 * Verifies the chunk-emitting decode path that {@link Stream} dispatches to
 * when its element type is a 1-byte primitive (per TYPES.md
 * §"Incremental Consumption"). Bytes flow as {@code Chunk} events after a
 * single {@code StartStream}, never as per-item {@code StartItem}/{@code EndItem}
 * pairs.
 */
public final class StreamOfBytesTests {

   private static final UnsignedInteger BYTE = new UnsignedInteger(1);

   @Test
   public void emitsStartEvenWithNoBytes() {
      ValueDecoder dec = new Stream(BYTE).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      assertFalse(dec.parse(bits, capture));
      assertEquals(capture.events, List.of(new StartStream()));
   }

   @Test
   public void emitsChunkWhenBytesArrive() {
      ValueDecoder dec = new Stream(BYTE).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x10, 0x20, 0x30 }, 0, 3);
      assertFalse(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartStream(),
            new Chunk(new byte[] { 0x10, 0x20, 0x30 })));
   }

   @Test
   public void emitsSeparateChunksAcrossFeeds() {
      ValueDecoder dec = new Stream(BYTE).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { 0x10, 0x20 }, 0, 2);
      assertFalse(dec.parse(bits, capture));

      bits.write(new byte[] { 0x30, 0x40 }, 0, 2);
      assertFalse(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartStream(),
            new Chunk(new byte[] { 0x10, 0x20 }),
            new Chunk(new byte[] { 0x30, 0x40 })));
   }

   @Test
   public void neverCompletesEvenWhenIdle() {
      ValueDecoder dec = new Stream(BYTE).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x10);
      assertFalse(dec.parse(bits, capture));
      assertFalse(dec.parse(bits, capture));
      assertFalse(dec.parse(bits, capture));
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
