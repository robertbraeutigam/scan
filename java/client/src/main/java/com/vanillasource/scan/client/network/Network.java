package com.vanillasource.scan.client.network;

public interface Network extends AutoCloseable {
   /**
    * Query all devices on the network. Note that this device may not have
    * access to all the devices listed. Only devices that are currently on the
    * network can answer this query. Not all devices may be detected however
    * due to network congestion or timeouts. This method may take several
    * seconds to complete, but will immediately update the supplied listener
    * each time a device answers.
    */
   void queryAll();

   /**
    * Free up this network instance, including ports and other network resources 
    * and close all logical connections.
    */
   void close();

   /**
    * Connect to a remote peer.
    * @param initiator The initiator of the connection.
    */
   Peer connect(PeerAddress address, Role role, Peer initiator);
}

