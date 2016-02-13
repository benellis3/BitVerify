package bitverify.network;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;

/**
 * Created by benellis on 08/02/2016.
 */
public class PeersEvent {
    public enum Level {PEERHANDLER, CONNECTIONMANAGER}
    private Set<InetSocketAddress> socketAddressList;
    private Level level;
    public PeersEvent(Set<InetSocketAddress> list,Level level) {
        this.level = level;
        socketAddressList = list;
    }
    public Level getLevel() {return level;}
    public Set<InetSocketAddress> getSocketAddresses() {return socketAddressList;}
}
