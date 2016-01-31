package bitverify.network;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PeerHandler {

    private Socket socket;
    private BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

    public PeerHandler(Socket socket) {
        this.socket = socket;

        // one thread to send messages in our queue to the peer
        Thread outgoing = new Thread() {
            public void run() {
                try {
                    PrintWriter w = new PrintWriter(socket.getOutputStream(), true);
                    while (true) {
                        w.println(messageQueue.take());
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
                    BufferedReader b = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    while (true) {
                        System.out.println(b.readLine());
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
        messageQueue.put(message);
    }
}
