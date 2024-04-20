package com.vanillasource.scan.client.network.network.direct;

import com.vanillasource.scan.client.network.data.VariableLengthInteger;
import com.vanillasource.util.TimeSource;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

@Test
public final class QueryIdsTests {
   private TimeSource timeSource;
   private QueryIds ids;

   public void testNewIdsReturns1Immediately() {
      VariableLengthInteger id = ids.nextQueryId();

      assertEquals(id, VariableLengthInteger.ZERO);
   }

   public void testNewIdsReturns255IdsWithoutWaiting() {
      for (int i=0; i<255; i++) {
         VariableLengthInteger id = ids.nextQueryId();

         assertEquals(id, VariableLengthInteger.createLong(i));
      }

      verify(timeSource, never()).sleep(anyLong());
   }

   public void testResetsAfter20Seconds() throws Exception {
      ids.nextQueryId();

      when(timeSource.currentTimeMillis()).thenReturn(20000L);
      VariableLengthInteger id = ids.nextQueryId();

      assertEquals(id, VariableLengthInteger.ZERO);
   }

   @BeforeMethod
   private void setUp() {
      timeSource = mock(TimeSource.class);
      ids = new QueryIds(timeSource);
   }
}
