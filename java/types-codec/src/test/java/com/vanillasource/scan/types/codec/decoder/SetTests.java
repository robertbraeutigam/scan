package com.vanillasource.scan.types.codec.decoder;

import com.vanillasource.scan.types.codec.Event;
import com.vanillasource.scan.types.codec.Event.Constructor;
import com.vanillasource.scan.types.codec.Event.ContainerKind;
import com.vanillasource.scan.types.codec.Event.EndContainer;
import com.vanillasource.scan.types.codec.Event.EndItem;
import com.vanillasource.scan.types.codec.Event.StartContainer;
import com.vanillasource.scan.types.codec.Event.StartItem;
import com.vanillasource.scan.types.codec.EventSink;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.bit.FedBitSource;
import com.vanillasource.scan.types.codec.type.Set;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class SetTests {

   @Test
   public void emptyBitmaskFourMembers() {
      ValueDecoder dec = new Set(4).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x00);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.SET, 0L),
            new EndContainer(ContainerKind.SET)));
   }

   @Test
   public void firstTwoOfFourMembersSet() {
      // Members 0,1 set: bits 7,6 of byte 0 = 1.
      ValueDecoder dec = new Set(4).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0xC0); // 0b1100_0000
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.SET, 2L),
            new StartItem(),
            new Constructor(0),
            new EndItem(),
            new StartItem(),
            new Constructor(1),
            new EndItem(),
            new EndContainer(ContainerKind.SET)));
   }

   @Test
   public void lastTwoOfFourMembersSet() {
      // Members 2,3 set: bits 5,4 of byte 0 = 1.
      ValueDecoder dec = new Set(4).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0x30); // 0b0011_0000
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.SET, 2L),
            new StartItem(),
            new Constructor(2),
            new EndItem(),
            new StartItem(),
            new Constructor(3),
            new EndItem(),
            new EndContainer(ContainerKind.SET)));
   }

   @Test
   public void allFourMembersSet() {
      ValueDecoder dec = new Set(4).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0xF0); // 0b1111_0000
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.SET, 4L),
            new StartItem(), new Constructor(0), new EndItem(),
            new StartItem(), new Constructor(1), new EndItem(),
            new StartItem(), new Constructor(2), new EndItem(),
            new StartItem(), new Constructor(3), new EndItem(),
            new EndContainer(ContainerKind.SET)));
   }

   @Test
   public void multiByteBitmaskTenMembers() {
      // 10 members → ceil(10/8) = 2 bytes. Set members 0, 7, 8, 9.
      // Byte 0: bit 7 (member 0) = 1, bit 0 (member 7) = 1 → 0b1000_0001 = 0x81
      // Byte 1: bit 7 (member 8) = 1, bit 6 (member 9) = 1 → 0b1100_0000 = 0xC0
      ValueDecoder dec = new Set(10).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(new byte[] { (byte) 0x81, (byte) 0xC0 }, 0, 2);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.SET, 4L),
            new StartItem(), new Constructor(0), new EndItem(),
            new StartItem(), new Constructor(7), new EndItem(),
            new StartItem(), new Constructor(8), new EndItem(),
            new StartItem(), new Constructor(9), new EndItem(),
            new EndContainer(ContainerKind.SET)));
   }

   @Test
   public void emitsNothingUntilFullBitmaskAvailable() {
      ValueDecoder dec = new Set(10).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      assertFalse(dec.parse(bits, capture));
      bits.write((byte) 0x81);
      assertFalse(dec.parse(bits, capture));
      bits.write((byte) 0xC0);
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events.size(), 1 + 4 * 3 + 1);
   }

   @Test
   public void waitsWhenSinkFillsMidEmission() {
      ValueDecoder dec = new Set(4).createDecoder();
      FedBitSource bits = new FedBitSource();
      EventCapture capture = new EventCapture();

      bits.write(0xC0);
      capture.capacity = 2; // StartContainer + StartItem, then full
      assertFalse(dec.parse(bits, capture));
      assertEquals(capture.events.size(), 2);

      capture.capacity = Integer.MAX_VALUE;
      assertTrue(dec.parse(bits, capture));

      assertEquals(capture.events, List.of(
            new StartContainer(ContainerKind.SET, 2L),
            new StartItem(),
            new Constructor(0),
            new EndItem(),
            new StartItem(),
            new Constructor(1),
            new EndItem(),
            new EndContainer(ContainerKind.SET)));
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
