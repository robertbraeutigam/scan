package com.vanillasource.scan.client.network.physical;

import java.net.InetAddress;
import java.nio.ByteBuffer;

public interface PhysicalNetworkListener {
   void receiveMulticast(InetAddress sender, ByteBuffer packet);

   PhysicalPeer receiveConnection(InetAddress address, PhysicalPeer initiator);
}

