package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitReader;
import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.DecodingEvent;
import com.vanillasource.scan.types.codec.DecodingEventHandler;

import java.util.List;

/** Walks a list of struct or constructor fields, emitting Start/EndField around each. */
final class FieldsDecoderFrame implements DecoderFrame {
    private final List<Field> fields;
    private int fieldIndex;

    FieldsDecoderFrame(List<Field> fields) {
        this.fields = fields;
    }

    @Override
    public Result step(BitReader bits, DecodingEventHandler events) {
        return advanceToNextField(events);
    }

    @Override
    public Result onChildCompleted(BitReader bits, DecodingEventHandler events) {
        events.onEvent(new DecodingEvent.EndField(fieldIndex));
        fieldIndex++;
        return advanceToNextField(events);
    }

    private Result advanceToNextField(DecodingEventHandler events) {
        while (fieldIndex < fields.size()) {
            events.onEvent(new DecodingEvent.StartField(fieldIndex));
            DecoderFrame childFrame = fields.get(fieldIndex).type().createDecodeFrame();
            if (childFrame != null) {
                return new Result.Push(childFrame);
            }
            events.onEvent(new DecodingEvent.EndField(fieldIndex));
            fieldIndex++;
        }
        return new Result.Done();
    }
}
