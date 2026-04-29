package com.vanillasource.scan.types.codec.encoder;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.Chunk;
import com.vanillasource.scan.types.codec.Event.StartStream;
import com.vanillasource.scan.types.codec.EventSource;
import com.vanillasource.scan.types.codec.ValueEncoder;
import com.vanillasource.scan.types.codec.bit.BufferedBitSink;
import com.vanillasource.scan.types.codec.type.ByteStream;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public final class ByteStreamEncoderTests {

   @Test
   public void writesNothingForJustStart() {
      ValueEncoder enc = new ByteStream().createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartStream()));
      BufferedBitSink sink = new BufferedBitSink();

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.toBytes().length, 0);
   }

   @Test
   public void writesBytesFromChunks() {
      ValueEncoder enc = new ByteStream().createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartStream(),
            new Chunk(new byte[] { 0x10, 0x20, 0x30 })));
      BufferedBitSink sink = new BufferedBitSink();

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.toBytes(), new byte[] { 0x10, 0x20, 0x30 });
   }

   @Test
   public void writesMultipleChunksAsContiguousBytes() {
      ValueEncoder enc = new ByteStream().createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartStream(),
            new Chunk(new byte[] { 0x10, 0x20 }),
            new Chunk(new byte[] { 0x30 }),
            new Chunk(new byte[] { 0x40, 0x50 })));
      BufferedBitSink sink = new BufferedBitSink();

      assertFalse(enc.generate(events, sink));
      assertEquals(sink.toBytes(), new byte[] { 0x10, 0x20, 0x30, 0x40, 0x50 });
   }

   @Test
   public void neverCompletes() {
      ValueEncoder enc = new ByteStream().createEncoder();
      ListEventSource events = new ListEventSource(List.of(
            new StartStream(),
            new Chunk(new byte[] { 0x10 })));
      BufferedBitSink sink = new BufferedBitSink();

      assertFalse(enc.generate(events, sink));
      assertFalse(enc.generate(events, sink));
      assertFalse(enc.generate(events, sink));
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
