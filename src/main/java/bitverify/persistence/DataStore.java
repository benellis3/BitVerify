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

    /**
     * Gets the number of blocks in the store.
     * @throws SQLException
     */
    public long getBlocksCount() throws SQLException;

    /**
     * Gets the most recent block on the active blockchain.
     * @throws SQLException
     */
    public Block getMostRecentBlock() throws SQLException;

    /**
     * Gets the N most recent blocks on the active blockchain.
     * Blocks returned only contain their headers, no entries.
     * @param n the number of recent-most blocks to get.
     * @throws SQLException
     */
    public List<Block> getNMostRecentBlocks(int n) throws SQLException;

    /**
     * Inserts the given block into the store, unless it is already present.
     * @param b the block
     * @return true if the block was inserted, false if it was already present.
     * @throws SQLException
     */
    public boolean insertBlock(Block b) throws SQLException;

    /**
     * Get a particular block.
     * @param blockID the block id
     * @throws SQLException
     */
    public Block getBlock(byte[] blockID) throws SQLException;

    /**
     * Get a particular entry.
     * @param id the entry id
     * @throws SQLException
     */
    public Entry getEntry(UUID id) throws SQLException;

    /**
     * Get all entries matching the given file hash.
     * @param fileHash the file hash
     * @throws SQLException
     */
    public List<Entry> getEntries(byte[] fileHash) throws SQLException;

    /**
     * Get all entries that are unconfirmed (not part of a block on the active blockchain).
     * @throws SQLException
     */
    public List<Entry> getUnconfirmedEntries() throws SQLException;

    /**
     * Insert an entry into the store.
     * @param e the entry
     * @throws SQLException
     */
    public void insertEntry(Entry e) throws SQLException;

    /**
     * Get a property's value. Will return null if the property is not stored.
     * @param key the property key
     * @throws SQLException
     */
    public String getProperty(String key) throws SQLException;

    /**
     * Set a property. Its key and value must be at most 255 characters long.
     * @param key   the property key
     * @param value the property value
     * @throws SQLException
     */
    public void setProperty(String key, String value) throws SQLException;



}
