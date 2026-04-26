package com.vanillasource.scan.types.codec;

import com.vanillasource.scan.types.codec.DecodingEvent.FloatingPointScalar;
import com.vanillasource.scan.types.codec.DecodingEvent.IntegerScalar;
import com.vanillasource.scan.types.codec.DecodingEvent.UnitScalar;
import com.vanillasource.scan.types.codec.Type.FloatingPoint;
import com.vanillasource.scan.types.codec.Type.SignedInteger;
import com.vanillasource.scan.types.codec.Type.Unit;
import com.vanillasource.scan.types.codec.Type.UnsignedInteger;
import com.vanillasource.scan.types.codec.Type.VariableLengthInteger;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public final class PrimitiveCodecTests {

    private static long roundTripInteger(Type type, long value) {
        ValueEncoder enc = new ValueEncoder(type);
        enc.writeInteger(value);
        byte[] bytes = enc.toByteArray();
        EventCapture cap = new EventCapture();
        ValueDecoder dec = new ValueDecoder(type, cap);
        dec.feed(bytes);
        assertTrue(dec.isComplete(), "decoder should complete");
        assertEquals(cap.events.size(), 1, "exactly one event expected");
        IntegerScalar event = (IntegerScalar) cap.events.get(0);
        return event.value();
    }

    private static double roundTripFloat(Type type, double value) {
        ValueEncoder enc = new ValueEncoder(type);
        enc.writeFloat(value);
        byte[] bytes = enc.toByteArray();
        EventCapture cap = new EventCapture();
        ValueDecoder dec = new ValueDecoder(type, cap);
        dec.feed(bytes);
        FloatingPointScalar event = (FloatingPointScalar) cap.events.get(0);
        return event.value();
    }

    @Test
    public void unitRoundTripIsZeroBytes() {
        Unit type = new Unit();
        ValueEncoder enc = new ValueEncoder(type);
        assertTrue(enc.isComplete(), "Unit encoder is complete at construction");
        assertEquals(enc.toByteArray(), new byte[0]);

        EventCapture cap = new EventCapture();
        ValueDecoder dec = new ValueDecoder(type, cap);
        assertTrue(dec.isComplete(), "Unit decoder is complete at construction");
        assertEquals(cap.events.size(), 1);
        assertTrue(cap.events.get(0) instanceof UnitScalar);
    }

    @Test
    public void unsignedIntegerRoundTripAllSizes() {
        for (int n = 1; n <= 8; n++) {
            UnsignedInteger type = new UnsignedInteger(n);
            assertEquals(roundTripInteger(type, 0L), 0L);
            assertEquals(roundTripInteger(type, 1L), 1L);
            long max = (n == 8) ? -1L : (1L << (n * 8)) - 1;  // -1L = unsigned 0xFFFFFFFFFFFFFFFF
            assertEquals(roundTripInteger(type, max), max);
        }
    }

    @Test
    public void unsignedIntegerBigEndianBytes() {
        UnsignedInteger u4 = new UnsignedInteger(4);
        ValueEncoder enc = new ValueEncoder(u4);
        enc.writeInteger(0x12345678L);
        assertEquals(enc.toByteArray(), new byte[] { 0x12, 0x34, 0x56, 0x78 });
    }

    @Test
    public void unsignedIntegerRejectsOverflow() {
        UnsignedInteger u1 = new UnsignedInteger(1);
        ValueEncoder enc = new ValueEncoder(u1);
        assertThrows(IllegalArgumentException.class, () -> enc.writeInteger(256));
    }

    @Test
    public void unsignedIntegerRejectsNegative() {
        UnsignedInteger u1 = new UnsignedInteger(1);
        ValueEncoder enc = new ValueEncoder(u1);
        assertThrows(IllegalArgumentException.class, () -> enc.writeInteger(-1));
    }

    @Test
    public void signedIntegerRoundTripAllSizes() {
        for (int n = 1; n <= 8; n++) {
            SignedInteger type = new SignedInteger(n);
            long max = (n == 8) ? Long.MAX_VALUE : (1L << (n * 8 - 1)) - 1;
            long min = (n == 8) ? Long.MIN_VALUE : -(1L << (n * 8 - 1));
            assertEquals(roundTripInteger(type, 0L), 0L);
            assertEquals(roundTripInteger(type, max), max);
            assertEquals(roundTripInteger(type, min), min);
            assertEquals(roundTripInteger(type, -1L), -1L);
        }
    }

    @Test
    public void signedIntegerSignExtension() {
        SignedInteger s1 = new SignedInteger(1);
        ValueEncoder enc = new ValueEncoder(s1);
        enc.writeInteger(-1);
        byte[] bytes = enc.toByteArray();
        assertEquals(bytes, new byte[] { (byte) 0xFF });

        EventCapture cap = new EventCapture();
        ValueDecoder dec = new ValueDecoder(s1, cap);
        dec.feed(bytes);
        assertEquals(((IntegerScalar) cap.events.get(0)).value(), -1L);
    }

    @Test
    public void signedIntegerRejectsOutOfRange() {
        SignedInteger s1 = new SignedInteger(1);
        ValueEncoder tooHigh = new ValueEncoder(s1);
        assertThrows(IllegalArgumentException.class, () -> tooHigh.writeInteger(128));
        ValueEncoder tooLow = new ValueEncoder(s1);
        assertThrows(IllegalArgumentException.class, () -> tooLow.writeInteger(-129));
    }

    @Test
    public void floatingPointRoundTrip32() {
        FloatingPoint f4 = new FloatingPoint(4);
        for (double v : new double[] {
                0.0, -0.0, 1.0, -1.0, 3.14f, Float.MAX_VALUE, Float.MIN_VALUE,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY }) {
            double round = roundTripFloat(f4, v);
            assertEquals(Float.floatToRawIntBits((float) round), Float.floatToRawIntBits((float) v));
        }
    }

    @Test
    public void floatingPointRoundTrip64() {
        FloatingPoint f8 = new FloatingPoint(8);
        for (double v : new double[] {
                0.0, -0.0, 1.0, -1.0, Math.PI, Double.MAX_VALUE, Double.MIN_VALUE,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            double round = roundTripFloat(f8, v);
            assertEquals(Double.doubleToRawLongBits(round), Double.doubleToRawLongBits(v));
        }
    }

    @Test
    public void floatingPointPreservesNaNBitPattern() {
        FloatingPoint f8 = new FloatingPoint(8);
        long quietNanBits = 0x7FF8000000000001L;
        double quietNan = Double.longBitsToDouble(quietNanBits);
        double round = roundTripFloat(f8, quietNan);
        assertEquals(Double.doubleToRawLongBits(round), quietNanBits);
    }

    @Test
    public void varintZeroEncodesAsSingleZeroByte() {
        for (int maxN = 1; maxN <= 8; maxN++) {
            ValueEncoder enc = new ValueEncoder(new VariableLengthInteger(maxN));
            enc.writeInteger(0);
            assertEquals(enc.toByteArray(), new byte[] { 0x00 },
                    "maxN=" + maxN + " should encode 0 as one zero byte");
        }
    }

    @Test
    public void varintMaxN1UsesAllEightBitsInSingleByte() {
        VariableLengthInteger v1 = new VariableLengthInteger(1);
        ValueEncoder enc = new ValueEncoder(v1);
        enc.writeInteger(255);
        assertEquals(enc.toByteArray(), new byte[] { (byte) 0xFF });
        assertEquals(roundTripInteger(v1, 255), 255L);
    }

    @Test
    public void varintMaxN1RejectsValueAbove255() {
        VariableLengthInteger v1 = new VariableLengthInteger(1);
        ValueEncoder enc = new ValueEncoder(v1);
        assertThrows(IllegalArgumentException.class, () -> enc.writeInteger(256));
    }

    @Test
    public void varintSevenBitBoundary() {
        VariableLengthInteger v3 = new VariableLengthInteger(3);
        ValueEncoder e127 = new ValueEncoder(v3);
        e127.writeInteger(127);
        assertEquals(e127.toByteArray(), new byte[] { 0x7F });
        ValueEncoder e128 = new ValueEncoder(v3);
        e128.writeInteger(128);
        assertEquals(e128.toByteArray(), new byte[] { (byte) 0x81, 0x00 });
        assertEquals(roundTripInteger(v3, 127), 127L);
        assertEquals(roundTripInteger(v3, 128), 128L);
    }

    @Test
    public void varintMaxN2Holds15BitsViaTrailingFullByte() {
        VariableLengthInteger v2 = new VariableLengthInteger(2);
        long max15 = (1L << 15) - 1;
        ValueEncoder enc = new ValueEncoder(v2);
        enc.writeInteger(max15);
        assertEquals(enc.toByteArray(), new byte[] { (byte) 0xFF, (byte) 0xFF });
        assertEquals(roundTripInteger(v2, max15), max15);

        ValueEncoder over = new ValueEncoder(v2);
        assertThrows(IllegalArgumentException.class, () -> over.writeInteger(1L << 15));
    }

    @Test
    public void varintRoundTripAcrossBoundaries() {
        for (int maxN = 1; maxN <= 8; maxN++) {
            VariableLengthInteger type = new VariableLengthInteger(maxN);
            long capacity = (maxN == 8) ? ((1L << 57) - 1)
                    : ((1L << (7 * (maxN - 1) + 8)) - 1);
            for (long v : new long[] { 0, 1, 127, 128, 16383, 16384, capacity }) {
                if (v <= capacity) {
                    assertEquals(roundTripInteger(type, v), v,
                            "maxN=" + maxN + " value=" + v);
                }
            }
        }
    }

    @Test
    public void varintRejectsNegative() {
        VariableLengthInteger v4 = new VariableLengthInteger(4);
        ValueEncoder enc = new ValueEncoder(v4);
        assertThrows(IllegalArgumentException.class, () -> enc.writeInteger(-1));
    }

    @Test
    public void encoderRejectsTypeMismatch() {
        ValueEncoder intEnc = new ValueEncoder(new UnsignedInteger(2));
        assertThrows(IllegalStateException.class, () -> intEnc.writeFloat(1.0));
        ValueEncoder floatEnc = new ValueEncoder(new FloatingPoint(4));
        assertThrows(IllegalStateException.class, () -> floatEnc.writeInteger(1));
    }

    @Test
    public void encoderRejectsWriteAfterComplete() {
        ValueEncoder enc = new ValueEncoder(new UnsignedInteger(1));
        enc.writeInteger(7);
        assertThrows(IllegalStateException.class, () -> enc.writeInteger(1));
    }

    @Test
    public void encoderRejectsToByteArrayWhenIncomplete() {
        ValueEncoder enc = new ValueEncoder(new UnsignedInteger(2));
        assertFalse(enc.isComplete());
        assertThrows(IllegalStateException.class, enc::toByteArray);
    }

    @Test
    public void decoderRejectsFeedAfterComplete() {
        ValueDecoder dec = new ValueDecoder(new UnsignedInteger(1), e -> { });
        dec.feed(new byte[] { 0x42 });
        assertTrue(dec.isComplete());
        assertThrows(IllegalStateException.class, () -> dec.feed(new byte[] { 0 }));
    }

    @Test
    public void decoderHandlesByteByByteFeedForFixedPrimitive() {
        UnsignedInteger u4 = new UnsignedInteger(4);
        ValueEncoder enc = new ValueEncoder(u4);
        enc.writeInteger(0xCAFEBABEL);
        byte[] bytes = enc.toByteArray();

        EventCapture cap = new EventCapture();
        ValueDecoder dec = new ValueDecoder(u4, cap);
        for (int i = 0; i < bytes.length - 1; i++) {
            dec.feed(new byte[] { bytes[i] });
            assertFalse(dec.isComplete(), "should still be waiting at byte " + i);
            assertTrue(cap.events.isEmpty());
        }
        dec.feed(new byte[] { bytes[bytes.length - 1] });
        assertTrue(dec.isComplete());
        assertEquals(((IntegerScalar) cap.events.get(0)).value(), 0xCAFEBABEL);
    }

    @Test
    public void decoderHandlesByteByByteFeedForVarInt() {
        VariableLengthInteger v4 = new VariableLengthInteger(4);
        ValueEncoder enc = new ValueEncoder(v4);
        long value = 1_000_000L;
        enc.writeInteger(value);
        byte[] bytes = enc.toByteArray();
        assertTrue(bytes.length > 1, "value should require multiple bytes");

        EventCapture cap = new EventCapture();
        ValueDecoder dec = new ValueDecoder(v4, cap);
        for (int i = 0; i < bytes.length - 1; i++) {
            dec.feed(new byte[] { bytes[i] });
            assertFalse(dec.isComplete(), "varint not done at byte " + i);
        }
        dec.feed(new byte[] { bytes[bytes.length - 1] });
        assertTrue(dec.isComplete());
        assertEquals(((IntegerScalar) cap.events.get(0)).value(), value);
    }

    @Test
    public void decoderRangeFeedConsumesOnlyDeclaredSlice() {
        UnsignedInteger u2 = new UnsignedInteger(2);
        EventCapture cap = new EventCapture();
        ValueDecoder dec = new ValueDecoder(u2, cap);
        byte[] padded = new byte[] { 0x00, (byte) 0xAB, (byte) 0xCD, 0x00 };
        dec.feed(padded, 1, 2);
        assertEquals(((IntegerScalar) cap.events.get(0)).value(), 0xABCDL);
    }
}
