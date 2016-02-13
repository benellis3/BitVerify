package bitverify.network;


import bitverify.persistence.DataStore;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import bitverify.network.proto.MessageProto;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PeerProtocol  {
    private static final Logger LOGGER = Logger.getLogger(PeerProtocol.class.getName());
    private enum State {WAIT, IDLE}
    private Collection<PeerHandler> peers;
    private State state;
    private Bus bus;
    private DataStore ds;
    private ExecutorService es;
    private int listenPort;
    private InetAddress myAddress;
    public PeerProtocol(Collection<PeerHandler> handler, ExecutorService es, Bus bus,
                        DataStore ds, InetSocketAddress address) {
        peers = handler;
        state = State.IDLE;
        this.bus = bus;
        this.listenPort = address.getPort();
        this.myAddress = address.getAddress();
        this.es = es;
        this.ds = ds;
        bus.register(this);
    }
    public void send() {
        for(PeerHandler p : peers) {
            MessageProto.GetPeers getPeers = MessageProto.GetPeers.newBuilder()
                    .setMyAddress(MessageProto.NetAddress.newBuilder()
                            .setHostName(p.getConnectedHost().getHostName())
                            .setPort(listenPort))
                    .build();
            MessageProto.Message message = MessageProto.Message.newBuilder().setType(MessageProto.Message.Type.GETPEERS)
                    .setGetPeers(getPeers).build();
            p.send(message);
            state = State.WAIT;
            Timer timer = new Timer();
            // resend if no response has been received
            while (state != State.IDLE)
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        p.send(message);
                    }
                }, (long) 3000);
        }
    }
    public void unregister() {bus.unregister(this);}
    @Subscribe
    public void onPeersEvent(PeersEvent pe) {
        es.execute(() -> {
            Set<InetSocketAddress> addresses = pe.getSocketAddresses();
            // get the InetSocketAddress collection from the peers
            Set<InetSocketAddress> peerAddresses = peers.parallelStream()
                    .map(PeerHandler::getListenAddress)
                    .collect(Collectors.toSet());
            for(InetSocketAddress address : addresses) {
                if(!peerAddresses.contains(address)) {
                    es.execute(() -> {
                        try {
                            if(address.getPort() != listenPort && address.getAddress() != myAddress)
                                peers.add(new PeerHandler(address,listenPort, es, ds, bus));
                        }
                        catch(TimeoutException to) {
                            System.out.println("Timeout when constructing new peer");
                        }
                        catch(IOException | InterruptedException | ExecutionException ie) {
                            ie.printStackTrace();
                        }
                    });
                }
            }
        });
    }
}
