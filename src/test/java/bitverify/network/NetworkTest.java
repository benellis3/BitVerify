package bitverify.network;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
        ConnectionManager instance1 = new ConnectionManager(new ArrayList<String>(), 35000, 35001);
        Thread.sleep(100);
        ConnectionManager instance2 = new ConnectionManager(new ArrayList<String>() {{ add("localhost"); }}, 35001, 35000);
        Thread.sleep(100);
        String test = "Test Yay";
        instance1.broadcast(test);
        Thread.sleep(100);
        assertEquals(test.trim(), outContent.toString().trim());

    }
}
