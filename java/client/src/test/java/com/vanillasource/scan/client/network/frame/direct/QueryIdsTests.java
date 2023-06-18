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
import org.mockito.ArgumentCaptor;
import java.util.concurrent.CompletableFuture;

@Test
public final class QueryIdsTests {
   private ScheduledExecutorService scheduler;
   private QueryIds ids;

   public void testNewIdsReturns1Immediately() {
      int id = ids.nextQueryId().join();

      assertEquals(id, 1);
   }

   public void testNewIdsReturns255IdsImmediately() {
      for (int i=0; i<255; i++) {
         int id = ids.nextQueryId().join();

         assertEquals(id, i+1);
      }
   }

   public void test256thIdBlocks() {
      for (int i=0; i<255; i++) {
         ids.nextQueryId().join();
      }

      assertFalse(ids.nextQueryId().isDone());
   }

   public void testResetIdsReturns1Again() throws Exception {
      ids.nextQueryId().join();

      callScheduledTask();
      int id = ids.nextQueryId().join();

      assertEquals(id, 1);
   }

   public void test256thIdIsAvailableAfterReset() throws Exception {
      for (int i=0; i<255; i++) {
         ids.nextQueryId().join();
      }
      CompletableFuture<Integer> nextId = ids.nextQueryId();

      callScheduledTask();

      assertTrue(nextId.isDone());
      assertEquals(nextId.join().intValue(), 1);
   }

   @BeforeMethod
   @SuppressWarnings("unchecked")
   private void setUp() {
      scheduler = mock(ScheduledExecutorService.class);
      when(scheduler.schedule(Mockito.<Callable<Void>>any(), anyLong(), any())).thenReturn(mock(ScheduledFuture.class));
      ids = new QueryIds(scheduler);
   }

   @SuppressWarnings("unchecked")
   private void callScheduledTask() throws Exception {
      ArgumentCaptor<Callable<Void>> captor = ArgumentCaptor.forClass((Class) Callable.class);
      verify(scheduler, atLeastOnce()).schedule(captor.capture(), anyLong(), any());
      captor.getValue().call();
   }
}
