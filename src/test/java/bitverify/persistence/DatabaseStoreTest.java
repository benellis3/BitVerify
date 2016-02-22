package bitverify.persistence;

import bitverify.block.Block;
import bitverify.entries.Entry;
import bitverify.entries.EntryTest;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import static org.junit.Assert.assertEquals;

import com.j256.ormlite.table.TableUtils;
import org.junit.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by Rob on 13/02/2016.
 */
public class DatabaseStoreTest {
    static class TestObject {
        @DatabaseField(dataType = DataType.BYTE_ARRAY, index=true, columnDefinition = "VARBINARY(16)")
        private byte[] ID;
        @DatabaseField
        private String name;
        TestObject() { }

        public TestObject(byte[] ID, String name) {
            this.ID = ID;
            this.name = name;
        }
    }

    static class TestObject2 {
        @DatabaseField(dataType = DataType.BYTE_ARRAY, uniqueIndex = true)
        private byte[] blockID;
        @DatabaseField()
        private int entryID;

        TestObject2() { }

        public TestObject2(byte[] blockID, int entryID) {
            this.blockID = blockID;
            this.entryID = entryID;
        }
    }

    @Test
    public void TestDatabase() throws SQLException {
        DataStore ds = new DatabaseStore("jdbc:h2:mem:bitverifytest");

        // genesis block should be present and most recent
        Block b1 = ds.getBlock(Block.getGenesisBlock().getBlockID());
        Block b2 = ds.getMostRecentBlock();
        assertEquals(b1, b2);

        // insert a couple of entries
        Entry e1 = EntryTest.generateEntry1();
        ds.insertEntry(e1);
        Entry e2 = EntryTest.generateEntry2();
        ds.insertEntry(e2);

        // should get resulting entries as unconfirmed
        List<Entry> e = ds.getUnconfirmedEntries();
        assertEquals(2, e.size());
        assertEquals(e.get(0).getEntryID(), e1.getEntryID());
        assertEquals(e.get(1).getEntryID(), e2.getEntryID());

        // inserting a duplicate block should fail leaving only 1 block in store
        InsertBlockResult inserted = ds.insertBlock(Block.getGenesisBlock());
        assertEquals(1, ds.getBlocksCount());
        assertEquals(InsertBlockResult.FAIL_ORPHAN, inserted);
    }

    @Test
    public void TestUnique() throws SQLException {
        // make a database
        ConnectionSource cs = new JdbcConnectionSource("jdbc:h2:mem:account");
        TableUtils.createTableIfNotExists(cs, TestObject2.class);
        Dao<TestObject2, byte[]> testDao = DaoManager.createDao(cs, TestObject2.class);

        TestObject2 o = new TestObject2(new byte[] {1,2,3}, 5);
        TestObject2 o2 = new TestObject2(new byte[] {1,2,3}, 6);
        testDao.create(o);
        try {
            testDao.create(o2);
        } catch (SQLException e) {
            System.out.print("Cause: ");
            System.out.println(e.getCause());
            System.out.print("Cause code: ");
            System.out.println(((SQLException) e.getCause()).getErrorCode());
            System.out.print("Cause state: ");
            System.out.println(((SQLException) e.getCause()).getSQLState());
        }

        assertEquals(testDao.countOf(), 1);

    }

    @Test
    public void TestVarbinary() throws SQLException {
        // make a database
        ConnectionSource cs = new JdbcConnectionSource("jdbc:h2:mem:account");
        TableUtils.createTableIfNotExists(cs, TestObject.class);
        Dao<TestObject, byte[]> testDao = DaoManager.createDao(cs, TestObject.class);

        Random r = new Random();

        byte[] special = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};

        for (int i = 0; i < 100; i++) {
            byte[] b = new byte[16];
            r.nextBytes(b);
            testDao.create(new TestObject(b, "bob" + i));
        }

        testDao.create(new TestObject(special, "special"));

        for (int i = 100; i < 200; i++) {
            byte[] b = new byte[16];
            r.nextBytes(b);
            testDao.create(new TestObject(b, "bob" + i));
        }

        assertEquals(testDao.queryForEq("ID", special).get(0).name, "special");
    }
    
    @Test
    public void TestInsertBlocks() throws SQLException {
        DataStore ds = new DatabaseStore("jdbc:h2:mem:bitverifytest");

        ArrayList<Entry> entryList = new ArrayList<Entry>();
        
        for (int x = 0; x < 100; x++){
        	entryList.add(EntryTest.generateEntry1());
        }
        
        long timeStamp = 100;
        
        Block initialBlock = Block.getGenesisBlock();
        Block prevBlock = initialBlock;
        Block block = initialBlock;
        
        int numBlocks = 300;
        
        for (int x = 0; x < numBlocks; x++){
        	timeStamp += 100;
        	block = new Block(prevBlock,timeStamp,0x03000004,0,entryList);
        	ds.insertBlock(block);
        	prevBlock = block;
        	//Check the block registers as existing in the database
        	assertEquals(true,ds.blockExists(block.getBlockID()));
        }
        
        //Check the number of blocks in the database (+1 because of the genesis block)
        assertEquals(numBlocks+1,ds.getBlocksCount());
        //Check get most recent block actually gets the most recent block
        assertEquals(block,ds.getMostRecentBlock());
        
        //Test the block iterator
        DatabaseIterator<Block> blockIt = ds.getAllBlocks();
        
        Block curBlock;
        
        int height = 0;
        
        while (blockIt.moveNext()){
        	curBlock = blockIt.current();
        	assertEquals(height++,curBlock.getHeight());
        	assertEquals(true,ds.blockExists(curBlock.getBlockID()));
        }
        
        //Starting from the end of the blockchain
        List<Block> nMost = ds.getNMostRecentBlocks(numBlocks+1, block);
        
        assertEquals(numBlocks+1,nMost.size());
        assertEquals(initialBlock,nMost.get(numBlocks));
        
        //Test if blocks not in datatabase
        Block b1 = new Block(Block.getGenesisBlock(),100,0x03000004,0,entryList);
        assertEquals(false,ds.blockExists(b1.getBlockID()));
        
        //Check returns all blocks when we ask for N greater than the length of blockchain
        int erroneousN = numBlocks+10;
        
        List<Block> nMostError = ds.getNMostRecentBlocks(erroneousN, block);
        
        assertEquals(nMostError.size(),numBlocks+1);
        
		
    }

}
