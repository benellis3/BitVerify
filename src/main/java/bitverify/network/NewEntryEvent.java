package bitverify.network;

import bitverify.entries.Entry;

/**
 * Created by benellis on 08/02/2016.
 */
public class NewEntryEvent {
    private Entry newEntry;
    public NewEntryEvent(Entry e) {newEntry = e;}
    public Entry getNewEntry() {return newEntry;}
}
