package bitverify.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import bitverify.network.proto.MessageProto.Message;
import bitverify.persistence.DataStore;
import com.squareup.otto.Bus;


/**
 * Created by benellis on 03/02/2016.
 */
public class PeerHandler {
    private BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    private Socket socket;
    public PeerHandler(Socket s, ExecutorService es, DataStore ds, Bus bus) {
        socket = s;
        // create new PeerSend Runnable with the right queue
        es.execute(new PeerSend(messageQueue, socket));
        es.execute(new PeerReceive(socket, ds, bus));
    }
    public PeerHandler(InetSocketAddress address, ExecutorService es, DataStore ds, Bus bus) throws IOException{
        socket = new Socket(address.getAddress(), address.getPort());
        es.execute(new PeerSend(messageQueue, socket));
        es.execute(new PeerReceive(socket, ds, bus));
    }
    public InetSocketAddress getAddress() {return new InetSocketAddress(socket.getInetAddress(),
                                                socket.getPort());}

    public void send(Message msg) {
        messageQueue.add(msg); // returns immediately.
    }
 }
