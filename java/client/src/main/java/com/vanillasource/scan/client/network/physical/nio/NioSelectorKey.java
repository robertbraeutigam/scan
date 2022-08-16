package com.vanillasource.scan.client.network.physical.nio;

import java.nio.channels.SelectionKey;

public final class NioSelectorKey {
   private final NioSelector nioSelector;
   private final SelectionKey selectionKey;

   public NioSelectorKey(NioSelector nioSelector, SelectionKey selectionKey) {
      this.nioSelector = nioSelector;
      this.selectionKey = selectionKey;
   }

   public boolean isReadable() {
      return selectionKey.isReadable();
   }

   public boolean isWritable() {
      return selectionKey.isWritable();
   }

   public void enableRead() {
      nioSelector.onSelectionThread(() -> selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ));
   }

   public void disableRead() {
      nioSelector.onSelectionThread(() -> selectionKey.interestOps(selectionKey.interestOps() & (~SelectionKey.OP_READ)));
   }

   public void enableWrite() {
      nioSelector.onSelectionThread(() -> selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE));
   }

   public void disableWrite() {
      nioSelector.onSelectionThread(() -> selectionKey.interestOps(selectionKey.interestOps() & (~SelectionKey.OP_WRITE)));
   }
}
