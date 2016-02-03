package bitverify.network;

import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import bitverify.network.proto.MessageProto.Message;


/**
 * Created by benellis on 03/02/2016.
 */
public class PeerHandler {
    BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    Socket socket;
    public PeerHandler(Socket s, ExecutorService es) {
        socket = s;
        // create new PeerSend Runnable with the right queue
        es.execute(new PeerSend(messageQueue, socket));
        es.execute(new PeerReceive(socket));
    }

    public void send(String msg) {
        Message message = Message.newBuilder().setMsg(msg).build();
        messageQueue.add(message);
    }
 }
