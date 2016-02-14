package bitverify.network;

import bitverify.entries.Entry;
import bitverify.entries.EntryTest;
import bitverify.persistence.DataStore;
import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.internal.runners.statements.Fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class NetworkTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private PrintStream oldStdOut;
    // database not ready yet.
    //private DataStore dataStore;
    private static final int NUM_CONNECTIONS = 3;
    private static final int LARGE_INITIAL_PORT = 32500;
    private static final int DISCOVERY_INITIAL_PORT = 11000;
    @Before
    public void setUpStreams() {
        oldStdOut = System.out;
        System.setOut(new PrintStream(outContent));
        // Set up event bus
        //dataStore = new DataStore();
    }

    @After
    public void cleanUpStreams() {
        System.setOut(oldStdOut);
    }

    @Test
    public void PeerHandlerEqualsTest() throws Exception{
        ConnectionManager conn = new ConnectionManager(new ArrayList<>(), LARGE_INITIAL_PORT, null,
                new Bus(ThreadEnforcer.ANY));
        ConnectionManager conn1 = new ConnectionManager(new ArrayList<InetSocketAddress>() {{
            add(new InetSocketAddress("localhost", LARGE_INITIAL_PORT));}},LARGE_INITIAL_PORT + 1,
                null, new Bus(ThreadEnforcer.ANY));
        Thread.sleep(300);
        boolean success = false;
        Collection<PeerHandler> connPeers = conn.peers();
        for(PeerHandler p : connPeers) {
            try {
                if (p.equals(new PeerHandler(new InetSocketAddress("localhost", LARGE_INITIAL_PORT + 1),
                        LARGE_INITIAL_PORT, Executors.newFixedThreadPool(5), null, new Bus(ThreadEnforcer.ANY))))
                    success = true; break;
            }
            catch(Exception e) {
                fail("Timeout");
            }
        }
        assertTrue(success);
    }
    /**
     * This test tests the basic sending functionality of the ConnectionManager.
     * This allows a basic Entry message to be sent around the network. It does
     * <b> NOT </b> test the peer discovery part of the program.
     */
    @Test
    public void LargerNetworkTest() throws Exception {
        List<InetSocketAddress> addressList = new CopyOnWriteArrayList<>();
        List<ConnectionManager> connectionList = new ArrayList<>();
        // create list of connectionManagers.
        for(int i = 0; i < NUM_CONNECTIONS; i++) {
                connectionList.add(new ConnectionManager(new ArrayList<>(addressList), LARGE_INITIAL_PORT + i, null,
                        new Bus(ThreadEnforcer.ANY)));
                addressList.add(new InetSocketAddress("localhost", LARGE_INITIAL_PORT + i));
        }
        // sleep to allow connections to be established
        Thread.sleep(300);
        // create a simple entry
        Entry e = EntryTest.generateEntry1();
        for(ConnectionManager conn : connectionList) {
            conn.broadcastEntry(e);
        }
        Thread.sleep(600);

        // create string of appropriate length to be the return.
        int NUM_STRINGS = NUM_CONNECTIONS * (NUM_CONNECTIONS - 1);
        String cmp = "";
        for(int i = 0; i < NUM_STRINGS - 1; i++) {
            cmp += e.getMetadata().getDocDescription() + System.lineSeparator();
        }
        cmp += e.getMetadata().getDocDescription();
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
        for(int i = 0; i < NUM_CONNECTIONS; i++) {
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
            add(new InetSocketAddress("localhost", DISCOVERY_INITIAL_PORT));}}, DISCOVERY_INITIAL_PORT + NUM_CONNECTIONS,
                null, new Bus(ThreadEnforcer.ANY));
        Thread.sleep(1000);
        conn.printPeers();
        assertEquals(cmp, conn.getNumPeers());
    }
}

