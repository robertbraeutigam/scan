package com.vanillasource.scan.client.network.frame;

import com.vanillasource.scan.client.network.PeerAddress;
import com.vanillasource.scan.client.network.Role;

public interface FrameNetworkListener {
   void receiveAnnouncement(PeerAddress address);

   FramePeer receiveConnection(PeerAddress address, Role role, FramePeer initiator);
}

