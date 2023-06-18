/**
 * Copyright (C) 2023 Robert Braeutigam.
 *
 * All rights reserved.
 */

package com.vanillasource.scan.client.network.frame.direct;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import static org.testng.Assert.*;
import java.util.concurrent.ScheduledExecutorService;
import static org.mockito.Mockito.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Callable;
import org.mockito.Mockito;

@Test
public final class QueryIdsTests {
   private ScheduledExecutorService scheduler;
   private QueryIds ids;

   public void testNewIdsReturns1Immediately() {
      int id = ids.nextQueryId().join();

      assertEquals(id, 1);
   }

   public void testNewIdsReturns255IdsImmediately() {
      for (int i=0; i<254; i++) {
         int id = ids.nextQueryId().join();

         assertEquals(id, i+1);
      }
   }

   @BeforeMethod
   @SuppressWarnings("unchecked")
   private void setUp() {
      scheduler = mock(ScheduledExecutorService.class);
      when(scheduler.schedule(Mockito.<Callable<Void>>any(), anyLong(), any())).thenReturn(mock(ScheduledFuture.class));
      ids = new QueryIds(scheduler);
   }
}
