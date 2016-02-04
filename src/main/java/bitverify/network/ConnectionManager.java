package bitverify.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bitverify.network.proto.MessageProto.Message;

/**
 * This class is responsible for co-ordination of network functions.
 * It controls the incoming and outgoing parts of the connection.
 * @author Ben Ellis, Robert Eady
 */
public class ConnectionManager {
    private ExecutorService es;
    private List<PeerHandler> peers;
    public ConnectionManager(List<InetSocketAddress> initialPeers, int listenPort) throws IOException{
        peers = new ArrayList<>();
        es = Executors.newCachedThreadPool();
        // connect to each given peer
        for (InetSocketAddress peerAddress : initialPeers) {
            try {
                Socket s = new Socket(peerAddress.getAddress(), peerAddress.getPort());
                peers.add(new PeerHandler(s,es));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Create new runnable to listen for new connections.
        es.execute(() -> {
                    try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
                        while (true) {
                            Socket s = serverSocket.accept();
                            peers.add(new PeerHandler(s, es));
                        }
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
        );
    }
    /**
     * Send the given message to all connected peers.
     * @param message The string to send.
     */
    public void broadcast(String message) {
        for(PeerHandler peer : peers) {
            peer.send(message);
        }
    }

}
