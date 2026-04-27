package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitReader;
import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.DecodingEvent;
import com.vanillasource.scan.types.codec.DecodingEventHandler;

/** One-shot frame for {@link Unit}: emits {@link DecodingEvent.UnitScalar} and completes. */
final class UnitDecoderFrame implements DecoderFrame {
    @Override
    public Result step(BitReader bits, DecodingEventHandler events) {
        events.onEvent(new DecodingEvent.UnitScalar());
        return new Result.Done();
    }

    @Override
    public Result onChildCompleted(BitReader bits, DecodingEventHandler events) {
        throw new IllegalStateException("primitive frame has no children");
    }
}
