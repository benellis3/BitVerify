package bitverify.network;

import bitverify.LogEvent;
import bitverify.LogEventSource;
import bitverify.network.proto.MessageProto;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.logging.Level;

/**
 * Handles sending a single request for peers, then awaiting the response.
 */
public class PeersFuture extends ProtocolFuture<Set<InetSocketAddress>> {
    private final PeerHandler peer;

    public PeersFuture(PeerHandler peer, Bus bus) {
        super(bus);
        this.peer = peer;
    }

    @Override
    public void run() {
        MessageProto.GetPeers getPeers = MessageProto.GetPeers.newBuilder().build();
        MessageProto.Message message = MessageProto.Message.newBuilder()
                .setType(MessageProto.Message.Type.GETPEERS)
                .setGetPeers(getPeers)
                .build();

        // if peer is shut down, return null straight away
        if (peer.send(message)) {
            // register for replies once we've sent the request
            bus.register(this);
        } else {
            resultLatch.countDown();
        }
    }

    @Subscribe
    public void onPeersEvent(PeersEvent pe) {
        // deregister now we've got our response
        bus.unregister(this);
        result = pe.getSocketAddresses();
        // notify that we got a response
        resultLatch.countDown();
    }
}
