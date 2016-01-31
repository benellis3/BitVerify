package bitverify.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class ConnectionManager {

    List<PeerHandler> peers = new ArrayList<>();

    public ConnectionManager(List<InetSocketAddress> initialPeers, int listenPort) {

        // connect to each given peer
        for (InetSocketAddress peerAddress : initialPeers) {
            try {
                Socket s = new Socket(peerAddress.getAddress(), peerAddress.getPort());
                peers.add(new PeerHandler(s));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // start listening for connections from other peers
        Thread listener = new Thread() {
            public void run() {
                try (ServerSocket socket = new ServerSocket(listenPort)) {
                    while (true) {
                        PeerHandler p = new PeerHandler(socket.accept());
                        peers.add(p);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        listener.setDaemon(true);
        listener.start();
    }

    /**
     * Send the given message to all connected peers.
     * @param message
     */
    public void broadcast(String message) {
        for (PeerHandler peer : peers) {
            try {
                peer.send(message);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
