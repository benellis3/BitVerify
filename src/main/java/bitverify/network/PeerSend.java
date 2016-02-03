package bitverify.network;

import bitverify.network.proto.MessageProto.Message;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;

/**
 * Sends a single message over a Socket
 */
public class PeerSend implements Runnable {
    private BlockingQueue<Message> messageQueue;
    private Socket s;
    public PeerSend(BlockingQueue<Message> q,  Socket socket) {
        s = socket;
        messageQueue = q;
    }
    @Override
    public void run() {
        try {
            OutputStream os = s.getOutputStream();
            while(true) {
                Message message = messageQueue.take();
                message.writeDelimitedTo(os);
            }
        }
        catch (IOException | InterruptedException ie) {
            ie.printStackTrace();
        }
    }
}
