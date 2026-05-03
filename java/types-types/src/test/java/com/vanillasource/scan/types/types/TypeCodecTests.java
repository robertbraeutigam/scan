package com.vanillasource.scan.types.types;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.type.Array;
import com.vanillasource.scan.types.codec.type.FloatingPoint;
import com.vanillasource.scan.types.codec.type.Set;
import com.vanillasource.scan.types.codec.type.SignedInteger;
import com.vanillasource.scan.types.codec.type.Stream;
import com.vanillasource.scan.types.codec.type.Struct;
import com.vanillasource.scan.types.codec.type.Union;
import com.vanillasource.scan.types.codec.type.Unit;
import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import com.vanillasource.scan.types.codec.type.VariableLengthInteger;
import com.vanillasource.scan.types.types.codec.TypeCodec;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public final class TypeCodecTests {

    @Test
    public void roundTripsUnit() {
        assertSameStructure(new Unit(), roundTrip(new Unit()));
    }

    @Test
    public void roundTripsUnsignedInteger() {
        assertSameStructure(new UnsignedInteger(4), roundTrip(new UnsignedInteger(4)));
    }

    @Test
    public void roundTripsSignedInteger() {
        assertSameStructure(new SignedInteger(8), roundTrip(new SignedInteger(8)));
    }

    @Test
    public void roundTripsVariableLengthInteger() {
        assertSameStructure(new VariableLengthInteger(2), roundTrip(new VariableLengthInteger(2)));
    }

    @Test
    public void roundTripsFloatingPoint() {
        assertSameStructure(new FloatingPoint(4), roundTrip(new FloatingPoint(4)));
        assertSameStructure(new FloatingPoint(8), roundTrip(new FloatingPoint(8)));
    }

    @Test
    public void roundTripsEmptyStruct() {
        assertSameStructure(new Struct(), roundTrip(new Struct()));
    }

    @Test
    public void roundTripsStruct() {
        Type original = new Struct(new SignedInteger(2), new UnsignedInteger(4), new FloatingPoint(8));
        assertSameStructure(original, roundTrip(original));
    }

    @Test
    public void roundTripsSingleConstructorUnion() {
        Type original = new Union(List.of(List.of(new UnsignedInteger(1))));
        assertSameStructure(original, roundTrip(original));
    }

    @Test
    public void roundTripsMultiConstructorUnion() {
        Type original = new Union(List.of(
                List.of(),
                List.of(new FloatingPoint(8)),
                List.of(new SignedInteger(4), new UnsignedInteger(2))));
        assertSameStructure(original, roundTrip(original));
    }

    @Test
    public void roundTripsFixedSizeArray() {
        Type original = new Array(24, 24, new FloatingPoint(8));
        assertSameStructure(original, roundTrip(original));
    }

    @Test
    public void roundTripsBoundedArray() {
        Type original = new Array(0, 128, new UnsignedInteger(1));
        assertSameStructure(original, roundTrip(original));
    }

    @Test
    public void roundTripsUnboundedArray() {
        Type original = new Array(0, Integer.MAX_VALUE, new UnsignedInteger(1));
        assertSameStructure(original, roundTrip(original));
    }

    @Test
    public void roundTripsSet() {
        assertSameStructure(new Set(7), roundTrip(new Set(7)));
    }

    @Test
    public void roundTripsStream() {
        Type original = new Stream(new FloatingPoint(8));
        assertSameStructure(original, roundTrip(original));
    }

    @Test
    public void roundTripsStreamOfBytes() {
        Type original = new Stream(new UnsignedInteger(1));
        assertSameStructure(original, roundTrip(original));
    }

    @Test
    public void roundTripsNestedStructInArray() {
        Type original = new Array(0, 100,
                new Struct(new UnsignedInteger(2), new SignedInteger(2)));
        assertSameStructure(original, roundTrip(original));
    }

    @Test
    public void roundTripsOptionLikeUnion() {
        Type original = new Union(List.of(
                List.of(),
                List.of(new FloatingPoint(8))));
        assertSameStructure(original, roundTrip(original));
    }

    @Test
    public void roundTripsDeeplyNested() {
        Type original = new Struct(
                new UnsignedInteger(1),
                new Array(0, Integer.MAX_VALUE,
                        new Union(List.of(
                                List.of(new SignedInteger(2)),
                                List.of(new Stream(new UnsignedInteger(1)))))),
                new Set(3));
        assertSameStructure(original, roundTrip(original));
    }

    @Test
    public void emitsBytesAndDecodesIdentical() {
        Type original = new Struct(new UnsignedInteger(2), new FloatingPoint(8));
        byte[] first = TypeCodec.serialize(original);
        Type recovered = TypeCodec.deserialize(first);
        byte[] second = TypeCodec.serialize(recovered);
        assertEquals(second, first, "wire bytes must be stable across round-trip");
    }

    private static Type roundTrip(Type t) {
        byte[] bytes = TypeCodec.serialize(t);
        return TypeCodec.deserialize(bytes);
    }

    private static void assertSameStructure(Type expected, Type actual) {
        assertTrue(structurallyEqual(expected, actual),
                "expected " + describe(expected) + " but got " + describe(actual));
    }

    private static boolean structurallyEqual(Type a, Type b) {
        if (a.getClass() != b.getClass()) {
            return false;
        }
        if (a instanceof Unit) {
            return true;
        }
        if (a instanceof UnsignedInteger ua) {
            return ua.byteSize() == ((UnsignedInteger) b).byteSize();
        }
        if (a instanceof SignedInteger sa) {
            return sa.byteSize() == ((SignedInteger) b).byteSize();
        }
        if (a instanceof VariableLengthInteger va) {
            return va.maxBytes() == ((VariableLengthInteger) b).maxBytes();
        }
        if (a instanceof FloatingPoint fa) {
            return fa.byteSize() == ((FloatingPoint) b).byteSize();
        }
        if (a instanceof Struct sa) {
            return listsEqual(sa.fieldTypes(), ((Struct) b).fieldTypes());
        }
        if (a instanceof Union ua) {
            List<List<Type>> ac = ua.constructorFields();
            List<List<Type>> bc = ((Union) b).constructorFields();
            if (ac.size() != bc.size()) {
                return false;
            }
            for (int i = 0; i < ac.size(); i++) {
                if (!listsEqual(ac.get(i), bc.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof Array aa) {
            Array bb = (Array) b;
            return aa.min() == bb.min() && aa.max() == bb.max()
                    && structurallyEqual(aa.itemType(), bb.itemType());
        }
        if (a instanceof Set sa) {
            return sa.memberCount() == ((Set) b).memberCount();
        }
        if (a instanceof Stream sa) {
            return structurallyEqual(sa.itemType(), ((Stream) b).itemType());
        }
        return false;
    }

    private static boolean listsEqual(List<Type> a, List<Type> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!structurallyEqual(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static String describe(Type t) {
        if (t instanceof Unit) return "Unit";
        if (t instanceof UnsignedInteger u) return "UnsignedInteger(" + u.byteSize() + ")";
        if (t instanceof SignedInteger s) return "SignedInteger(" + s.byteSize() + ")";
        if (t instanceof VariableLengthInteger v) return "VariableLengthInteger(" + v.maxBytes() + ")";
        if (t instanceof FloatingPoint f) return "FloatingPoint(" + f.byteSize() + ")";
        if (t instanceof Struct s) {
            StringBuilder sb = new StringBuilder("Struct(");
            for (int i = 0; i < s.fieldTypes().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(describe(s.fieldTypes().get(i)));
            }
            return sb.append(")").toString();
        }
        if (t instanceof Union u) {
            StringBuilder sb = new StringBuilder("Union(");
            List<List<Type>> cs = u.constructorFields();
            for (int i = 0; i < cs.size(); i++) {
                if (i > 0) sb.append(" | ");
                sb.append("[");
                for (int j = 0; j < cs.get(i).size(); j++) {
                    if (j > 0) sb.append(", ");
                    sb.append(describe(cs.get(i).get(j)));
                }
                sb.append("]");
            }
            return sb.append(")").toString();
        }
        if (t instanceof Array a) return "Array(" + a.min() + ".." + a.max() + ", " + describe(a.itemType()) + ")";
        if (t instanceof Set s) return "Set(" + s.memberCount() + ")";
        if (t instanceof Stream s) return "Stream(" + describe(s.itemType()) + ")";
        return t.getClass().getSimpleName();
    }
}
