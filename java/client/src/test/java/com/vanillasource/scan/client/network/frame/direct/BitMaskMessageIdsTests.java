package com.vanillasource.scan.client.network.frame.direct;

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
      int id = ids.reserveId();

      assertEquals(id, 10);
   }

   public void testFirstIdIsAvailableAgainAfterRelease() {
      ids.reserveId();
      ids.releaseId(10);

      int id = ids.reserveId();

      assertEquals(id, 10);
   }

   public void testFirstIdIsAvailableAgainAfterReleaseEvenIfOthersAreStillReserved() {
      ids.reserveId();
      ids.reserveId();
      ids.reserveId();
      ids.releaseId(10);

      int id = ids.reserveId();

      assertEquals(id, 10);
   }

   public void testAllIdsAreAvailable() {
      for (int i=0; i<10; i++) {
         assertEquals(ids.reserveId(), i+10);
      }
   }

   @Test(expectedExceptions = TimeoutException.class)
   public void testOverflowIdBlocks() throws ExecutionException, InterruptedException, TimeoutException {
      for (int i=0; i<10; i++) {
         ids.reserveId();
      }
      
      CompletableFuture<Integer> id = CompletableFuture.supplyAsync(() -> ids.reserveId(), Executors.newVirtualThreadPerTaskExecutor());

      id.get(10, TimeUnit.MILLISECONDS);
   }

   public void testHeavyOverreservationIsEventuallyResolved() {
      List<CompletableFuture<Integer>> reservations = new ArrayList<>();
      for (int i=0; i<100; i++) {
         reservations.add(CompletableFuture.supplyAsync(() -> ids.reserveId(), Executors.newVirtualThreadPerTaskExecutor()));
      }

      reservations.forEach(reservation -> reservation.thenAccept(ids::releaseId));

      CompletableFuture.allOf(reservations.toArray(CompletableFuture[]::new)).join();
   }

   @BeforeMethod
   private void setUp() {
      ids = new BitMaskMessageIds(10, 19);
   }
}

