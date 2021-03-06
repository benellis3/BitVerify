package bitverify.network;

import java.io.*;

import java.net.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

import bitverify.ExceptionLogEvent;
import bitverify.LogEvent;
import bitverify.LogEventSource;
import bitverify.block.Block;
import bitverify.entries.Entry;
import bitverify.mining.Miner;
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
import sun.text.resources.cldr.bn.FormatData_bn_IN;


/**
 * This class is responsible for co-ordination of network functions.
 * It controls the incoming and outgoing parts of the connection.
 * @author Ben Ellis, Robert Eady
 */
public class ConnectionManager {
    private DataStore dataStore;
    private ExecutorService es;
    private Bus bus;
    private Map<InetSocketAddress, PeerHandler> peers;
    private static final String PEER_URL = "http://52.48.86.95:4000/nodes"; // for testing
    private BlockProtocol blockProtocol;
    private InetSocketAddress ourListenAddress;

    private static final int GET_PEERS_TIMEOUT_SECONDS = 10;

    /**
     * Instantiate a new Connection Manager, which will establish networking communications
     * @param listenPort the local port to listen for new connections on
     * @param dataStore the data store
     * @param bus the event bus
     */
    public ConnectionManager(int listenPort, DataStore dataStore, Bus bus) {
        this(getInitialPeers(), listenPort, dataStore, bus);
    }

    // also used for testing with a pre-defined set of initial peers
    ConnectionManager(List<InetSocketAddress> initialPeers, int ourListenPort, DataStore ds, Bus bus) {
        peers = new ConcurrentHashMap<>();
        this.bus = bus;
        bus.register(this);

        blockProtocol = new BlockProtocol();
        dataStore = ds;

        // create a special executor service that makes daemon threads.
        // this way the application can shut down without having to terminate network threads first.

        Thread.UncaughtExceptionHandler ueh = (t, e) -> {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            bus.post(new ExceptionLogEvent("An exception in a network thread was not caught: " + sw.toString(),
                            LogEventSource.NETWORK, Level.SEVERE, e));
        };

        ThreadFactory daemonThreadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler(ueh);
            return thread;
        };
        es = Executors.newCachedThreadPool(daemonThreadFactory);

