package bitverify.persistence;


import bitverify.block.Block;
import bitverify.block.BlockHeader;
import bitverify.entries.Entry;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.support.ConnectionSource;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class DatabaseStore implements DataStore {

    private ConnectionSource connectionSource;
    private Dao<Entry, UUID> entryDao;
    private Dao<BlockHeader, byte[]> blockHeaderDao;

    private PreparedQuery<BlockHeader> mostRecentBlockHeaderQuery;

    public DatabaseStore(ConnectionSource cs) throws SQLException {
        connectionSource = cs;
        entryDao = DaoManager.createDao(connectionSource, Entry.class);
        blockHeaderDao = DaoManager.createDao(connectionSource, BlockHeader.class);

        mostRecentBlockHeaderQuery = blockHeaderDao.queryBuilder().orderBy("height", false).orderBy("timeStamp", true).limit(1L).prepare();
    }

    public long getBlocksCount() throws SQLException {
    	return blockHeaderDao.countOf();
    }
    
    public Block getMostRecentBlock() throws SQLException {
        BlockHeader bh = blockHeaderDao.queryForFirst(mostRecentBlockHeaderQuery);
        List<Entry> entries = entryDao.queryForEq("blockID", bh.getBlockID());
        return new Block(bh, entries);
    }

    public List<Block> getNMostRecentBlocks(int n) throws SQLException {
        //Sorted with the recent block at the header of the block
        //n = 2 means return the most recent block and the one before
        return null;
    }

    public void createBlock(Block b) throws SQLException {
        blockHeaderDao.create(b.getHeader());
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


    // TODO: implement similar methods for Blocks, and any other functions we require.
}
