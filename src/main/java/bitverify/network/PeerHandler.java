package bitverify.network;

import bitverify.network.proto.MessageProto.Message;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PeerHandler {

    private Socket socket;
    private BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    public PeerHandler(Socket socket) {
        this.socket = socket;

        // one thread to send messages in our queue to the peer
        Thread outgoing = new Thread() {
            public void run() {
                try {
                    OutputStream bos = socket.getOutputStream();
                    while (true) {
                        Message msg = messageQueue.take();
                        msg.writeDelimitedTo(bos);
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };

        // another thread to print messages received from the peer
        Thread incoming = new Thread() {
            public void run() {
                try {
                    Message message;
                    InputStream is = socket.getInputStream();
                    while (true) {
                        message = Message.parseDelimitedFrom(is);
                        System.out.println(message.getMsg());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        incoming.setDaemon(true);
        incoming.start();
        outgoing.setDaemon(true);
        outgoing.start();


    }

    /**
     * Add a message to the outgoing queue to be sent to this peer.
     * @param message
     * @throws InterruptedException
     */
    public void send(String message) throws InterruptedException {
        Message msg = Message.newBuilder().setMsg(message).build();
        messageQueue.put(msg);
    }
}
