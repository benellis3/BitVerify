package bitverify.network;

import java.net.InetSocketAddress;
import java.util.Set;

/**
 * Created by benellis on 08/02/2016.
 */
public class PeersEvent {
    private Set<InetSocketAddress> socketAddressList;
    private PeerHandler peer;

    public PeersEvent(Set<InetSocketAddress> list, PeerHandler peer) {
        socketAddressList = list;
        this.peer = peer;
    }
    public Set<InetSocketAddress> getSocketAddresses() {
        return socketAddressList;
    }

    public PeerHandler getPeer() {
        return peer;
    }
}
