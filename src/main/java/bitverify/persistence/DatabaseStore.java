package bitverify.persistence;


import bitverify.block.Block;
import bitverify.block.BlockHeader;
import bitverify.entries.Entry;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;
import java.util.*;

public class DatabaseStore implements DataStore {

    private ConnectionSource connectionSource;
    private Dao<Entry, UUID> entryDao;
    private Dao<Block, byte[]> blockDao;

    private PreparedQuery<Block> mostRecentBlockQuery;

    private long currentHeight;

    public DatabaseStore(String databasePath) throws SQLException {
        connectionSource = new JdbcConnectionSource(databasePath);
        entryDao = DaoManager.createDao(connectionSource, Entry.class);
        blockDao = DaoManager.createDao(connectionSource, Block.class);

        initialSetup();

        mostRecentBlockQuery = blockDao.queryBuilder()
                .orderBy("height", false)
                .orderBy("timeStamp", true)
                .limit(1L)
                .prepare();
    }

    private void initialSetup() throws SQLException {
        // create tables
        TableUtils.createTableIfNotExists(connectionSource, Entry.class);
        TableUtils.createTableIfNotExists(connectionSource, Block.class);

        // make sure genesis block is present
        blockDao.createIfNotExists(Block.getGenesisBlock());
    }

    public long getBlocksCount() throws SQLException {
        return blockDao.countOf();
    }

    public Block getMostRecentBlock() throws SQLException {
        Block b = blockDao.queryForFirst(mostRecentBlockQuery);
        b.setEntriesList(entryDao.queryForEq("blockID", b.getBlockID()));
        return b;
    }

    /**
     * Gets the N most recent blocks on what is regarded as the
     * @param n
     * @return
     * @throws SQLException
     */
    public List<Block> getNMostRecentBlocks(int n) throws SQLException {
        // Sorted with the recent block at the header of the block
        // n = 2 means return the most recent block and the one before
        CloseableIterator<Block> initialResults = blockDao.queryBuilder()
                .orderBy("height", false)
                .orderBy("timeStamp", true)
                .where()
                .between("height", currentHeight - n + 1, currentHeight)
                .iterator();

        // place results in (ID => Block) map
        HashMap<byte[], Block> map = new HashMap<>();

        boolean firstResult = true;
        Block current = null;
        while (initialResults.hasNext()) {
            Block b = initialResults.next();
            if (firstResult) {
                current = b;
                firstResult = false;
            }
            map.put(b.getBlockID(), b);
        }

        if (current == null)
            return Collections.<Block>emptyList();

        // output list is at most n blocks long, but might be shorter.
        ArrayList<Block> output = new ArrayList<>(n);

        // now only return blocks in the main chain
        for (int i = 0; i < n && current != null; i++) {
            output.add(current);
            current = map.get(current.getPrevBlockHash());
        }

        return output;
    }

    public boolean insertBlock(Block b) throws SQLException {
        blockDao.create(b);
        List<Entry> entries = b.getEntriesList();
        for (Entry entry : entries) {
            entry.setBlockID(b.getBlockID());
            entryDao.createOrUpdate(entry);
        }
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
        return entryDao.queryBuilder().where().isNull("blockID").query();
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