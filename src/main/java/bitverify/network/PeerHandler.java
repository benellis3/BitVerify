package bitverify.network;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.*;

import bitverify.network.proto.MessageProto.Version;
import bitverify.network.proto.MessageProto.Message;
import bitverify.network.proto.MessageProto.NetAddress;
import bitverify.network.proto.MessageProto.Ack;
import bitverify.persistence.DataStore;
import com.squareup.otto.Bus;


/**
 * Created by benellis on 03/02/2016.
 */
public class PeerHandler {
    private BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    private Socket socket;
    private InetSocketAddress listenAddress;
    public PeerHandler(Socket s, ExecutorService es, DataStore ds, Bus bus) throws
            TimeoutException, ExecutionException, InterruptedException{
        socket = s;
        // create new PeerSend Runnable with the right queue
        // if constructed with a socket need to ensure that the version and ack messages
        // have been exchanged.
        Future<InetSocketAddress> addressFuture = es.submit(new VersionReceiveHandler());
        // wait for its completion on a separate thread
        try {
            listenAddress = addressFuture.get(3, TimeUnit.SECONDS); // blocks thread
        }
        finally {
            addressFuture.cancel(true);
        }
        es.execute(new PeerSend(messageQueue, socket));
        es.execute(new PeerReceive(socket, ds, bus));
        // send ack message to denote that we have connected
        Ack ack = Ack.newBuilder().build();
        Message msg = Message.newBuilder().setType(Message.Type.ACK).setAck(ack).build();
        send(msg);
    }
    public PeerHandler(InetSocketAddress address, ExecutorService es, DataStore ds, Bus bus) throws IOException{
        this.listenAddress = address;
        socket = new Socket(address.getAddress(), address.getPort());
        es.execute(new PeerSend(messageQueue, socket));
        es.execute(new PeerReceive(socket, ds, bus));
    }
    public InetSocketAddress getAddress() {return new InetSocketAddress(socket.getInetAddress(),
                                                socket.getPort());}
    public InetSocketAddress getLocalAddress() {return new InetSocketAddress(socket.getInetAddress(),
                                                socket.getLocalPort());}
    public int getConnectedPort(){
        return listenAddress.getPort();
    }
    public InetAddress getConnectedHost(){
        return listenAddress.getAddress();
    }
    public void send(Message msg) {
        messageQueue.add(msg); // returns immediately.
    }

    public class VersionReceiveHandler implements Callable<InetSocketAddress> {
        @Override
        public InetSocketAddress call() throws IOException {
            Message msg;
            InputStream is = socket.getInputStream();
            Version versionMsg;
            while(true) {
                msg = Message.parseDelimitedFrom(is);
                Message.Type type = msg.getType();
                if (type == Message.Type.VERSION) {
                    versionMsg = msg.getVersion();
                    break;
                }
            }
            NetAddress netAddress = versionMsg.getListenPort();
            // safe cast since one subclasses the other
            InetSocketAddress socketAddr = (InetSocketAddress) socket.getRemoteSocketAddress();
            String hostName = socketAddr.getHostName();
            InetSocketAddress ret = new InetSocketAddress(hostName,netAddress.getPort());
            return ret;
        }
    }
 }
