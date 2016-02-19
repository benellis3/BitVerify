package bitverify.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
import bitverify.network.proto.MessageProto.GetPeers;
import bitverify.network.proto.MessageProto.GetHeadersMessage;
import bitverify.network.proto.MessageProto.Message.Type;
import bitverify.persistence.InsertBlockResult;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.google.protobuf.ByteString;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;


/**
 * This class is responsible for co-ordination of network functions.
 * It controls the incoming and outgoing parts of the connection.
 *
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
        es = Executors.newCachedThreadPool();
        blockProtocol = new BlockProtocol();
        dataStore = ds;
        ourListenPort = listenPort;

        // Create new runnable to listen for new connections.
        ServerSocket serverSocket = new ServerSocket(listenPort);

        es.execute(() -> {
                    try {
                        while (true) {
                            Socket s = serverSocket.accept();
                            // separate thread since it blocks waiting for messages.
                            es.execute(() -> {
                                try {
                                    PeerHandler ph = new PeerHandler(s, es, ds, bus);
                                    InetSocketAddress address = ph.acceptConnection();
                                    peers.put(address, ph);
                                } catch (TimeoutException time) {
                                    // this means the connection could not be established before timeout
                                    System.out.println("No response received within the time limit");
                                } catch (InterruptedException | ExecutionException ie) {
                                    ie.printStackTrace();
                                }
                            });
                        }
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
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
        try {
            Socket socket = new Socket(peerAddress.getAddress(), peerAddress.getPort());
            PeerHandler ph = new PeerHandler(socket, es, dataStore, bus);
            ph.establishConnection(ourListenPort, peerAddress);
            peers.put(peerAddress, ph);
        } catch (TimeoutException toe) {
            System.out.println("Failed to contact an initial peer");
        } catch (InterruptedException | ExecutionException | IOException ie) {
            ie.printStackTrace();
        }
    }

    /**
     * Peers are contacted in the constructor, but this method will send a GETPEERS message
     * to all of the current peers that the class knows about.
     */
    public void getPeers() {
        for (PeerHandler p : peers.values()) {
            GetPeers getPeers = GetPeers.newBuilder().build();
            Message message = Message.newBuilder()
                    .setType(Message.Type.GETPEERS)
                    .setGetPeers(getPeers)
                    .build();
            p.send(message);
        }
    }

    public int getNumPeers() {
        return peers.size();
    }

    /**
     * Send the given message to all connected peers
     *
     * @param block The block to be broadcast
     * @throws IOException in the case that serialization fails
     */
    public void broadcastBlock(Block block) throws IOException {
        List<Entry> entryList = block.getEntriesList();
        List<ByteString> byteStringList = new ArrayList<>();
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
     *
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
            InetSocketAddress address = p.getListenAddress();
            System.out.println("Connected to: " + address.getHostName() + " " + address.getPort());
        }
    }

    protected Collection<PeerHandler> peers() {
        return peers.values();
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
     *
     * @param event The GetPeersEvent
     */
    @Subscribe
    public void onGetPeersEvent(GetPeersEvent event) {
        // extract the InetSocketAddresses from the Peers.
        es.execute(() -> {
            InetSocketAddress addressFrom = event.getSocketAddress();
            List<NetAddress> peerAddresses = new ArrayList<>();
            PeerHandler sender = null;
            for (PeerHandler p : peers.values()) {
                InetSocketAddress peerListenAddress = p.getListenAddress();
                if (p.getListenAddress().equals(addressFrom)) {
                    sender = p;
                } else {
                    peerAddresses.add(NetAddress.newBuilder()
                            .setHostName(peerListenAddress.getHostName())
                            .setPort(peerListenAddress.getPort())
                            .build());
                }
            }
            Peers peer = Peers.newBuilder().addAllAddress(peerAddresses).build();
            Message msg = Message.newBuilder().setType(Message.Type.PEERS).setPeers(peer).build();
            sender.send(msg);
        });
    }

    /**
     * @param
     * @return
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


    /**
     * Handles sending a single request for headers to a peer, then awaiting the response.
     */
    public class HeadersFuture implements RunnableFuture<List<Block>> {
        private List<Block> result;
        private final CountDownLatch resultLatch = new CountDownLatch(1);
        private final PeerHandler peer;
        private final byte[] fromBlockID;

        public HeadersFuture(PeerHandler peer, byte[] fromBlockID) {
            this.peer = peer;
            this.fromBlockID = fromBlockID;
        }

        @Override
        public void run() {
            // send a GetBlockHeaders message
            // ask for blocks from our latest known block
            GetHeadersMessage getHeadersMessage = GetHeadersMessage.newBuilder()
                    .setFromBlock(ByteString.copyFrom(fromBlockID))
                    .build();
            Message m = Message.newBuilder()
                    .setType(Type.GET_HEADERS)
                    .setGetHeaders(getHeadersMessage)
                    .build();

            peer.send(m);
            // register for replies once we've sent the request
            bus.register(this);
        }

        @Subscribe
        public void onHeadersMessage(HeadersMessageEvent e) {
            // deregister now we've got our response
            bus.unregister(this);

            List<ByteString> serializedHeaders = e.getHeadersMessage().getHeadersList();
            List<Block> headers = new ArrayList<>(serializedHeaders.size());

            try {
                for (ByteString bytes : serializedHeaders)
                    headers.add(Block.deserialize(bytes.toByteArray()));
                result = headers;
            } catch (IOException ex) {
                // a header was invalidly formatted, we will discard the sequence and re-request from another peer
            }

            // notify that we got a response (even if it was rubbish)
            resultLatch.countDown();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return resultLatch.getCount() == 0;
        }

        @Override
        public List<Block> get() throws InterruptedException, ExecutionException {
            resultLatch.await();
            return result;
        }

        @Override
        public List<Block> get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            if (resultLatch.await(timeout, unit))
                return result;
            else {
                bus.unregister(this);
                throw new TimeoutException();
            }
        }
    }

    private static final int MAX_HEADERS = 10000;

    public class BlockProtocol {
        private static final int MAX_SIMULTANEOUS_BLOCKS_PER_PEER = 20;

        // blocks we are downloading now
        private Map<Block, InetSocketAddress> awaitedBlockHeaders = new ConcurrentHashMap();
        // keep a count of how many blocks we've downloaded from each peer, so we can blacklist ones which don't respond
        private Map<InetSocketAddress, AtomicInteger> blocksDownloadedFromPeer = new ConcurrentHashMap<>();
        // blocks we will download in the future
        private Queue<Block> futureBlockHeaders = new ConcurrentLinkedQueue<>();

        private static final int DOWNLOAD_INTERVAL_MS = 10000;
        private Timer timer = new Timer();
        private Object timerLock = new Object();

        private Random rand = new Random();


        BlockProtocol() {
            // we're always on the lookout for new blocks.
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
            PeerHandler p = chooseNewPeer();
            byte[] fromBlockID = dataStore.getMostRecentBlock().getBlockID();

            // we will break once we've got all the headers, having dispatched download tasks asynchronously.
            // for now, if a peer suddenly gives us some invalid headers, we don't cancel previous download
            // tasks or discard earlier block headers they gave us.
            while (true) {
                HeadersFuture h = new HeadersFuture(p, fromBlockID);
                h.run();
                List<Block> receivedHeaders = h.get();

                if (receivedHeaders == null) {
                    // choose a new peer and try again
                    p = chooseNewPeer();
                } else {
                    byte[] firstPredecessorID = receivedHeaders.get(0).getPrevBlockHash();
                    // first header might be either:
                    // 1. the header following our requested 'from' block ID
                    // 2. a header following some older block we have - TODO: on our primary chain
                    if ((Arrays.equals(fromBlockID, firstPredecessorID) || dataStore.getBlock(firstPredecessorID) != null)
                            && Block.verifyChain(receivedHeaders)) {

                        futureBlockHeaders.addAll(receivedHeaders);
                        // start downloading blocks
                        downloadQueuedBlocks();

                        if (receivedHeaders.size() < MAX_HEADERS)
                            break;
                        else
                            fromBlockID = receivedHeaders.get(receivedHeaders.size() - 1).getBlockID();

                    } else {
                        // choose a new peer and try again
                        p = chooseNewPeer();
                    }
                }
            }
        }

        private PeerHandler chooseNewPeer() {
            // TODO: support excluding peers we know are unreliable/have failed us before
            synchronized (peers) {
                return peers.get(rand.nextInt(peers.size()));
            }
        }



        /**
         * Download the blocks in futureBlockHeaders in a distributed fashion, in parallel.
         */
        private void downloadQueuedBlocks() {
            // every N seconds, we take all the headers awaiting download and distribute the work evenly amongst peers,
            // up to the maximum number of blocks that we're allowed to download from a peer at once.
            // When a block is received from a peer, we immediately download the next one from the same peer,
            // so we can assume the work stays evenly distributed.

            synchronized (timerLock) {
                if (timer != null)
                    timer.cancel();
                timer = new Timer();

                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        for (int i = 0; i < MAX_SIMULTANEOUS_BLOCKS_PER_PEER; i++) {
                            // TODO: blacklist peers which haven't responded previously
                            for (PeerHandler peer : peers.values()) {
                                Block b = futureBlockHeaders.poll();
                                if (b == null) {
                                    // no more blocks to get so cancel ourselves
                                    timer.cancel();
                                    return;
                                } else {
                                    InetSocketAddress address = peer.getListenAddress();
                                    awaitedBlockHeaders.put(b, address);
                                    downloadBlock(peer, b.getBlockID());
                                    // not very efficient, but safe
                                    blocksDownloadedFromPeer.putIfAbsent(address, new AtomicInteger(0));
                                }

                            }
                        }
                    }
                }, 0, DOWNLOAD_INTERVAL_MS);
            }
        }


        private void downloadBlock(PeerHandler peer, byte[] blockID) {
            MessageProto.GetBlocksMessage gbm = MessageProto.GetBlocksMessage.newBuilder()
                    .setBlockID(ByteString.copyFrom(blockID))
                    .build();

            MessageProto.Message message = MessageProto.Message.newBuilder()
                    .setType(MessageProto.Message.Type.GET_BLOCK)
                    .setGetBlock(gbm)
                    .build();

            peer.send(message);
        }


        @Subscribe
        public void onBlockMessage(BlockMessageEvent e) {
            MessageProto.BlockMessage message = e.getBlockMessage();

            byte[] blockBytes = message.getBlockBytes().toByteArray();

            try {
                // deserialize block
                Block block = Block.deserialize(blockBytes);

                // see if we requested this block from this peer
                InetSocketAddress a = awaitedBlockHeaders.remove(block);
                if (e.getPeer().equals(a)) {
                    // if so can download another block from peer (providing there are more queued up)
                    blocksDownloadedFromPeer.get(e.getPeer()).incrementAndGet();
                    Block next = futureBlockHeaders.poll();
                    if (next != null)
                        downloadBlock(peers.get(e.getPeer()), next.getBlockID());
                }

                // check we don't already have it in our store
                if (dataStore.getBlock(block.getBlockID()) != null)
                    return;

                // verify its hash meets its target
                if (!Miner.blockHashMeetDifficulty(block))
                    return;

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
                            bus.post(new NewBlockEvent(block));
                            break;
                        case FAIL_ORPHAN:
                            // keep block in memory and try to store it once its parent has been downloaded.

                    }
                }
            } catch (IOException ioe) {
                // error in the serialised block or entries received, so discard the block.
                ioe.printStackTrace();
            } catch (SQLException sqle) {
                throw new RuntimeException("Error connecting to database :(", sqle);
            }
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

