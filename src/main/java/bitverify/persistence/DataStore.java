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

    /**
     * Inserts the given block into the store, unless it is already present.
     * @param b the block
     * @return true if the block was inserted, false if it was already present.
     * @throws SQLException
     */
    public boolean insertBlock(Block b) throws SQLException;

    public Block getBlock(byte[] blockID) throws SQLException;

    public Entry getEntry(UUID id) throws SQLException;

    public Iterable<Entry> getEntries() throws SQLException;

    public List<Entry> getEntries(byte[] fileHash) throws SQLException;

    public List<Entry> getUnconfirmedEntries() throws SQLException;

    public void insertEntry(Entry e) throws SQLException;

    public void updateEntry(Entry e) throws SQLException;

    public void deleteEntry(Entry e) throws SQLException;

}
