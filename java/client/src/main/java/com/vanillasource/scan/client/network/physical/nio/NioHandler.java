package com.vanillasource.scan.client.network.physical.nio;

import java.io.IOException;

public interface NioHandler {
   public void handle(NioSelectorKey key) throws IOException;
}
