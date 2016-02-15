package bitverify.block;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import bitverify.crypto.KeyDecodingException;
import bitverify.entries.Entry;
import bitverify.entries.EntryTest;


public class BlockTest {
    
    Block genesis = Block.simpleGenesisBlock();
    
    @Test
    public void createGenesisBlock(){
        Block genesis = Block.simpleGenesisBlock();
        assertTrue(genesis.isVerified());
        assertEquals(genesis.getNonce(),58);
    }
    
    @Test
    public void singleEntrySerialize() throws IOException{
        Entry entry1;
        try {
            entry1 = EntryTest.generateEntry1();
        } catch (KeyDecodingException | IOException e) {
            fail();
            return;
        }
        
        List<Entry> entries = Arrays.asList(entry1);
        int target = 3;
        int nonce = 0;
        Block firstBlock = new Block(genesis,target,nonce,entries);;
        byte[] serialBlock = firstBlock.serialize();
        
        Block deserializedBlock = Block.deserialize(serialBlock);
        
        System.out.println(firstBlock.getPrevBlockHash());
        System.out.println(deserializedBlock.getPrevBlockHash());
        
        assertTrue(Arrays.equals(firstBlock.getPrevBlockHash(), deserializedBlock.getPrevBlockHash()));
        assertEquals(firstBlock.getTimeStamp(),deserializedBlock.getTimeStamp());
        assertTrue(Arrays.equals(firstBlock.hash(), deserializedBlock.hash()));
        
        
    }
    
}