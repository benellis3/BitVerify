package bitverify.network;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.*;

import bitverify.network.proto.MessageProto.Version;
import bitverify.network.proto.MessageProto.Message;
import bitverify.network.proto.MessageProto.NetAddress;
import bitverify.network.proto.MessageProto.Ack;
import bitverify.persistence.DataStore;
import com.squareup.otto.Bus;


/**
 * Created by benellis on 03/02/2016.
 */
public class PeerHandler {
    private BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    private Socket socket;
    private InetSocketAddress listenAddress;
    private Bus bus;
    /**
     * This constructor should be used when receiving a new connection from the peer.
     * This will attempt to construct a PeerHandler object from a socket. It
     * is VERY important to note that this method BLOCKS waiting for a version message
     * and therefore should be run from within a separate thread.
     * @param s the socket to connect to
     * @param es the ExecutorService for the creator
     * @param ds the Database access class
     * @param bus the application event bus.
     */
    public PeerHandler(Socket s, ExecutorService es, DataStore ds, Bus bus) throws
            TimeoutException, ExecutionException, InterruptedException{

        socket = s;
        // create new PeerSend Runnable with the right queue
        // if constructed with a socket need to ensure that the version and ack messages
        // have been exchanged.
        Future<InetSocketAddress> addressFuture = es.submit(new VersionReceiveHandler());

        try {
            listenAddress = addressFuture.get(3, TimeUnit.SECONDS); // blocks thread
        }
        finally {
            addressFuture.cancel(true);
        }
        // create new PeerSend Runnable with the right queue
        es.execute(new PeerSend(messageQueue, socket));
        es.execute(new PeerReceive(socket, ds, bus));
        // send ack message to denote that we have connected
        Ack ack = Ack.newBuilder().build();
        Message msg = Message.newBuilder().setType(Message.Type.ACK).setAck(ack).build();
        send(msg);
    }
    /**
     * This constructor should be used when connecting to a peer when starting up.
     * This constructor blocks waiting for the person we are connecting to to send an ACK message
     * @param address the InetSocketAddress to connect to
     * @param listenPort the port that the host is listening on.
     * @param es the ExecutorService of the connection manager
     * @param ds the database access class
     * @param bus the application event bus
     * @throws IOException in the event socket creation fails.
     */
    public PeerHandler(InetSocketAddress address, int listenPort,
                       ExecutorService es, DataStore ds, Bus bus)
            throws IOException, TimeoutException, ExecutionException, InterruptedException{

        this.listenAddress = address;
        socket = new Socket(address.getAddress(), address.getPort());
        es.execute(new PeerSend(messageQueue, socket));
        NetAddress netAddress = NetAddress.newBuilder()
                .setHostName(InetAddress.getLocalHost().getHostName()) //not relevant
                .setPort(listenPort).build();
        Version version = Version.newBuilder().setListenPort(netAddress).build();
        Message msg = Message.newBuilder()
                .setType(Message.Type.VERSION)
                .setVersion(version).build();
        send(msg);
        // await ACK in order to receive messages
        Future<InetSocketAddress> fut = es.submit(new AckReceiveHandler());
        try {
            fut.get(3, TimeUnit.SECONDS);
        }
        finally {
            fut.cancel(true);
        }
        // allow connections to be received.
        es.execute(new PeerReceive(socket, ds, bus));
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null) return false;
        if(obj instanceof PeerHandler) {
            PeerHandler p = (PeerHandler) obj;
            return listenAddress.equals(p.getListenAddress());
        }
        return false;
    }
    public InetSocketAddress getAddress() {return new InetSocketAddress(socket.getInetAddress(),
                                                socket.getPort());}
    public InetSocketAddress getLocalAddress() {return new InetSocketAddress(socket.getInetAddress(),
                                                socket.getLocalPort());}
    public int getConnectedPort(){
        return listenAddress.getPort();
    }
    public InetAddress getConnectedHost(){
        return listenAddress.getAddress();
    }
    public InetSocketAddress getListenAddress() {return listenAddress;}
    /**
     * This sends a message and does not block.
     * @param msg The message to send
     */
    public void send(Message msg) {
        messageQueue.add(msg); // returns immediately.
    }

    /**
     * This implements the Request-Response Protocol for
     * getting and receiving peers.
     * @return A collection view of the InetSocketAddresses
     * of some peers in the network.
     */
    public void getPeers() {
        PeerProtocol p = new PeerProtocol(this, bus);
        p.send();
    }
    public class VersionReceiveHandler implements Callable<InetSocketAddress> {
        @Override
        public InetSocketAddress call() throws IOException {
            Message msg;
            InputStream is = socket.getInputStream();
            Version versionMsg;
            while(true) {
                msg = Message.parseDelimitedFrom(is);
                Message.Type type = msg.getType();
                if (type == Message.Type.VERSION) {
                    versionMsg = msg.getVersion();
                    break;
                }
            }
            NetAddress netAddress = versionMsg.getListenPort();
            // safe cast since one subclasses the other
            InetSocketAddress socketAddr = (InetSocketAddress) socket.getRemoteSocketAddress();
            String hostName = socketAddr.getHostName();
            InetSocketAddress ret = new InetSocketAddress(hostName,netAddress.getPort());
            return ret;
        }
    }
    // currently this information is not used - Is there anything useful
    // that we could put in the ACK message?
    public class AckReceiveHandler implements Callable<InetSocketAddress> {
        @Override
        public InetSocketAddress call() throws Exception {
            Message msg;
            InputStream is = socket.getInputStream();
            Ack ack;
            while(true) {
                msg = Message.parseDelimitedFrom(is);
                Message.Type type = msg.getType();
                if(type == Message.Type.ACK) {
                    ack = msg.getAck();
                    break;
                }
            }
            NetAddress netAddress = ack.getAddr();
            InetSocketAddress addr = new InetSocketAddress(netAddress.getHostName(), netAddress.getPort());
            return addr;
        }
    }
 }
