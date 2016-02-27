package bitverify.persistence;


import bitverify.block.Block;

import bitverify.crypto.Identity;
import bitverify.entries.Entry;
import bitverify.network.BlockID;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.stmt.*;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import org.h2.engine.Database;


import java.sql.SQLException;
import java.util.*;


public class DatabaseStore implements DataStore {

    private TransactionManager t;

    private Dao<Entry, UUID> entryDao;
    private Dao<Block, Void> blockDao;
    private Dao<BlockEntry, Void> blockEntryDao;
    private Dao<Property, String> propertyDao;
    private Dao<Identity, Integer> identityDao;

    private PreparedQuery<Entry> entriesForBlockQuery;

    private Block latestBlock;

    final int DUPLICATE_ERROR_CODE = 23001;

    public DatabaseStore(String databasePath) throws SQLException {

        ConnectionSource cs = new JdbcPooledConnectionSource(databasePath);
        t = new TransactionManager(cs);

        entryDao = DaoManager.createDao(cs, Entry.class);
        blockDao = DaoManager.createDao(cs, Block.class);
        blockEntryDao = DaoManager.createDao(cs, BlockEntry.class);
        propertyDao = DaoManager.createDao(cs, Property.class);
        identityDao = DaoManager.createDao(cs, Identity.class);

        initializeDatabase(cs);

        prepareEntriesForBlockQuery();

        String latestBlockIDString = getProperty("latestBlockID");
        if (latestBlockIDString == null) {
            Block g = Block.getGenesisBlock();
            g.setActive(true);
            setLatestBlock(g);
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
            TableUtils.createTableIfNotExists(cs, Identity.class);

            // make sure genesis block is present
            Block g = Block.getGenesisBlock();
            if (!blockExists(g.getBlockID())) {
                g.setActive(true);
                blockDao.create(g);
            }
            return null;
        });
    }

    private void setLatestBlock(Block b) throws SQLException {
        latestBlock = b;
        setProperty("latestBlockID",  new BlockID(b.getBlockID()).toString());
    }

    public boolean blockExists(byte[] blockID) throws SQLException {
        return blockDao.queryBuilder()
                .where()
                .eq("blockID", blockID)
                .countOf() > 0;
    }

    private void prepareEntriesForBlockQuery() throws SQLException {
        QueryBuilder<BlockEntry, Void> blockEntryQB = blockEntryDao.queryBuilder()
                .selectColumns("entryID");
        blockEntryQB.where().eq("blockID", new SelectArg());

        QueryBuilder<Entry, UUID> entryQB = entryDao.queryBuilder();
        entryQB.where().in("entryID", blockEntryQB);
        entriesForBlockQuery = entryQB.prepare();
    }

    public long getBlocksCount() throws SQLException {
        return blockDao.countOf();
    }

    public long getActiveBlocksCount() throws SQLException {
        return blockDao.queryBuilder().where().eq("active", true).countOf();
    }

    public DatabaseIterator<Block> getAllBlocks() throws SQLException {
        CloseableIterator<Block> blocks = blockDao.queryBuilder()
                .orderBy("height", false)
                .orderBy("timeStamp", true)
                .iterator();

        return new DatabaseIterator<Block>(blocks) {
            @Override
            public boolean moveNext() throws SQLException {
                current = ci.nextThrow();
                if (current != null) {
                    current.setEntriesList(getEntriesForBlock(current.getBlockID()));
                    return true;
                }
                return false;
            }
        };
    }


    public Block getMostRecentBlock() throws SQLException {
        return latestBlock;
    }

    /**
     * Must be synchronised since we set the argument value, then query,
     * and don't want another thread to change the argument in the mean time.
     * @param blockID
     * @return
     * @throws SQLException
     */
    private synchronized List<Entry> getEntriesForBlock(byte[] blockID) throws SQLException {
        entriesForBlockQuery.setArgumentHolderValue(0, blockID);
        return entryDao.query(entriesForBlockQuery);
    }

    public synchronized List<Block> getNMostRecentBlocks(int n, Block fromBlock) throws SQLException {
        // Sorted with the recent block at (or near) the header of the block
        // n = 2 means return the most recent block and the one before
        CloseableIterator<Block> initialResults = blockDao.queryBuilder()
                .orderBy("height", false)
                .orderBy("timeStamp", true)
                .where()
                .between("height", fromBlock.getHeight() - n + 1, fromBlock.getHeight())
                .iterator();

        // place results in a list
        ArrayList<Block> output = new ArrayList<>(n);

        byte[] expectedID = fromBlock.getBlockID();

        try {
            while (initialResults.hasNext()) {
                Block b = initialResults.next();
                if (Arrays.equals(b.getBlockID(), expectedID)) {
                    output.add(b);
                    b.setEntriesList(getEntriesForBlock(b.getBlockID()));
                    expectedID = b.getPrevBlockHash();
                }
            }
        } finally {
            initialResults.close();
        }

        return output;
    }

    public synchronized List<Block> getNMostRecentBlocks(int n) throws SQLException {
        return getNMostRecentBlocks(n, latestBlock);
    }

    public synchronized List<Block> getActiveBlocksAfter(byte[] idFrom, int limit) throws SQLException {
        // try to retrieve the starting block from the database.
        Block startBlock = getBlock(idFrom);
        if (startBlock == null)
            return null;

        Where<Block, Void> w = blockDao.queryBuilder()
                .orderBy("height", true)
                .orderBy("timeStamp", false)
                .where();
        if (limit == -1) {
            w.gt("height", startBlock.getHeight());
            limit = Integer.MAX_VALUE;
        } else {
            long startHeight = startBlock.getHeight() + 1;
            w.between("height", startHeight, startHeight + limit - 1);
        }
        w.and().eq("active", true);

        CloseableIterator<Block> results = w.iterator();

        List<Block> output = new ArrayList<>();
        // results will be ordered earliest->latest along the blockchain

        byte[] expectedParentID = startBlock.getBlockID();
        try {
            while (results.hasNext() && limit > 0) {
                Block b = results.next();
                if (Arrays.equals(b.getPrevBlockHash(), expectedParentID)) {
                    b.setEntriesList(getEntriesForBlock(b.getBlockID()));
                    output.add(b);
                    expectedParentID = b.getBlockID();
                }
            }
        } finally {
            results.close();
        }

        return output;
    }

    public synchronized InsertBlockResult insertBlock(Block b) throws SQLException {
        return t.callInTransaction(() -> {
            boolean blockIsNewLatest = false;
            List<Block> blocksToActivate = new ArrayList<>();
            List<Block> blocksToDeactivate = new ArrayList<>();

            if (Arrays.equals(b.getPrevBlockHash(), latestBlock.getBlockID())) {

                // extending the active blockchain
                b.setHeight(latestBlock.getHeight() + 1);
                blockIsNewLatest = true;

            } else {
                // see if this will be the new latest block
                Block parent = getBlock(b.getPrevBlockHash());
                if (parent == null) {
                    // orphan blocks are not inserted into the database
                    return InsertBlockResult.FAIL_ORPHAN;

                } else {
                    long oldHeight = latestBlock.getHeight();
                    Block oldLatestBlock = latestBlock;

                    long newHeight = parent.getHeight() + 1;
                    b.setHeight(newHeight);

                    if (newHeight > oldHeight || (newHeight == oldHeight && b.getTimeStamp() < oldLatestBlock.getTimeStamp())) {

                        blockIsNewLatest = true;
                        // so determine which blocks to activate/deactivate
                        CloseableIterator<Block> blocksFromHighest = blockDao.queryBuilder().orderBy("height", false).iterator();

                        Block current = null;
                        byte[] prevBlockOnOldChain = oldLatestBlock.getBlockID();
                        byte[] prevBlockOnNewChain = b.getPrevBlockHash();

                        // stop when the parent on both chains is the same block (and so it will remain active)
                        try {
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
                        } finally {
                            // must be sure to close the iterator
                            blocksFromHighest.close();
                        }

                    } else {
                        // this block will be inactive
                        blocksToDeactivate.add(b);
                    }
                }
            }


            try {
                if (blockIsNewLatest)
                    b.setActive(true);


                // deactivate first, in case an entry will get reactivated.
                for (Block block : blocksToDeactivate) {
                    updateBlockActive(block, false);
                    block.setEntriesList(getEntriesForBlock(block.getBlockID()));
                    setBlockEntriesConfirmed(block, false, false);
                }

                if (!blockIsNewLatest)
                    setBlockEntriesConfirmed(b, false, true);

                for (Block block : blocksToActivate) {
                    updateBlockActive(block, true);
                    block.setEntriesList(getEntriesForBlock(block.getBlockID()));
                    setBlockEntriesConfirmed(block, true, false);
                }

                if (blockIsNewLatest)
                    setBlockEntriesConfirmed(b, true, true);



                // now insert block-entry mappings into link table
                for (Entry e : b.getEntriesList())
                    blockEntryDao.create(new BlockEntry(b.getBlockID(), e.getEntryID()));

                if (blockIsNewLatest)
                    setLatestBlock(b);

                // always add block to database
                blockDao.create(b);

                // block was successfully inserted
                return InsertBlockResult.SUCCESS;

            } catch (SQLException e) {
                // catch duplicate block error
                if (isDuplicateError(e))
                    return InsertBlockResult.FAIL_DUPLICATE;
                else
                    throw e;
            }
        });
    }

    public boolean isBlockOnActiveChain(byte[] blockID) throws SQLException {
        Block block = getBlock(blockID);
        return (block != null && block.isActive());
    }

    /**
     * Sets all of the entries in this block as confirmed or unconfirmed.
     * Not an atomic operation so should call this from a transaction.
     * @param block the block whose entries will be affected
     * @param confirmed whether the entries are now confirmed or unconfirmed
     * @param insert whether to inset entries from the block if they're not already present
     * @throws SQLException
     */
    private void setBlockEntriesConfirmed(Block block, boolean confirmed, boolean insert) throws SQLException {
        // create/update entries
        if (insert) {
            for (Entry e : block.getEntriesList()) {
                e.setConfirmed(confirmed);
                entryDao.createOrUpdate(e);
            }
        } else {
            for (Entry e : block.getEntriesList()) {
                e.setConfirmed(confirmed);
                entryDao.update(e);
            }
        }
    }

    private void updateBlockActive(Block block, boolean active) throws SQLException {
        UpdateBuilder<Block, Void> ub = blockDao.updateBuilder();
        ub.updateColumnValue("active", active);
        ub.where().eq("blockID", block.getBlockID());
        ub.update();
    }

    public synchronized Block getBlock(byte[] blockID) throws SQLException {
        Block b = blockDao.queryBuilder().limit(1L).where().eq("blockID", blockID).queryForFirst();
        if (b != null)
            b.setEntriesList(getEntriesForBlock(blockID));
        return b;
    }

    public synchronized long getEntriesCount() throws SQLException {
        return entryDao.countOf();
    }

    public synchronized Entry getEntry(UUID id) throws SQLException {
        return entryDao.queryForId(id);
    }

    public synchronized List<Entry> getEntries(byte[] docHash) throws SQLException {
        return entryDao.queryForEq("docHash", docHash);
    }

    public synchronized List<Entry> getUnconfirmedEntries() throws SQLException {
        return entryDao.queryForEq("confirmed", false);
    }

    public synchronized DatabaseIterator<Entry> getConfirmedEntries() throws SQLException {
        return new DatabaseIterator<>(entryDao.queryBuilder().where().eq("confirmed", true).iterator());
    }

    public synchronized DatabaseIterator<Entry> getAllEntries() throws SQLException {
        return new DatabaseIterator<>(entryDao.closeableIterator());
    }

    public DatabaseIterator<Entry> searchEntries(String searchQuery) throws SQLException {
        if (searchQuery.isEmpty())
            return getAllEntries();

        String[] queries = searchQuery.split("\\s+"); // split on groups of whitespace
        Where<Entry, UUID> w = entryDao.queryBuilder().where();
        for (String query : queries) {
            String likeQuery = "%" + query + "%";
            w.like("docName", likeQuery);
            w.like("docDescription", likeQuery);
            w.like("docGeoLocation", likeQuery);
            w.like("docLink", likeQuery);
        }
        // OR all of our like clauses together
        return new DatabaseIterator<>(w.or(queries.length * 4).iterator());
    }

    public boolean insertEntry(Entry entry) throws SQLException {
        // by default, entry will be unconfirmed
        try {
            entryDao.create(entry);
            return true;
        } catch (SQLException e) {
            // catch duplicate block error
            if (isDuplicateError(e))
                return false;
            else
                throw e;
        }
    }

    private boolean isDuplicateError(SQLException e) {
        if (e.getCause() instanceof SQLException) {
            SQLException c = (SQLException) e.getCause();
            return c.getErrorCode() == DUPLICATE_ERROR_CODE && !c.getMessage().contains("CONSTRAINT");
        }
        return false;
    }


    public String getProperty(String key) throws SQLException {
        Property p = propertyDao.queryForId(key);
        return p == null ? null : p.getValue();
    }

    public void setProperty(String key, String value) throws SQLException {
        if (key.length() > 255 || value.length() > 255)
            throw new IllegalArgumentException("Property key and value must be at most 255 characters long");
        propertyDao.createOrUpdate(new Property(key, value));
    }

    public List<Identity> getIdentities() throws SQLException {
        return identityDao.queryForAll();
    }

    public void updateIdentity(Identity identity) throws SQLException {
        identityDao.update(identity);
    }

    public void insertIdentity(Identity identity) throws SQLException {
        identityDao.create(identity);
    }

    public List<byte[]> getActiveBlocksSample(int maxBlockIDs) throws SQLException {

        List<byte[]> result = new ArrayList<>(maxBlockIDs);
        long numActiveBlocks = getActiveBlocksCount();
        // for testing
        long numBlocks = getBlocksCount();
        System.out.println(numBlocks);
        if (maxBlockIDs > 1) {
            try (DatabaseIterator<Block> activeBlocks = getActiveBlocks()) {

                // always send the genesis block
                numActiveBlocks--;
                maxBlockIDs--;

                double iter = (double) maxBlockIDs / numActiveBlocks;
                int numAdded = 0;
                double counter = 0;

                while (numActiveBlocks > 0 && activeBlocks.moveNext()) {
                    Block b = activeBlocks.current();

                    if (counter >= numAdded) {
                        numAdded++;
                        result.add(b.getBlockID());
                    }
                    counter += iter;
                    numActiveBlocks--;
                }
            }
        }
        result.add(Block.getGenesisBlock().getBlockID());
        return result;

    }

    private DatabaseIterator<Block> getActiveBlocks() throws SQLException {
        return new DatabaseIterator<>(blockDao.queryBuilder()
                .orderBy("height", false)
                .orderBy("timeStamp", true)
                .where().eq("active", true)
                .iterator());
    }

}