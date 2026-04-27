package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitWriter;
import com.vanillasource.scan.types.codec.EncoderFrame;
import com.vanillasource.scan.types.codec.SizeConstraint;

/** Awaits a {@code startArray} call; once declared, descends into one element at a time until the count is reached. */
final class ArrayEncoderFrame implements EncoderFrame {
    private final Array array;
    private boolean declared;
    private int declaredCount;
    private int itemsCompleted;

    ArrayEncoderFrame(Array array) {
        this.array = array;
    }

    @Override
    public String describe() {
        return declared
                ? ("array item " + itemsCompleted + " of " + declaredCount)
                : "array (count not yet declared)";
    }

    @Override
    public Result startArray(BitWriter bits, int count) {
        if (declared) {
            throw new IllegalStateException("array count already declared");
        }
        SizeConstraint sc = array.size();
        sc.validate(count);
        bits.closeBitByte();
        if (!sc.isFixed()) {
            long encoded = (long) count - sc.lowerBound();
            bits.writeVarInt(encoded, sc.countVarintSize());
        }
        declaredCount = count;
        declared = true;
        if (count == 0) {
            bits.closeBitByte();
            return new Result.Done();
        }
        EncoderFrame childFrame = array.element().createEncodeFrame();
        if (childFrame == null) {
            bits.closeBitByte();
            return new Result.Done();
        }
        return new Result.Push(childFrame);
    }

    @Override
    public Result onChildCompleted(BitWriter bits) {
        itemsCompleted++;
        bits.closeBitByte();
        if (itemsCompleted >= declaredCount) {
            return new Result.Done();
        }
        return new Result.Push(array.element().createEncodeFrame());
    }
}
