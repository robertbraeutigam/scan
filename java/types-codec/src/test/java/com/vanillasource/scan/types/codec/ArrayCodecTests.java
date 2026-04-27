package com.vanillasource.scan.types.codec;

import com.vanillasource.scan.types.codec.DecodingEvent.Chunk;
import com.vanillasource.scan.types.codec.DecodingEvent.Constructor;
import com.vanillasource.scan.types.codec.DecodingEvent.ContainerKind;
import com.vanillasource.scan.types.codec.DecodingEvent.EndContainer;
import com.vanillasource.scan.types.codec.DecodingEvent.EndField;
import com.vanillasource.scan.types.codec.DecodingEvent.EndItem;
import com.vanillasource.scan.types.codec.DecodingEvent.IntegerScalar;
import com.vanillasource.scan.types.codec.DecodingEvent.StartContainer;
import com.vanillasource.scan.types.codec.DecodingEvent.StartField;
import com.vanillasource.scan.types.codec.DecodingEvent.StartItem;
import com.vanillasource.scan.types.codec.DecodingEvent.UnitScalar;
import com.vanillasource.scan.types.codec.types.Array;
import com.vanillasource.scan.types.codec.types.Field;
import com.vanillasource.scan.types.codec.types.SignedInteger;
import com.vanillasource.scan.types.codec.types.Struct;
import com.vanillasource.scan.types.codec.types.Union;
import com.vanillasource.scan.types.codec.types.Unit;
import com.vanillasource.scan.types.codec.types.UnsignedInteger;
import com.vanillasource.scan.types.codec.types.VariableLengthInteger;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public final class ArrayCodecTests {

    private static List<DecodingEvent> decode(Type root, byte[] bytes) {
        EventCapture cap = new EventCapture();
        ValueDecoder dec = new ValueDecoder(root, cap);
        dec.write(bytes);
        assertTrue(dec.isComplete(), "decoder should complete after full feed");
        return cap.events;
    }

    private static Field f(String name, Type type) {
        return new Field(name, type);
    }

    private static com.vanillasource.scan.types.codec.types.Constructor ctor(String name, Field... fields) {
        return new com.vanillasource.scan.types.codec.types.Constructor(name, List.of(fields));
    }

    private static byte[] joinChunks(List<DecodingEvent> events) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (DecodingEvent e : events) {
            if (e instanceof Chunk c) {
                out.writeBytes(c.bytes());
            }
        }
        return out.toByteArray();
    }

    @Test
    public void fixedSizeArrayWritesNoCount() {
        Array t = new Array(new UnsignedInteger(1), SizeConstraint.exact(3));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ValueEncoder enc = new ValueEncoder(t, baos);
        enc.startArray(3);
        enc.writeInteger(0x10);
        enc.writeInteger(0x20);
        enc.writeInteger(0x30);
        assertTrue(enc.isComplete());
        // No count on the wire — just the three bytes back-to-back.
        assertEquals(baos.toByteArray(), new byte[] { 0x10, 0x20, 0x30 });

        List<DecodingEvent> events = decode(t, new byte[] { 0x10, 0x20, 0x30 });
        assertEquals(events.get(0), new StartContainer(ContainerKind.ARRAY));
        assertEquals(events.get(events.size() - 1), new EndContainer(ContainerKind.ARRAY));
        assertEquals(joinChunks(events), new byte[] { 0x10, 0x20, 0x30 });
    }

    @Test
    public void fixedSizeArrayRejectsWrongCount() {
        Array t = new Array(new UnsignedInteger(1), SizeConstraint.exact(3));
        ValueEncoder enc = new ValueEncoder(t, new ByteArrayOutputStream());
        assertThrows(IllegalArgumentException.class, () -> enc.startArray(2));
    }

    @Test
    public void rangeSizedArrayWritesVarintCount() {
        // Range(0, 100) needs ceil(log2(101)) = 7 bits, so v=1 (capacity 8 bits).
        Array t = new Array(new UnsignedInteger(2), new SizeConstraint.Range(0, 100));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ValueEncoder enc = new ValueEncoder(t, baos);
        enc.startArray(2);
        enc.writeInteger(0xABCD);
        enc.writeInteger(0x1234);
        byte[] bytes = baos.toByteArray();
        // Count byte (varint(1) of 2) is 0x02; then 4 item bytes.
        assertEquals(bytes, new byte[] { 0x02, (byte) 0xAB, (byte) 0xCD, 0x12, 0x34 });

        List<DecodingEvent> events = decode(t, bytes);
        assertEquals(events.get(0), new StartContainer(ContainerKind.ARRAY));
        assertEquals(events.get(events.size() - 1), new EndContainer(ContainerKind.ARRAY));
        assertEquals(joinChunks(events), new byte[] { (byte) 0xAB, (byte) 0xCD, 0x12, 0x34 });
    }

    @Test
    public void rangeSizedArrayWithMinOffsetEncodesCountMinusMin() {
        // Range(2, 5): v=1 (range = 3, 2 bits needed). Wire value = count - 2.
        Array t = new Array(new UnsignedInteger(1), new SizeConstraint.Range(2, 5));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ValueEncoder enc = new ValueEncoder(t, baos);
        enc.startArray(4);
        enc.writeInteger(0x01);
        enc.writeInteger(0x02);
        enc.writeInteger(0x03);
        enc.writeInteger(0x04);
        byte[] bytes = baos.toByteArray();
        // Wire count = 4 - 2 = 2 -> 0x02.
        assertEquals(bytes, new byte[] { 0x02, 0x01, 0x02, 0x03, 0x04 });

        List<DecodingEvent> events = decode(t, bytes);
        assertEquals(joinChunks(events), new byte[] { 0x01, 0x02, 0x03, 0x04 });
    }

    @Test
    public void rangeSizedArrayRejectsOutOfRangeCount() {
        Array t = new Array(new UnsignedInteger(1), new SizeConstraint.Range(2, 5));
        ValueEncoder tooLow = new ValueEncoder(t, new ByteArrayOutputStream());
        assertThrows(IllegalArgumentException.class, () -> tooLow.startArray(1));
        ValueEncoder tooHigh = new ValueEncoder(t, new ByteArrayOutputStream());
        assertThrows(IllegalArgumentException.class, () -> tooHigh.startArray(6));
    }

    @Test
    public void allSizedArrayUsesVarintMaxN8ForCount() {
        // SizeConstraint.All -> v=8 per spec.
        Array t = new Array(new UnsignedInteger(1), new SizeConstraint.All());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ValueEncoder enc = new ValueEncoder(t, baos);
        enc.startArray(0);
        assertTrue(enc.isComplete());
        // VarInt(8) of 0 = 0x00 (single byte).
        assertEquals(baos.toByteArray(), new byte[] { 0x00 });

        List<DecodingEvent> events = decode(t, new byte[] { 0x00 });
        assertEquals(events, List.of(
                new StartContainer(ContainerKind.ARRAY),
                new EndContainer(ContainerKind.ARRAY)));
    }

    @Test
    public void emptyFixedArrayProducesNoBytes() {
        Array t = new Array(new UnsignedInteger(1), SizeConstraint.exact(0));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ValueEncoder enc = new ValueEncoder(t, baos);
        enc.startArray(0);
        assertTrue(enc.isComplete());
        assertEquals(baos.toByteArray(), new byte[0]);

        List<DecodingEvent> events = decode(t, new byte[0]);
        assertEquals(events, List.of(
                new StartContainer(ContainerKind.ARRAY),
                new EndContainer(ContainerKind.ARRAY)));
    }

    @Test
    public void unitArrayHasZeroByteItems() {
        Array t = new Array(new Unit(), new SizeConstraint.Range(0, 5));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ValueEncoder enc = new ValueEncoder(t, baos);
        enc.startArray(3);
        assertTrue(enc.isComplete());
        // Just count byte 0x03; items take 0 bytes each.
        assertEquals(baos.toByteArray(), new byte[] { 0x03 });

        // Decoder emits no Chunk events for 0-byte items, but still emits Start/EndContainer.
        List<DecodingEvent> events = decode(t, new byte[] { 0x03 });
        assertEquals(events, List.of(
                new StartContainer(ContainerKind.ARRAY),
                new EndContainer(ContainerKind.ARRAY)));
    }

    @Test
    public void complexItemArrayUsesStartItemEndItem() {
        // Array of structs.
        Struct point = new Struct(List.of(f("x", new SignedInteger(1)), f("y", new SignedInteger(1))));
        Array t = new Array(point, new SizeConstraint.Range(0, 10));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ValueEncoder enc = new ValueEncoder(t, baos);
        enc.startArray(2);
        enc.writeInteger(1);   // points[0].x
        enc.writeInteger(-1);  // points[0].y
        enc.writeInteger(2);   // points[1].x
        enc.writeInteger(-2);  // points[1].y
        byte[] bytes = baos.toByteArray();
        assertEquals(bytes, new byte[] { 0x02, 0x01, (byte) 0xFF, 0x02, (byte) 0xFE });

        List<DecodingEvent> events = decode(t, bytes);
        assertEquals(events, List.of(
                new StartContainer(ContainerKind.ARRAY),
                new StartItem(),
                new StartField(0), new IntegerScalar(1L), new EndField(0),
                new StartField(1), new IntegerScalar(-1L), new EndField(1),
                new EndItem(),
                new StartItem(),
                new StartField(0), new IntegerScalar(2L), new EndField(0),
                new StartField(1), new IntegerScalar(-2L), new EndField(1),
                new EndItem(),
                new EndContainer(ContainerKind.ARRAY)));
    }

    @Test
    public void unionItemArrayResetsBitStateBetweenItems() {
        Union u = new Union(List.of(
                ctor("X", f("v", new UnsignedInteger(1))),
                ctor("Y", f("v", new UnsignedInteger(1)))));
        Array t = new Array(u, SizeConstraint.exact(3));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ValueEncoder enc = new ValueEncoder(t, baos);
        enc.startArray(3);
        enc.writeConstructor(1); enc.writeInteger(0xAA);
        enc.writeConstructor(0); enc.writeInteger(0xBB);
        enc.writeConstructor(1); enc.writeInteger(0xCC);
        byte[] bytes = baos.toByteArray();
        // Per item: byte0 = disc bit in MSB (others 0), byte1 = U(1).
        assertEquals(bytes, new byte[] {
                (byte) 0x80, (byte) 0xAA,
                0x00,        (byte) 0xBB,
                (byte) 0x80, (byte) 0xCC });

        List<DecodingEvent> events = decode(t, bytes);
        assertEquals(events, List.of(
                new StartContainer(ContainerKind.ARRAY),
                new StartItem(),
                new Constructor(1),
                new StartField(0), new IntegerScalar(0xAAL), new EndField(0),
                new EndItem(),
                new StartItem(),
                new Constructor(0),
                new StartField(0), new IntegerScalar(0xBBL), new EndField(0),
                new EndItem(),
                new StartItem(),
                new Constructor(1),
                new StartField(0), new IntegerScalar(0xCCL), new EndField(0),
                new EndItem(),
                new EndContainer(ContainerKind.ARRAY)));
    }

    @Test
    public void aggregateEntryClosesEnclosingBitByte() {
        Union u = new Union(List.of(ctor("A"), ctor("B")));
        Array arr = new Array(new UnsignedInteger(1), SizeConstraint.exact(2));
        Struct t = new Struct(List.of(f("flag", u), f("payload", arr)));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ValueEncoder enc = new ValueEncoder(t, baos);
        enc.writeConstructor(1);   // 1-bit disc
        enc.startArray(2);
        enc.writeInteger(0xAA);
        enc.writeInteger(0xBB);
        byte[] bytes = baos.toByteArray();
        // Byte 0: 0x80 (flag bit). Byte 1..2: array items 0xAA, 0xBB.
        assertEquals(bytes, new byte[] { (byte) 0x80, (byte) 0xAA, (byte) 0xBB });

        List<DecodingEvent> events = decode(t, bytes);
        assertTrue(events.contains(new StartContainer(ContainerKind.ARRAY)));
        assertTrue(events.contains(new EndContainer(ContainerKind.ARRAY)));
    }

    @Test
    public void nestedArrayOfArraysRoundTrips() {
        // Array(Array(U(1))) — outer of 2, inner of 3.
        Array inner = new Array(new UnsignedInteger(1), SizeConstraint.exact(3));
        Array outer = new Array(inner, SizeConstraint.exact(2));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ValueEncoder enc = new ValueEncoder(outer, baos);
        enc.startArray(2);
        enc.startArray(3);
        enc.writeInteger(1);
        enc.writeInteger(2);
        enc.writeInteger(3);
        enc.startArray(3);
        enc.writeInteger(4);
        enc.writeInteger(5);
        enc.writeInteger(6);
        byte[] bytes = baos.toByteArray();
        // Both fixed, no counts. Just 6 raw bytes.
        assertEquals(bytes, new byte[] { 1, 2, 3, 4, 5, 6 });

        List<DecodingEvent> events = decode(outer, bytes);
        assertEquals(events.get(0), new StartContainer(ContainerKind.ARRAY));
        assertEquals(events.get(1), new StartItem());
        assertEquals(events.get(2), new StartContainer(ContainerKind.ARRAY));
        assertEquals(events.get(3), new Chunk(new byte[] { 1, 2, 3 }));
        assertEquals(events.get(4), new EndContainer(ContainerKind.ARRAY));
        assertEquals(events.get(5), new EndItem());
        assertEquals(events.get(6), new StartItem());
        assertEquals(events.get(7), new StartContainer(ContainerKind.ARRAY));
        assertEquals(events.get(8), new Chunk(new byte[] { 4, 5, 6 }));
        assertEquals(events.get(9), new EndContainer(ContainerKind.ARRAY));
        assertEquals(events.get(10), new EndItem());
        assertEquals(events.get(11), new EndContainer(ContainerKind.ARRAY));
    }

    @Test
    public void countVarintSpansMultipleBytes() {
        Array t = new Array(new UnsignedInteger(1), new SizeConstraint.Range(0, 65535));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ValueEncoder enc = new ValueEncoder(t, baos);
        enc.startArray(500);  // 500 needs 9 bits; 2-byte varint.
        for (int i = 0; i < 500; i++) {
            enc.writeInteger(i & 0xFF);
        }
        byte[] bytes = baos.toByteArray();
        // Verify round trip — count bytes split across feeds too.
        EventCapture cap = new EventCapture();
        ValueDecoder dec = new ValueDecoder(t, cap);
        for (byte b : bytes) {
            dec.write(new byte[] { b });
        }
        assertTrue(dec.isComplete());
        // Should see exactly one StartContainer and EndContainer; lots of Chunks.
        assertEquals(cap.events.get(0), new StartContainer(ContainerKind.ARRAY));
        assertEquals(cap.events.get(cap.events.size() - 1), new EndContainer(ContainerKind.ARRAY));
        // All chunks together = 500 bytes.
        byte[] joined = joinChunks(cap.events);
        assertEquals(joined.length, 500);
        for (int i = 0; i < 500; i++) {
            assertEquals(joined[i] & 0xFF, i & 0xFF);
        }
    }

    @Test
    public void decoderEmitsMultipleChunksAcrossFeeds() {
        Array t = new Array(new UnsignedInteger(1), SizeConstraint.exact(4));

        EventCapture cap = new EventCapture();
        ValueDecoder dec = new ValueDecoder(t, cap);
        // First feed: StartContainer comes out at construction.
        assertEquals(cap.events, List.of(new StartContainer(ContainerKind.ARRAY)));

        dec.write(new byte[] { 0x11, 0x22 });
        dec.write(new byte[] { 0x33, 0x44 });
        assertTrue(dec.isComplete());
        assertEquals(cap.events, List.of(
                new StartContainer(ContainerKind.ARRAY),
                new Chunk(new byte[] { 0x11, 0x22 }),
                new Chunk(new byte[] { 0x33, 0x44 }),
                new EndContainer(ContainerKind.ARRAY)));
    }

    @Test
    public void encoderRejectsArrayOfVarInt() {
        assertThrows(UnsupportedOperationException.class, () ->
                new ValueEncoder(new Array(new VariableLengthInteger(4), SizeConstraint.exact(2)),
                        new ByteArrayOutputStream()));
    }

    @Test
    public void encoderRejectsPackedBitElement() {
        Union packed = new Union(List.of(ctor("A"), ctor("B")));
        Array t = new Array(packed, SizeConstraint.exact(3));
        assertThrows(UnsupportedOperationException.class,
                () -> new ValueEncoder(t, new ByteArrayOutputStream()));
    }

    @Test
    public void startArrayRejectedAtNonArrayPosition() {
        ValueEncoder enc = new ValueEncoder(new UnsignedInteger(1), new ByteArrayOutputStream());
        assertThrows(IllegalStateException.class, () -> enc.startArray(0));
    }

    @Test
    public void writeIntegerRejectedBeforeStartArray() {
        Array t = new Array(new UnsignedInteger(1), SizeConstraint.exact(2));
        ValueEncoder enc = new ValueEncoder(t, new ByteArrayOutputStream());
        assertThrows(IllegalStateException.class, () -> enc.writeInteger(0));
    }

    @Test
    public void byteByByteFeedForComplexItemArray() {
        Struct point = new Struct(List.of(f("x", new UnsignedInteger(1)), f("y", new UnsignedInteger(1))));
        Array t = new Array(point, new SizeConstraint.Range(0, 10));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ValueEncoder enc = new ValueEncoder(t, baos);
        enc.startArray(3);
        for (int i = 0; i < 3; i++) {
            enc.writeInteger(i + 1);
            enc.writeInteger(i + 10);
        }
        byte[] bytes = baos.toByteArray();

        EventCapture incremental = new EventCapture();
        ValueDecoder dec = new ValueDecoder(t, incremental);
        for (int i = 0; i < bytes.length - 1; i++) {
            dec.write(new byte[] { bytes[i] });
            assertFalse(dec.isComplete());
        }
        dec.write(new byte[] { bytes[bytes.length - 1] });
        assertTrue(dec.isComplete());

        EventCapture oneShot = new EventCapture();
        ValueDecoder oneShotDec = new ValueDecoder(t, oneShot);
        oneShotDec.write(bytes);
        assertEquals(incremental.events, oneShot.events);
    }
}
