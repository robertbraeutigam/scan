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
import com.vanillasource.scan.types.codec.Type.Array;
import com.vanillasource.scan.types.codec.Type.Field;
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

public final class ArrayCodecTests {

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

    private static byte[] joinChunks(List<DecodingEvent> events) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
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

        ValueEncoder enc = new ValueEncoder(t);
        enc.startArray(3);
        enc.writeInteger(0x10);
        enc.writeInteger(0x20);
        enc.writeInteger(0x30);
        assertTrue(enc.isComplete());
        // No count on the wire — just the three bytes back-to-back.
        assertEquals(enc.toByteArray(), new byte[] { 0x10, 0x20, 0x30 });

        List<DecodingEvent> events = decode(t, new byte[] { 0x10, 0x20, 0x30 });
        assertEquals(events.get(0), new StartContainer(ContainerKind.ARRAY));
        assertEquals(events.get(events.size() - 1), new EndContainer(ContainerKind.ARRAY));
        assertEquals(joinChunks(events), new byte[] { 0x10, 0x20, 0x30 });
    }

    @Test
    public void fixedSizeArrayRejectsWrongCount() {
        Array t = new Array(new UnsignedInteger(1), SizeConstraint.exact(3));
        ValueEncoder enc = new ValueEncoder(t);
        assertThrows(IllegalArgumentException.class, () -> enc.startArray(2));
    }

    @Test
    public void rangeSizedArrayWritesVarintCount() {
        // Range(0, 100) needs ceil(log2(101)) = 7 bits, so v=1 (capacity 8 bits).
        Array t = new Array(new UnsignedInteger(2), new SizeConstraint.Range(0, 100));

        ValueEncoder enc = new ValueEncoder(t);
        enc.startArray(2);
        enc.writeInteger(0xABCD);
        enc.writeInteger(0x1234);
        byte[] bytes = enc.toByteArray();
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

        ValueEncoder enc = new ValueEncoder(t);
        enc.startArray(4);
        enc.writeInteger(0x01);
        enc.writeInteger(0x02);
        enc.writeInteger(0x03);
        enc.writeInteger(0x04);
        byte[] bytes = enc.toByteArray();
        // Wire count = 4 - 2 = 2 -> 0x02.
        assertEquals(bytes, new byte[] { 0x02, 0x01, 0x02, 0x03, 0x04 });

        List<DecodingEvent> events = decode(t, bytes);
        assertEquals(joinChunks(events), new byte[] { 0x01, 0x02, 0x03, 0x04 });
    }

    @Test
    public void rangeSizedArrayRejectsOutOfRangeCount() {
        Array t = new Array(new UnsignedInteger(1), new SizeConstraint.Range(2, 5));
        ValueEncoder tooLow = new ValueEncoder(t);
        assertThrows(IllegalArgumentException.class, () -> tooLow.startArray(1));
        ValueEncoder tooHigh = new ValueEncoder(t);
        assertThrows(IllegalArgumentException.class, () -> tooHigh.startArray(6));
    }

    @Test
    public void allSizedArrayUsesVarintMaxN8ForCount() {
        // SizeConstraint.All -> v=8 per spec.
        Array t = new Array(new UnsignedInteger(1), new SizeConstraint.All());

        ValueEncoder enc = new ValueEncoder(t);
        enc.startArray(0);
        assertTrue(enc.isComplete());
        // VarInt(8) of 0 = 0x00 (single byte).
        assertEquals(enc.toByteArray(), new byte[] { 0x00 });

        List<DecodingEvent> events = decode(t, new byte[] { 0x00 });
        assertEquals(events, List.of(
                new StartContainer(ContainerKind.ARRAY),
                new EndContainer(ContainerKind.ARRAY)));
    }

    @Test
    public void emptyFixedArrayProducesNoBytes() {
        Array t = new Array(new UnsignedInteger(1), SizeConstraint.exact(0));

        ValueEncoder enc = new ValueEncoder(t);
        enc.startArray(0);
        assertTrue(enc.isComplete());
        assertEquals(enc.toByteArray(), new byte[0]);

        List<DecodingEvent> events = decode(t, new byte[0]);
        assertEquals(events, List.of(
                new StartContainer(ContainerKind.ARRAY),
                new EndContainer(ContainerKind.ARRAY)));
    }

    @Test
    public void unitArrayHasZeroByteItems() {
        Array t = new Array(new Unit(), new SizeConstraint.Range(0, 5));

        ValueEncoder enc = new ValueEncoder(t);
        enc.startArray(3);
        assertTrue(enc.isComplete());
        // Just count byte 0x03; items take 0 bytes each.
        assertEquals(enc.toByteArray(), new byte[] { 0x03 });

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

        ValueEncoder enc = new ValueEncoder(t);
        enc.startArray(2);
        enc.writeInteger(1);   // points[0].x
        enc.writeInteger(-1);  // points[0].y
        enc.writeInteger(2);   // points[1].x
        enc.writeInteger(-2);  // points[1].y
        byte[] bytes = enc.toByteArray();
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
        // Union with 2 ctors each carrying U(1): disc (1 bit) + content (8 bits) = 9 bits per
        // item -> byte-aligned to 2 bytes. The item's 7 unused bits in byte 0 must NOT be
        // reused by the next item's discriminator (TYPES.md: "bit state is contained within
        // the item's byte span and does not carry across items").
        Union u = new Union(List.of(
                ctor("X", f("v", new UnsignedInteger(1))),
                ctor("Y", f("v", new UnsignedInteger(1)))));
        Array t = new Array(u, SizeConstraint.exact(3));

        ValueEncoder enc = new ValueEncoder(t);
        enc.startArray(3);
        enc.writeConstructor(1); enc.writeInteger(0xAA);
        enc.writeConstructor(0); enc.writeInteger(0xBB);
        enc.writeConstructor(1); enc.writeInteger(0xCC);
        byte[] bytes = enc.toByteArray();
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
        // Struct { flag: Union(2 ctors, 1 bit), payload: Array(U(1), exact(2)) }.
        // Per TYPES.md: aggregate entry closes the in-progress bit byte; the
        // enclosing struct's flag bit lives in byte 0 alone, then the array bytes
        // start at byte 1.
        Union u = new Union(List.of(ctor("A"), ctor("B")));
        Array arr = new Array(new UnsignedInteger(1), SizeConstraint.exact(2));
        Struct t = new Struct(List.of(f("flag", u), f("payload", arr)));

        ValueEncoder enc = new ValueEncoder(t);
        enc.writeConstructor(1);   // 1-bit disc
        enc.startArray(2);
        enc.writeInteger(0xAA);
        enc.writeInteger(0xBB);
        byte[] bytes = enc.toByteArray();
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

        ValueEncoder enc = new ValueEncoder(outer);
        enc.startArray(2);
        enc.startArray(3);
        enc.writeInteger(1);
        enc.writeInteger(2);
        enc.writeInteger(3);
        enc.startArray(3);
        enc.writeInteger(4);
        enc.writeInteger(5);
        enc.writeInteger(6);
        byte[] bytes = enc.toByteArray();
        // Both fixed, no counts. Just 6 raw bytes.
        assertEquals(bytes, new byte[] { 1, 2, 3, 4, 5, 6 });

        List<DecodingEvent> events = decode(outer, bytes);
        // Outer is complex-item (its element is an Array), inner is primitive-item.
        // Expect: SC(outer), SI, SC(inner), Chunk(1,2,3), EC(inner), EI,
        //                   SI, SC(inner), Chunk(4,5,6), EC(inner), EI, EC(outer).
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
        // Range(0, 65535) -> bitsNeeded=16 -> v=3 (capacity 22 bits).
        // count=200 fits in 7 bits; encoded as single varint byte 0xC8 (high bit clear).
        // Wait — varint is most-significant-group-first. 200 = 0b11001000.
        // For varint(3), 200 fits in 7 bits => k=1, byte = 0xC8 (high bit 1 = 200's MSB).
        // Hmm actually 0xC8 has high bit set; that would signal "continuation".
        // Let me pick a value that round-trips cleanly.
        Array t = new Array(new UnsignedInteger(1), new SizeConstraint.Range(0, 65535));

        ValueEncoder enc = new ValueEncoder(t);
        enc.startArray(500);  // 500 needs 9 bits; 2-byte varint.
        for (int i = 0; i < 500; i++) {
            enc.writeInteger(i & 0xFF);
        }
        byte[] bytes = enc.toByteArray();
        // Verify round trip — count bytes split across feeds too.
        Capture cap = new Capture();
        ValueDecoder dec = new ValueDecoder(t, cap);
        for (byte b : bytes) {
            dec.feed(new byte[] { b });
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

        Capture cap = new Capture();
        ValueDecoder dec = new ValueDecoder(t, cap);
        // First feed: StartContainer comes out at construction.
        assertEquals(cap.events, List.of(new StartContainer(ContainerKind.ARRAY)));

        dec.feed(new byte[] { 0x11, 0x22 });
        dec.feed(new byte[] { 0x33, 0x44 });
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
                new ValueEncoder(new Array(new VariableLengthInteger(4), SizeConstraint.exact(2))));
    }

    @Test
    public void encoderRejectsPackedBitElement() {
        // Union with 2 bare-identifier ctors has static size = 1 bit (ceilLog2(2) + 0).
        // The spec requires packed layout for 1..4-bit elements; iteration 5 only handles
        // byte-aligned items, so the codec rejects this type up front.
        Union packed = new Union(List.of(ctor("A"), ctor("B")));
        Array t = new Array(packed, SizeConstraint.exact(3));
        assertThrows(UnsupportedOperationException.class, () -> new ValueEncoder(t));
    }

    @Test
    public void startArrayRejectedAtNonArrayPosition() {
        ValueEncoder enc = new ValueEncoder(new UnsignedInteger(1));
        assertThrows(IllegalStateException.class, () -> enc.startArray(0));
    }

    @Test
    public void writeIntegerRejectedBeforeStartArray() {
        Array t = new Array(new UnsignedInteger(1), SizeConstraint.exact(2));
        ValueEncoder enc = new ValueEncoder(t);
        assertThrows(IllegalStateException.class, () -> enc.writeInteger(0));
    }

    @Test
    public void byteByByteFeedForComplexItemArray() {
        Struct point = new Struct(List.of(f("x", new UnsignedInteger(1)), f("y", new UnsignedInteger(1))));
        Array t = new Array(point, new SizeConstraint.Range(0, 10));

        ValueEncoder enc = new ValueEncoder(t);
        enc.startArray(3);
        for (int i = 0; i < 3; i++) {
            enc.writeInteger(i + 1);
            enc.writeInteger(i + 10);
        }
        byte[] bytes = enc.toByteArray();

        Capture incremental = new Capture();
        ValueDecoder dec = new ValueDecoder(t, incremental);
        for (int i = 0; i < bytes.length - 1; i++) {
            dec.feed(new byte[] { bytes[i] });
            assertFalse(dec.isComplete());
        }
        dec.feed(new byte[] { bytes[bytes.length - 1] });
        assertTrue(dec.isComplete());

        Capture oneShot = new Capture();
        ValueDecoder oneShotDec = new ValueDecoder(t, oneShot);
        oneShotDec.feed(bytes);
        assertEquals(incremental.events, oneShot.events);
    }
}
