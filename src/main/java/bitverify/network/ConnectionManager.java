package bitverify.network;

import java.io.IOException;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bitverify.network.proto.MessageProto.Message;

/**
 * This class is responsible for co-ordination of network functions.
 * It controls the incoming and outgoing parts of the connection.
 * @author Ben Ellis, Robert Eady
 */
public class ConnectionManager {
    private OutgoingManager outgoing;
    private IncomingManager in;
    private ExecutorService es;
    public ConnectionManager(List<InetSocketAddress> initialPeers, int listenPort) throws IOException{
        List<Socket> socketList = new ArrayList<>();
        // connect to each given peer
        for (InetSocketAddress peerAddress : initialPeers) {
            try {
                Socket s = new Socket(peerAddress.getAddress(), peerAddress.getPort());
                socketList.add(s);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        outgoing = new OutgoingManager(socketList);
        in = new IncomingManager(listenPort, this);
        es = Executors.newSingleThreadExecutor();
        es.execute(in);
    }
    public void addPeer(Socket s) {outgoing.addPeer(s);}
    /**
     * Send the given message to all connected peers.
     * @param message
     */
    public void broadcast(String message) {
        outgoing.broadCast(Message.newBuilder().setMsg(message).build());
    }

}
