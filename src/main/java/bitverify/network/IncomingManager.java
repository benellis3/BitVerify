package bitverify.network;


import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Processes requests by other nodes in the network
 */
public class IncomingManager implements Runnable {
    private ConnectionManager connectionManager;
    private final int port;
    private final ExecutorService es;
    public IncomingManager(int port, ConnectionManager conn) throws IOException {
        connectionManager = conn;
        this.port = port;
        es = Executors.newCachedThreadPool();
    }
    @Override
    public void run() {
        try(ServerSocket serverSocket = new ServerSocket(port)) {
            while(true) {
                Socket s = serverSocket.accept();
                connectionManager.addPeer(s);
                es.execute(new PeerReceive(s));
            }
        }
        catch(IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
