package com.vanillasource.scan.client.network.frame;

import com.vanillasource.scan.client.network.PeerAddress;
import com.vanillasource.scan.client.network.Role;
import com.vanillasource.scan.client.network.data.VariableLengthInteger;

import java.util.Collection;

public interface FrameNetwork {
   void identityQuery(VariableLengthInteger queryId, Collection<PeerAddress> addresses);

   void identityAnnouncement(PeerAddress[] addresses);

   void close();

   FramePeer connect(PeerAddress address, Role role, FramePeer initiator);
}
