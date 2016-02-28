package bitverify.network;

import bitverify.LogEvent;
import bitverify.LogEventSource;
import bitverify.block.Block;
import bitverify.crypto.Asymmetric;
import bitverify.crypto.Hash;
import bitverify.crypto.Identity;
import bitverify.crypto.KeyDecodingException;
import bitverify.entries.Entry;
import bitverify.entries.EntryTest;
import bitverify.mining.Miner;
import bitverify.network.proto.MessageProto;
import bitverify.persistence.DataStore;
import bitverify.persistence.DatabaseIterator;
import bitverify.persistence.DatabaseStore;
import bitverify.persistence.InsertBlockResult;
import com.j256.ormlite.logger.LocalLog;
import com.j256.ormlite.stmt.query.In;
import com.j256.ormlite.support.ConnectionSource;
import com.squareup.otto.Bus;
import com.squareup.otto.DeadEvent;
import com.squareup.otto.Subscribe;
import com.squareup.otto.ThreadEnforcer;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.internal.runners.statements.Fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import static org.junit.Assert.*;

public class NetworkTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private PrintStream oldStdOut;
    // database not ready yet.
    //private DataStore dataStore;
    private static final int NUM_CONNECTIONS = 4;
    private static final int LARGE_INITIAL_PORT = 32500;
    private static final int DISCOVERY_INITIAL_PORT = 11000;
    private static final int TARGET = 5;
    private static final int NONCE = 3;

    //@Before
    public void setUpStreams() {
        oldStdOut = System.out;
        System.setOut(new PrintStream(outContent));
        // Set up event bus
        //dataStore = new DataStore();
    }

    //@After
    public void cleanUpStreams() {
        System.setOut(oldStdOut);
    }

    @Test
    public void PeerHandlerEqualsTest() throws Exception {
        ConnectionManager conn = new ConnectionManager(new ArrayList<>(), LARGE_INITIAL_PORT, null,
                new Bus(ThreadEnforcer.ANY));
        ConnectionManager conn1 = new ConnectionManager(new ArrayList<InetSocketAddress>() {{
            add(new InetSocketAddress("localhost", LARGE_INITIAL_PORT));
        }}, LARGE_INITIAL_PORT + 1,
                null, new Bus(ThreadEnforcer.ANY));
        Thread.sleep(500);
        boolean success = false;
        Collection<PeerHandler> connPeers = conn.peers();
        PeerHandler ph = new PeerHandler(null, null, null, null, LARGE_INITIAL_PORT + 2);
        try {
            ph.establishConnection(new InetSocketAddress("localhost", LARGE_INITIAL_PORT + 1));
        } catch (NullPointerException e) {
            // as expected since the executor service was not provided
            // but we've done enough to set the listen address for testing equals()
        }
        for (PeerHandler p : connPeers) {
            try {
                if (p.equals(ph))
                    success = true;
                break;
            } catch (Exception e) {
                fail("Timeout");
            }
        }
        assertTrue(success);
    }

    /**
     * This test tests the deserialization of blocks and their sending around the network.
     * It is similar to LargerNetworkTest() below, but with Block instead of Entries.
     */
    @Test
    public void BlockNetworkTest() throws Exception {
        System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");
        List<InetSocketAddress> addressList = new CopyOnWriteArrayList<>();
        List<ConnectionManager> connectionList = new ArrayList<>();
        // create list of connectionManagers.
        for (int i = 0; i < NUM_CONNECTIONS; i++) {
            connectionList.add(new ConnectionManager(new ArrayList<>(addressList), LARGE_INITIAL_PORT + i,
                    new DatabaseStore("jdbc:h2:mem:blockNetworkTest" + i), new Bus(ThreadEnforcer.ANY)));
            addressList.add(new InetSocketAddress("localhost", LARGE_INITIAL_PORT + i));
        }
        // sleep to allow connections to be established
        Thread.sleep(300);
        // create a simple Block
        Entry e1 = EntryTest.generateEntry1();
        Entry e2 = EntryTest.generateEntry2();
        Block testBlock = new Block(Block.getGenesisBlock(), TARGET, NONCE, new ArrayList<Entry>() {{
            add(e1);
            add(e2);
        }});
        for (ConnectionManager conn : connectionList) {
            conn.broadcastBlock(testBlock);
        }
        Thread.sleep(600);
        // ConnectionManager should print the nonce
        // we should only get the num connections as we are attempting to insert duplicates
        int NUM_STRINGS = NUM_CONNECTIONS;
        String cmp = "";
        for (int i = 0; i < NUM_STRINGS - 1; i++) {
            cmp += NONCE + System.lineSeparator();
        }
        cmp += NONCE;
        assertEquals(cmp, outContent.toString().trim());
    }

    /**
     * This test tests the basic sending functionality of the ConnectionManager.
     * This allows a basic Entry message to be sent around the network. It does
     * <b> NOT </b> test the peer discovery part of the program.
     */
    @Test
    public void LargerNetworkTest() throws Exception {
        // suppress datastore logging
        System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");

        List<InetSocketAddress> addressList = new CopyOnWriteArrayList<>();
        List<ConnectionManager> connectionList = new ArrayList<>();
        // create list of connectionManagers.
        for (int i = 0; i < NUM_CONNECTIONS; i++) {
            connectionList.add(new ConnectionManager(new ArrayList<>(addressList), LARGE_INITIAL_PORT + i,
                    new DatabaseStore("jdbc:h2:mem:blockNetworkTest" + i),
                    new Bus(ThreadEnforcer.ANY)));
            addressList.add(new InetSocketAddress("localhost", LARGE_INITIAL_PORT + i));
        }
        // sleep to allow connections to be established
        Thread.sleep(500);
        // create a simple entry
        Entry e = EntryTest.generateEntry1();
        for (ConnectionManager conn : connectionList) {
            conn.broadcastEntry(e);
        }
        Thread.sleep(500);

        // create string of appropriate length to be the return.
        int NUM_STRINGS = NUM_CONNECTIONS * (NUM_CONNECTIONS - 1);
        String cmp = "";
        for (int i = 0; i < NUM_STRINGS - 1; i++) {
            cmp += e.getDocDescription() + System.lineSeparator();
        }
        cmp += e.getDocDescription();
        assertEquals(cmp, outContent.toString().trim());
    }

    /**
     * This tests the ability of a pre-existing network to discover a new node.*
     */
    @Test
    public void peerDiscoveryTest() throws Exception {
        // create initial network
        List<InetSocketAddress> addressList = new CopyOnWriteArrayList<>();
        List<ConnectionManager> connectionList = new ArrayList<>();
        // create list of connectionManagers.
        int cmp = 0;
        for (int i = 0; i < NUM_CONNECTIONS; i++) {
            connectionList.add(new ConnectionManager(addressList, DISCOVERY_INITIAL_PORT + i, null,
                    new Bus(ThreadEnforcer.ANY)));
            InetSocketAddress sock = new InetSocketAddress("localhost", DISCOVERY_INITIAL_PORT + i);
            addressList.add(sock);
            cmp++;
        }
        // Allow connections to be established
        Thread.sleep(200);
        // Create a new Connection manager
        ConnectionManager conn = new ConnectionManager(new ArrayList<InetSocketAddress>() {{
            add(new InetSocketAddress("localhost", DISCOVERY_INITIAL_PORT));
        }}, DISCOVERY_INITIAL_PORT + NUM_CONNECTIONS,
                null, new Bus(ThreadEnforcer.ANY));
        Thread.sleep(1000);
        conn.printPeers();
        assertEquals(cmp, conn.peers().size());
    }

    @Test
    public void staggeredNetworkTest() throws Exception {
        // suppress datastore logging
        System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");

        InetSocketAddress addr1 = new InetSocketAddress("localhost", LARGE_INITIAL_PORT);
        MiniNode n1 = new MiniNode("jdbc:h2:mem:networkTest1", "Node1", LARGE_INITIAL_PORT, new ArrayList<>());
        n1.startMiner();
        // let it mine for a bit
        Thread.sleep(2000);
        n1.miner.stopMining();
        System.out.println("==================================================== STOPPED MINING ON NODE 1 ====================================================");

        // now make another peer and see if they synchronise
        MiniNode n2 = new MiniNode("jdbc:h2:mem:networkTest2", "Node2", LARGE_INITIAL_PORT+1, new ArrayList<InetSocketAddress>() {{
            add(addr1);
        }});
        // n2.startMiner();

        Thread.sleep(10000);

        assertEquals(n1.dataStore.getBlocksCount(), n2.dataStore.getBlocksCount());
    }

    @Test
    public void forkedNetworkTest() throws Exception {
        // suppress datastore logging
        System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");

        // machine 1 builds a blockchain while not connected to anyone else.
        MiniNode nA = new MiniNode("jdbc:h2:mem:networkTestA", "nA", LARGE_INITIAL_PORT - 1, new ArrayList<>());
        nA.startMiner();
        // let it mine, while adding entries
        Thread.sleep(5000);
        while (nA.dataStore.getActiveBlocksCount() < 2) {
            nA.addPredefinedEntry();
            Thread.sleep(1000);
        }

        // Thread.sleep(2 * amountOfMining);

        nA.miner.stopMining();
        System.out.println("node A has " + nA.dataStore.getActiveBlocksCount() + " blocks on its active chain");
        System.out.println("node A has " + nA.dataStore.getBlocksCount() + " blocks in total");

        // now machines 2 build blockchains in sync with each other.
        MiniNode[] n2 = new MiniNode[3];
        List<InetSocketAddress> addressList = new ArrayList<>();
        for (int i = 0; i < n2.length; i++) {
            n2[i] = new MiniNode("jdbc:h2:mem:networkTest" + i, "n" + i, LARGE_INITIAL_PORT + i, new ArrayList<>(addressList));
            n2[i].startMiner();
            addressList.add(new InetSocketAddress("localhost", LARGE_INITIAL_PORT + i));
        }

        // let them mine, while adding entries
        Thread.sleep(5000);
        while (n2[0].dataStore.getActiveBlocksCount() < 30) {
            n2[ThreadLocalRandom.current().nextInt(3)].addPredefinedEntry();
            Thread.sleep(1000);
        }

        //Thread.sleep(amountOfMining);

        for (int i = 0; i < n2.length; i++) {
            n2[i].miner.stopMining();
        }
        Thread.sleep(2000);

        for (int i = 0; i < n2.length; i++) {
            System.out.println("node " + i + " has " + n2[i].dataStore.getActiveBlocksCount() + " blocks on its active chain");
            System.out.println("node " + i + " has " + n2[i].dataStore.getBlocksCount() + " blocks in total");
        }

        // now we connect a machine with the original datastore and try to sync everyone up.
        MiniNode nB = new MiniNode("jdbc:h2:mem:networkTestA", "nB", LARGE_INITIAL_PORT + 10, new ArrayList<>(addressList));

        // let block transfer happen
        Thread.sleep(20000);

        System.out.println("node B has " + nB.dataStore.getActiveBlocksCount() + " blocks on its active chain");
        System.out.println("node B has " + nB.dataStore.getBlocksCount() + " blocks in total");
        for (int i = 0; i < n2.length; i++) {
            System.out.println("node " + i + " has " + n2[i].dataStore.getActiveBlocksCount() + " blocks on its active chain");
            System.out.println("node " + i + " has " + n2[i].dataStore.getBlocksCount() + " blocks in total");
        }

        System.out.println(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()));
        for (int i = 0; i < n2.length; i++) {
            assertEquals(nB.dataStore.getActiveBlocksCount(), n2[i].dataStore.getActiveBlocksCount());
            List<byte[]> chain1 = nB.dataStore.getActiveBlocksSample(20);
            List<byte[]> chain2 = n2[i].dataStore.getActiveBlocksSample(20);
            assertEquals(chain1.size(), chain2.size());
            for (int j = 0; j < chain1.size(); j++) {
                assertEquals(new BlockID(chain1.get(j)), new BlockID(chain2.get(j)));
            }
        }
        System.out.println(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()));

    }


    @Test
    public void storingEntriesTest() throws Exception {
        // suppress datastore logging
        // System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");

        // machine 1 builds a blockchain while not connected to anyone else.
        MiniNode nA = new MiniNode("jdbc:h2:mem:networkTestA", "nA", LARGE_INITIAL_PORT, new ArrayList<>());
        nA.startMiner();
        // let it mine, while adding entries
        Thread.sleep(5000);
        while (nA.dataStore.getActiveBlocksCount() < 10) {
            nA.addPredefinedEntry();
            Thread.sleep(1000);
        }

        nA.miner.stopMining();
        System.out.println("node A has " + nA.dataStore.getActiveBlocksCount() + " blocks on its active chain");
        System.out.println("node A has " + nA.dataStore.getBlocksCount() + " blocks in total");

        // now machine B syncs with this chain
        MiniNode nB = new MiniNode("jdbc:h2:mem:networkTestB", "nB", LARGE_INITIAL_PORT + 10, new ArrayList<InetSocketAddress>() {{ add(new InetSocketAddress("localhost", LARGE_INITIAL_PORT)); }});

        // let block transfer happen
        Thread.sleep(10000);

        System.out.println("node B has " + nB.dataStore.getActiveBlocksCount() + " blocks on its active chain");
        System.out.println("node B has " + nB.dataStore.getBlocksCount() + " blocks in total");

        assertEquals(nB.dataStore.getActiveBlocksCount(), nA.dataStore.getActiveBlocksCount());

        try (DatabaseIterator<Block> chainA = nA.dataStore.getAllBlocks();
             DatabaseIterator<Block> chainB = nB.dataStore.getAllBlocks()) {
            while (chainA.moveNext() & chainB.moveNext()) {
                // same block
                assertEquals(chainA.current().getTimeStamp(), chainB.current().getTimeStamp());
                // same number of entries in each block
                assertEquals(chainA.current().getEntriesList().size(), chainB.current().getEntriesList().size());
            }
            // must be same length
            assertEquals(null, chainA.current());
            assertEquals(null, chainB.current());
        }

        System.out.println(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()));
    }


    /**
     * A pint sized node for testing purposes.
     */
    private class MiniNode {
        Miner miner;
        DataStore dataStore;
        Bus bus;
        ConnectionManager man;
        Identity identity;

        private Thread t;

        public MiniNode(String dsString, String nodeName, int port, List<InetSocketAddress> initialPeers) throws IOException, SQLException {

            bus = new Bus(ThreadEnforcer.ANY);
            dataStore = new DatabaseStore(dsString);

            bus.register(new Object() {
                SimpleDateFormat d = new SimpleDateFormat("HH:mm:ss.SSS");
                @Subscribe
                public void onLogEvent(LogEvent l) {
                    if (l.getLevel().intValue() < Level.FINER.intValue()) return;
                    System.out.println(nodeName + ": " + d.format(new Date(l.getTimeStamp())) + ": log event from " + l.getSource().toString() + ", level " + l.getLevel() + ": " + l.getMessage());
                }

                @Subscribe
                public void onDeadEvent(DeadEvent e) {
                    System.out.println(nodeName + ": " + d.format(new Date()) + ": dead event: " + e.event.getClass().toString() + " from: " + e.source);
                }
            });

            man = new ConnectionManager(initialPeers, port, dataStore, bus);

            bus.register(new Object() {
                @Subscribe
                public void onBlockFoundEvent(Miner.BlockFoundEvent e) throws SQLException {
                    final Block block = e.getBlock();
                    man.broadcastBlock(block);
                }

            });

            bus.post(new LogEvent("Generating new key identity...", LogEventSource.CRYPTO, Level.INFO));
            AsymmetricCipherKeyPair keyPair = Asymmetric.generateNewKeyPair();
            identity = new Identity("default", keyPair);

            miner = new Miner(bus, dataStore, 2, 250, 2);
            t = new Thread(miner);
        }

        void startMiner() {
            t.start();
        }

        private void addPredefinedEntry() {

            byte[] hash = Hash.hashString("inimitation of some file asdasd");
            String fileName = "The Bible......";
            String fileDownload = "somewhere for sure";
            String fileDescription = "welllllllll";
            String fileGeo = "Israel... or stuff";

            Entry entry;
            try {
                entry = new Entry(identity.getKeyPair(), hash, fileDownload, fileName,
                        fileDescription, fileGeo, System.currentTimeMillis(), new String[0]);
                dataStore.insertEntry(entry);
                bus.post(new NewEntryEvent(entry));
            } catch (KeyDecodingException | IOException | SQLException e) {
                System.out.println("Oops. Error generating the predefined entry...");
                return;
            }
        }

    }
}

