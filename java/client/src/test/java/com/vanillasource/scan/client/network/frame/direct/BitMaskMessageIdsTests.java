/**
 * Copyright (C) 2023 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame.direct;

import org.testng.annotations.Test;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeMethod;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;

@Test
public final class BitMaskMessageIdsTests {
   private BitMaskMessageIds ids;

   public void testFirstIdIsAvailableOnNewIds() {
      int id = ids.reserveId().join();

      assertEquals(id, 10);
   }

   public void testFirstIdIsAvailableAgainAfterRelease() {
      ids.reserveId().join();
      ids.releaseId(10);

      int id = ids.reserveId().join();

      assertEquals(id, 10);
   }

   public void testFirstIdIsAvailableAgainAfterReleaseEvenIfOthersAreStillReserved() {
      ids.reserveId().join();
      ids.reserveId().join();
      ids.reserveId().join();
      ids.releaseId(10);

      int id = ids.reserveId().join();

      assertEquals(id, 10);
   }

   public void testAllIdsAreAvailable() {
      for (int i=0; i<10; i++) {
         assertEquals(ids.reserveId().join().intValue(), i+10);
      }
   }

   public void testOverflowIdBlocks() {
      for (int i=0; i<10; i++) {
         ids.reserveId().join();
      }
      
      CompletableFuture<Integer> id = ids.reserveId();

      assertFalse(id.isDone());
   }

   public void testHeavyOverreservationIsEventuallyResolved() {
      List<CompletableFuture<Integer>> reservations = new ArrayList<>();
      for (int i=0; i<100; i++) {
         reservations.add(ids.reserveId());
      }

      reservations.forEach(reservation -> reservation.thenAccept(ids::releaseId));

      assertTrue(CompletableFuture.allOf(reservations.toArray(CompletableFuture[]::new)).isDone());
   }

   @BeforeMethod
   private void setUp() {
      ids = new BitMaskMessageIds(10, 19);
   }
}

