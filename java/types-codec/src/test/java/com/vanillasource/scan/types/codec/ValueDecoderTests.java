package com.vanillasource.scan.types.codec;

import com.vanillasource.scan.types.codec.Event.IntegerScalar;
import com.vanillasource.scan.types.codec.bit.FedBitSource;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class ValueDecoderTests {

   @Test
   public void runsBothInOrderWhenBothCompleteImmediately() {
      ValueDecoder a = emit(new IntegerScalar(1, 0));
      ValueDecoder b = emit(new IntegerScalar(2, 0));
      EventCapture capture = new EventCapture();

      assertTrue(a.andThen(b).parse(new FedBitSource(), capture));

      assertEquals(capture.events, List.of(
            new IntegerScalar(1, 0),
            new IntegerScalar(2, 0)));
   }

   @Test
   public void fallsThroughToSecondWhenFirstCompletesInSameCall() {
      // Record at which call-index each side ran.
      int[] callIndex = { 0 };
      List<Integer> firstCalls = new ArrayList<>();
      List<Integer> secondCalls = new ArrayList<>();

      ValueDecoder first = (bits, sink) -> { firstCalls.add(callIndex[0]); return true; };
      ValueDecoder second = (bits, sink) -> { secondCalls.add(callIndex[0]); return true; };

      callIndex[0] = 1;
      assertTrue(first.andThen(second).parse(new FedBitSource(), new EventCapture()));

      assertEquals(firstCalls, List.of(1));
      assertEquals(secondCalls, List.of(1));
   }

   @Test
   public void doesNotInvokeSecondWhileFirstNotDone() {
      MultiStepDecoder first = new MultiStepDecoder(2);
      CountingDecoder second = new CountingDecoder();
      EventCapture capture = new EventCapture();

      ValueDecoder seq = first.andThen(second);

      assertFalse(seq.parse(new FedBitSource(), capture));
      assertEquals(second.calls, 0);
   }

   @Test
   public void resumesAtSecondAfterFirstCompletes() {
      MultiStepDecoder first = new MultiStepDecoder(2);
      CountingDecoder second = new CountingDecoder();
      EventCapture capture = new EventCapture();

      ValueDecoder seq = first.andThen(second);

      assertFalse(seq.parse(new FedBitSource(), capture));   // first step 1
      assertEquals(second.calls, 0);
      assertTrue(seq.parse(new FedBitSource(), capture));    // first step 2 + second
      assertEquals(second.calls, 1);
   }

   @Test
   public void doesNotRestartFirstAcrossCalls() {
      CountingDecoder first = new CountingDecoder();    // completes immediately
      MultiStepDecoder second = new MultiStepDecoder(3);
      EventCapture capture = new EventCapture();

      ValueDecoder seq = first.andThen(second);

      assertFalse(seq.parse(new FedBitSource(), capture)); // first done, second step 1
      assertFalse(seq.parse(new FedBitSource(), capture)); // second step 2
      assertTrue(seq.parse(new FedBitSource(), capture));  // second step 3

      assertEquals(first.calls, 1);
   }

   @Test
   public void propagatesSinkBackpressureFromFirst() {
      ValueDecoder a = emit(new IntegerScalar(1, 0));
      ValueDecoder b = emit(new IntegerScalar(2, 0));
      EventCapture capture = new EventCapture();
      capture.capacity = 0;

      ValueDecoder seq = a.andThen(b);

      assertFalse(seq.parse(new FedBitSource(), capture));
      assertEquals(capture.events, List.of());

      capture.capacity = Integer.MAX_VALUE;
      assertTrue(seq.parse(new FedBitSource(), capture));
      assertEquals(capture.events, List.of(
            new IntegerScalar(1, 0),
            new IntegerScalar(2, 0)));
   }

   @Test
   public void propagatesSinkBackpressureFromSecond() {
      ValueDecoder a = emit(new IntegerScalar(1, 0));
      ValueDecoder b = emit(new IntegerScalar(2, 0));
      EventCapture capture = new EventCapture();
      capture.capacity = 1; // room for first event only

      ValueDecoder seq = a.andThen(b);

      assertFalse(seq.parse(new FedBitSource(), capture));
      assertEquals(capture.events, List.of(new IntegerScalar(1, 0)));

      capture.capacity = Integer.MAX_VALUE;
      assertTrue(seq.parse(new FedBitSource(), capture));
      assertEquals(capture.events, List.of(
            new IntegerScalar(1, 0),
            new IntegerScalar(2, 0)));
   }

   @Test
   public void chainsMoreThanTwoDecoders() {
      ValueDecoder a = emit(new IntegerScalar(1, 0));
      ValueDecoder b = emit(new IntegerScalar(2, 0));
      ValueDecoder c = emit(new IntegerScalar(3, 0));
      EventCapture capture = new EventCapture();

      assertTrue(a.andThen(b).andThen(c).parse(new FedBitSource(), capture));

      assertEquals(capture.events, List.of(
            new IntegerScalar(1, 0),
            new IntegerScalar(2, 0),
            new IntegerScalar(3, 0)));
   }

   private static ValueDecoder emit(Event event) {
      return (bits, sink) -> {
         if (sink.writableEvents() <= 0) {
            return false;
         }
         sink.write(event);
         return true;
      };
   }

   private static final class CountingDecoder implements ValueDecoder {
      int calls = 0;

      @Override
      public boolean parse(BitSource bits, EventSink sink) {
         calls++;
         return true;
      }
   }

   private static final class MultiStepDecoder implements ValueDecoder {
      int remaining;

      MultiStepDecoder(int steps) { this.remaining = steps; }

      @Override
      public boolean parse(BitSource bits, EventSink sink) {
         remaining--;
         return remaining <= 0;
      }
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
