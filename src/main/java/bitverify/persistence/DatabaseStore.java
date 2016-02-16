package bitverify.persistence;


import bitverify.block.Block;

import bitverify.crypto.Identity;
import bitverify.entries.Entry;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;
import java.util.*;

public class DatabaseStore implements DataStore {

    private TransactionManager t;

    private Dao<Entry, UUID> entryDao;
    private Dao<Block, Void> blockDao;
    private Dao<BlockEntry, Void> blockEntryDao;
    private Dao<Property, String> propertyDao;
    private Dao<Identity, Integer> identityDao;

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
        identityDao = DaoManager.createDao(cs, Identity.class);

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
            TableUtils.createTableIfNotExists(cs, Identity.class);

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
                .selectColumns("entryID");
        blockEntryQB.where().eq("blockID", new SelectArg());

        QueryBuilder<Entry, UUID> entryQB = entryDao.queryBuilder();
        entryQB.where().in("entryID", blockEntryQB);
        entriesForBlockQuery = entryQB.prepare();
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

    public List<Block> getBlocksBetween(byte[] idFrom, byte[] idTo, int limit) throws SQLException {
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

        CloseableIterator<Block> results = w.iterator();

        List<Block> output = new ArrayList<>();
        // results will be ordered earliest->latest along the blockchain

        byte[] expectedParentID = startBlock.getBlockID();
        try {
            // separate cases depending on whether we must check the end ID on every iteration.
            if (idTo == null) {
                while (results.hasNext() && limit > 0) {
                    Block b = results.next();
                    if (Arrays.equals(b.getPrevBlockHash(), expectedParentID)) {
                        output.add(b);
                        expectedParentID = b.getBlockID();
                    }
                }
            } else {
                while (results.hasNext() && limit > 0) {
                    Block b = results.next();
                    // stop before returning the 'to' block
                    if (Arrays.equals(b.getBlockID(), idTo))
                        break;
                    if (Arrays.equals(b.getPrevBlockHash(), expectedParentID)) {
                        output.add(b);
                        expectedParentID = b.getBlockID();
                    }
                }
            }
        } finally {
            results.close();
        }

        return output;
    }

    public boolean insertBlock(Block b) throws SQLException {
        // TODO: check if we are unorphaning any blocks

        return t.callInTransaction(() -> {
            boolean blockIsNewLatest = false;
            List<Block> blocksToActivate = new ArrayList<>();
            List<Block> blocksToDeactivate = new ArrayList<>();

            if (Arrays.equals(b.getPrevBlockHash(), latestBlock.getBlockID())) {

                // extending the active blockchain
                b.setHeight(latestBlock.getHeight() + 1);

                blockIsNewLatest = true;
                blocksToActivate.add(b);

            } else {
                // see if this will be the new latest block

                Block parent = getBlock(b.getPrevBlockHash());
                if (parent == null) {
                    // orphan block, so it will be inactive.
                    b.setHeight(-1);
                    blocksToDeactivate.add(b);

                } else {
                    long oldHeight = latestBlock.getHeight();
                    Block oldLatestBlock = latestBlock;

                    long newHeight = parent.getHeight() + 1;
                    b.setHeight(newHeight);

                    if (newHeight > oldHeight || (newHeight == oldHeight && b.getTimeStamp() < oldLatestBlock.getTimeStamp())) {

                        blockIsNewLatest = true;
                        blocksToActivate.add(b);

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
                // always add block to database
                blockDao.create(b);

                if (blockIsNewLatest)
                    latestBlock = b;

                // deactivate first, in case an entry will get reactivated.
                for (Block block : blocksToDeactivate)
                    setBlockEntriesConfirmed(block, false);

                for (Block block : blocksToActivate)
                    setBlockEntriesConfirmed(block, true);

                // now insert block-entry mappings into link table
                for (Entry e : b.getEntriesList())
                    blockEntryDao.create(new BlockEntry(b.getBlockID(), e.getEntryID()));

                // block was successfully inserted
                return true;

            } catch (SQLException e) {
                // catch duplicate block error
                final int DUPLICATE_ERROR = 23001;
                if (e.getCause() instanceof SQLException && ((SQLException) e.getCause()).getErrorCode() == DUPLICATE_ERROR)
                    return false;
                else
                    throw e;
            }
        });
    }

    /**
     * Sets all of the entries in this block as confirmed or unconfirmed.
     * If the entries are not yet in the database, they will be added.
     * Not an atomic operation so should call this from a transaction.
     */
    private void setBlockEntriesConfirmed(Block block, boolean confirmed) throws SQLException {
        // create/update entries
        for (Entry e : block.getEntriesList()) {
            e.setConfirmed(confirmed);
            entryDao.createOrUpdate(e);
        }
    }

    public Block getBlock(byte[] blockID) throws SQLException {
        return blockDao.queryBuilder().limit(1L).where().eq("blockID", blockID).queryForFirst();
    }

    public Entry getEntry(UUID id) throws SQLException {
        return entryDao.queryForId(id);
    }

    public List<Entry> getEntries(byte[] docHash) throws SQLException {
        return entryDao.queryForEq("docHash", docHash);
    }

    public List<Entry> getUnconfirmedEntries() throws SQLException {
        return entryDao.queryForEq("confirmed", false);
    }

    public List<Entry> searchEntries(String searchQuery) throws SQLException {
        String[] queries = searchQuery.split("\\s+"); // split on groups of whitespace
        Where<Entry, UUID> w = entryDao.queryBuilder().where();
        for (String query : queries) {
            String likeQuery = "%" + searchQuery + "%";
            w.like("docName", likeQuery);
            w.like("docDescription", likeQuery);
        }
        // OR all of our like clauses together
        return w.or(queries.length * 2).query();
    }

    public void insertEntry(Entry e) throws SQLException {
        // by default, entry will be unconfirmed
        entryDao.create(e);
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


}