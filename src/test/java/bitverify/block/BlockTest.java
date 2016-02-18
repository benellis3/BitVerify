package bitverify.block;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import bitverify.crypto.KeyDecodingException;
import bitverify.entries.Entry;
import bitverify.entries.EntryTest;
import bitverify.mining.Miner;


public class BlockTest {
    
    Block genesis = Block.getGenesisBlock();
    
    @Test
    public void createGenesisBlock(){
        Block genesis = Block.getGenesisBlock();
        assertTrue(genesis.isVerified());
        assertEquals(genesis.getNonce(),0);
    }
    
    @Test
    public void singleEntrySerialize() throws IOException{
        Entry entry1 = EntryTest.generateEntry1();
        
        List<Entry> entries = Arrays.asList(entry1);
        int target = 0;
        int nonce = 0;
        Block firstBlock = new Block(genesis,target,nonce,entries);
        byte[] serialBlock = firstBlock.serializeHeader();
        
        Block deserializedBlock = Block.deserialize(serialBlock);
        
        assertTrue(firstBlock.equals(deserializedBlock));
        
    }
    
    @Test
    public void listTwoValidBlocksVerified() throws Exception{
        Entry entry1 = EntryTest.generateEntry1();
        List<Entry> entries1 = Arrays.asList(entry1);
        int target = 3;
        int nonce = 0;
        
        Block block1 = new Block(genesis,target,nonce,entries1);
        
        assertTrue(Block.verifyChain(Arrays.asList(genesis,block1)));
        
    }
    
    @Test
    public void listTwoInvalidBlocks() throws Exception{
        Entry entry1 = EntryTest.generateEntry1();
        Entry entry2 = EntryTest.generateEntry2();
        List<Entry> entries1 = Arrays.asList(entry1);
        List<Entry> entries2 = Arrays.asList(entry2);
        int target = 3;
        int nonce = 0;
        
        Block block1 = new Block(genesis,target,nonce,entries1);
        Block block2 = new Block(block1,target,nonce,entries2);
        
        assertFalse(Block.verifyChain(Arrays.asList(genesis,block2)));
    }
    
    @Test
    public void setOneEntryValid() throws IOException{
        Entry entry1 = EntryTest.generateEntry1();
        
        List<Entry> entries = Arrays.asList(entry1);
        int target = 3;
        int nonce = 0;
        Block firstBlock = new Block(genesis,target,nonce,entries);
        byte[] serialBlock = firstBlock.serializeHeader();
        Block deserializedBlock = Block.deserialize(serialBlock);
        assertTrue(deserializedBlock.setEntriesList(Arrays.asList(entry1)));
    }
    
    @Test
    public void setOneEntryInvalid() throws IOException{
        Entry entry1 = EntryTest.generateEntry1();
        Entry entry2 = EntryTest.generateEntry2();
        
        List<Entry> entries = Arrays.asList(entry1);
        int target = 3;
        int nonce = 0;
        Block firstBlock = new Block(genesis,target,nonce,entries);
        byte[] serialBlock = firstBlock.serializeHeader();
        Block deserializedBlock = Block.deserialize(serialBlock);
        assertFalse(deserializedBlock.setEntriesList(Arrays.asList(entry2)));
        
    }
}
