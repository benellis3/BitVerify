package bitverify.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import bitverify.network.proto.MessageProto.Message;

/**
 * This class is responsible for co-ordination of network functions.
 * It controls the incoming and outgoing parts of the connection.
 * @author Ben Ellis, Robert Eady
 */
public class ConnectionManager {
    private ExecutorService es;
    private List<PeerHandler> peers;
    private static final String PEER_URL = "0.0.0.0:4000"; // for testing
    
    public ConnectionManager(List<InetSocketAddress> initialPeers, int listenPort) throws IOException{
        peers = new ArrayList<>();
        es = Executors.newCachedThreadPool();
        // connect to each given peer
        for (InetSocketAddress peerAddress : initialPeers) {
            try {
                Socket s = new Socket(peerAddress.getAddress(), peerAddress.getPort());
                peers.add(new PeerHandler(s,es));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Create new runnable to listen for new connections.
        es.execute(() -> {
                    try (ServerSocket serverSocket = new ServerSocket(listenPort)) {
                        while (true) {
                            Socket s = serverSocket.accept();
                            peers.add(new PeerHandler(s, es));
                        }
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
        );
    }
    /**
     * Send the given message to all connected peers.
     * @param message The string to send.
     */
    public void broadcast(String message) {
        for(PeerHandler peer : peers) {
            peer.send(message);
        }
    }
    
    
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
