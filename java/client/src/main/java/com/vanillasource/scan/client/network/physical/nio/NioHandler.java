package com.vanillasource.scan.client.network.physical.nio;

import java.io.IOException;

public interface NioHandler {
   void handleConnectable(NioSelectorKey key) throws IOException;

   void handleReadable(NioSelectorKey key) throws IOException;

   void handleWritable(NioSelectorKey key) throws IOException;

   void handleAccept(NioSelectorKey key) throws IOException;
}
