package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitWriter;
import com.vanillasource.scan.types.codec.EncoderFrame;

import java.util.List;

/** Walks a list of struct or constructor fields, owning and stepping each field's frame in turn. */
final class FieldsEncoderFrame implements EncoderFrame {
    private final List<Field> fields;
    private int fieldIndex;
    private EncoderFrame currentChild;

    FieldsEncoderFrame(List<Field> fields) {
        this.fields = fields;
    }

    @Override
    public String describe() {
        if (currentChild != null) {
            return currentChild.describe();
        }
        return "field " + fieldIndex + " of " + fields.size();
    }

    @Override
    public Result onEntered(BitWriter bits) {
        return enterFields(bits);
    }

    @Override
    public Result writeInteger(BitWriter bits, long value) {
        return afterChild(bits, currentChild.writeInteger(bits, value));
    }

    @Override
    public Result writeFloat(BitWriter bits, double value) {
        return afterChild(bits, currentChild.writeFloat(bits, value));
    }

    @Override
    public Result writeConstructor(BitWriter bits, int index) {
        return afterChild(bits, currentChild.writeConstructor(bits, index));
    }

    @Override
    public Result startArray(BitWriter bits, int count) {
        return afterChild(bits, currentChild.startArray(bits, count));
    }

    private Result afterChild(BitWriter bits, Result r) {
        if (r instanceof Result.Done) {
            currentChild = null;
            fieldIndex++;
            return enterFields(bits);
        }
        return r;
    }

    private Result enterFields(BitWriter bits) {
        while (fieldIndex < fields.size()) {
            EncoderFrame child = fields.get(fieldIndex).type().createEncodeFrame();
            if (child == null) {
                fieldIndex++;
                continue;
            }
            currentChild = child;
            Result r = child.onEntered(bits);
            if (r instanceof Result.Done) {
                currentChild = null;
                fieldIndex++;
                continue;
            }
            return r;
        }
        return new Result.Done();
    }
}
