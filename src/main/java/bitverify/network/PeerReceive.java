package bitverify.network;

import bitverify.block.Block;
import bitverify.entries.Entry;
import bitverify.network.proto.MessageProto.GetPeers;
import bitverify.network.proto.MessageProto.Message;
import bitverify.network.proto.MessageProto.EntryMessage;
import bitverify.network.proto.MessageProto.BlockMessage;
import bitverify.network.proto.MessageProto.NetAddress;
import bitverify.network.proto.MessageProto.Peers;
import bitverify.network.proto.MessageProto.GetBlock;
import bitverify.persistence.DataStore;
import com.squareup.otto.Bus;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Created by benellis on 02/02/2016.
 */
public class PeerReceive implements Runnable {
    private Socket socket;
    private DataStore dataStore;
    private Bus eventBus;
    public PeerReceive(Socket s, DataStore ds, Bus bus) {
        socket = s;
        dataStore = ds;
        eventBus = bus;
    }
    @Override
    public void run() {
        try {
            Message message;
            InputStream is = socket.getInputStream();
            while(true) {
                message = Message.parseDelimitedFrom(is);
                switch(message.getType()) {
                    case GETBLOCK:
                        handleGetBlockMessage(message);
                        break;
                    case GETPEERS:
                        handleGetPeers(message);
                        break;
                    case ENTRY:
                        handleEntryMessage(message);
                        break;
                    case BLOCK:
                        handleBlockMessage(message);
                        break;
                    case PEERS:
                        handlePeers(message);
                        break;
                    default:
                        break;
                }

            }
        }
        catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void handleEntryMessage(Message message) {
        EntryMessage e = message.getEntry();
        byte[] bytes = e.getEntryBytes().toByteArray();
        Entry entry;
        try {
            entry = Entry.deserialize(bytes);
            // check the validity of the entry
            if (entry.testEntryHashSignature()) {
                // commented out for testing purposes
                //dataStore.insertEntry(entry);
                // raise a NewEntryEvent on the event bus
                eventBus.post(new NewEntryEvent(entry));

            }
        }
        catch(IOException ioe) {ioe.printStackTrace();}
        //catch(IOException | SQLException ioe) {ioe.printStackTrace();}
    }

    private void handleBlockMessage(Message message) {
        BlockMessage blockMessage = message.getBlock();
        byte[] bytes = blockMessage.getBlockBytes().toByteArray();
        // Requires integration with Niquo.
        Block block;
        //try {
            // deserialise block
            // block = Block.deserialize(bytes);
            // if(block.isValid()) {
                // dataStore.insertBlock(block);
                // eventBus.post(newBlockEvent(block));
            // }
        //}

    }
    private void handleGetPeers(Message message) {
        GetPeers getPeers = message.getGetPeers();
        // create an event which can be handed off to the connection manager.
        NetAddress netAddress = getPeers.getMyAddress();
        InetSocketAddress addr = new InetSocketAddress(netAddress.getHostName(), netAddress.getPort());
        eventBus.post(new GetPeersEvent(addr));
    }

    private void handlePeers(Message message) {
        Peers peers = message.getPeers();
        Collection<NetAddress> netAddressList = peers.getAddressList();
        // create a list of SocketInetAddresses from the netAddressList
        Set<InetSocketAddress> socketAddressList = ConcurrentHashMap.newKeySet();
        for(NetAddress netAddress : netAddressList) {
            socketAddressList.add(new InetSocketAddress(netAddress.getHostName(), netAddress.getPort()));
        }
        eventBus.post(new PeersEvent(socketAddressList, PeersEvent.Level.PEERHANDLER));
    }

    private void handleGetBlockMessage(Message message) {
        GetBlock getBlockMessage = message.getGetBlock();
        // retrieve blocks from the database and put an event on the event bus
        // which allows the appropriate block next block to be sent in a message

    }
}