        es.execute(() -> initialize(ourListenPort, initialPeers));
    }

    private void initialize(int ourListenPort, List<InetSocketAddress> initialPeers) {
        // first we obtain our own IP address
        try {
            ourListenAddress = new InetSocketAddress(getOurIPAddress(), ourListenPort);
        } catch (IOException e) {
            log("An error occurred while obtaining our IP address", Level.SEVERE, e);
        }
        // Create new runnable to listen for new connections.
        try {
            ServerSocket serverSocket = new ServerSocket(ourListenPort);
            es.execute(() -> {
                        try {
                            while (true) {
                                Socket s = serverSocket.accept();
                                // separate thread since it blocks waiting for messages.
                                es.execute(() -> {
                                    PeerHandler ph = new PeerHandler(s, es, dataStore, bus, ourListenPort);
                                    try {
                                        InetSocketAddress address = ph.acceptConnection();
                                        if (address == null) {
                                            ph.shutdown();
                                        } else {
                                            peers.put(address, ph);
                                        }
                                        // do block download against this peer
                                        blockProtocol.blockDownloadSynchronized(ph, false);
                                    } catch (TimeoutException time) {
                                        // this means the connection could not be established before timeout
                                        log("Did not establish connection to peer within time limit", Level.INFO);
                                        ph.shutdown();
                                    } catch (InterruptedException | ExecutionException ie) {
                                        log("An error occurred while establishing connection to peer", Level.INFO);
                                        log("Cause: " + ie.getCause().getMessage(), Level.INFO);
                                        ph.shutdown();
                                    }
                                });
                            }
                        } catch (IOException ioe) {
                            // if the server socket dies, we can still carry on but won't be able to accept new connections.
                            log("Server socket died: can no longer accept new incoming peer connections. " + ioe.getMessage(), Level.SEVERE, ioe);
                        }
                    }
            );
        } catch (IOException ioe) {
            // if the server socket dies, we can still carry on but won't be able to accept new connections.
            log("Server socket died: can no longer accept new incoming peer connections. " + ioe.getMessage(), Level.SEVERE, ioe);
        }

        // connect to each given peer and do get peers. For now, only ask our initial peers for more peers.
        es.submit(() -> {
            List<Future<?>> futures = new ArrayList<>();

            for (InetSocketAddress peerAddress : initialPeers) {
                if (ourListenAddress.equals(peerAddress))
                    continue;

                futures.add(es.submit(() -> {
                    try {
                        PeerHandler newPeerHandler = connectToPeer(peerAddress);
                        if (newPeerHandler == null)
                            return;

                        // do getpeers
                        log("sending get peers message", Level.FINE);
                        PeersFuture pf = new PeersFuture(newPeerHandler, bus);
                        pf.run();
                        Set<InetSocketAddress> newPeers = null;
                        try {
                            newPeers = pf.get(GET_PEERS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                            log("received peers reply with " + newPeers.size() + " peers", Level.FINE);
                        } catch (InterruptedException e) {
                            // this ought not to happen
                            log("while doing get peers, a mysterious InterruptedException occurred", Level.WARNING, e);
                        } catch (TimeoutException e) {
                            // in this case shutdown the peer .
                            log("timed out while waiting for headers reply. Disconnecting peer " + newPeerHandler.getPeerAddress(), Level.FINE);
                            disconnectPeer(newPeerHandler);
                        }

                        if (newPeers != null) {
                            for (InetSocketAddress address : newPeers) {
                                if (!peers.containsKey(address) && !ourListenAddress.equals(address)) {
                                    log("Connecting to a new peer as a result of peers message with address " + address, Level.FINE);
                                    connectToPeer(address);
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log("oh dear : " + ex.getMessage(), Level.FINE);
                        ex.printStackTrace();
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (InterruptedException|ExecutionException e) {
                    e.printStackTrace();
                    log("Unexpected exception occurred while connecting to initial peer", Level.WARNING, e);
                }
            }
            // once peers are connected, do block download from everyone, then send everyone your unconfirmed entries
            es.execute(() -> {
                try {
                    blockProtocol.initiateBlockDownload();
                } catch (InterruptedException e) {
                    log("InterruptedException while performing block download: " + e.getMessage(), Level.SEVERE, e);
                }
            });
        });
    }

    private String getOurIPAddress() throws IOException {
        URL url1 = new URL("http://checkip.amazonaws.com/");
        BufferedReader br = new BufferedReader(new InputStreamReader(url1.openStream()));
        return br.readLine();
    }


    private PeerHandler connectToPeer(InetSocketAddress peerAddress) {
        Socket socket;
        try {
            socket = new Socket();
            // may throw IOException
            socket.connect(peerAddress, GET_PEERS_TIMEOUT_SECONDS * 1000);
            // safe
            PeerHandler ph = new PeerHandler(socket, es, dataStore, bus, ourListenAddress.getPort());
            try {
                if (ph.establishConnection(peerAddress)) {
                    peers.put(peerAddress, ph);
                    return ph;
                } else {
                    ph.shutdown();
                }
            } catch (TimeoutException toe) {
                // this means the connection could not be established before timeout
                log("Did not establish connection to peer within time limit", Level.INFO);
                ph.shutdown();
            } catch (InterruptedException | ExecutionException e) {
                log("An error occurred while establishing connection to peer", Level.WARNING, e);
                ph.shutdown();
            }
        } catch (SocketTimeoutException e) {
            log("Could not connect to peer within time limit " + e.getMessage(), Level.INFO, e);
        }
        catch (IOException e) {
            log("An error occurred while creating an outgoing socket to a new peer: " + e.getMessage(), Level.INFO, e);
        }
        return null;
    }

    /**
     * Send the given message to all connected peers
     * @param block The block to be broadcast
     * @throws IOException in the case that serialization fails
     */
    public void broadcastBlock(Block block) {
        log("About to broadcast a block with ID " + new BlockID(block.getBlockID()), Level.FINE);
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
    public void broadcastEntry(Entry e) {
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
    
    public Set<InetSocketAddress> getPeerSet() {
    	return peers.keySet();
    }

    /**
     * Create a peers message to send to the sender of the
     * received getPeers message. This is sent to the thread pool to execute to avoid
     * significant latency.
     * @param event The GetPeersEvent
     */
    @Subscribe
    public void onGetPeersEvent(GetPeersEvent event) {
        log("handling a get peers message", Level.FINE);
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
            log("Sent a peers message with " + peer.getAddressCount() + " peers", Level.FINE);
        });
    }

    private static List<InetSocketAddress> getInitialPeers() {
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

    public void shutdownAllConnections() {
        peers.values().forEach(this::disconnectPeer);
    }

    private void disconnectPeer(PeerHandler peer) {
        // harmless if the peer never established a connection (although that shouldn't happen).
        peers.remove(peer.getPeerAddress());
        // disconnect the peer
        peer.shutdown();

        // re-request all of that peer's in-flight blocks from other peers.
        blockProtocol.futureBlockIDs.addAll(peer.getBlocksInFlight());
        final int numInFlight = peer.getBlocksInFlight().size();
        // these might be the only blocks outstanding so trigger more downloads.
        es.execute(() -> {
            blockProtocol.downloadQueuedBlocks();
            // only decrease the number of blocks in flight after trying to re-download, because it
            // mustn't look like we've finished our block download before we try to re-download those lost.
            blockProtocol.blocksInFlightCounter.decrease(numInFlight);
        });
    }

    @Subscribe
    public void onPeerError(PeerErrorEvent e) {
        disconnectPeer(e.getPeer());
    }

    private void log(String message, Level level) {
        bus.post(new LogEvent(message, LogEventSource.NETWORK, level));
    }

    private void log(String message, Level level, Throwable error) {
        bus.post(new ExceptionLogEvent(message, LogEventSource.NETWORK, level, error));
    }



    public class BlockProtocol {

        static final int MAX_HEADERS = 10000000;
        // at most how many block IDs from our active chain to provide in a GetHeaders message
        private static final int HEADERS_NUM_BLOCK_IDS = 20;
        private static final int HEADERS_TIMEOUT_SECONDS = 10;

        // blocks we will download in the future
        private final Deque<BlockID> futureBlockIDs = new ConcurrentLinkedDeque<>();

        // blocks we've received but don't yet have the parent of
        // a map from parentID => Block
        // TODO: limit the size of this collection (like a sliding download window)
        private final Map<BlockID, Block> orphanBlocks = new ConcurrentHashMap<>();
        //
        private final BlocksInFlightCounter blocksInFlightCounter = new BlocksInFlightCounter();
        private final Object insertBlockMonitor = new Object();

        public BlockProtocol() {
            bus.register(this);
        }

        /**
         * Start block download by requesting headers from the given peer.
         * This method is synchronized such that at most one block download can happen at a time.
         * @param peer the peer
         * @param distribute whether to distribute get block requests to all peers, or just to this one.
         * @return
         */
        private void blockDownloadSynchronized(PeerHandler peer, boolean distribute) {
            try {
                blocksInFlightCounter.onceZero((Runnable)() -> blockDownload(peer, distribute));
                // send unconfirmed entries when done
                if (distribute)
                    broadcastEntriesOnceBlockDownloadComplete();
                else
                    sendEntriesOnceBlockDownloadComplete(peer);
            } catch (InterruptedException e) {
                log("InterruptedException while performing block download: " + e.getMessage(), Level.SEVERE, e);
            }
        }

        private void broadcastEntriesOnceBlockDownloadComplete() throws InterruptedException {
            blocksInFlightCounter.onceZero(() -> {
                log("Now broadcasting unconfirmed entries", Level.FINE);
                try {
                    for (Entry e : dataStore.getUnconfirmedEntries()) {
                        log("broadcasting entry " + e.getEntryID(), Level.FINE);
                        broadcastEntry(e);
                    }
                } catch (SQLException e) {
                    log("Database exception occurred while performing unconfirmed entry broadcast: " + e.getMessage(), Level.SEVERE, e);
                }
            });
        }

        private void sendEntriesOnceBlockDownloadComplete(PeerHandler peer) throws InterruptedException {
            blocksInFlightCounter.onceZero(() -> {
                try {
                    log("Now sending unconfirmed entries to " + peer.getPeerAddress(), Level.FINE);
                    for (Entry e : dataStore.getUnconfirmedEntries()) {
                        log("sending entry " + e.getEntryID() + " to peer " + peer.getPeerAddress(), Level.FINE);
                        EntryMessage entryMessage = EntryMessage.newBuilder()
                                .setEntryBytes(ByteString.copyFrom(e.serialize()))
                                .build();
                        Message message = Message.newBuilder()
                                .setType(Message.Type.ENTRY)
                                .setEntry(entryMessage)
                                .build();
                        peer.send(message);
                    }
                } catch (SQLException e) {
                    log("Database exception occurred while performing unconfirmed entry broadcast: " + e.getMessage(), Level.SEVERE, e);
                }
            });
        }

        /**
         * Start block download by requesting headers from the given peer
         * @param peer the peer
         * @param distribute whether to distribute get block requests to all peers, or just to this one.
         * @return
         */
        private boolean blockDownload(PeerHandler peer, boolean distribute) {
            try {
                List<byte[]> fromBlockIDs = dataStore.getActiveBlocksSample(MAX_HEADERS);
                while (true) {
                    log("sending get headers message", Level.FINE);
                    HeadersFuture h = new HeadersFuture(peer, fromBlockIDs, bus);
                    h.run();
                    List<Block> receivedHeaders;
                    try {
                        receivedHeaders = h.get(HEADERS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        // this ought not to happen
                        log("unexpected InterruptedException while getting block headers", Level.WARNING, e);
                        return false;
                    } catch (TimeoutException e) {
                        // in this case shutdown the peer and try another.
                        log("timed out while waiting for headers reply. Disconnecting peer " + peer.getPeerAddress(), Level.FINE);
                        disconnectPeer(peer);
                        return false;
                    }

                    if (receivedHeaders == null) {
                        // choose a new peer and try again
                        log("did not get a valid headers response. Disconnecting peer " + peer.getPeerAddress(), Level.FINE);
                        disconnectPeer(peer);
                        return false;
                    } else if (receivedHeaders.isEmpty()) {
                        // we're done
                        log("headers download complete, 0 headers were received", Level.FINE);
                        return true;
                    } else {
                        log("received headers reply with " + receivedHeaders.size() + " headers", Level.FINE);
                        byte[] firstPredecessorID = receivedHeaders.get(0).getPrevBlockHash();
                        // first header must follow some older block we have on our primary chain
                        Block firstPredecessor = dataStore.getBlock(firstPredecessorID);
                        if (firstPredecessor == null) {
                            log("received headers were not accepted because we don't have the previous block. Choosing a new peer.", Level.FINE);
                            return false;
                        }
                        if (!firstPredecessor.isActive()) {
                            log("received headers were not accepted because the previous block is not active. Choosing a new peer.", Level.FINE);
                            return false;
                        }
                        if (!Block.verifyChain(receivedHeaders, bus)) {
                            // choose a new peer and try again
                            log("received headers were not accepted because the chain was invalid. Choosing a new peer.", Level.FINE);
                            return false;

                        } else {
                            log("Got " + receivedHeaders.size() + " valid headers", Level.FINE);
                            ArrayList<BlockID> blockIDs = new ArrayList<>(receivedHeaders.size());
                            for (Block b : receivedHeaders) {
                                if (!dataStore.blockExists(b.getBlockID()))
                                    blockIDs.add(new BlockID(b.getBlockID()));
                            }
                            log("About to download " + blockIDs.size() + " blocks", Level.FINE);
                            futureBlockIDs.addAll(blockIDs);

                            if (distribute)
                                downloadQueuedBlocks();
                            else
                                downloadQueuedBlocks(peer);

                            if (receivedHeaders.size() < MAX_HEADERS) {
                                // TODO: verify against other peers
                                log("headers download complete", Level.FINE);
                                return true;
                            } else {
                                // add the last block id provided to the FRONT of the list of block IDs to put in next getheaders message
                                log("may be some more headers to get, about to send another request", Level.FINE);
                                fromBlockIDs.add(0, receivedHeaders.get(receivedHeaders.size() - 1).getBlockID());
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                log("Database exception occurred while performing block download: " + e.getMessage(), Level.SEVERE, e);
                return false;
            }
        }


        /**
         * Performs the initial block download process. Best to call this on a separate thread as it will block.
         * @return true if we successfully got a chain of headers, false otherwise.
         */
        private void initiateBlockDownload() throws InterruptedException {
            // only do this once at any one time.
            blocksInFlightCounter.onceZero(() -> {
                log("Initiating block download", Level.FINE);

                // First obtain block headers from some particular peer - send a GetBlockHeaders message
                // then validate this sequence of headers upon receiving a BlockHeaders message

                // take snapshot of peers
                List<PeerHandler> peersSnapshot = new ArrayList<>(peers.values());
                shufflePeers(peersSnapshot);

                // we will break once we've got all the headers, having dispatched download tasks asynchronously.
                for (PeerHandler p : peersSnapshot) {
                    if (blockDownload(p, true))
                        return;
                }
            });
            // broadcast unconfirmed entries when done
            broadcastEntriesOnceBlockDownloadComplete();
        }

        /**
         * Shuffles a copy of the set of peers.
         * @return
         */
        private List<PeerHandler> shufflePeers(List<PeerHandler> p) {
            Collections.shuffle(p, ThreadLocalRandom.current());
            return p;
        }

        /**
         * Download the blocks in futureBlockIDs in a distributed fashion, in parallel.
         */
        private void downloadQueuedBlocks() {
            // distribute future blocks across peers, until all peers have a full queue or we run out of blocks.

            BlockID b = futureBlockIDs.pollFirst();
            boolean allPeersFull = false;

            while (b != null && !allPeersFull) {
                // if all peers won't accept another block request, stop.
                allPeersFull = true;
                for (PeerHandler peer : peers.values()) {
                    // break if there are no more blocks to be downloaded
                    if (b == null) {
                        break;
                    }
                    if (peer.requestBlock(b)) {
                        blocksInFlightCounter.increment();
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


        private void downloadQueuedBlocks(PeerHandler peer) {
            // distribute future blocks across peers, until all peers have a full queue or we run out of blocks.
            BlockID b;
            while ((b = futureBlockIDs.pollFirst()) != null) {
                if (peer.requestBlock(b)) {
                    blocksInFlightCounter.increment();
                } else {
                    break;
                }
            }
            // the last block was never requested (unless it's null) so put it back on the queue
            if (b != null)
                futureBlockIDs.addFirst(b);
        }


        @Subscribe
        public void onBlockMessage(BlockMessageEvent e) throws SQLException {
            BlockMessage message = e.getBlockMessage();
            PeerHandler peer = e.getPeer();

            byte[] blockBytes = message.getBlockBytes().toByteArray();

            try {
                // deserialize block
                Block block = Block.deserialize(blockBytes);
                log("Block received with ID " + Base64.getEncoder().encodeToString(block.getBlockID()), Level.FINE);
                log("there were " + peer.getBlocksInFlight().size() + " blocks in flight from peer " + peer.getPeerAddress(), Level.FINER);

                // see if we requested this block from this peer
                boolean blockWasExpected = peer.getBlocksInFlight().remove(new BlockID(block.getBlockID()));
                boolean shouldDecrementBlocksInFlight = false;
                if (blockWasExpected) {
                    log("there are now " + peer.getBlocksInFlight().size() + " blocks in flight from peer " + peer.getPeerAddress(), Level.FINER);
                    log("timer restarted - in flight block received from peer " + peer.getPeerAddress(), Level.FINE);
                    // if so restart the timer for blocks
                    peer.getBlockTimer().stop();
                    peer.getBlockTimer().start();

                    // only if we failed to download another.
                    shouldDecrementBlocksInFlight = !downloadAnotherBlock(peer);
                }

                // check we don't already have it in our store
                if (dataStore.blockExists(block.getBlockID())) {
                    log("block was rejected because it was a duplicate; ID " + new BlockID(block.getBlockID()), Level.FINE);
                    return;
                }
                // verify its hash meets its target
                if (!Miner.blockHashMeetDifficulty(block)) {
                    log("block was rejected because its hash didn't meet target difficulty; ID " + new BlockID(block.getBlockID()), Level.FINE);
                    return;
                }

                List<ByteString> entryBytesList = message.getEntriesList();
                List<Entry> entryList = new ArrayList<>();
                for (ByteString string : entryBytesList) {
                    entryList.add(Entry.deserialize(string.toByteArray()));
                }

                log("Block " + new BlockID(block.getBlockID()) + " was received with " + entryList.size() + " entries; entry hash is " + Base64.getEncoder().encodeToString(block.getEntriesHash()), Level.FINER);

                // check entries are valid
                if (!block.setEntriesList(entryList)) {
                    log("block was rejected because entries hash didn't match block header field; ID " + new BlockID(block.getBlockID()), Level.FINE);
                    bus.post(new LogEvent("entries hash comparison failed for block " + new BlockID(block.getBlockID())
                            + " expected hash "+ Base64.getEncoder().encodeToString(block.getEntriesHash())
                            + ", actual hash" + Base64.getEncoder().encodeToString(block.hashEntries()),
                            LogEventSource.NETWORK, Level.FINER));
                    log("received block had " + entryList.size() + " entries", Level.FINE);
                    return;
                }

                // synchronize here because otherwise an orphan's parent may be inserted between the call to getBlock and the orphanBlocks.put operation.
                synchronized (insertBlockMonitor) {
                    // get the parent block
                    Block parent = dataStore.getBlock(block.getPrevBlockHash());
                    if (parent == null) {
                        log("block is an orphan and therefore wasn't added to database; ID " + new BlockID(block.getBlockID()), Level.FINE);
                        // keep block in memory and try to store it once its parent has been downloaded.
                        final BlockID orphanBlockKey = new BlockID(block.getPrevBlockHash());
                        orphanBlocks.put(orphanBlockKey, block);
                        log("there are now " + orphanBlocks.size() + " orphan blocks.", Level.FINE);

                        // do some more block downloading if this block was broadcast to us
                        if (!blockWasExpected) {
                            es.execute(() -> {
                                try {
                                    blocksInFlightCounter.onceZero(() -> {
                                        // block may become unorphaned by the time we finish our previous block download
                                        if (orphanBlocks.containsKey(orphanBlockKey)) {
                                            log("initiating another block download because an orphan block was broadcast to us", Level.FINE);
                                            blockDownload(peer, false);
                                        } else {
                                            log("aborted another block download because the block was unorphaned.", Level.FINE);
                                        }

                                    });
                                } catch (InterruptedException ex) {
                                    log("unexpected interrupted exception while performing block download: " + ex.getMessage(), Level.SEVERE, ex);
                                }

                            });
                        }
                    } else {
                        // verify it was mined with the right difficulty
                        if (!Miner.checkBlockDifficulty(dataStore, block, parent, bus)) {
                            log("block was rejected because the difficulty was too low, ID " + new BlockID(block.getBlockID()), Level.FINE);
                            return;
                        }

                        try {
                            InsertBlockResult result = dataStore.insertBlock(block);

                            switch (result) {
                                case SUCCESS:
                                    // parent exists so store this block
                                    log("block was successfully added to database; ID " + new BlockID(block.getBlockID()), Level.FINE);
                                    bus.post(new NewBlockEvent(block));
                                    // may now be able to insert orphan blocks
                                    insertOrphans(block);
                                    break;
                                case FAIL_ORPHAN:
                                    assert false;
                                    break;
                                case FAIL_DUPLICATE:
                                    log("block was rejected because it was a duplicate; ID " + new BlockID(block.getBlockID()), Level.FINE);
                                    break;
                            }
                        } catch (Exception ex) {
                            log("OH DEAR: " + ex.getMessage(), Level.SEVERE, ex);
                            ex.printStackTrace();
                        }

                    }
                }
                if (shouldDecrementBlocksInFlight)
                    blocksInFlightCounter.decrement();

                log("total blocks in flight: " + blocksInFlightCounter.get(), Level.FINE);

            } catch (IOException ioe) {
                // error in the serialised block or entries received, so discard the block.
                log("block was rejected because of an error deserializing it: " + ioe, Level.FINE);
            }
        }

        @Subscribe
        public void onBlockNotFoundMessage(BlockNotFoundMessageEvent e) {
            log("Received a block not found message", Level.FINE);
            PeerHandler peer = e.getPeer();

            // see if we requested this block from this peer
            BlockID blockID = new BlockID(e.getMessage().getBlockID());
            if (peer.getBlocksInFlight().remove(blockID)) {

                // if so restart the timer for blocks
                peer.getBlockTimer().stop();
                peer.getBlockTimer().start();

                // can download another block from peer (providing there are more queued up)
                boolean downloadedAnother = downloadAnotherBlock(peer);

                // now ask another peer for this block.
                // Shuffle in case the first two peers both don't have the block - otherwise we would alternate between them and never obtain it.
                for (PeerHandler p : shufflePeers(new ArrayList<>(peers.values()))) {
                    if (p != peer && peer.requestBlock(blockID)) {
                        blocksInFlightCounter.increment();
                        return;
                    }
                }

                if (!downloadedAnother)
                    blocksInFlightCounter.decrement(); // only if we failed to download another block

                // if we get here, all peers were full, so put this block back on the queue
                // full peers imply it will be taken and requested at some point without having to trigger a download ourselves
                // it's also possible but unlikely that nobody has it, in which case it will linger on the futureBlockIDs queue forever.
                futureBlockIDs.addFirst(blockID);
            }
        }

        /**
         * Try to download another block from the same peer
         * @return true if we requested another block, false otherwise. To be used to determine whether to mutate the blocksInFlightCounter.
         */
        private boolean downloadAnotherBlock(PeerHandler peer) {
            BlockID next = futureBlockIDs.poll();
            if (next == null) {
                if (peer.getBlocksInFlight().isEmpty()) {
                    // stop the timer if there are no more blocks in flight
                    log("timer stopped - no more blocks in flight from peer " + peer.getPeerAddress(), Level.FINE);
                    peer.getBlockTimer().stop();
                }
                return false;
            } else if (peer.requestBlock(next)) {
                return true;
            } else {
                // put it back on the queue if we can't download another block (e.g. due to a race or it being not found by this peer)
                futureBlockIDs.addFirst(next);
                if (peer.getBlocksInFlight().isEmpty()) {
                    // stop the timer if there are no more blocks in flight
                    log("timer stopped - no more blocks in flight from peer " + peer.getPeerAddress(), Level.FINE);
                    peer.getBlockTimer().stop();
                }
                return false;
            }
        }

        private void insertOrphans(Block parentBlock) throws SQLException {
            Block b = orphanBlocks.remove(new BlockID(parentBlock.getBlockID()));
            if (b != null) {
                // verify it was mined with the right difficulty
                if (!Miner.checkBlockDifficulty(dataStore, b, parentBlock, bus)) {
                    log("previously orphaned block was rejected because the difficulty was too low, ID " + new BlockID(b.getBlockID()), Level.FINE);
                    log("previously orphaned block's parent has height " + parentBlock.getHeight(), Level.FINER);
                    return;
                }

                // could fail due to duplicate, but if so we don't care, we've still unorphaned it
                try {
                    InsertBlockResult r =  dataStore.insertBlock(b);

                    switch (r) {
                        case SUCCESS:
                            log("managed to insert a block with " + b.getEntriesList().size() + " entries that was previously an orphan, ID " + new BlockID(b.getBlockID()), Level.FINE);
                            break;
                        case FAIL_DUPLICATE:
                            log("tried to insert a block with " + b.getEntriesList().size() + " entries that was previously an orphan, ID " + new BlockID(b.getBlockID()) + ", but it's now a duplicate so all OK", Level.FINE);
                            break;
                        case FAIL_ORPHAN:
                            assert false;
                            break;
                    }
                    log("there are now " + orphanBlocks.size() + " orphan blocks.", Level.FINE);
                    // now see if this allows us to unorphan any more blocks
                    insertOrphans(b);

                } catch (Exception ex) {
                    log("OH DEAR: " + ex.getMessage(), Level.SEVERE, ex);
                    ex.printStackTrace();
                }


            }
        }

        @Subscribe
        public void onBlockTimeout(BlockTimeoutEvent e) {
            PeerHandler peer = e.getPeer();
            log("block request timed out, disconnecting peer " + peer.getPeerAddress(), Level.FINE);
            // will re-request all of that peer's in-flight blocks from other peers.
            disconnectPeer(peer);
        }
    }
}

