package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitReader;
import com.vanillasource.scan.types.codec.DecoderFrame;
import com.vanillasource.scan.types.codec.DecodingEvent;
import com.vanillasource.scan.types.codec.DecodingEventHandler;
import com.vanillasource.scan.types.codec.SizeConstraint;
import com.vanillasource.scan.types.codec.VarIntDecoder;

import java.util.OptionalInt;

/**
 * Drives an {@link Array}: closes the bit byte, emits StartContainer, reads the
 * count (fixed or varint), then either chunk-emits raw bytes for fixed-byte
 * primitive items or descends per-item with Start/EndItem markers.
 */
final class ArrayDecoderFrame implements DecoderFrame {
    private final Array array;
    private boolean entered;
    private boolean countRead;
    private boolean primitiveItems;
    private int itemByteSize;
    private int declaredCount;
    private int itemsCompleted;
    private long bytesRemaining;
    private VarIntDecoder countDecoder;

    ArrayDecoderFrame(Array array) {
        this.array = array;
    }

    @Override
    public Result step(BitReader bits, DecodingEventHandler events) {
        if (!entered) {
            bits.closeBitByte();
            events.onEvent(new DecodingEvent.StartContainer(DecodingEvent.ContainerKind.ARRAY));
            entered = true;
            OptionalInt fixed = array.element().fixedPrimitiveByteSize();
            primitiveItems = fixed.isPresent();
            if (primitiveItems) {
                itemByteSize = fixed.getAsInt();
            }
        }
        if (!countRead) {
            if (!readCount(bits)) {
                return new Result.WaitForInput();
            }
        }
        if (primitiveItems) {
            return stepPrimitiveItems(bits, events);
        }
        return advanceToNextItem(bits, events);
    }

    @Override
    public Result onChildCompleted(BitReader bits, DecodingEventHandler events) {
        events.onEvent(new DecodingEvent.EndItem());
        itemsCompleted++;
        bits.closeBitByte();
        return advanceToNextItem(bits, events);
    }

    private Result advanceToNextItem(BitReader bits, DecodingEventHandler events) {
        while (itemsCompleted < declaredCount) {
            events.onEvent(new DecodingEvent.StartItem());
            DecoderFrame childFrame = array.element().createDecodeFrame();
            if (childFrame != null) {
                return new Result.Push(childFrame);
            }
            events.onEvent(new DecodingEvent.EndItem());
            itemsCompleted++;
        }
        bits.closeBitByte();
        events.onEvent(new DecodingEvent.EndContainer(DecodingEvent.ContainerKind.ARRAY));
        return new Result.Done();
    }

    private boolean readCount(BitReader bits) {
        SizeConstraint sc = array.size();
        if (sc.isFixed()) {
            declaredCount = sc.lowerBound();
            countRead = true;
        } else {
            if (countDecoder == null) {
                countDecoder = new VarIntDecoder(sc.countVarintSize());
            }
            while (bits.available() > 0) {
                if (countDecoder.feed(bits.readOneByte())) {
                    declaredCount = (int) (countDecoder.value() + sc.lowerBound());
                    countRead = true;
                    break;
                }
            }
            if (!countRead) {
                return false;
            }
        }
        if (primitiveItems) {
            bytesRemaining = (long) declaredCount * itemByteSize;
        }
        return true;
    }

    private Result stepPrimitiveItems(BitReader bits, DecodingEventHandler events) {
        if (bytesRemaining > 0) {
            if (bits.available() == 0) {
                return new Result.WaitForInput();
            }
            int n = (int) Math.min(bits.available(), bytesRemaining);
            byte[] chunk = new byte[n];
            bits.readBytes(chunk, 0, n);
            events.onEvent(new DecodingEvent.Chunk(chunk));
            bytesRemaining -= n;
            if (bytesRemaining > 0) {
                return new Result.WaitForInput();
            }
        }
        bits.closeBitByte();
        events.onEvent(new DecodingEvent.EndContainer(DecodingEvent.ContainerKind.ARRAY));
        return new Result.Done();
    }
}
