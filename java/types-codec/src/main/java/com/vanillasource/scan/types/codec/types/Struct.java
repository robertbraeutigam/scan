package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.EncoderFrame;
import com.vanillasource.scan.types.codec.Type;

import java.util.List;
import java.util.OptionalInt;

public record Struct(List<Field> fields) implements Type {
    public Struct {
        fields = List.copyOf(fields);
        Field.requireStreamLastIfPresent(fields);
    }

    @Override public boolean containsStream() {
        return Field.anyContainsStream(fields);
    }

    @Override public OptionalInt staticBitSize() {
        return Field.sumBitSizes(fields);
    }

    @Override
    public DecoderFrame createDecodeFrame() {
        return fields.isEmpty() ? null : new DecoderFrames.FieldsFrame(fields);
    }

    @Override
    public EncoderFrame createEncodeFrame() {
        return fields.isEmpty() ? null : new EncoderFrames.FieldsFrame(fields);
    }
}
