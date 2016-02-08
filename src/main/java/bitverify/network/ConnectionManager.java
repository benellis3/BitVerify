package bitverify.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bitverify.entries.Entry;
import bitverify.persistence.DataStore;
import bitverify.network.proto.MessageProto.Peers;
import bitverify.network.proto.MessageProto.NetAddress;
import bitverify.network.proto.MessageProto.Message;
import bitverify.network.proto.MessageProto.EntryMessage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

/**
 * This class is responsible for co-ordination of network functions.
 * It controls the incoming and outgoing parts of the connection.
 * @author Ben Ellis, Robert Eady
 */
public class ConnectionManager {
    private DataStore ds;
    private ExecutorService es;
    private Bus bus;
    private List<PeerHandler> peers;
    private static final String PEER_URL = "0.0.0.0:4000"; // for testing
    
    public ConnectionManager(List<InetSocketAddress> initialPeers, int listenPort, DataStore ds, Bus bus) throws IOException{
        peers = new ArrayList<>();
        this.bus = bus;
        es = Executors.newCachedThreadPool();
        // connect to each given peer
        for (InetSocketAddress peerAddress : initialPeers) {
            try {
                Socket s = new Socket(peerAddress.getAddress(), peerAddress.getPort());
                peers.add(new PeerHandler(s,es, ds, bus));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Create new runnable to listen for new connections.
        es.execute(() -> {
                    try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
                        while (true) {
                            Socket s = serverSocket.accept();
                            peers.add(new PeerHandler(s, es, ds, bus));
                        }
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
        );
    }
    /**
     * Send the given message to all connected peers.
     * @param e The entry which must be broadcast
     * @throws IOException in the case that serialization fails
     */
    public void broadcastEntry(Entry e) throws IOException {
        EntryMessage entryMessage = EntryMessage.newBuilder()
                .setEntryBytes(ByteString.copyFrom(e.serialize()))
                .build();
        Message message = Message.newBuilder()
                .setType(Message.Type.ENTRY)
                .setEntry(entryMessage)
                .build();
        for(PeerHandler peer : peers) {
            peer.send(message);
        }
    }

    /**
     * This method is responsible for adding the peers to the list of peers.
     * it achieves this by adding a runnable to the thread pool to avoid latency.
     * @param event the PeersEvent that was raised by the PeerReceive runnable
     */
    @Subscribe
    public void onPeersEvent(PeersEvent event) {
        List<InetSocketAddress> addressList = event.getSocketAddressList();
        es.execute(() -> {
            for(InetSocketAddress addr : addressList) {
                try {
                    peers.add(new PeerHandler(addr, es, ds, bus));
                }
                catch(IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });
    }
    /**
     * Create a peers message to send to the sender of the
     * received getPeers message. This is sent to the thread pool to execute to avoid
     * significant latency.
     * @param event The GetPeersEvent
     */
    @Subscribe
    public void onGetPeersEvent(GetPeersEvent event) {
        InetSocketAddress address = event.getSocketAddress();

        // extract the InetSocketAddresses from the Peers.
        // Up to the recipient to check that the received list does not contain
        // itself.
        ArrayList<InetSocketAddress> inetList = new ArrayList<>();
        es.execute(() -> {
            PeerHandler senderHandler = null;
            for(PeerHandler p : peers) {
                InetSocketAddress addr = p.getAddress();
                if(addr.equals(address)) senderHandler = p;
                inetList.add(addr);
            }
            inetList.remove(address);
            // turn the inetList into a NetAddress list
            ArrayList<NetAddress> netAddresses = new ArrayList<>();
            for(InetSocketAddress socketAddress : inetList) {
                netAddresses.add(NetAddress.newBuilder().setPort(socketAddress.getPort()).
                        setHostName(socketAddress.getHostName()).build());
            }
            Peers peers = Peers.newBuilder().addAllAddress(netAddresses).build();
            Message msg = Message.newBuilder().setType(Message.Type.PEERS).setPeers(peers).build();
            senderHandler.send(msg); // should always be in the list since we have a connection to all nodes.
        });
    }

    /**
     * @param
     * @return
     */
    private List<InetSocketAddress> getInitialPeers() {
        URL url;
		try {
			// Set up input stream to node server
			url = new URL(PEER_URL);
	        URLConnection conn = url.openConnection();
	        conn.setConnectTimeout(10000);
	        conn.setReadTimeout(45000);
	        InputStream input = conn.getInputStream();
	        
	        // Convert input to json
	        Map<String, List<String>> returnedData = new Gson().fromJson(
                    new InputStreamReader(input, "UTF-8"),
                    new TypeToken<Map<String, List<String>>>() {
                    }.getType());
	        
	        // Get list of nodes
	        List<String> addresses = returnedData.get("nodes");
	        
	        //Convert that list into SocketAddresses
	        List<InetSocketAddress> socketAddresses = new ArrayList<InetSocketAddress>();
	        for (String address : addresses) {
	        	String[] components = address.split(":");
	        	if (components.length == 2) {
	        		// TODO checking input here so we don't crash
	        		socketAddresses.add(new InetSocketAddress(components[0], Integer.parseInt(components[1])));
	        	}
	        }
	        return socketAddresses;
	        
		} catch (MalformedURLException e) {
			//TODO actually handle these exceptions
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
		
    }

}
