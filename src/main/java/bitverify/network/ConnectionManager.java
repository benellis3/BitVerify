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
import com.sun.istack.internal.NotNull;


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
    private static int listenPort;

    private void _initialise(List<InetSocketAddress> initialPeers, int listenPort, DataStore ds, Bus bus) {
        peers = ConcurrentHashMap.newKeySet();
        this.bus = bus;
        this.listenPort = listenPort;
        bus.register(this);
        es = Executors.newCachedThreadPool();
        // Create new runnable to listen for new connections.
        es.execute(() -> {
                    try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
                        while (true) {
                            Socket s = serverSocket.accept();
                            // separate thread since it blocks waiting for messages.
                            es.execute(() -> {
                                try {
                                    PeerHandler p = new PeerHandler(s, es, ds, bus);
                                    if(!peers.contains(p)) peers.add(p);
                                }
                                catch(TimeoutException time) {
                                    // this means no response was received
                                    LOGGER.log(Level.FINE, "No response received within the time limit");
                                }
                                catch(InterruptedException | ExecutionException ie) {
                                    ie.printStackTrace();
                                }
                            });
                        }
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
        );
        // connect to each given peer - the PeerHandler Class ensures
        // that the VERSION - ACK protocol is obeyed. Creation is done
        // in a separate thread as the PeerHandler constructor will
        // block.
        es.execute(() -> {
            PeerHandler p;
            for (InetSocketAddress peerAddress : initialPeers) {
                try {
                    p = new PeerHandler(peerAddress,listenPort,es, ds, bus);
                    if(!peers.contains(p)) peers.add(p);
                }
                catch(TimeoutException toe) {
                    LOGGER.log(Level.FINE, "Failed to contact an initial peer");
                    continue;
                }
                catch(InterruptedException | ExecutionException | IOException ie) {
                    ie.printStackTrace();
                    continue;
                }
                // Get the initial peers to connect to.
                GetPeers getPeers = GetPeers.newBuilder()
                        .setMyAddress(NetAddress.newBuilder()
                                .setHostName(p.getListenAddress().getHostName())
                                .setPort(p.getListenAddress().getPort()))
                        .build();
                Message message = Message.newBuilder().setType(Message.Type.GETPEERS)
                        .setGetPeers(getPeers).build();
                p.send(message);
            }
        });
    }
    public ConnectionManager(int listenPort, DataStore ds, Bus bus) throws IOException{
    	List<InetSocketAddress> initialPeers = getInitialPeers(); // it works!
        _initialise(initialPeers, listenPort, ds, bus);
    }
    // This is used primarily for testing
    public ConnectionManager(List<InetSocketAddress> initialPeers,int listenPort, DataStore ds, Bus bus) {
        _initialise(initialPeers, listenPort, ds, bus);
    }
    /**
     * Peers are contacted in the constructor, but this method will send a GETPEERS message
     * to all of the current peers that the class knows about.
     */
    public void getPeers() {
        for(PeerHandler p : peers) {
            GetPeers getPeers = GetPeers.newBuilder()
                    .setMyAddress(NetAddress.newBuilder()
                            .setHostName(p.getAddress().getHostName())
                            .setPort(p.getLocalAddress().getPort()))
                    .build();
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
    /**
     * For testing only - not relevant to actual version
     */
    @Subscribe
    public void onNewEntryEvent(NewEntryEvent nee) {
        // prints the document description
        System.out.println(nee.getNewEntry().getMetadata().getDocDescription());
    }
    /**
     * This method is responsible for adding the peers to the list of peers.
     * it achieves this by adding a runnable to the thread pool to avoid latency.
     * @param event the PeersEvent that was raised by the PeerReceive runnable
     */
    @Subscribe
    public void onPeersEvent(PeersEvent event) {
        if(event.getLevel() == PeersEvent.Level.PEERHANDLER) return;
        Set<InetSocketAddress> addresses = event.getSocketAddresses();
        es.execute(() -> {
            for(InetSocketAddress addr : addresses) {
                try {
                    peers.add(new PeerHandler(addr,listenPort, es, ds, bus));
                }
                catch(TimeoutException toe) {
                    LOGGER.log(Level.FINE, "Timeout when responding to peers");
                }
                catch(IOException | ExecutionException | InterruptedException ioe) {
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
        es.execute(() -> {
            List<NetAddress> addresses = new ArrayList<>();
            PeerHandler sender = null;
            for(PeerHandler p : peers) {
                if(!p.getAddress().equals(address)) {
                    addresses.add(NetAddress.newBuilder()
                            .setHostName(p.getConnectedHost().getHostName())
                            .setPort(p.getConnectedPort()).build());
                }
                else sender = p;
            }
            Peers peer = Peers.newBuilder().addAllAddress(addresses).build();
            Message msg = Message.newBuilder().setType(Message.Type.PEERS).setPeers(peer).build();
            if(sender != null) sender.send(msg);
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
