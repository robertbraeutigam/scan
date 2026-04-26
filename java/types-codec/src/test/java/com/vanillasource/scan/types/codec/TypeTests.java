package com.vanillasource.scan.types.codec;

import com.vanillasource.scan.types.codec.Type.Array;
import com.vanillasource.scan.types.codec.Type.Constructor;
import com.vanillasource.scan.types.codec.Type.Field;
import com.vanillasource.scan.types.codec.Type.FloatingPoint;
import com.vanillasource.scan.types.codec.Type.SignedInteger;
import com.vanillasource.scan.types.codec.Type.Stream;
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

public final class TypeTests {

    @Test
    public void primitiveByteSizesAccept1Through8() {
        for (int n = 1; n <= 8; n++) {
            new UnsignedInteger(n);
            new SignedInteger(n);
            new VariableLengthInteger(n);
        }
    }

    @Test
    public void primitiveByteSizeRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> new UnsignedInteger(0));
        assertThrows(IllegalArgumentException.class, () -> new SignedInteger(0));
        assertThrows(IllegalArgumentException.class, () -> new VariableLengthInteger(0));
    }

    @Test
    public void primitiveByteSizeRejectsNine() {
        assertThrows(IllegalArgumentException.class, () -> new UnsignedInteger(9));
        assertThrows(IllegalArgumentException.class, () -> new SignedInteger(9));
        assertThrows(IllegalArgumentException.class, () -> new VariableLengthInteger(9));
    }

    @Test
    public void floatingPointAcceptsOnly4And8() {
        new FloatingPoint(4);
        new FloatingPoint(8);
        assertThrows(IllegalArgumentException.class, () -> new FloatingPoint(2));
        assertThrows(IllegalArgumentException.class, () -> new FloatingPoint(6));
    }

    @Test
    public void unionRequiresAtLeastOneConstructor() {
        assertThrows(IllegalArgumentException.class, () -> new Union(List.of()));
    }

    @Test
    public void unionWithSingleConstructorAccepted() {
        new Union(List.of(new Constructor("Only", List.of())));
    }

    @Test
    public void streamElementCannotBeStream() {
        Stream inner = new Stream(new UnsignedInteger(1));
        assertThrows(IllegalArgumentException.class, () -> new Stream(inner));
    }

    @Test
    public void arrayElementCannotContainStream() {
        Stream inner = new Stream(new UnsignedInteger(1));
        assertThrows(IllegalArgumentException.class,
                () -> new Array(inner, SizeConstraint.exact(4)));
        Struct streamBearing = new Struct(List.of(
                new Field("payload", inner)));
        assertThrows(IllegalArgumentException.class,
                () -> new Array(streamBearing, new SizeConstraint.All()));
    }

    @Test
    public void setElementCannotContainStream() {
        Stream inner = new Stream(new UnsignedInteger(1));
        assertThrows(IllegalArgumentException.class,
                () -> new Type.Set(inner, new SizeConstraint.All()));
    }

    @Test
    public void structAcceptsStreamAsLastField() {
        Struct s = new Struct(List.of(
                new Field("header", new UnsignedInteger(2)),
                new Field("payload", new Stream(new UnsignedInteger(1)))));
        assertTrue(s.containsStream());
    }

    @Test
    public void structRejectsStreamBeforeEnd() {
        assertThrows(IllegalArgumentException.class, () -> new Struct(List.of(
                new Field("payload", new Stream(new UnsignedInteger(1))),
                new Field("trailer", new UnsignedInteger(2)))));
    }

    @Test
    public void structRejectsTransitivelyStreamBearingFieldBeforeEnd() {
        Struct nested = new Struct(List.of(
                new Field("data", new Stream(new UnsignedInteger(1)))));
        assertThrows(IllegalArgumentException.class, () -> new Struct(List.of(
                new Field("first", nested),
                new Field("second", new UnsignedInteger(1)))));
    }

    @Test
    public void constructorAcceptsStreamAsLastField() {
        new Constructor("WithStream", List.of(
                new Field("hdr", new UnsignedInteger(1)),
                new Field("body", new Stream(new UnsignedInteger(1)))));
    }

    @Test
    public void constructorRejectsStreamBeforeEnd() {
        assertThrows(IllegalArgumentException.class, () -> new Constructor("Bad", List.of(
                new Field("body", new Stream(new UnsignedInteger(1))),
                new Field("trailer", new UnsignedInteger(1)))));
    }

    @Test
    public void containsStreamReportsFalseForPrimitivesAndUnit() {
        assertFalse(new Unit().containsStream());
        assertFalse(new UnsignedInteger(1).containsStream());
        assertFalse(new FloatingPoint(8).containsStream());
        assertFalse(new VariableLengthInteger(4).containsStream());
    }

    @Test
    public void containsStreamReportsTrueForStreamItself() {
        assertTrue(new Stream(new UnsignedInteger(1)).containsStream());
    }

    @Test
    public void containsStreamReportsTrueWhenNestedInUnionConstructor() {
        Union u = new Union(List.of(
                new Constructor("A", List.of(new Field("x", new UnsignedInteger(1)))),
                new Constructor("B", List.of(new Field("y", new Stream(new UnsignedInteger(1)))))));
        assertTrue(u.containsStream());
    }

    @Test
    public void containsStreamReportsFalseForStreamFreeStructUnion() {
        Struct s = new Struct(List.of(
                new Field("a", new UnsignedInteger(1)),
                new Field("b", new SignedInteger(2))));
        assertFalse(s.containsStream());
        Union u = new Union(List.of(
                new Constructor("Only", List.of(new Field("x", s)))));
        assertFalse(u.containsStream());
    }

    @Test
    public void structFieldsAreDefensivelyCopied() {
        List<Field> mutable = new ArrayList<>();
        mutable.add(new Field("a", new UnsignedInteger(1)));
        Struct s = new Struct(mutable);
        mutable.add(new Field("b", new UnsignedInteger(1)));
        assertEquals(s.fields().size(), 1);
        assertThrows(UnsupportedOperationException.class,
                () -> s.fields().add(new Field("c", new UnsignedInteger(1))));
    }

    @Test
    public void recordEqualityIsStructural() {
        assertEquals(new UnsignedInteger(4), new UnsignedInteger(4));
        assertEquals(
                new Struct(List.of(new Field("x", new UnsignedInteger(1)))),
                new Struct(List.of(new Field("x", new UnsignedInteger(1)))));
        assertEquals(
                new Array(new UnsignedInteger(1), SizeConstraint.exact(3)),
                new Array(new UnsignedInteger(1), new SizeConstraint.Range(3, 3)));
    }

    @Test
    public void sizeConstraintFixedDetection() {
        assertTrue(SizeConstraint.exact(5).isFixed());
        assertTrue(new SizeConstraint.Range(5, 5).isFixed());
        assertFalse(new SizeConstraint.Range(0, 10).isFixed());
        assertFalse(new SizeConstraint.All().isFixed());
    }

    @Test
    public void sizeConstraintRangeRejectsNegativeOrInverted() {
        assertThrows(IllegalArgumentException.class, () -> new SizeConstraint.Range(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> new SizeConstraint.Range(10, 5));
    }
}
