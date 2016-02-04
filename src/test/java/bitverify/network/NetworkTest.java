package bitverify.network;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class NetworkTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private PrintStream oldStdOut;
    private static final int NUM_CONNECTIONS = 15;
    private static final int LARGE_INITIAL_PORT = 35000;
    @Before
    public void setUpStreams() {
        oldStdOut = System.out;
        System.setOut(new PrintStream(outContent));
    }

    @After
    public void cleanUpStreams() {
        System.setOut(oldStdOut);
    }

    /*
     * This is the same test as above but more general and can be done on a larger scale.
     */
    @Test
    public void LargerNetworkTest() throws Exception {
        List<InetSocketAddress> addressList = new ArrayList<>();
        List<ConnectionManager> connectionList = new ArrayList<>();
        // create list of connectionManagers.
        for(int i = 0; i < NUM_CONNECTIONS; i++) {
                connectionList.add(new ConnectionManager(addressList, LARGE_INITIAL_PORT + i));
                addressList.add(new InetSocketAddress("localhost", LARGE_INITIAL_PORT + i));
        }
        // sleep to allow connections to be established
        Thread.sleep(100);
        String test = "TEST";
        for(ConnectionManager conn : connectionList) {
            conn.broadcast(test);
        }
        Thread.sleep(100);

        // create string of appropriate length to be the return.
        int NUM_STRINGS = NUM_CONNECTIONS * (NUM_CONNECTIONS - 1);
        String cmp = "";
        for(int i = 0; i < NUM_STRINGS - 1; i++) {
            cmp += test + System.lineSeparator();
        }
        cmp += test;
        assertEquals(cmp, outContent.toString().trim());
    }
}

