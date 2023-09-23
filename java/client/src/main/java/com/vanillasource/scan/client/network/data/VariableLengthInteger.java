package com.vanillasource.scan.client.network.data;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

public class VariableLengthInteger {
   public static VariableLengthInteger ZERO = createLong(0);

   private final BigInteger maxValue;
   private final BigInteger value;

   private VariableLengthInteger(BigInteger maxValue, BigInteger value) {
      this.maxValue = maxValue;
      this.value = value;
   }

   public static VariableLengthInteger createLong(long value) {
      return new VariableLengthInteger(BigInteger.TWO.pow(7*7+8).subtract(BigInteger.ONE), BigInteger.valueOf(value));
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
