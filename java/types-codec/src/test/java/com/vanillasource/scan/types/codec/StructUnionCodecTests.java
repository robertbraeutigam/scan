package com.vanillasource.scan.types.codec;

import com.vanillasource.scan.types.codec.DecodingEvent.Constructor;
import com.vanillasource.scan.types.codec.DecodingEvent.EndField;
import com.vanillasource.scan.types.codec.DecodingEvent.FloatingPointScalar;
import com.vanillasource.scan.types.codec.DecodingEvent.IntegerScalar;
import com.vanillasource.scan.types.codec.DecodingEvent.StartField;
import com.vanillasource.scan.types.codec.DecodingEvent.UnitScalar;
import com.vanillasource.scan.types.codec.Type.Field;
import com.vanillasource.scan.types.codec.Type.FloatingPoint;
import com.vanillasource.scan.types.codec.Type.SignedInteger;
import com.vanillasource.scan.types.codec.Type.Struct;
import com.vanillasource.scan.types.codec.Type.Union;
import com.vanillasource.scan.types.codec.Type.Unit;
import com.vanillasource.scan.types.codec.Type.UnsignedInteger;
import com.vanillasource.scan.types.codec.Type.VariableLengthInteger;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public final class StructUnionCodecTests {

    private static final class Capture implements DecodingEventHandler {
        final List<DecodingEvent> events = new ArrayList<>();

        @Override
        public void onEvent(DecodingEvent event) {
            events.add(event);
        }
    }

    private static List<DecodingEvent> decode(Type root, byte[] bytes) {
        Capture cap = new Capture();
        ValueDecoder dec = new ValueDecoder(root, cap);
        dec.feed(bytes);
        assertTrue(dec.isComplete(), "decoder should complete after full feed");
        return cap.events;
    }

    private static Field f(String name, Type type) {
        return new Field(name, type);
    }

    private static Type.Constructor ctor(String name, Field... fields) {
        return new Type.Constructor(name, List.of(fields));
    }

    @Test
    public void flatStructRoundTrip() {
        Struct t = new Struct(List.of(
                f("a", new UnsignedInteger(1)),
                f("b", new UnsignedInteger(2))));

        ValueEncoder enc = new ValueEncoder(t);
        assertFalse(enc.isComplete());
        enc.writeInteger(0x07);
        assertFalse(enc.isComplete());
        enc.writeInteger(0xBEEF);
        assertTrue(enc.isComplete());
        byte[] bytes = enc.toByteArray();
        assertEquals(bytes, new byte[] { 0x07, (byte) 0xBE, (byte) 0xEF });

        List<DecodingEvent> events = decode(t, bytes);
        assertEquals(events, List.of(
                new StartField(0), new IntegerScalar(0x07L), new EndField(0),
                new StartField(1), new IntegerScalar(0xBEEFL), new EndField(1)));
    }

    @Test
    public void nestedStructRoundTrip() {
        Struct inner = new Struct(List.of(
                f("x", new UnsignedInteger(1)),
                f("y", new UnsignedInteger(1))));
        Struct outer = new Struct(List.of(
                f("a", new UnsignedInteger(1)),
                f("inner", inner),
                f("c", new UnsignedInteger(1))));

        ValueEncoder enc = new ValueEncoder(outer);
        enc.writeInteger(0x01);  // a
        enc.writeInteger(0x02);  // inner.x
        enc.writeInteger(0x03);  // inner.y
        enc.writeInteger(0x04);  // c
        assertTrue(enc.isComplete());
        byte[] bytes = enc.toByteArray();
        assertEquals(bytes, new byte[] { 0x01, 0x02, 0x03, 0x04 });

        List<DecodingEvent> events = decode(outer, bytes);
        assertEquals(events, List.of(
                new StartField(0), new IntegerScalar(1L), new EndField(0),
                new StartField(1),
                new StartField(0), new IntegerScalar(2L), new EndField(0),
                new StartField(1), new IntegerScalar(3L), new EndField(1),
                new EndField(1),
                new StartField(2), new IntegerScalar(4L), new EndField(2)));
    }

    @Test
    public void unitFieldEmitsUnitScalarAndZeroBytes() {
        Struct t = new Struct(List.of(
                f("u", new Unit()),
                f("v", new UnsignedInteger(1))));

        ValueEncoder enc = new ValueEncoder(t);
        enc.writeInteger(0x42);  // Unit field auto-skipped
        assertTrue(enc.isComplete());
        byte[] bytes = enc.toByteArray();
        assertEquals(bytes, new byte[] { 0x42 });

        List<DecodingEvent> events = decode(t, bytes);
        assertEquals(events, List.of(
                new StartField(0), new UnitScalar(), new EndField(0),
                new StartField(1), new IntegerScalar(0x42L), new EndField(1)));
    }

    @Test
    public void unionEmptyConstructorWritesOnlyDiscriminator() {
        // T = None | Some { value: U(1) }
        Union t = new Union(List.of(
                ctor("None"),
                ctor("Some", f("value", new UnsignedInteger(1)))));

        ValueEncoder enc = new ValueEncoder(t);
        enc.writeConstructor(0);  // None
        assertTrue(enc.isComplete());
        // 1-bit disc 0 in MSB of byte 0; 7 unused bits remain 0.
        assertEquals(enc.toByteArray(), new byte[] { 0x00 });

        List<DecodingEvent> events = decode(t, new byte[] { 0x00 });
        assertEquals(events, List.of(new Constructor(0)));
    }

    @Test
    public void unionPopulatedConstructorEmitsConstructorThenFields() {
        Union t = new Union(List.of(
                ctor("None"),
                ctor("Some", f("value", new UnsignedInteger(1)))));

        ValueEncoder enc = new ValueEncoder(t);
        enc.writeConstructor(1);
        enc.writeInteger(0x5A);
        assertTrue(enc.isComplete());
        byte[] bytes = enc.toByteArray();
        // Disc bit 1 in MSB of byte 0 (= 0x80), then 1 byte byte-aligned for value.
        // Byte 0: 0x80, byte 1: 0x5A.
        assertEquals(bytes, new byte[] { (byte) 0x80, 0x5A });

        List<DecodingEvent> events = decode(t, bytes);
        assertEquals(events, List.of(
                new Constructor(1),
                new StartField(0), new IntegerScalar(0x5AL), new EndField(0)));
    }

    @Test
    public void unionDiscriminatorStraddlesByteBoundary() {
        // 9-bit discriminator: 300 constructors -> ceilLog2(300) = 9 bits.
        List<Type.Constructor> ctors = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            ctors.add(ctor("C" + i));
        }
        Union t = new Union(ctors);

        // Pick an index whose 9-bit pattern crosses bytes interestingly: 0b101010101 = 341.
        // But max valid is 299, so use 0b100010111 = 279 (top bit set, plus low byte 0x17).
        int chosen = 279;
        assertTrue(chosen < 300);

        ValueEncoder enc = new ValueEncoder(t);
        enc.writeConstructor(chosen);
        assertTrue(enc.isComplete());
        byte[] bytes = enc.toByteArray();

        // 9-bit MSB-first encoding:
        //   byte 0 = top 8 bits of discriminator
        //   byte 1 = bottom 1 bit << 7  (rest 0)
        int top8 = (chosen >>> 1) & 0xFF;
        int bottom1 = chosen & 0x1;
        assertEquals(bytes.length, 2);
        assertEquals(bytes[0] & 0xFF, top8);
        assertEquals(bytes[1] & 0xFF, bottom1 << 7);

        List<DecodingEvent> events = decode(t, bytes);
        assertEquals(events, List.of(new Constructor(chosen)));
    }

    @Test
    public void multipleUnionsShareABitByte() {
        // Struct { a: Union(2 ctors, 1 bit), b: Union(2 ctors, 1 bit), c: U(1) }.
        // The two 1-bit discs both fall in byte 0; six trailing bits of byte 0 stay 0;
        // the U(1) is byte-aligned at byte 1.
        Union u2a = new Union(List.of(ctor("X"), ctor("Y")));
        Union u2b = new Union(List.of(ctor("P"), ctor("Q")));
        Struct t = new Struct(List.of(
                f("a", u2a),
                f("b", u2b),
                f("c", new UnsignedInteger(1))));

        ValueEncoder enc = new ValueEncoder(t);
        enc.writeConstructor(1);  // a -> Y
        enc.writeConstructor(0);  // b -> P
        enc.writeInteger(0x5A);   // c
        assertTrue(enc.isComplete());
        byte[] bytes = enc.toByteArray();
        // Byte 0: 1 0 0 0 0 0 0 0 = 0x80 (a=1 in MSB, b=0 next, six zero bits)
        // Byte 1: 0x5A
        assertEquals(bytes, new byte[] { (byte) 0x80, 0x5A });

        List<DecodingEvent> events = decode(t, bytes);
        assertEquals(events, List.of(
                new StartField(0), new Constructor(1), new EndField(0),
                new StartField(1), new Constructor(0), new EndField(1),
                new StartField(2), new IntegerScalar(0x5AL), new EndField(2)));
    }

    @Test
    public void byteAlignedFieldDoesNotCloseBitByte() {
        // Struct { a: Union(2 ctors, 1 bit), b: U(1), c: Union(2 ctors, 1 bit) }.
        // Per TYPES.md §"Writing Byte-Aligned Data": byte-aligned writes do not
        // close the active bit byte, so a's bit and c's bit share byte 0; b sits
        // physically after byte 0 even though c's bit was emitted later.
        Union u2 = new Union(List.of(ctor("X"), ctor("Y")));
        Struct t = new Struct(List.of(
                f("a", u2),
                f("b", new UnsignedInteger(1)),
                f("c", u2)));

        ValueEncoder enc = new ValueEncoder(t);
        enc.writeConstructor(1);  // a=Y -> bit byte 0 bit 7 = 1
        enc.writeInteger(0xAB);   // b -> byte 1 = 0xAB
        enc.writeConstructor(1);  // c=Y -> bit byte 0 bit 6 = 1
        assertTrue(enc.isComplete());
        byte[] bytes = enc.toByteArray();
        // Byte 0: 1 1 0 0 0 0 0 0 = 0xC0
        // Byte 1: 0xAB
        assertEquals(bytes, new byte[] { (byte) 0xC0, (byte) 0xAB });

        List<DecodingEvent> events = decode(t, bytes);
        assertEquals(events, List.of(
                new StartField(0), new Constructor(1), new EndField(0),
                new StartField(1), new IntegerScalar(0xABL), new EndField(1),
                new StartField(2), new Constructor(1), new EndField(2)));
    }

    @Test
    public void singleConstructorUnionUsesZeroBitDiscriminator() {
        // n=1 -> 0-bit discriminator; nothing is written for it.
        Union t = new Union(List.of(ctor("Only", f("value", new UnsignedInteger(2)))));

        ValueEncoder enc = new ValueEncoder(t);
        enc.writeConstructor(0);
        enc.writeInteger(0x1234);
        assertTrue(enc.isComplete());
        assertEquals(enc.toByteArray(), new byte[] { 0x12, 0x34 });

        List<DecodingEvent> events = decode(t, new byte[] { 0x12, 0x34 });
        assertEquals(events, List.of(
                new Constructor(0),
                new StartField(0), new IntegerScalar(0x1234L), new EndField(0)));
    }

    @Test
    public void nestedUnionInsideConstructor() {
        // Outer = O { inner: Inner }; Inner = A | B { x: U(1) }
        Union inner = new Union(List.of(ctor("A"), ctor("B", f("x", new UnsignedInteger(1)))));
        Struct outer = new Struct(List.of(f("inner", inner)));

        ValueEncoder enc = new ValueEncoder(outer);
        enc.writeConstructor(1);  // inner -> B
        enc.writeInteger(0x77);
        assertTrue(enc.isComplete());
        // Byte 0: 1-bit disc in MSB = 0x80, byte 1: 0x77.
        assertEquals(enc.toByteArray(), new byte[] { (byte) 0x80, 0x77 });

        List<DecodingEvent> events = decode(outer, new byte[] { (byte) 0x80, 0x77 });
        assertEquals(events, List.of(
                new StartField(0),
                new Constructor(1),
                new StartField(0), new IntegerScalar(0x77L), new EndField(0),
                new EndField(0)));
    }

    @Test
    public void encoderRejectsConstructorAtPrimitivePosition() {
        ValueEncoder enc = new ValueEncoder(new UnsignedInteger(1));
        assertThrows(IllegalStateException.class, () -> enc.writeConstructor(0));
    }

    @Test
    public void encoderRejectsIntegerAtUnionPosition() {
        Union t = new Union(List.of(ctor("A"), ctor("B")));
        ValueEncoder enc = new ValueEncoder(t);
        assertThrows(IllegalStateException.class, () -> enc.writeInteger(0));
    }

    @Test
    public void encoderRejectsConstructorIndexOutOfRange() {
        Union t = new Union(List.of(ctor("A"), ctor("B")));
        ValueEncoder enc = new ValueEncoder(t);
        assertThrows(IllegalArgumentException.class, () -> enc.writeConstructor(2));
        ValueEncoder enc2 = new ValueEncoder(t);
        assertThrows(IllegalArgumentException.class, () -> enc2.writeConstructor(-1));
    }

    @Test
    public void encoderRejectsWriteAfterStructCompletes() {
        Struct t = new Struct(List.of(f("a", new UnsignedInteger(1))));
        ValueEncoder enc = new ValueEncoder(t);
        enc.writeInteger(1);
        assertTrue(enc.isComplete());
        assertThrows(IllegalStateException.class, () -> enc.writeInteger(0));
    }

    @Test
    public void decoderHandlesByteByByteFeedForStructWithUnion() {
        Union u = new Union(List.of(ctor("None"), ctor("Some", f("value", new UnsignedInteger(2)))));
        Struct t = new Struct(List.of(
                f("flag", u),
                f("tail", new VariableLengthInteger(3))));

        ValueEncoder enc = new ValueEncoder(t);
        enc.writeConstructor(1);
        enc.writeInteger(0x1234);
        enc.writeInteger(20000);
        byte[] bytes = enc.toByteArray();

        Capture cap = new Capture();
        ValueDecoder dec = new ValueDecoder(t, cap);
        for (int i = 0; i < bytes.length - 1; i++) {
            dec.feed(new byte[] { bytes[i] });
            assertFalse(dec.isComplete(), "should still be waiting at byte " + i);
        }
        dec.feed(new byte[] { bytes[bytes.length - 1] });
        assertTrue(dec.isComplete());

        // Same event sequence as a single-shot feed.
        List<DecodingEvent> oneShot = decode(t, bytes);
        assertEquals(cap.events, oneShot);
    }

    @Test
    public void decoderRejectsInvalidDiscriminator() {
        // 3 constructors -> 2-bit discriminator. Encoded byte 0xC0 = 0b11000000 -> disc = 3 (out of range).
        Union t = new Union(List.of(ctor("A"), ctor("B"), ctor("C")));
        Capture cap = new Capture();
        ValueDecoder dec = new ValueDecoder(t, cap);
        assertThrows(IllegalStateException.class, () -> dec.feed(new byte[] { (byte) 0xC0 }));
    }

    @Test
    public void floatFieldInsideStruct() {
        Struct t = new Struct(List.of(
                f("temp", new FloatingPoint(4)),
                f("flag", new SignedInteger(1))));

        ValueEncoder enc = new ValueEncoder(t);
        enc.writeFloat(1.5f);
        enc.writeInteger(-2);
        assertTrue(enc.isComplete());
        byte[] bytes = enc.toByteArray();

        List<DecodingEvent> events = decode(t, bytes);
        assertEquals(events.size(), 6);
        assertTrue(events.get(0) instanceof StartField sf && sf.index() == 0);
        assertTrue(events.get(1) instanceof FloatingPointScalar);
        assertEquals(((FloatingPointScalar) events.get(1)).value(), 1.5d);
        assertTrue(events.get(2) instanceof EndField ef && ef.index() == 0);
        assertTrue(events.get(3) instanceof StartField sf2 && sf2.index() == 1);
        assertEquals(((IntegerScalar) events.get(4)).value(), -2L);
        assertTrue(events.get(5) instanceof EndField ef2 && ef2.index() == 1);
    }

    @Test
    public void emptyStructIsCompleteAtConstruction() {
        Struct t = new Struct(List.of());
        ValueEncoder enc = new ValueEncoder(t);
        assertTrue(enc.isComplete());
        assertEquals(enc.toByteArray(), new byte[0]);

        Capture cap = new Capture();
        ValueDecoder dec = new ValueDecoder(t, cap);
        assertTrue(dec.isComplete());
        assertTrue(cap.events.isEmpty());
    }
}
