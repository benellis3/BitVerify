package bitverify.network;


import java.net.InetSocketAddress;

/**
 * Created by benellis on 08/02/2016.
 */
public class GetPeersEvent {
    private InetSocketAddress socketAddress;

    public GetPeersEvent(InetSocketAddress addr) {
        socketAddress = addr;
    }

    public InetSocketAddress getSocketAddress(){return socketAddress;}
}
