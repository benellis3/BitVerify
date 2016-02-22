package bitverify.network;

import java.net.InetSocketAddress;

import bitverify.entries.Entry;

/**
 * Created by benellis on 08/02/2016.
 */
public class NewEntryEvent {
    private Entry newEntry;
    private InetSocketAddress address;
    
    public NewEntryEvent(Entry e) {
    	newEntry = e;
    	address = null;
    }
    public NewEntryEvent(Entry e, InetSocketAddress address){
    	newEntry = e;
    	this.address = address;
    }
    public Entry getNewEntry() {return newEntry;}
    public InetSocketAddress getInetSocketAddress() {return address;}
}
