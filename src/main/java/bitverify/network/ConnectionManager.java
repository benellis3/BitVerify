package bitverify.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import bitverify.ExceptionLogEvent;
import bitverify.LogEvent;
import bitverify.LogEventSource;
import bitverify.block.Block;
import bitverify.entries.Entry;
import bitverify.mining.Miner;
import bitverify.network.proto.MessageProto;
import bitverify.network.proto.MessageProto.BlockMessage;
import bitverify.persistence.DataStore;
import bitverify.network.proto.MessageProto.Peers;
import bitverify.network.proto.MessageProto.NetAddress;
import bitverify.network.proto.MessageProto.Message;
import bitverify.network.proto.MessageProto.EntryMessage;
import bitverify.persistence.InsertBlockResult;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.google.protobuf.ByteString;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;


/**
 * This class is responsible for co-ordination of network functions.
 * It controls the incoming and outgoing parts of the connection.
 * @author Ben Ellis, Robert Eady
 */
public class ConnectionManager {
    private static final Logger LOGGER = Logger.getLogger(ConnectionManager.class.getName());
    private DataStore dataStore;
    private ExecutorService es;
    private Bus bus;
    private Map<InetSocketAddress, PeerHandler> peers;
    private static final String PEER_URL = "http://52.48.86.95:4000/nodes"; // for testing
    private BlockProtocol blockProtocol;
    private int ourListenPort;

