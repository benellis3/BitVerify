package bitverify.persistence;


import bitverify.block.Block;

import bitverify.entries.Entry;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Callable;

public class DatabaseStore implements DataStore {

    private TransactionManager t;

    private Dao<Entry, UUID> entryDao;
    private Dao<Block, Void> blockDao;
    private Dao<BlockEntry, Void> blockEntryDao;
    private Dao<Property, String> propertyDao;

    private PreparedQuery<Block> mostRecentBlockQuery;
    private PreparedQuery<Entry> entriesForBlockQuery;

    private Block latestBlock;

    public DatabaseStore(String databasePath) throws SQLException {

        ConnectionSource cs = new JdbcPooledConnectionSource(databasePath);
        t = new TransactionManager(cs);

        entryDao = DaoManager.createDao(cs, Entry.class);
        blockDao = DaoManager.createDao(cs, Block.class);
        blockEntryDao = DaoManager.createDao(cs, BlockEntry.class);
        propertyDao = DaoManager.createDao(cs, Property.class);

        initializeDatabase(cs);

        mostRecentBlockQuery = blockDao.queryBuilder()
                .orderBy("height", false)
                .orderBy("timeStamp", true)
                .limit(1L)
                .prepare();

        prepareEntriesForBlockQuery();

        String latestBlockIDString = getProperty("latestBlockID");
        if (latestBlockIDString == null) {
            latestBlock = Block.getGenesisBlock();
            latestBlock.setEntriesList(Collections.emptyList());
        } else {
            byte[] id = Base64.getDecoder().decode(latestBlockIDString);
            latestBlock = getBlock(id);
            latestBlock.setEntriesList(getEntriesForBlock(id));
        }
    }

    private void initializeDatabase(ConnectionSource cs) throws SQLException {
        t.callInTransaction(() -> {
            // create tables
            TableUtils.createTableIfNotExists(cs, Entry.class);
            TableUtils.createTableIfNotExists(cs, Block.class);
            TableUtils.createTableIfNotExists(cs, BlockEntry.class);
            TableUtils.createTableIfNotExists(cs, Property.class);

            // make sure genesis block is present
            Block g = Block.getGenesisBlock();
            if (!blockExists(g.getBlockID()))
                blockDao.create(g);

            return null;
        });
    }

    private boolean blockExists(byte[] blockID) throws SQLException {
        return blockDao.queryBuilder()
                .limit(1L)
                .where()
                .eq("blockID", blockID)
                .queryForFirst()
                != null;
    }

    private void prepareEntriesForBlockQuery() throws SQLException {
        QueryBuilder<BlockEntry, Void> blockEntryQB = blockEntryDao.queryBuilder()
                .selectColumns("entry_id");
        blockEntryQB.where().eq("block_id", new SelectArg());

        QueryBuilder<Entry, UUID> entryQB = entryDao.queryBuilder();
        entryQB.where().in("entryID",  blockEntryQB);
        entriesForBlockQuery = entryQB.prepare();
    }

    /**
     * Get a property's value. Will return null if the property is not stored.
     * @param key the property key
     * @throws SQLException
     */
    private String getProperty(String key) throws SQLException {
        return propertyDao.queryForId(key).getValue();
    }

    /**
     * Set a property. Its key and value must be at most 255 characters long.
     * @param key the property key
     * @param value the property value
     * @throws SQLException
     */
    private void setProperty(String key, String value) throws SQLException {
        if (key.length() > 255 || value.length() > 255)
            throw new IllegalArgumentException("Property key and value must be at most 255 characters long");
        propertyDao.createOrUpdate(new Property(key, value));
    }


    public long getBlocksCount() throws SQLException {
        return blockDao.countOf();
    }

    public Block getMostRecentBlock() throws SQLException {
        return latestBlock;
    }

    private List<Entry> getEntriesForBlock(byte[] blockID) throws SQLException {
        entriesForBlockQuery.setArgumentHolderValue(0, blockID);
        return entryDao.query(entriesForBlockQuery);
    }

    /**
     * Gets the N most recent blocks on what is regarded as the longest blockchain.
     * Blocks returned only contain their headers, no entries.
     * @param n
     * @return
     * @throws SQLException
     */
    public List<Block> getNMostRecentBlocks(int n) throws SQLException {
        // Sorted with the recent block at (or near) the header of the block
        // n = 2 means return the most recent block and the one before
        CloseableIterator<Block> initialResults = blockDao.queryBuilder()
                .orderBy("height", false)
                .orderBy("timeStamp", true)
                .where()
                .between("height", latestBlock.getHeight() - n + 1, latestBlock.getHeight())
                .iterator();

        // place results in a list
        ArrayList<Block> output = new ArrayList<>(n);

        byte[] expectedID = latestBlock.getBlockID();

        try {
            while (initialResults.hasNext()) {
                Block b = initialResults.next();
                if (Arrays.equals(b.getBlockID(), expectedID)) {
                    output.add(b);
                    expectedID = b.getPrevBlockHash();
                }
            }
        } finally {
            initialResults.close();
        }

        return output;
    }

