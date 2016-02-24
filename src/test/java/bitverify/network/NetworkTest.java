package bitverify.network;

import bitverify.LogEvent;
import bitverify.block.Block;
import bitverify.entries.Entry;
import bitverify.entries.EntryTest;
import bitverify.mining.Miner;
import bitverify.network.proto.MessageProto;
import bitverify.persistence.DataStore;
import bitverify.persistence.DatabaseStore;
import com.j256.ormlite.logger.LocalLog;
import com.j256.ormlite.stmt.query.In;
import com.j256.ormlite.support.ConnectionSource;
import com.squareup.otto.Bus;
import com.squareup.otto.DeadEvent;
import com.squareup.otto.Subscribe;
import com.squareup.otto.ThreadEnforcer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.internal.runners.statements.Fail;

import java.io.ByteArrayOutputStream;
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
        PeerHandler ph = new PeerHandler(null, null, null, null, LARGE_INITIAL_PORT + 2, null);
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
        Bus bus1 = new Bus(ThreadEnforcer.ANY);
        DatabaseStore ds1 = new DatabaseStore("jdbc:h2:mem:networkTest1");

        bus1.register(new Object() {
            @Subscribe
            public void onLogEvent(LogEvent l) {
                if (l.getLevel().intValue() < Level.FINER.intValue()) return;
                SimpleDateFormat d = new SimpleDateFormat("HH:mm:ss");
                System.out.println("NODE 1: " + d.format(new Date(l.getTimeStamp())) + ": log event from " + l.getSource().toString() + ", level " + l.getLevel() + ": " + l.getMessage());
            }

            @Subscribe
            public void onDeadEvent(DeadEvent e) {
                SimpleDateFormat d = new SimpleDateFormat("HH:mm:ss");
                System.out.println("NODE 1: " + d.format(new Date()) + ": dead event: " + e.event.getClass().toString() + " from: " + e.source);
            }
        });

        ConnectionManager man1 = new ConnectionManager(new ArrayList<>(), LARGE_INITIAL_PORT, ds1, bus1);

        bus1.register(new Object() {
            @Subscribe
            public void onBlockFoundEvent(Miner.BlockFoundEvent e) throws SQLException {
                final Block block = e.getBlock();
                ds1.insertBlock(block);
                man1.broadcastBlock(block);
            }

        });

        Miner miner1 = new Miner(bus1, ds1, 2, 1000, 2);
        Thread t = new Thread(miner1);
        t.start();
        // let it mine for a bit
        Thread.sleep(2000);
        // miner1.stopMining();
        // System.out.println("==================================================== STOPPED MINING ON NODE 1 ====================================================");

        // now make another peer and see if they synchronise
        Bus bus2 = new Bus(ThreadEnforcer.ANY);
        bus2.register(new Object() {
            @Subscribe
            public void onLogEvent(LogEvent l) {
                if (l.getLevel().intValue() < Level.FINER.intValue()) return;
                SimpleDateFormat d = new SimpleDateFormat("HH:mm:ss");
                System.out.println("NODE 2: " + d.format(new Date(l.getTimeStamp())) + ": log event from " + l.getSource().toString() + ", level " + l.getLevel() + ": " + l.getMessage());
            }

            @Subscribe
            public void onDeadEvent(DeadEvent e) {
                SimpleDateFormat d = new SimpleDateFormat("HH:mm:ss");
                System.out.println("NODE 2: " + d.format(new Date()) + ": dead event: " + e.event.getClass().toString() + " from: " + e.source);
            }
        });
        DatabaseStore ds2 = new DatabaseStore("jdbc:h2:mem:networkTest2");
        ConnectionManager man2 = new ConnectionManager(new ArrayList<InetSocketAddress>() {{
            add(addr1);
        }}, LARGE_INITIAL_PORT + 1, ds2, bus2);


        bus2.register(new Object() {
            @Subscribe
            public void onBlockFoundEvent(Miner.BlockFoundEvent e) throws SQLException {
                final Block block = e.getBlock();
                ds2.insertBlock(block);
                man2.broadcastBlock(block);
            }

        });

        Miner miner2 = new Miner(bus2, ds2, 2, 2000, 2);
        Thread t2 = new Thread(miner2);
        t2.start();

        Thread.sleep(30000);

        assertEquals(ds1.getBlocksCount(), ds2.getBlocksCount());
    }
}

