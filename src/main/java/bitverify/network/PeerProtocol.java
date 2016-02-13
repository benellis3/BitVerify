package bitverify.network;


import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import bitverify.network.proto.MessageProto;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class PeerProtocol  {
    private enum State {WAIT, IDLE}
    private PeerHandler p;
    private State state;
    private Bus bus;
    public PeerProtocol(PeerHandler handler, Bus bus) {
        p = handler;
        state = State.IDLE;
        this.bus = bus;
        bus.register(this);
    }
    public void send() {
        MessageProto.GetPeers getPeers = MessageProto.GetPeers.newBuilder()
                .setMyAddress(MessageProto.NetAddress.newBuilder()
                        .setHostName(p.getListenAddress().getHostName())
                        .setPort(p.getListenAddress().getPort()))
                .build();
        MessageProto.Message message = MessageProto.Message.newBuilder().setType(MessageProto.Message.Type.GETPEERS)
                .setGetPeers(getPeers).build();
        p.send(message);
        state = State.WAIT;
        Timer timer = new Timer();
        // resend if no response has been received
        while(state != State.IDLE)
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    p.send(message);
                }
            }, (long) 3000);
    }

    @Subscribe
    public void onPeersEvent(PeersEvent pe) {
        if(pe.getLevel() == PeersEvent.Level.CONNECTIONMANAGER || state == State.IDLE) return;
        Set<InetSocketAddress> addresses = pe.getSocketAddresses();
        bus.post(new PeersEvent(addresses, PeersEvent.Level.CONNECTIONMANAGER));
    }
}
