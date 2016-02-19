package bitverify.network;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.*;

import bitverify.network.proto.MessageProto.*;
import bitverify.persistence.DataStore;
import com.squareup.otto.Bus;


/**
 * Created by benellis on 03/02/2016.
 */
public class PeerHandler {
    private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    private final DataStore dataStore;
    private final Socket socket;
    private final Bus bus;
    private final ExecutorService executorService;
    private InetSocketAddress listenAddress;
    /**
     * Use this constructor to create a PeerHandler object from an already connected socket.
     * You must call establishConnection or acceptConnection before making other communications with the peer.
     * @param s the socket to connect to
     * @param es the ExecutorService for the creator
     * @param ds the Database access class
     * @param bus the application event bus.
     */
    public PeerHandler(Socket s, ExecutorService es, DataStore ds, Bus bus)
            throws TimeoutException, ExecutionException, InterruptedException {
        socket = s;
        executorService = es;
        this.bus = bus;
        this.dataStore = ds;
    }

    private static final int TIMEOUT_SECONDS = 5;

    /**
     * Establishes a new outgoing connection with a peer. This method will block until the connection setup has been negiotiated.
     * @param ourListenPort the port our client is listening on
     * @param listenAddress the address of the peer
     * @throws InterruptedException
     * @throws ExecutionException a socket IO error occurred
     * @throws TimeoutException a timeout occurred while waiting for a connection setup message
     */
    public void establishConnection(int ourListenPort, InetSocketAddress listenAddress) throws InterruptedException, ExecutionException, TimeoutException {
        this.listenAddress = listenAddress;
        // 1. send version message
        executorService.submit(new PeerSend(messageQueue, socket));
        sendVersionMessage(ourListenPort);
        // 2. receive version-ack message
        executorService.submit(() -> receiveMessage(Message.Type.VERSION_ACK)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        // 3. send ack message
        sendAckMessage();
        // can now send and receive other messages
        executorService.submit(new PeerReceive(socket, dataStore, bus, listenAddress));
    }

    public InetSocketAddress acceptConnection() throws ExecutionException, InterruptedException, TimeoutException {
        // 1. receive and check version message
        Message m = executorService.submit(() -> receiveMessage(Message.Type.VERSION)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        listenAddress = new InetSocketAddress(socket.getInetAddress(), m.getVersion().getListenPort());
        // 2. send version-ack message
        executorService.submit(new PeerSend(messageQueue, socket));
        sendVersionAckMessage();
        // 3. receive ack message
        executorService.submit(() -> receiveMessage(Message.Type.ACK)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        // can now send and receive other messages
        executorService.submit(new PeerReceive(socket, dataStore, bus, listenAddress));
        return listenAddress;
    }

    private Message receiveMessage(Message.Type type) throws IOException {
        InputStream is = socket.getInputStream();
        Message msg;
        do {
            msg = Message.parseDelimitedFrom(is);
        } while (msg.getType() != type);
        return msg;
    }

    private void sendVersionMessage(int ourListenPort) {
        Version version = Version.newBuilder()
                .setListenPort(ourListenPort)
                .build();
        Message msg = Message.newBuilder()
                .setType(Message.Type.VERSION)
                .setVersion(version)
                .build();
        send(msg);
    }

    private void sendAckMessage() {
        Ack ack = Ack.newBuilder().build();
        Message msg = Message.newBuilder()
                .setType(Message.Type.ACK)
                .setAck(ack)
                .build();
        send(msg);
    }

    private void sendVersionAckMessage() {
        VersionAck ack = VersionAck.newBuilder().build();
        Message msg = Message.newBuilder()
                .setType(Message.Type.VERSION_ACK)
                .setVersionAck(ack)
                .build();
        send(msg);
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

    public InetSocketAddress getListenAddress() {return listenAddress;}
    /**
     * This sends a message and does not block.
     * @param msg The message to send
     */
    public void send(Message msg) {
        messageQueue.add(msg); // returns immediately.
    }


 }
