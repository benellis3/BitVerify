package bitverify.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import bitverify.entries.Entry;
import bitverify.network.proto.MessageProto;
import bitverify.network.proto.MessageProto.*;
import bitverify.persistence.DataStore;
import com.google.protobuf.ByteString;
import com.squareup.otto.Bus;


/**
 * Created by benellis on 03/02/2016.
 */
public class PeerHandler {
    private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    private final DataStore dataStore;
    private final Socket socket;
    // only post messages, don't register
    private final Bus bus;
    private final ExecutorService executorService;
    private final int ourListenPort;
    private InetSocketAddress peerAddress;

    private volatile boolean shutdown;

    private static final int MAX_SIMULTANEOUS_BLOCKS_PER_PEER = 20;
    private static final int BLOCK_TIMEOUT_SECONDS = 5;
    private static final int SETUP_TIMEOUT_SECONDS = 5;

    private ArrayBlockingQueue<byte[]> blocksInFlight = new ArrayBlockingQueue<>(MAX_SIMULTANEOUS_BLOCKS_PER_PEER);
    private RestartableTimer blockTimer;


    /**
     * Use this constructor to create a PeerHandler object from an already connected socket.
     * You must call establishConnection or acceptConnection before making other communications with the peer.
     * @param s   the socket to connect to
     * @param es  the ExecutorService for the creator
     * @param ds  the Database access class
     * @param bus the application event bus.
     * @param ourListenPort the port our client is listening on
     * @param onBlockTimeout the function to call when this peer times out waiting to receive a requested block.
     */
    public PeerHandler(Socket s, ExecutorService es, DataStore ds, Bus bus, int ourListenPort, Consumer<PeerHandler> onBlockTimeout) {
        socket = s;
        executorService = es;
        this.bus = bus;
        this.dataStore = ds;
        this.ourListenPort = ourListenPort;

        blockTimer = new RestartableTimer(() -> onBlockTimeout.accept(this), BLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Cancels this peer connection.
     */
    public void shutdown() {
        shutdown = true;
        try {
            socket.close();
        } catch (IOException e) {
            // nothing we can do here.
        }
    }

    /**
     * Establishes a new outgoing connection with a peer. This method will block until the connection setup has been negotiated.
     * @param listenAddress the address of the peer
     * @throws InterruptedException
     * @throws ExecutionException   a socket IO error occurred
     * @throws TimeoutException     a timeout occurred while waiting for a connection setup message
     */
    public void establishConnection(InetSocketAddress listenAddress)
            throws InterruptedException, ExecutionException, TimeoutException {
        this.peerAddress = listenAddress;
        // 1. send version message
        executorService.submit(new PeerSend());
        sendVersionMessage(ourListenPort);
        // 2. receive version-ack message
        executorService.submit(() -> receiveMessage(Message.Type.VERSION_ACK)).get(SETUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        // 3. send ack message
        sendAckMessage();
        // can now send and receive other messages
        executorService.submit(new PeerReceive());
    }

    /**
     * Accepts a new incoming connection with a peer. This method will block until the connection setup has been negotiated.
     * @throws ExecutionException   a socket IO error occurred
     * @throws InterruptedException
     * @throws TimeoutException     a timeout occurred while waiting for a connection setup message
     */
    public InetSocketAddress acceptConnection() throws ExecutionException, InterruptedException, TimeoutException {
        // 1. receive and check version message
        Message m = executorService.submit(() -> receiveMessage(Message.Type.VERSION)).get(SETUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        peerAddress = new InetSocketAddress(socket.getInetAddress(), m.getVersion().getListenPort());
        // 2. send version-ack message
        executorService.submit(new PeerSend());
        sendVersionAckMessage();
        // 3. receive ack message
        executorService.submit(() -> receiveMessage(Message.Type.ACK)).get(SETUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        // can now send and receive other messages
        executorService.submit(new PeerReceive());
        return peerAddress;
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
        VersionAck ack = VersionAck.newBuilder()
                .setListenPort(ourListenPort)
                .build();
        Message msg = Message.newBuilder()
                .setType(Message.Type.VERSION_ACK)
                .setVersionAck(ack)
                .build();
        send(msg);
    }

    /**
     * Gets the address of the peer we are connected to.
     */
    public InetSocketAddress getPeerAddress() {
        return peerAddress;
    }

    /**
     * This sends a message and does not block.
     * @param msg The message to send
     */
    public void send(Message msg) {
        messageQueue.add(msg); // returns immediately.
    }


    public Queue<byte[]> getBlocksInFlight() {
        return blocksInFlight;
    }

    public RestartableTimer getBlockTimer() {
        return blockTimer;
    }

    /**
     * Requests the specified block by sending a getBlock message, provided we aren't already
     * awaiting the maximum number of blocks we may simultaneously download from a peer.
     * @param blockID the ID of the block to request
     * @return true if the request was sent, false if we're already downloading the maximum number of blocks.
     */
    public boolean requestBlock(byte[] blockID) {
        if (!blocksInFlight.offer(blockID))
            return false;

        MessageProto.GetBlocksMessage gbm = MessageProto.GetBlocksMessage.newBuilder()
                .setBlockID(ByteString.copyFrom(blockID))
                .build();

        MessageProto.Message message = MessageProto.Message.newBuilder()
                .setType(MessageProto.Message.Type.GET_BLOCK)
                .setGetBlock(gbm)
                .build();

        send(message);

        // start timer if not already running
        blockTimer.start();
        return true;
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj instanceof PeerHandler) {
            PeerHandler p = (PeerHandler) obj;
            return peerAddress.equals(p.getPeerAddress());
        }
        return false;
    }


    class PeerReceive implements Runnable {

        @Override
        public void run() {
            try {
                Message message;
                InputStream is = socket.getInputStream();
                while (true) {
                    message = Message.parseDelimitedFrom(is);
                    switch (message.getType()) {
                        case GETPEERS:
                            handleGetPeers(message);
                            break;
                        case ENTRY:
                            handleEntryMessage(message);
                            break;
                        case BLOCK:
                            handleBlockMessage(message);
                            break;
                        case PEERS:
                            handlePeers(message);
                            break;
                        default:
                            break;
                    }

                }
            } catch (IOException | SQLException e) {
                // Connection manager already knows we are closing if shutdown is true.
                if (!shutdown)
                    bus.post(new PeerErrorEvent(PeerHandler.this, e));
            }
        }

        private void handleEntryMessage(Message message) throws SQLException, IOException {
            EntryMessage e = message.getEntry();
            byte[] bytes = e.getEntryBytes().toByteArray();
            Entry entry = Entry.deserialize(bytes);

            // check the validity of the entry
            if (entry.testEntryHashSignature()) {
                try {
                dataStore.insertEntry(entry);
                } catch (Exception ex) { System.out.println(ex); }
                // raise a NewEntryEvent on the event bus
                bus.post(new NewEntryEvent(entry));
            }
        }

        private void handleBlockMessage(Message m) {
            // create an event which can be handed off to the connection manager.
            bus.post(new BlockMessageEvent(m.getBlock(), peerAddress));
        }


        private void handleGetPeers(Message message) {
            // create an event which can be handed off to the connection manager.
            bus.post(new GetPeersEvent(PeerHandler.this));
        }

        private void handlePeers(Message message) {
            Peers peers = message.getPeers();
            Collection<NetAddress> netAddressList = peers.getAddressList();
            // create a list of InetSocketAddresses from the netAddressList
            Set<InetSocketAddress> socketAddressList = ConcurrentHashMap.newKeySet();
            for (NetAddress netAddress : netAddressList) {
                socketAddressList.add(new InetSocketAddress(netAddress.getHostName(), netAddress.getPort()));
            }
            bus.post(new PeersEvent(socketAddressList));
        }
    }


    /**
     * Sends a messages from a queue over a Socket
     */
    class PeerSend implements Runnable {

        @Override
        public void run() {
            try {
                OutputStream os = socket.getOutputStream();
                while (true) {
                    Message message = messageQueue.take();
                    message.writeDelimitedTo(os);
                }
            } catch (IOException | InterruptedException e) {
                // Connection manager already knows we are closing if shutdown is true.
                if (!shutdown)
                    bus.post(new PeerErrorEvent(PeerHandler.this, e));
            }
        }
    }


}
