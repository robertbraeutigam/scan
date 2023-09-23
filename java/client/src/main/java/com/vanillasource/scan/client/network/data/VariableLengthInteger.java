package com.vanillasource.scan.client.network.data;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

public class VariableLengthInteger {
   private static final BigInteger LONG_MAX_INTEGER = BigInteger.TWO.pow(7*7+8).subtract(BigInteger.ONE);
   public static final VariableLengthInteger ZERO = createLong(0);
   public static final VariableLengthInteger LONG_MAX = createBigInteger(LONG_MAX_INTEGER);

   private final BigInteger maxValue;
   private final BigInteger value;

   private VariableLengthInteger(BigInteger maxValue, BigInteger value) {
      this.maxValue = maxValue;
      this.value = value;
   }

   public static VariableLengthInteger createLong(long value) {
      return createBigInteger(BigInteger.valueOf(value));
   }

   public static VariableLengthInteger createBigInteger(BigInteger value) {
      return new VariableLengthInteger(LONG_MAX_INTEGER, value);
   }

   public Optional<VariableLengthInteger> increase() {
      if (value.equals(maxValue)) {
         return Optional.empty();
      } else {
         return Optional.of(new VariableLengthInteger(maxValue, value.add(BigInteger.ONE)));
      }
   }

   public Optional<VariableLengthInteger> decrease() {
      if (value.equals(BigInteger.ZERO)) {
         return Optional.empty();
      } else {
         return Optional.of(new VariableLengthInteger(maxValue, value.subtract(BigInteger.ONE)));
      }
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      VariableLengthInteger that = (VariableLengthInteger) o;
      return Objects.equals(value, that.value);
   }

   @Override
   public int hashCode() {
      return Objects.hash(value);
   }
}
