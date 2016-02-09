package bitverify.persistence;


import bitverify.block.Block;
import bitverify.block.BlockHeader;
import bitverify.entries.Entry;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.support.ConnectionSource;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class DataStore {

    private ConnectionSource connectionSource;
    private Dao<Entry, UUID> entryDao;

    public DataStore(ConnectionSource cs) throws SQLException {
        connectionSource = cs;
        entryDao = DaoManager.createDao(connectionSource, Entry.class);
    }

    public int getNumberBlocks() throws SQLException{
    	//Placeholder
    	return 8;
    }
    
    public Block getMostRecentBlock() throws SQLException{
    	//Placeholder
    	return (new Block());
    }
    
    public Block getNthMostRecentBlock(int n) throws SQLException{
    	//Placeholder
    	return (new Block());
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
        // TODO: ammend entry class to contain a blockID of the parent block.
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
    
    public void createBlock(Block b) throws SQLException {
    	// Todo
    }


    // get most recent block (at end of chain)
    // get nth predecessor block from a given block

    // TODO: implement similar methods for Blocks, and any other functions we require.
}
