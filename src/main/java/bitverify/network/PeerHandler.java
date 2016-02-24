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
import java.util.logging.Level;

import bitverify.ExceptionLogEvent;
import bitverify.LogEvent;
import bitverify.LogEventSource;
import bitverify.block.Block;
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
    private static final int MAX_HEADERS = 1000;

    private ArrayBlockingQueue<BlockID> blocksInFlight = new ArrayBlockingQueue<>(MAX_SIMULTANEOUS_BLOCKS_PER_PEER);
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
     * Returns true if the message was added to the queue to be sent, or false if this peer is being shut down.
     * @param msg The message to send
     */
    public boolean send(Message msg) {
        if (shutdown)
            return false;

        messageQueue.add(msg); // returns immediately.
        log("Sending a " + msg.getType() + " message to peer " + getPeerAddress(), Level.FINER);
        return true;
    }

    public Queue<BlockID> getBlocksInFlight() {
        return blocksInFlight;
    }

    public RestartableTimer getBlockTimer() {
        return blockTimer;
    }

    /**
     * Requests the specified block by sending a getBlock message, provided we aren't already
     * awaiting the maximum number of blocks we may simultaneously download from a peer.
     * @param blockID the ID of the block to request
     * @return true if the request was sent, false if we're already downloading the maximum number of blocks or this peer is being shut down.
     */
    public boolean requestBlock(BlockID blockID) {
        if (!blocksInFlight.offer(blockID))
            return false;

        MessageProto.GetBlockMessage gbm = MessageProto.GetBlockMessage.newBuilder()
                .setBlockID(ByteString.copyFrom(blockID.getBlockID()))
                .build();

        MessageProto.Message message = MessageProto.Message.newBuilder()
                .setType(MessageProto.Message.Type.GET_BLOCK)
                .setGetBlock(gbm)
                .build();


        // if peer is shutting down so message can't be sent, just return false to indicate failure
        if (send(message)) {
            log("sent a get block message for block " + blockID + " from peer " + getPeerAddress(), Level.FINE);
            blockTimer.start();
            return true;
        } else {
            return false;
        }
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

    private void log(String message, Level level) {
        bus.post(new LogEvent(message, LogEventSource.NETWORK, level));
    }

    private void log(String message, Level level, Throwable error) {
        bus.post(new ExceptionLogEvent(message, LogEventSource.NETWORK, level, error));
    }


    class PeerReceive implements Runnable {

        @Override
        public void run() {
            try {
                Message message;
                InputStream is = socket.getInputStream();
                while (true) {
                    message = Message.parseDelimitedFrom(is);
                    log("received message of type " + message.getType(), Level.FINER);
                    switch (message.getType()) {
                        case GETPEERS:
                            handleGetPeers(message.getGetPeers());
                            break;
                        case ENTRY:
                            handleEntryMessage(message.getEntry());
                            break;
                        case BLOCK:
                            handleBlockMessage(message.getBlock());
                            break;
                        case BLOCK_NOT_FOUND:
                            handleBlockNotFoundMessage(message.getBlockNotFound());
                            break;
                        case PEERS:
                            handlePeers(message.getPeers());
                            break;
                        case GET_HEADERS:
                            handleGetHeaders(message.getGetHeaders());
                            break;
                        case GET_BLOCK:
                            handleGetBlock(message.getGetBlock());
                            break;
                        case HEADERS:
                            handleHeaders(message.getHeaders());
                            break;
                        default:
                            bus.post(new LogEvent("Network message went unhandled, type " + message.getType().toString(), LogEventSource.NETWORK, Level.WARNING));
                            break;
                    }

                }
            } catch (IOException | SQLException e) {
                log("exception while receiving: " + e.getMessage(), Level.WARNING, e);
                // Connection manager already knows we are closing if shutdown is true.
                if (!shutdown)
                    bus.post(new PeerErrorEvent(PeerHandler.this, e));
            }
        }

        private void handleEntryMessage(EntryMessage message) throws SQLException, IOException {
            byte[] bytes = message.getEntryBytes().toByteArray();
            Entry entry = Entry.deserialize(bytes);

            // check the validity of the entry
            if (entry.testEntryHashSignature()) {
                try {
                    dataStore.insertEntry(entry);
                } catch (Exception ex) {
                    System.out.println(ex);
                }
                // raise a NewEntryEvent on the event bus
                bus.post(new NewEntryEvent(entry));
            }
        }

        private void handleGetBlock(GetBlockMessage message) throws SQLException {
            Block b = dataStore.getBlock(message.getBlockID().toByteArray());
            if (b == null) {
                log("Sending block not found message in response to get block for " + Base64.getEncoder().encodeToString(message.getBlockID().toByteArray()), Level.FINE);
                BlockNotFoundMessage bm = BlockNotFoundMessage.newBuilder()
                        .setBlockID(message.getBlockID())
                        .build();
                Message m = Message.newBuilder()
                        .setType(Message.Type.BLOCK_NOT_FOUND)
                        .setBlockNotFound(bm)
                        .build();
                send(m);
            } else {
                log("Sending block message in response to get block for " + Base64.getEncoder().encodeToString(b.getBlockID()), Level.FINE);
                try {
                    BlockMessage.Builder bmb = BlockMessage.newBuilder()
                            .setBlockBytes(ByteString.copyFrom(b.serializeHeader()));
                    for (Entry e : b.getEntriesList())
                        bmb.addEntries(ByteString.copyFrom(e.serialize()));

                    BlockMessage bm = bmb.build();

                    Message m = Message.newBuilder()
                            .setType(Message.Type.BLOCK)
                            .setBlock(bm)
                            .build();
                    send(m);
                } catch (Exception e) {
                    log("Oh dear: " + e.toString(), Level.SEVERE, e);
                    e.printStackTrace();
                }
            }
        }

        private void handleBlockMessage(BlockMessage m) {
            // create an event which can be handed off to the connection manager.
            log("handing off block message event", Level.FINER);
            try {
                bus.post(new BlockMessageEvent(m, PeerHandler.this));
            } catch (Exception e) {
                log("Oh dear: " + e.toString(), Level.SEVERE, e);
                e.printStackTrace();
            }
        }

        private void handleBlockNotFoundMessage(BlockNotFoundMessage m) {
            bus.post(new BlockNotFoundMessageEvent(m, PeerHandler.this));
        }

        private void handleGetPeers(GetPeers message) {
            // create an event which can be handed off to the connection manager.
            bus.post(new GetPeersEvent(PeerHandler.this));
        }

        private void handlePeers(Peers message) {
            Collection<NetAddress> netAddressList = message.getAddressList();
            // create a list of InetSocketAddresses from the netAddressList
            Set<InetSocketAddress> socketAddressList = ConcurrentHashMap.newKeySet();
            for (NetAddress netAddress : netAddressList) {
                socketAddressList.add(new InetSocketAddress(netAddress.getHostName(), netAddress.getPort()));
            }
            bus.post(new PeersEvent(socketAddressList));
        }

        private void handleHeaders(HeadersMessage message) {
            bus.post(new HeadersMessageEvent(message));
        }


        private void handleGetHeaders(GetHeadersMessage message) throws SQLException {
            try {


                // for each start at ID,
                for (ByteString bytes : message.getFromList()) {
                    byte[] blockID = bytes.toByteArray();

                    // if block is on primary chain
                    if (dataStore.isBlockOnActiveChain(blockID)) {

                        // send the blocks following it
                        List<Block> blocks = dataStore.getActiveBlocksAfter(blockID, MAX_HEADERS);

                        HeadersMessage.Builder hb = HeadersMessage.newBuilder();
                        for (Block block : blocks)
                            hb.addHeaders(ByteString.copyFrom(block.serializeHeader()));
                        HeadersMessage h = hb.build();

                        Message m = Message.newBuilder()
                                .setType(Message.Type.HEADERS)
                                .setHeaders(h)
                                .build();
                        send(m);
                        log("Sent headers message with " + blocks.size() + " headers", Level.FINE);
                        for (int i = 0; i < blocks.size(); i++)
                            log("header " + i + " was " + new BlockID(blocks.get(i).getBlockID()), Level.FINEST);
                        return;
                    }
                }
                // if we get here, we didn't have any matching blocks so send back an empty headers message
                HeadersMessage hm = HeadersMessage.newBuilder().build();
                Message m = Message.newBuilder()
                        .setType(Message.Type.HEADERS)
                        .setHeaders(hm)
                        .build();
                send(m);

            } catch (Exception ex) {
                log("Oh dear " + ex.getMessage(), Level.SEVERE, ex);
                ex.printStackTrace();
            }
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
