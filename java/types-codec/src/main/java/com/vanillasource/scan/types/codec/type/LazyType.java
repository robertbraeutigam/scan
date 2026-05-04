package com.vanillasource.scan.types.codec.type;

import com.vanillasource.scan.types.codec.Type;
import com.vanillasource.scan.types.codec.ValueDecoder;
import com.vanillasource.scan.types.codec.ValueEncoder;

/**
 * Forward-reference {@link Type} holder used to break cycles when hand-building
 * recursive runtime types. Construct the placeholder, build the type using it
 * in the recursive position, then call {@link #set(Type)} once before any
 * decode or encode happens.
 */
public final class LazyType implements Type {
    private Type target;

    public void set(Type target) {
        if (target == null) {
            throw new NullPointerException("target");
        }
        if (this.target != null) {
            throw new IllegalStateException("LazyType already set");
        }
        this.target = target;
    }

    private Type require() {
        if (target == null) {
            throw new IllegalStateException("LazyType used before set()");
        }
        return target;
    }

    @Override
    public ValueDecoder createDecoder() {
        return require().createDecoder();
    }

    @Override
    public ValueEncoder createEncoder() {
        return require().createEncoder();
    }

    @Override
    public boolean isPrimitiveByte() {
        return require().isPrimitiveByte();
    }
}
