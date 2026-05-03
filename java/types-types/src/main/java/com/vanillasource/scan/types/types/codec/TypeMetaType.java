package com.vanillasource.scan.types.types.codec;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.type.Array;
import com.vanillasource.scan.types.codec.type.Struct;
import com.vanillasource.scan.types.codec.type.Union;
import com.vanillasource.scan.types.codec.type.UnsignedInteger;
import com.vanillasource.scan.types.codec.type.VariableLengthInteger;

import java.util.List;

/**
 * The meta-type — runtime {@link Type} corresponding to the {@code Type} union
 * defined in TYPES.md §"Types Binary Representation". This schema is built into
 * every peer (it never travels on the wire); {@link TypeCodec} drives a
 * runtime {@link Type} value through it.
 *
 * <p>The meta-type is recursive (a {@code Type} value can carry nested
 * {@code Type} values inside {@code Struct}, {@code Union}, {@code Array} and
 * {@code Stream}). The cycle is broken by a {@link LazyType} forward-reference
 * set after the union is built.
 */
public final class TypeMetaType {
    public static final Type META_TYPE = buildMetaType();

    private TypeMetaType() {
    }

    private static Type buildMetaType() {
        LazyType typeT = new LazyType();
        Type byteT = new UnsignedInteger(1);
        Type vli8 = new VariableLengthInteger(8);
        Type fieldArrayT = new Array(0, Integer.MAX_VALUE, typeT);
        Type ctorArrayT = new Array(0, Integer.MAX_VALUE, fieldArrayT);

        Type metaT = new Union(List.of(
                List.of(),                          // 0: Unit
                List.of(byteT),                     // 1: UnsignedInteger { byteSize }
                List.of(byteT),                     // 2: SignedInteger   { byteSize }
                List.of(byteT),                     // 3: VariableLengthInteger { maxBytes }
                List.of(byteT),                     // 4: FloatingPoint   { byteSize }
                List.of(fieldArrayT),               // 5: Struct          { fieldTypes }
                List.of(ctorArrayT),                // 6: Union           { constructors }
                List.of(vli8, vli8, (Type) typeT),  // 7: Array           { min, max, itemType }
                List.of(vli8),                      // 8: Set             { memberCount }
                List.of((Type) typeT)               // 9: Stream          { itemType }
        ));

        typeT.set(metaT);
        return metaT;
    }
}
