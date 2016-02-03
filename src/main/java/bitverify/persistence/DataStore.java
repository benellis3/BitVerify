package bitverify.persistence;


import bitverify.entries.Entry;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.support.ConnectionSource;

import java.sql.SQLException;

public class DataStore {

    private ConnectionSource connectionSource;
    private Dao<Entry, Long> entryDao;

    public DataStore(ConnectionSource cs) throws SQLException {
        connectionSource = cs;
        entryDao = DaoManager.createDao(connectionSource, Entry.class);
    }

    public Entry getEntryById(long id) throws SQLException {
        return entryDao.queryForId(id);
    }

    public void updateEntry(Entry e) throws SQLException {
        entryDao.update(e);
    }

    public void deleteEntry(Entry e) throws SQLException {
        entryDao.delete(e);
    }

    // TODO: implement similar methods for Blocks, and any other functions we require.
}
