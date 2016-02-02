package bitverify.network;

import bitverify.network.proto.MessageProto.Message;

import java.io.IOException;
import java.net.Socket;

/**
 * Sends a single message over a Socket
 */
public class PeerSend implements Runnable {
    private Message message;
    private Socket s;

    public PeerSend(Message msg, Socket socket) {
        s = socket;
        message = msg;
    }
    @Override
    public void run() {
        try {
            message.writeDelimitedTo(s.getOutputStream());
        }
        catch (IOException ie) {
            ie.printStackTrace();
        }
    }
}
