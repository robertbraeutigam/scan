package com.vanillasource.scan.client.network.network;

import com.vanillasource.scan.client.network.data.VariableLengthInteger;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeMethod;

import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;

@Test
public final class BitMaskMessageIdsTests {
   private BitMaskMessageIds ids;

   public void testFirstIdIsAvailableOnNewIds() {
      VariableLengthInteger id = ids.reserveId();

      assertEquals(id, VariableLengthInteger.createLong(10));
   }

   public void testFirstIdIsAvailableAgainAfterRelease() {
      ids.reserveId();
      ids.releaseId(VariableLengthInteger.createLong(10));

      VariableLengthInteger id = ids.reserveId();

      assertEquals(id, VariableLengthInteger.createLong(10));
   }

   public void testFirstIdIsAvailableAgainAfterReleaseEvenIfOthersAreStillReserved() {
      ids.reserveId();
      ids.reserveId();
      ids.reserveId();
      ids.releaseId(VariableLengthInteger.createLong(10));

      VariableLengthInteger id = ids.reserveId();

      assertEquals(id, VariableLengthInteger.createLong(10));
   }

   public void testAllIdsAreAvailable() {
      for (int i=0; i<10; i++) {
         assertEquals(ids.reserveId(), VariableLengthInteger.createLong(i+10));
      }
   }

   @Test(expectedExceptions = TimeoutException.class)
   public void testOverflowIdBlocks() throws ExecutionException, InterruptedException, TimeoutException {
      for (int i=0; i<10; i++) {
         ids.reserveId();
      }
      
      CompletableFuture<VariableLengthInteger> id = CompletableFuture.supplyAsync(() -> ids.reserveId(), Executors.newVirtualThreadPerTaskExecutor());

      id.get(10, TimeUnit.MILLISECONDS);
   }

   public void testHeavyOverreservationIsEventuallyResolved() {
      List<CompletableFuture<VariableLengthInteger>> reservations = new ArrayList<>();
      for (int i=0; i<100; i++) {
         reservations.add(CompletableFuture.supplyAsync(() -> ids.reserveId(), Executors.newVirtualThreadPerTaskExecutor()));
      }

      reservations.forEach(reservation -> reservation.thenAccept(ids::releaseId));

      CompletableFuture.allOf(reservations.toArray(CompletableFuture[]::new)).join();
   }

   @BeforeMethod
   private void setUp() {
      ids = new BitMaskMessageIds(VariableLengthInteger.createLong(10), VariableLengthInteger.createLong(19));
   }
}

