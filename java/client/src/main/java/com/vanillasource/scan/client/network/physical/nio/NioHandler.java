package com.vanillasource.scan.client.network.physical.nio;

import java.io.IOException;

public interface NioHandler {
   public void handleReadable(NioSelectorKey key) throws IOException;

   public void handleWritable(NioSelectorKey key) throws IOException;
}
