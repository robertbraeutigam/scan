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
    private DecoderFrame currentChild;

    FieldsDecoderFrame(List<Field> fields) {
        this.fields = fields;
    }

    @Override
    public Result step(BitReader bits, DecodingEventHandler events) {
        while (true) {
            if (currentChild != null) {
                Result r = currentChild.step(bits, events);
                if (r instanceof Result.WaitForInput) {
                    return r;
                }
                currentChild = null;
                events.onEvent(new DecodingEvent.EndField(fieldIndex));
                fieldIndex++;
            }
            if (fieldIndex >= fields.size()) {
                return new Result.Done();
            }
            events.onEvent(new DecodingEvent.StartField(fieldIndex));
            DecoderFrame child = fields.get(fieldIndex).type().createDecodeFrame();
            if (child == null) {
                events.onEvent(new DecodingEvent.EndField(fieldIndex));
                fieldIndex++;
            } else {
                currentChild = child;
            }
        }
    }
}
