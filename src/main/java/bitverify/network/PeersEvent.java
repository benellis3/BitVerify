package bitverify.network;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;

/**
 * Created by benellis on 08/02/2016.
 */
public class PeersEvent {
    private Set<InetSocketAddress> socketAddressList;
    public PeersEvent(Set<InetSocketAddress> list) {
        socketAddressList = list;
    }
    public Set<InetSocketAddress> getSocketAddresses() {return socketAddressList;}
}
