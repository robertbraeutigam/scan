package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitWriter;
import com.vanillasource.scan.types.codec.EncoderFrame;

import java.util.List;

/** Walks a list of struct or constructor fields, descending into each field's frame in turn. */
final class FieldsEncoderFrame implements EncoderFrame {
    private final List<Field> fields;
    private int fieldIndex;

    FieldsEncoderFrame(List<Field> fields) {
        this.fields = fields;
    }

    @Override
    public String describe() {
        return "field " + fieldIndex + " of " + fields.size();
    }

    @Override
    public Result onPushed(BitWriter bits) {
        return descendNextField();
    }

    @Override
    public Result onChildCompleted(BitWriter bits) {
        fieldIndex++;
        return descendNextField();
    }

    private Result descendNextField() {
        while (fieldIndex < fields.size()) {
            EncoderFrame childFrame = fields.get(fieldIndex).type().createEncodeFrame();
            if (childFrame != null) {
                return new Result.Push(childFrame);
            }
            fieldIndex++;
        }
        return new Result.Done();
    }
}
