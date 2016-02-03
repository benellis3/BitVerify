package bitverify.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import bitverify.network.proto.MessageProto.Message;



/**
 * Created by benellis on 03/02/2016.
 */
public class PeerHandler {
    private BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    private Socket socket;
    // Timeout thread executor.
    private boolean hasReceived = false;
    private boolean hasResponded = false;
    private boolean shutDown = false;
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private void initialisePeerHandler(Socket s, ExecutorService es, long initialDelay,
                                              long period, TimeUnit timeUnit) {
        socket = s;
        // create new PeerSend Runnable with the right queue
        es.execute(new PeerSend());
        es.execute(new PeerReceive());
        // schedule a timer in order to ensure that
        scheduler.scheduleAtFixedRate(() -> {
            if(!hasReceived) {
                // For now we will use the empty message to denote
                // timeout. This will be a message type later.
                send("");
                // wait for a reasonable time for a response.
                try {
                    Thread.sleep(6000);
                }
                catch(InterruptedException ie) {
                    ie.printStackTrace();
                }
                if(!hasResponded) {
                    // tear down this connection
                    try {
                        // Set shutdown flag
                        shutDown = true;
                        socket.close();
                    }
                    catch(IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
            }
        }, initialDelay, period, timeUnit);
    }

    public PeerHandler(Socket s, ExecutorService es, long initialDelay,
                       long period, TimeUnit timeUnit) {
        initialisePeerHandler(s,es,initialDelay,period,timeUnit);
    }
    public PeerHandler(Socket s, ExecutorService es) {
        initialisePeerHandler(s,es,30,30,TimeUnit.MINUTES);
    }

    public void send(String msg) {
        Message message = Message.newBuilder().setMsg(msg).build();
        messageQueue.add(message);
    }

    // receive runnable
    private class PeerReceive implements Runnable {
        @Override
        public void run() {
            try {
                Message message;
                InputStream is = socket.getInputStream();
                while(!shutDown) {
                    message = Message.parseDelimitedFrom(is);
                    // is it a timeout message?
                    if(message.getMsg().equals("")) {
                        //set hasResponded Flag
                        hasResponded = true;
                    }
                    else System.out.println(message.getMsg());
                    if(!hasReceived)
                        hasReceived = true;
                }
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
    /*
     * Sending data class. Done as an inner class because it is too big to really
     * be done just in the constructor but benefits from residing in the same file as PeerHandler
     */
    private class PeerSend implements Runnable {
        @Override
        public void run() {
            try {
                OutputStream os = socket.getOutputStream();
                while(!shutDown) {
                    Message message = messageQueue.take();
                    message.writeDelimitedTo(os);
                }
            }
            catch (IOException | InterruptedException ie) {
                ie.printStackTrace();
            }
        }
    }
 }
