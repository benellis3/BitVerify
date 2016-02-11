package bitverify.persistence;

import bitverify.block.Block;
import bitverify.entries.Entry;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Created by Rob on 09/02/2016.
 */
public interface DataStore {

    public long getBlocksCount() throws SQLException;
    public Block getMostRecentBlock() throws SQLException;
    public List<Block> getNMostRecentBlocks(int n) throws SQLException;

    public Entry getEntry(UUID id) throws SQLException;
    public Iterable<Entry> getEntries() throws SQLException;
    public List<Entry> getEntries(byte[] fileHash) throws SQLException;
    public List<Entry> getUnconfirmedEntries() throws SQLException;
    public void insertEntry(Entry e) throws SQLException;
    public void updateEntry(Entry e) throws SQLException;
    public void deleteEntry(Entry e) throws SQLException;
    public boolean insertBlock(Block b) throws SQLException;


    // get most recent block (at end of chain)
    // get nth predecessor block from a given block

    // TODO: implement similar methods for Blocks, and any other functions we require.

}
