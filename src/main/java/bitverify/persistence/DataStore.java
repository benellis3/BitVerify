package bitverify.persistence;

import bitverify.block.Block;
import bitverify.crypto.Identity;
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
    long getBlocksCount() throws SQLException;

    /**
     * Gets the number of blocks in the active blockchain.
     * @throws SQLException
     */
    long getActiveBlocksCount() throws SQLException;

    /**
     * Gets the most recent block on the active blockchain. Never returns null.
     * @throws SQLException
     */
    Block getMostRecentBlock() throws SQLException;

    /**
     * Gets the N most recent blocks on the active blockchain.
     * Blocks returned only contain their headers, no entries.
     * @param n the number of recent-most blocks to get.
     * @throws SQLException
     */
    List<Block> getNMostRecentBlocks(int n) throws SQLException;

    /**
     * Gets the N most recent blocks, from (including) the given blockID backwards.
     * Blocks returned only contain their headers, no entries.
     * @param n the number of recent-most blocks to get.
     * @throws SQLException
     */
    List<Block> getNMostRecentBlocks(int n, Block fromBlock) throws SQLException;

    /**
     * Get all of the blocks after a certain block or between two blocks, up to a limited number.
     * @param idFrom get blocks after this block ID
     * @param limit  the maximum number of block IDs to get. Provide -1 if there is no limit.
     */
    List<Block> getActiveBlocksAfter(byte[] idFrom, int limit) throws SQLException;

    /**
     * Inserts the given block into the store, unless it is already present or would be an orphan.
     * @param b the block
     * @return An InsertBlockResult object indicating the result of this operation
     * - success, or failure due to the block being an orphan, or a duplicate.
     * @throws SQLException
     */
    InsertBlockResult insertBlock(Block b) throws SQLException;

    boolean blockExists(byte[] blockID) throws SQLException;

    /**
     * Get a particular block.
     * @param blockID the block id
     * @throws SQLException
     */
    Block getBlock(byte[] blockID) throws SQLException;

    /**
     * Get all blocks in the datastore.
     * @throws SQLException
     */
    DatabaseIterator<Block> getAllBlocks() throws SQLException;

    /**
     * Determine if we have this block on our active chain.
     * @param blockID the block ID
     * @return true if this block is in the datastore and is on the active chain; otherwise false.
     * @throws SQLException
     */
    boolean isBlockOnActiveChain(byte[] blockID) throws SQLException;

    /**
     * Gets the number of entries in the store.
     * @throws SQLException
     */
    long getEntriesCount() throws SQLException;

    /**
     * Get a particular entry.
     * @param id the entry id
     * @throws SQLException
     */
    Entry getEntry(UUID id) throws SQLException;

    /**
     * Get all entries matching the given file hash.
     * @param fileHash the file hash
     * @throws SQLException
     */
    List<Entry> getEntries(byte[] fileHash) throws SQLException;

    /**
     * Get all entries that are unconfirmed (not part of a block on the active blockchain).
     * @throws SQLException
     */
    List<Entry> getUnconfirmedEntries() throws SQLException;

    /**
     * Get all entries that are confirmed (part of a block on the active blockchain).
     * @throws SQLException
     */
    DatabaseIterator<Entry> getConfirmedEntries() throws SQLException;

    /**
     * Get all stored entries.
     * @throws SQLException
     */
    DatabaseIterator<Entry> getAllEntries() throws SQLException;

    /**
     * Get all entries where one of the string metadata fields partially matches the given query.
     * Query is split on whitespace, so for example searching for "london bridge" will return
     * any entries containing "london" and any entries containing "bridge".
     * @param searchQuery the search query
     * @return all matching entries
     * @throws SQLException
     */
    DatabaseIterator<Entry> searchEntries(String searchQuery) throws SQLException;

    /**
     * Insert an entry into the store, unless it already exists
     * @param e the entry
     * @return true if the entry was inserted successfully, false if it is a duplicate.
     * @throws SQLException
     */
    boolean insertEntry(Entry e) throws SQLException;

    /**
     * Get a property's value. Will return null if the property is not stored.
     * @param key the property key
     * @throws SQLException
     */
    String getProperty(String key) throws SQLException;


    /**
     * Set a property. Its key and value must be at most 255 characters long.
     * @param key   the property key
     * @param value the property value
     * @throws SQLException
     */
    void setProperty(String key, String value) throws SQLException;

    List<Identity> getIdentities() throws SQLException;

    void updateIdentity(Identity identity) throws SQLException;

    void insertIdentity(Identity identity) throws SQLException;

    List<byte[]> getActiveBlocksSample(int maxBlockIDs) throws SQLException;

    void updateEntry(Entry entry) throws SQLException;
}
