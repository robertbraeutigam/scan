package com.vanillasource.scan.client.network.data;

import org.testng.annotations.Test;

import java.util.Optional;

import static org.testng.Assert.*;

@Test
public final class VariableLengthIntegerTests {
   public void testZeroEqualsStaticZero() {
      assertEquals(VariableLengthInteger.createLong(0), VariableLengthInteger.ZERO);
   }

   public void tesSameVLIsEquals() {
      assertEquals(VariableLengthInteger.createLong(123L), VariableLengthInteger.createLong(123));
   }

   public void testDifferentVLIsDoNotEqual() {
      assertNotEquals(VariableLengthInteger.createLong(123), VariableLengthInteger.createLong(124));
   }

   public void testIncreaseWillIncreaseByOne() {
      assertEquals(VariableLengthInteger.createLong(123).increase(), Optional.of(VariableLengthInteger.createLong(124)));
   }

   public void testIncreaseOnMaxResultsInNone() {
      assertEquals(VariableLengthInteger.LONG_MAX.increase(), Optional.empty());
   }

   public void testDecreaseWillDecreaseByOne() {
      assertEquals(VariableLengthInteger.createLong(123).decrease(), Optional.of(VariableLengthInteger.createLong(122)));
   }

   public void testZeroCanNotBeDecreased() {
      assertEquals(VariableLengthInteger.ZERO.decrease(), Optional.empty());
   }

   public void testSubtractWorksIfResultPositive() {
      assertEquals(VariableLengthInteger.createLong(123).subtract(VariableLengthInteger.createLong(123)), Optional.of(VariableLengthInteger.ZERO));
   }

   public void testSubtractIsNoneIfResultIsNegative() {
      assertEquals(VariableLengthInteger.createLong(123).subtract(VariableLengthInteger.createLong(124)), Optional.empty());
   }

   public void testIntConversionWorksForSmallNumbers() {
      assertEquals(VariableLengthInteger.createLong(123456L).intValue(), Optional.of(123456));
   }

   public void testIntConversionFailsForLargeNumbers() {
      assertEquals(VariableLengthInteger.createLong(Integer.MAX_VALUE).increase().flatMap(VariableLengthInteger::intValue), Optional.empty());
   }
}