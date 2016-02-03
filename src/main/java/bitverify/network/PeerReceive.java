package bitverify.network;

import bitverify.network.proto.MessageProto.Message;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

/**
 * Created by benellis on 02/02/2016.
 */
public class PeerReceive implements Runnable {
    private Socket socket;
    public PeerReceive(Socket s) {
        socket = s;

    }
    @Override
    public void run() {
        try {
            Message message;
            InputStream is = socket.getInputStream();
            while(true) {
                message = Message.parseDelimitedFrom(is);
                System.out.println(message.getMsg());
            }
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
