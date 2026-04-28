package com.vanillasource.scan.types.codec;

public interface Type {
   ValueDecoder createDecoder();

   ValueEncoder createEncoder();
}
