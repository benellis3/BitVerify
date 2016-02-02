package bitverify.network;

import bitverify.network.proto.MessageProto.Message;

import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by benellis on 02/02/2016.
 */
public class OutgoingManager {
    private ExecutorService es = Executors.newCachedThreadPool();
    private List<Socket> socketList;
    public OutgoingManager(List<Socket> sList) {
        socketList = sList;
    }
    public void broadCast(Message msg) {
        for(Socket s : socketList) {
            es.execute(new PeerSend(msg, s));
        }
    }
    public void addPeer(Socket socket) {
        socketList.add(socket);
    }
}
