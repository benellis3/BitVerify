package bitverify.persistence;

import bitverify.block.Block;
import bitverify.entries.Entry;
import bitverify.entries.EntryTest;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.logger.LocalLog;
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
        
        int oldNumBlocks = numBlocks;
        
        //Create a new chain
        initialBlock = Block.getGenesisBlock();
        prevBlock = initialBlock;
        block = initialBlock;

        numBlocks = oldNumBlocks-1;

        for (int x = 0; x < numBlocks; x++){
        	timeStamp += 100;
        	block = new Block(prevBlock,timeStamp,0x03000004,0,entryList);
        	ds.insertBlock(block);
        	prevBlock = block;
        	//Check the block registers as existing in the database
        	assertEquals(true,ds.blockExists(block.getBlockID()));
        }
        
        //This new chain is shorter than the old one
        assertEquals(false,ds.isBlockOnActiveChain(block.getBlockID()));
        
        
        for (int x = 0; x < 2; x++){
        	timeStamp += 100;
        	block = new Block(prevBlock,timeStamp,0x03000004,0,entryList);
        	ds.insertBlock(block);
        	prevBlock = block;
        	//Check the block registers as existing in the database
        	assertEquals(true,ds.blockExists(block.getBlockID()));
        }
        
      //This new chain is now one block longer than the old one, it should be primary chain
      assertEquals(true,ds.isBlockOnActiveChain(block.getBlockID()));
      //It has one more block than the previous chain
      assertEquals(oldNumBlocks+1,block.getHeight());
    }
    
    @Test
    public void TestInsertEntries() throws SQLException {
    	DataStore dataStore = new DatabaseStore("jdbc:h2:mem:bitverifytest2");
    	
    	//Insert single unconfirmed entry
    	dataStore.insertEntry(EntryTest.generateEntry1());
    	
    	//Check there are the same number of unconfirmed entries as total entries
    	DatabaseIterator<Entry> entriesIT = dataStore.getAllEntries();
    	List<Entry> unComfirmedEntriesIT = dataStore.getUnconfirmedEntries();
    	
    	int entriesITSize = 0;
    	
    	while (entriesIT.moveNext()){
    		entriesITSize++;
    	}
    	
    	assertEquals(unComfirmedEntriesIT.size(),entriesITSize);
    	
    	//Check confirmed entries are correctly added
    	ArrayList<Entry> entries = new ArrayList<Entry>();
    	entries.add(EntryTest.generateEntry1());
    	
    	ArrayList<Entry> entries2 = new ArrayList<Entry>();
    	entries2.add(EntryTest.generateEntry2());
    	
    	//Add blocks to chain containing the two generated entries
    	Block block = new Block(Block.getGenesisBlock(),100,0x03000004,0,entries);
    	Block block2 = new Block(block,100,0x03000004,0,entries2);
    	
    	dataStore.insertBlock(block);
    	dataStore.insertBlock(block2);
    	
    	assertEquals(dataStore.getBlocksCount(),3);
    	
    	DatabaseIterator<Entry> entryIT = dataStore.getConfirmedEntries();

        ArrayList<Entry> entriesBack = new ArrayList<Entry>();

        while (entryIT.moveNext()){
        	entriesBack.add(entryIT.current());
        }
    	
        assertEquals(2,entriesBack.size());
    }

    @Test
    public void activeBlockchainSamplingTest() throws SQLException {
        System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");

        int[] numBlocks =  {1, 4, 7, 12, 16, 21, 27, 33, 39};
        int[] sampleSize = {3, 4, 5, 6,  7,  10, 12, 16, 17};
        for (int x = 0; x < numBlocks.length; x++) {
            DataStore ds = new DatabaseStore("jdbc:h2:mem:bitverifytest3" + x);
            Block prev = ds.getMostRecentBlock();
            for (int i = 0; i < numBlocks[x] - 1; i++) {
                prev = new Block(prev, 0, 0, new ArrayList<>());
                ds.insertBlock(prev);
            }
            assertEquals(numBlocks[x], ds.getBlocksCount());

            List<byte[]> activeBlocksSample = ds.getActiveBlocksSample(sampleSize[x]);
            assertEquals(Math.min(sampleSize[x], numBlocks[x]), activeBlocksSample.size());
            System.out.println("Suceeded for x=" + x);
        }

    }

}
