package bitverify.persistence;


import bitverify.block.Block;
import bitverify.block.BlockHeader;
import bitverify.entries.Entry;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class DatabaseStore implements DataStore {

    private ConnectionSource connectionSource;
    private Dao<Entry, UUID> entryDao;
    private Dao<Block, byte[]> blockDao;

    private PreparedQuery<Block> mostRecentBlockQuery;

    public DatabaseStore(String databasePath) throws SQLException {
        connectionSource = new JdbcConnectionSource(databasePath);
        entryDao = DaoManager.createDao(connectionSource, Entry.class);
        blockDao = DaoManager.createDao(connectionSource, Block.class);

        initialSetup();

        mostRecentBlockQuery = blockDao.queryBuilder().orderBy("height", false).orderBy("timeStamp", true).limit(1L).prepare();
    }

    private void initialSetup() throws SQLException {
        // create tables
        TableUtils.createTableIfNotExists(connectionSource, Entry.class);
        TableUtils.createTableIfNotExists(connectionSource, BlockHeader.class);

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

    public List<Block> getNMostRecentBlocks(int n) throws SQLException {
        //Sorted with the recent block at the header of the block
        //n = 2 means return the most recent block and the one before
        return null;
    }

    public void createBlock(Block b) throws SQLException {
        blockDao.create(b);
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
        return entryDao.queryForEq("blockID", null);
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