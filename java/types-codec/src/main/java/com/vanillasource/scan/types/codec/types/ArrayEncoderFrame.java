package com.vanillasource.scan.types.codec.types;

import com.vanillasource.scan.types.codec.BitWriter;
import com.vanillasource.scan.types.codec.EncoderFrame;
import com.vanillasource.scan.types.codec.SizeConstraint;

/** Awaits a {@code startArray} call; once declared, runs each element's frame inline until the count is reached. */
final class ArrayEncoderFrame implements EncoderFrame {
    private final Array array;
    private boolean declared;
    private int declaredCount;
    private int itemsCompleted;
    private EncoderFrame currentChild;

    ArrayEncoderFrame(Array array) {
        this.array = array;
    }

    @Override
    public String describe() {
        if (currentChild != null) {
            return currentChild.describe();
        }
        return declared
                ? ("array item " + itemsCompleted + " of " + declaredCount)
                : "array (count not yet declared)";
    }

    @Override
    public Result startArray(BitWriter bits, int count) {
        if (declared) {
            if (currentChild == null) {
                return EncoderFrame.super.startArray(bits, count);
            }
            return afterChild(bits, currentChild.startArray(bits, count));
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
        return enterNextItem(bits);
    }

    @Override
    public Result writeInteger(BitWriter bits, long value) {
        if (currentChild == null) {
            return EncoderFrame.super.writeInteger(bits, value);
        }
        return afterChild(bits, currentChild.writeInteger(bits, value));
    }

    @Override
    public Result writeFloat(BitWriter bits, double value) {
        if (currentChild == null) {
            return EncoderFrame.super.writeFloat(bits, value);
        }
        return afterChild(bits, currentChild.writeFloat(bits, value));
    }

    @Override
    public Result writeConstructor(BitWriter bits, int index) {
        if (currentChild == null) {
            return EncoderFrame.super.writeConstructor(bits, index);
        }
        return afterChild(bits, currentChild.writeConstructor(bits, index));
    }

    private Result afterChild(BitWriter bits, Result r) {
        if (r instanceof Result.Done) {
            currentChild = null;
            itemsCompleted++;
            bits.closeBitByte();
            return enterNextItem(bits);
        }
        return r;
    }

    private Result enterNextItem(BitWriter bits) {
        while (itemsCompleted < declaredCount) {
            EncoderFrame child = array.element().createEncodeFrame();
            if (child == null) {
                bits.closeBitByte();
                return new Result.Done();
            }
            currentChild = child;
            Result r = child.onEntered(bits);
            if (r instanceof Result.Done) {
                currentChild = null;
                itemsCompleted++;
                bits.closeBitByte();
                continue;
            }
            return r;
        }
        bits.closeBitByte();
        return new Result.Done();
    }
}
