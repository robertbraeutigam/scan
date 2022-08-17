package com.vanillasource.scan.client.network.physical.nio;

import java.nio.channels.SelectionKey;

public final class NioSelectorKey {
   private final NioSelector nioSelector;
   private final SelectionKey selectionKey;

   public NioSelectorKey(NioSelector nioSelector, SelectionKey selectionKey) {
      this.nioSelector = nioSelector;
      this.selectionKey = selectionKey;
   }

   public void cancel() {
      selectionKey.cancel();
   }

   public void enableConnect() {
      enable(SelectionKey.OP_CONNECT);
   }

   public void disableConnect() {
      disable(SelectionKey.OP_CONNECT);
   }

   public void enableRead() {
      enable(SelectionKey.OP_READ);
   }

   public void disableRead() {
      disable(SelectionKey.OP_READ);
   }

   public void enableWrite() {
      enable(SelectionKey.OP_WRITE);
   }

   public void disableWrite() {
      disable(SelectionKey.OP_WRITE);
   }

   private void enable(int flag) {
      nioSelector.onSelectionThread(() -> selectionKey.interestOps(selectionKey.interestOps() | flag));
   }

   private void disable(int flag) {
      nioSelector.onSelectionThread(() -> selectionKey.interestOps(selectionKey.interestOps() & (~flag)));
   }
}
