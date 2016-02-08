package bitverify.network;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Created by benellis on 08/02/2016.
 */
public class PeersEvent {
    private List<InetSocketAddress> socketAddressList;

    public PeersEvent(List<InetSocketAddress> list) {
        socketAddressList = list;
    }

    public List<InetSocketAddress> getSocketAddressList() {return socketAddressList;}
}