    public boolean insertBlock(Block b) throws SQLException {
        // TODO: check if we are unorphaning any blocks

        return t.callInTransaction(() -> {
            if (blockExists(b.getBlockID()))
                return false;

            if (Arrays.equals(b.getPrevBlockHash(), latestBlock.getBlockID())) {
                // extending the active blockchain
                b.setHeight(latestBlock.getHeight() + 1);
                blockDao.create(b);
                setBlockEntriesConfirmed(b, true);
                latestBlock = b;

            } else {
                // see if this will be the new latest block

                Block parent = getBlock(b.getPrevBlockHash());
                if (parent == null) {
                    // orphan block, so it will be inactive.
                    b.setHeight(-1);
                    blockDao.create(b);
                    setBlockEntriesConfirmed(b, false);

                } else {
                    long oldHeight = latestBlock.getHeight();
                    Block oldLatestBlock = latestBlock;

                    long newHeight = parent.getHeight() + 1;
                    b.setHeight(newHeight);

                    if (newHeight > oldHeight || (newHeight == oldHeight && b.getTimeStamp() < oldLatestBlock.getTimeStamp())) {
                        // this is the new latest block
                        blockDao.create(b);
                        latestBlock = b;

                        // so determine which blocks to activate/deactivate
                        CloseableIterator<Block> blocksFromHighest = blockDao.queryBuilder().orderBy("height", false).iterator();

                        List<Block> blocksToActivate = new ArrayList<>();
                        List<Block> blocksToDeactivate = new ArrayList<>();

                        Block current = null;
                        byte[] prevBlockOnOldChain = oldLatestBlock.getBlockID();
                        byte[] prevBlockOnNewChain = b.getPrevBlockHash();

                        // stop when the parent on both chains is the same block (and so it will remain active)
                        while (blocksFromHighest.hasNext() && !Arrays.equals(prevBlockOnNewChain, prevBlockOnOldChain)) {

                            current = blocksFromHighest.next();
                            // current could be:
                            // 1. a block on the new active chain - activate it
                            // 2. a block on the old active chain - deactivate it
                            // 3. otherwise a block on another inactive chain that will remain inactive

                            if (Arrays.equals(current.getBlockID(), prevBlockOnNewChain)) {
                                prevBlockOnNewChain = current.getPrevBlockHash();
                                blocksToActivate.add(current);

                            } else if (Arrays.equals(current.getBlockID(), prevBlockOnOldChain)) {
                                prevBlockOnOldChain = current.getPrevBlockHash();
                                blocksToDeactivate.add(current);
                            }
                        }

                        // deactivate first, in case an entry will get reactivated.
                        for (Block block : blocksToDeactivate)
                            setBlockEntriesConfirmed(block, false);

                        for (Block block : blocksToActivate)
                            setBlockEntriesConfirmed(block, true);

                        // finally activate the new block
                        setBlockEntriesConfirmed(b, true);

                    } else {
                        // this block will be inactive
                        blockDao.create(b);
                        setBlockEntriesConfirmed(b, false);
                    }
                }
            }

            // block was successfully inserted
            return true;
        });
    }

    /**
     * Sets all of the entries in this block as confirmed or unconfirmed.
     * If the entries are not yet in the database, they will be added.
     * Assumes that the block was not a duplicate, so no entries are mapped to this block at the moment.
     * Not an atomic operation so should call this from a transaction.
     * @param block
     * @param confirmed
     * @throws SQLException
     */
    private void setBlockEntriesConfirmed(Block block, boolean confirmed) throws SQLException {
        List<Entry> entries = block.getEntriesList();
        if (entries == null)
            throw new IllegalArgumentException("Must initialise block entries before creating block in database");

        // first create/update entries
        for (Entry e : entries) {
            e.setConfirmed(confirmed);
            entryDao.createOrUpdate(e);
        }

        // now insert block-entry mappings into link table
        for (Entry e : entries) {
            blockEntryDao.create(new BlockEntry(block.getBlockID(), e.getEntryID()));
        }
    }

    @Override
    public Block getBlock(byte[] blockID) throws SQLException {
        return blockDao.queryBuilder().limit(1L).where().eq("blockID", blockID).queryForFirst();
    }

    public Entry getEntry(UUID id) throws SQLException {
        return entryDao.queryForId(id);
    }

    public Iterable<Entry> getEntries() throws SQLException {
        return entryDao;
    }

    public List<Entry> getEntries(byte[] fileHash) throws SQLException {
        return entryDao.queryForEq("fileHash", fileHash);
    }

    public List<Entry> getUnconfirmedEntries() throws SQLException {
        return entryDao.queryForEq("confirmed", false);
    }

    public void insertEntry(Entry e) throws SQLException {
        entryDao.create(e);
    }

    public void updateEntry(Entry e) throws SQLException {
        entryDao.update(e);
    }

    public void deleteEntry(Entry e) throws SQLException {
        entryDao.delete(e);
    }
}