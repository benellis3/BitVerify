package bitverify.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import bitverify.entries.Entry;
import bitverify.network.proto.MessageProto;
import bitverify.persistence.DataStore;
import bitverify.network.proto.MessageProto.Peers;
import bitverify.network.proto.MessageProto.NetAddress;
import bitverify.network.proto.MessageProto.Message;
import bitverify.network.proto.MessageProto.EntryMessage;
import bitverify.network.proto.MessageProto.GetPeers;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.google.protobuf.ByteString;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;


/**
 * This class is responsible for co-ordination of network functions.
 * It controls the incoming and outgoing parts of the connection.
 * @author Ben Ellis, Robert Eady
 */
public class ConnectionManager {
    private static final Logger LOGGER = Logger.getLogger(ConnectionManager.class.getName());
    private DataStore ds;
    private ExecutorService es;
    private Bus bus;
    private Collection<PeerHandler> peers;
    private static final String PEER_URL = "http://52.48.86.95:4000/nodes"; // for testing

    // underscore courtesy of Laszlo Makk :p
    private void _initialise(List<InetSocketAddress> initialPeers, int listenPort, DataStore ds, Bus bus) throws IOException{
        peers = ConcurrentHashMap.newKeySet();
        this.bus = bus;
        bus.register(this);
        es = Executors.newCachedThreadPool();
        // Create new runnable to listen for new connections.
        ServerSocket serverSocket;
        serverSocket = new ServerSocket(listenPort);

        es.execute(() -> {
                    try {
                        while (true) {
                            Socket s = serverSocket.accept();
                            // separate thread since it blocks waiting for messages.
                            es.execute(() -> {
                                try {
                                    PeerHandler p = new PeerHandler(s, es, ds, bus);
                                    peers.add(p);
                                }
                                catch(TimeoutException time) {
                                    // this means no response was received
                                    System.out.println("No response received within the time limit");
                                }
                                catch(InterruptedException | ExecutionException ie) {
                                    ie.printStackTrace();
                                }
                            });
                        }
                    }
                    catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
        );
        // connect to each given peer - the PeerHandler Class ensures
        // that the VERSION - ACK protocol is obeyed. Creation is done
        // in a separate thread as the PeerHandler constructor will
        // block.
        es.execute(() -> {
            for (InetSocketAddress peerAddress : initialPeers) {
                es.execute(()-> {
                    try {
                        PeerHandler p = new PeerHandler(peerAddress,listenPort,es, ds, bus);
                        peers.add(p);
                    }
                    catch(TimeoutException toe) {
                        System.out.println("Failed to contact an initial peer");
                    }
                    catch(InterruptedException | ExecutionException | IOException ie) {
                        ie.printStackTrace();
                    }
                });
            }
            PeerProtocol peerProtocol = new PeerProtocol(peers, es, bus, ds, listenPort);
            peerProtocol.send();
        });
    }
    public ConnectionManager(int listenPort, DataStore ds, Bus bus) throws IOException{
    	List<InetSocketAddress> initialPeers = getInitialPeers(); // it works!
        _initialise(initialPeers, listenPort, ds, bus);
    }
    // This is used primarily for testing
    public ConnectionManager(List<InetSocketAddress> initialPeers,int listenPort, DataStore ds, Bus bus)
            throws IOException{
        _initialise(initialPeers, listenPort, ds, bus);
    }
    /**
     * Peers are contacted in the constructor, but this method will send a GETPEERS message
     * to all of the current peers that the class knows about.
     */
    public void getPeers() {
        for(PeerHandler p : peers) {
            GetPeers getPeers = GetPeers.newBuilder().build();
            Message message = Message.newBuilder().setType(Message.Type.GETPEERS)
                    .setGetPeers(getPeers).build();
            p.send(message);
        }
    }
    public int getNumPeers() {return peers.size();}
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
    public void printPeers() {
        for(PeerHandler p : peers) {
            InetSocketAddress address = p.getAddress();
            System.out.println("Connected to: " + address.getHostName() + " " + address.getPort());
        }
    }
    protected Collection<PeerHandler> peers(){return peers;}
    /**
     * For testing only - not relevant to actual version
     */
    @Subscribe
    public void onNewEntryEvent(NewEntryEvent nee) {
        // prints the document description
        System.out.println(nee.getNewEntry().getMetadata().getDocDescription());
    }
    /**
     * Create a peers message to send to the sender of the
     * received getPeers message. This is sent to the thread pool to execute to avoid
     * significant latency.
     * @param event The GetPeersEvent
     */
    @Subscribe
    public void onGetPeersEvent(GetPeersEvent event) {
        // extract the InetSocketAddresses from the Peers.
        es.execute(() -> {
            InetSocketAddress addressFrom = event.getSocketAddress();
            List<NetAddress> peerAddresses = new ArrayList<>();
            PeerHandler sender = null;
            for(PeerHandler p : peers) {
                InetSocketAddress peerListenAddress = p.getListenAddress();
                if (p.getListenAddress().equals(addressFrom)) {
                    sender = p;
                } else {
                    peerAddresses.add(NetAddress.newBuilder()
                            .setHostName(peerListenAddress.getHostName())
                            .setPort(peerListenAddress.getPort())
                            .build());
                }
            }
            Peers peer = Peers.newBuilder().addAllAddress(peerAddresses).build();
            Message msg = Message.newBuilder().setType(Message.Type.PEERS).setPeers(peer).build();
            sender.send(msg);
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
