package bitverify.network;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class NetworkTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private PrintStream oldStdOut;

    @Before
    public void setUpStreams() {
        oldStdOut = System.out;
        System.setOut(new PrintStream(outContent));
    }

    @After
    public void cleanUpStreams() {
        System.setOut(oldStdOut);
    }

    @Test
    public void NetworkTest() throws Exception {
        ConnectionManager instance1 = new ConnectionManager(new ArrayList<>(), 35000);
        InetSocketAddress instance1Address = new InetSocketAddress("localhost", 35000);
        ConnectionManager instance2 = new ConnectionManager(new ArrayList<InetSocketAddress>() {
            { add(instance1Address); }}, 35001);
        InetSocketAddress instance2Address = new InetSocketAddress("localhost", 35001);
        ConnectionManager instance3 = new ConnectionManager(new ArrayList<InetSocketAddress>() {
            { add(instance1Address); add(instance2Address); }}, 35002);

        Thread.sleep(100);
        String test = "Test Yay";
        instance1.broadcast(test);
        instance2.broadcast(test);
        Thread.sleep(100);
        assertEquals(test + System.lineSeparator() + test + System.lineSeparator() + test + System.lineSeparator()
                + test, outContent.toString().trim());

    }
}