    // underscore courtesy of Laszlo Makk :p
    private void _initialise(List<InetSocketAddress> initialPeers, int listenPort, DataStore ds, Bus bus) throws IOException {
        peers = new ConcurrentHashMap<>();
        this.bus = bus;
        bus.register(this);

        blockProtocol = new BlockProtocol();
        dataStore = ds;
        ourListenPort = listenPort;

        // create a special executor service that makes daemon threads.
        // this way the application can shut down without having to terminate network threads first.
        ThreadFactory daemonThreadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) -> bus.post(
                    new ExceptionLogEvent("An exception in a network thread was not caught: " + e.getMessage(),
                            LogEventSource.NETWORK, Level.SEVERE, e)));
            return thread;
        };
        es = Executors.newCachedThreadPool(daemonThreadFactory);

        // Create new runnable to listen for new connections.
        ServerSocket serverSocket = new ServerSocket(listenPort);

        es.execute(() -> {
                    try {
                        while (true) {
                            Socket s = serverSocket.accept();
                            // separate thread since it blocks waiting for messages.
                            es.execute(() -> {
                                PeerHandler ph = new PeerHandler(s, es, ds, bus, ourListenPort, blockProtocol::onBlockTimeout);
                                try {
                                    InetSocketAddress address = ph.acceptConnection();
                                    peers.put(address, ph);
                                } catch (TimeoutException time) {
                                    // this means the connection could not be established before timeout
                                    log("Did not establish connection to peer within time limit", Level.INFO);
                                    ph.shutdown();
                                } catch (InterruptedException | ExecutionException ie) {
                                    log("An error occurred while establishing connection to peer", Level.INFO);
                                    ph.shutdown();
                                }
                            });
                        }
                    } catch (IOException ioe) {
                        // if the server socket dies, we can still carry on but won't be able to accept new connections.
                        log("Server socket died: can no longer accept new incoming peer connections", Level.WARNING);
                    }
                }
        );
        // connect to each given peer - the PeerHandler Class ensures
        // that the VERSION - ACK protocol is obeyed. Creation is done
        // in a separate thread as the PeerHandler constructor will
        // block.
        es.execute(() -> {
            for (InetSocketAddress peerAddress : initialPeers) {
                es.execute(() -> connectToPeer(peerAddress));
            }

            PeerProtocol peerProtocol = new PeerProtocol(listenPort);
            // send some getpeers messages.
            peerProtocol.send();
        });
    }

    public ConnectionManager(int listenPort, DataStore dataStore, Bus bus) throws IOException {
        List<InetSocketAddress> initialPeers = getInitialPeers(); // it works!
        _initialise(initialPeers, listenPort, dataStore, bus);
    }

    // This is used primarily for testing
    public ConnectionManager(List<InetSocketAddress> initialPeers, int listenPort, DataStore dataStore, Bus bus)
            throws IOException {
        _initialise(initialPeers, listenPort, dataStore, bus);
    }

    private void connectToPeer(InetSocketAddress peerAddress) {
        Socket socket;
        try {
            // may throw IOException
            socket = new Socket(peerAddress.getAddress(), peerAddress.getPort());
            // safe
            PeerHandler ph = new PeerHandler(socket, es, dataStore, bus, ourListenPort, blockProtocol::onBlockTimeout);
            try {
                ph.establishConnection(peerAddress);
                peers.put(peerAddress, ph);
            } catch (TimeoutException toe) {
                // this means the connection could not be established before timeout
                log("Did not establish connection to peer within time limit", Level.INFO);
                ph.shutdown();
            } catch (InterruptedException | ExecutionException e) {
                log("An error occurred while establishing connection to peer", Level.INFO);
                ph.shutdown();
            }
        } catch (IOException e) {
            log("An error occurred while creating an outgoing socket to a new peer", Level.INFO);
        }
    }

    /**
     * Send the given message to all connected peers
     * @param block The block to be broadcast
     * @throws IOException in the case that serialization fails
     */
    public void broadcastBlock(Block block) {
        List<Entry> entryList = block.getEntriesList();
        List<ByteString> byteStringList = new ArrayList<>(entryList.size());
        for (Entry e : entryList) {
            byteStringList.add(ByteString.copyFrom(e.serialize()));
        }

        BlockMessage blockMessage = BlockMessage.newBuilder()
                .setBlockBytes(ByteString.copyFrom(block.serializeHeader()))
                .addAllEntries(byteStringList)
                .build();

        Message msg = Message.newBuilder()
                .setType(Message.Type.BLOCK)
                .setBlock(blockMessage)
                .build();

        for (PeerHandler peer : peers.values()) {
            peer.send(msg);
        }
    }

    /**
     * Send the given message to all connected peers.
     * @param e The entry which must be broadcast
     * @throws IOException in the case that serialization fails
     */
    public void broadcastEntry(Entry e) throws IOException {
        EntryMessage entryMessage = EntryMessage.newBuilder()
                .setEntryBytes(ByteString.copyFrom(e.serialize()))
                .build();
        Message message = Message.newBuilder()
                .setType(Message.Type.ENTRY)
                .setEntry(entryMessage)
                .build();
        for (PeerHandler peer : peers.values()) {
            peer.send(message);
        }
    }

    public void printPeers() {
        for (PeerHandler p : peers.values()) {
            InetSocketAddress address = p.getPeerAddress();
            System.out.println("Connected to: " + address.getHostName() + " " + address.getPort());
        }
        System.out.println("There are " + peers.values().size() + " connected peers.");
    }

    public Collection<PeerHandler> peers() {
        return Collections.unmodifiableCollection(peers.values());
    }

    /**
     * For testing only - not relevant to actual version
     */
    @Subscribe
    public void onNewEntryEvent(NewEntryEvent nee) {
        // prints the document description
        System.out.println(nee.getNewEntry().getDocDescription());
    }

    @Subscribe
    public void onNewBlockEvent(NewBlockEvent nbe) {
        System.out.println(nbe.getNewBlock().getNonce());
    }

    /**
     * Create a peers message to send to the sender of the
     * received getPeers message. This is sent to the thread pool to execute to avoid
     * significant latency.
     * @param event The GetPeersEvent
     */
    @Subscribe
    public void onGetPeersEvent(GetPeersEvent event) {
        // extract the InetSocketAddresses from the Peers.
        es.execute(() -> {
            InetSocketAddress addressFrom = event.getPeer().getPeerAddress();
            Peers.Builder peerMessageBuilder = Peers.newBuilder();
            for (PeerHandler p : peers.values()) {
                InetSocketAddress peerListenAddress = p.getPeerAddress();
                if (!p.getPeerAddress().equals(addressFrom)) {
                    peerMessageBuilder.addAddress(NetAddress.newBuilder()
                            .setHostName(peerListenAddress.getHostName())
                            .setPort(peerListenAddress.getPort())
                            .build());
                }
            }
            Peers peer = peerMessageBuilder.build();
            Message msg = Message.newBuilder()
                    .setType(Message.Type.PEERS)
                    .setPeers(peer)
                    .build();
            event.getPeer().send(msg);
        });
    }

    /**
     * @param
     */
    private List<InetSocketAddress> getInitialPeers() {
        URL url;
        try {
            // Set up input stream to node server
            url = new URL(PEER_URL);
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(45000);
            InputStream input = conn.getInputStream();

            // Convert input to json
            Map<String, List<String>> returnedData = new Gson().fromJson(
                    new InputStreamReader(input, "UTF-8"),
                    new TypeToken<Map<String, List<String>>>() {
                    }.getType());

            // Get list of nodes
            List<String> addresses = returnedData.get("nodes");

            //Convert that list into SocketAddresses
            List<InetSocketAddress> socketAddresses = new ArrayList<InetSocketAddress>();
            for (String address : addresses) {
                String[] components = address.split(":");
                if (components.length == 2) {
                    // TODO checking input here so we don't crash
                    socketAddresses.add(new InetSocketAddress(components[0], Integer.parseInt(components[1])));
                }
            }
            return socketAddresses;

        } catch (MalformedURLException e) {
            //TODO actually handle these exceptions
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;

    }

    private void log(String message, Level level) {
        bus.post(new LogEvent(message, LogEventSource.NETWORK, level));
    }

    public class BlockProtocol {

        private static final int MAX_HEADERS = 10000;
        private static final int HEADERS_TIMEOUT_SECONDS = 10;

        // blocks we will download in the future
        private Deque<byte[]> futureBlockIDs = new ConcurrentLinkedDeque<>();

        // blocks we've received but don't yet have the parent of
        // a map from parentID => Block
        // TODO: limit the size of this collection (like a sliding download window)
        private Map<byte[], Block> orphanBlocks = new ConcurrentHashMap<>();

        public BlockProtocol() {
            bus.register(this);
        }

        /**
         * Performs the initial block download process.
         * Method will block while waiting for headers to arrive, so should be called on a separate thread.
         * @throws SQLException A database error occurred.
         */
        public void initiateBlockDownload() throws SQLException {
            // First obtain block headers from some particular peer - send a GetBlockHeaders message
            // then validate this sequence of headers upon receiving a BlockHeaders message
            PeerHandler p = chooseRandomPeer();
            List<byte[]> fromBlockIDs = dataStore.getActiveBlocksSample();

            // we will break once we've got all the headers, having dispatched download tasks asynchronously.
            // for now, if a peer suddenly gives us some invalid headers, we don't shutdown previous download
            // tasks or discard earlier block headers they gave us.
            while (true) {
                HeadersFuture h = new HeadersFuture(p, fromBlockIDs, bus);
                h.run();
                List<Block> receivedHeaders = null;
                try {
                    receivedHeaders = h.get(HEADERS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    // this ought not to happen but we can just try again.
                } catch (TimeoutException e) {
                    // in this case shutdown the peer and try another.
                    disconnectPeer(p);
                }

                if (receivedHeaders == null) {
                    // choose a new peer and try again
                    p = chooseRandomPeer();
                } else {
                    byte[] firstPredecessorID = receivedHeaders.get(0).getPrevBlockHash();
                    // first header must follow some older block we have on our primary chain
                    Block first = dataStore.getBlock(firstPredecessorID);
                    if (first != null && first.isActive() && Block.verifyChain(receivedHeaders)) {
                        ArrayList<byte[]> blockIDs = new ArrayList<>(receivedHeaders.size());
                        for (Block b : receivedHeaders) {
                            if (!dataStore.blockExists(b.getBlockID()))
                                blockIDs.add(b.getBlockID());
                        }
                        futureBlockIDs.addAll(blockIDs);
                        downloadQueuedBlocks();

                        if (receivedHeaders.size() < MAX_HEADERS)
                            break;
                        else
                            fromBlockIDs.add(0, receivedHeaders.get(receivedHeaders.size() - 1).getBlockID());

                    } else {
                        // choose a new peer and try again
                        p = chooseRandomPeer();
                    }
                }
            }
        }

        /**
         * Makes a copy of the set of peers and chooses one at random. Be aware that this peer could have been shut down while we were choosing it!
         * @return
         */
        private PeerHandler chooseRandomPeer() {
            // make a copy of the peers collection to safely get a random element
            ArrayList<PeerHandler> p = new ArrayList<>(peers.values());
            return p.get(ThreadLocalRandom.current().nextInt(p.size()));
        }

        /**
         * Makes a copy of the set of peers and shuffles it.
         * @return
         */
        private List<PeerHandler> shufflePeers() {
            ArrayList<PeerHandler> p = new ArrayList<>(peers.values());
            Collections.shuffle(p, ThreadLocalRandom.current());
            return p;
        }

        /**
         * Download the blocks in futureBlockIDs in a distributed fashion, in parallel.
         */
        private void downloadQueuedBlocks() {
            // distribute future blocks across peers, until all peers have a full queue or we run out of blocks.

            byte[] b = futureBlockIDs.pollFirst();
            boolean allPeersFull = false;

            while (b != null && !allPeersFull) {
                // if all peers won't accept another block request, stop.
                allPeersFull = true;
                for (PeerHandler peer : peers.values()) {
                    // provide the future headers queue so the peer can obtain more blocks to download when done
                    if (peer.requestBlock(b)) {
                        allPeersFull = false;
                        // take the next block
                        b = futureBlockIDs.pollFirst();
                    }
                }
            }
            // the last block was never requested (unless it's null) so put it back on the queue
            if (b != null)
                futureBlockIDs.addFirst(b);
        }


        @Subscribe
        public void onBlockMessage(BlockMessageEvent e) {
            log("Block message received", Level.FINE);
            BlockMessage message = e.getBlockMessage();
            PeerHandler peer = e.getPeer();

            byte[] blockBytes = message.getBlockBytes().toByteArray();

            try {
                // deserialize block
                Block block = Block.deserialize(blockBytes);

                // see if we requested this block from this peer
                if (peer.getBlocksInFlight().remove(block.getBlockID())) {

                    // if so restart the timer for blocks
                    peer.getBlockTimer().stop();
                    peer.getBlockTimer().start();

                    // can download another block from peer (providing there are more queued up)
                    byte[] next = futureBlockIDs.poll();
                    if (next == null && peer.getBlocksInFlight().isEmpty()) {
                        // stop the timer if there are no more blocks in flight
                        peer.getBlockTimer().stop();
                    } else {
                        if (!peer.requestBlock(next)) {
                            // put it back on the queue if we can't download another block (due to a race)
                            futureBlockIDs.addFirst(next);
                        }
                    }
                }

                // check we don't already have it in our store
                if (dataStore.blockExists(block.getBlockID())) {
                    log("received a block we already have", Level.FINE);
                    return;
                }
                // verify its hash meets its target
                if (!Miner.blockHashMeetDifficulty(block)) {
                    log("block was rejected because target didn't meet difficulty", Level.FINE);
                    return;
                }

                List<ByteString> entryBytesList = message.getEntriesList();
                List<Entry> entryList = new ArrayList<>();
                for (ByteString string : entryBytesList) {
                    entryList.add(Entry.deserialize(string.toByteArray()));
                }

                if (block.setEntriesList(entryList)) {
                    // parent exists so store this block
                    InsertBlockResult result = dataStore.insertBlock(block);
                    switch (result) {
                        case SUCCESS:
                            log("block was successfully added to database", Level.FINE);
                            bus.post(new NewBlockEvent(block));
                            // may now be able to insert orphan blocks
                            insertOrphans(block.getBlockID());
                            break;
                        case FAIL_ORPHAN:
                            log("block is an orphan and therefore wasn't added to database", Level.FINE);
                            // keep block in memory and try to store it once its parent has been downloaded.
                            orphanBlocks.put(block.getPrevBlockHash(), block);

                    }
                }
            } catch (IOException ioe) {
                // error in the serialised block or entries received, so discard the block.
                ioe.printStackTrace();
            } catch (SQLException sqle) {
                throw new RuntimeException("Error connecting to database :(", sqle);
            }
        }

        @Subscribe
        public void onBlockNotFoundMessage(BlockNotFoundMessageEvent e) {
            PeerHandler peer = e.getPeer();

            // see if we requested this block from this peer
            byte[] blockID = e.getMessage().getBlockID().toByteArray();
            if (peer.getBlocksInFlight().remove(blockID)) {

                // if so restart the timer for blocks
                peer.getBlockTimer().stop();
                peer.getBlockTimer().start();

                // can download another block from peer (providing there are more queued up)
                byte[] next = futureBlockIDs.poll();
                if (next == null && peer.getBlocksInFlight().isEmpty()) {
                    // stop the timer if there are no more blocks in flight
                    peer.getBlockTimer().stop();
                } else {
                    if (!peer.requestBlock(next)) {
                        // put it back on the queue if we can't download another block (due to a race)
                        futureBlockIDs.addFirst(next);
                    }
                }

                // now ask another peer for this block.
                // Shuffle in case the first two peers both don't have the block - otherwise we would alternate between them and never obtain it.
                for (PeerHandler p : shufflePeers()) {
                    // provide the future headers queue so the peer can obtain more blocks to download when done
                    if (p != peer && peer.requestBlock(blockID)) {
                        return;
                    }
                }
                // if we get here, all peers were full, so put this block back on the queue
                // full peers imply it will be taken and requested at some point without having to trigger a download ourselves
                futureBlockIDs.addFirst(blockID);
            }
        }

        private void insertOrphans(byte[] parentBlockID) throws SQLException {
            Block b = orphanBlocks.remove(parentBlockID);
            if (b != null) {
                // could fail due to duplicate, but if so we don't care, we've still unorphaned it
                dataStore.insertBlock(b);
                // now see if this allows us to unorphan any more blocks
                insertOrphans(b.getBlockID());
            }
        }

        private void onBlockTimeout(PeerHandler peer) {
            disconnectPeer(peer);
            // re-request all of that peer's in-flight blocks from other peers.
            futureBlockIDs.addAll(peer.getBlocksInFlight());
            // these might be the only blocks outstanding so trigger more downloads.
            downloadQueuedBlocks();
        }

        @Subscribe
        public void onPeerError(PeerErrorEvent e) {
            disconnectPeer(e.getPeer());
        }

        private void disconnectPeer(PeerHandler peer) {
            // harmless if the peer never established a connection (although that shouldn't happen).
            peers.remove(peer.getPeerAddress());
            // disconnect the peer
            peer.shutdown();
        }
    }

    private enum State {WAIT, IDLE}

    public class PeerProtocol {

        private State state;
        private int listenPort;

        public PeerProtocol(int listenPort) {
            state = State.IDLE;
            this.listenPort = listenPort;

            bus.register(this);
        }

        public void send() {
            peers.values().forEach(p -> {
                MessageProto.GetPeers getPeers = MessageProto.GetPeers.newBuilder().build();
                MessageProto.Message message = MessageProto.Message.newBuilder()
                        .setType(MessageProto.Message.Type.GETPEERS)
                        .setGetPeers(getPeers)
                        .build();
                p.send(message);
                state = State.WAIT;
                // register for peers event messages
                bus.register(this);
                Timer timer = new Timer();
                // resend if no response has been received
                while (state != State.IDLE)
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            p.send(message);
                        }
                    }, 3000L);

            });

        }

        @Subscribe
        public void onPeersEvent(PeersEvent pe) {
            bus.unregister(this);
            es.execute(() -> {
                state = State.IDLE;
                Set<InetSocketAddress> newAddresses = pe.getSocketAddresses();
                for (InetSocketAddress address : newAddresses) {
                    if (!peers.containsKey(address)) {
                        es.execute(() -> connectToPeer(address));
                    }
                }
            });
        }
    }

}

