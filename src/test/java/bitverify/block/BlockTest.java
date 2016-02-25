package bitverify.block;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.squareup.otto.Bus;
import com.squareup.otto.ThreadEnforcer;
import org.junit.Test;

import bitverify.crypto.KeyDecodingException;
import bitverify.entries.Entry;
import bitverify.entries.EntryTest;
import bitverify.mining.Miner;


public class BlockTest {
    static Bus bus = new Bus(ThreadEnforcer.ANY);

    @Test
    public void createGenesisBlock() throws Exception{
        Block genesis = Block.getGenesisBlock();
        assertTrue(genesis.isVerified());
        assertTrue(Block.verifyChain(Arrays.asList(genesis), bus));
    }

    @Test
    public void changedNonceGenesis() throws Exception{
        Block genesis = Block.getGenesisBlock();
        genesis.incrementNonce();
        assertFalse(Block.verifyChain(Arrays.asList(genesis), bus));
    }

    @Test
    public void singleEntrySerialize() throws Exception{
        Block genesis = Block.getGenesisBlock();
        Entry entry1 = EntryTest.generateEntry1();

        List<Entry> entries = Arrays.asList(entry1);
        int target = Integer.MAX_VALUE;
        int nonce = 0;
        Block firstBlock = new Block(genesis,target,nonce,entries);
        byte[] serialBlock = firstBlock.serializeHeader();

        Block deserializedBlock = Block.deserialize(serialBlock);

        assertTrue(firstBlock.equals(deserializedBlock));
        assertFalse(deserializedBlock.isVerified());
        assertTrue(deserializedBlock.setEntriesList(entries));
        assertTrue(deserializedBlock.isVerified());
        assertTrue(Block.verifyChain(Arrays.asList(genesis,firstBlock), bus));
    }

    @Test
    public void listTwoValidBlocksVerified() throws Exception{
        Block genesis = Block.getGenesisBlock();
        Entry entry1 = EntryTest.generateEntry1();
        List<Entry> entries1 = Arrays.asList(entry1);
        int target = Integer.MAX_VALUE;
        int nonce = 0;

        Block block1 = new Block(genesis,target,nonce,entries1);

        assertTrue(Block.verifyChain(Arrays.asList(genesis,block1), bus));

    }

    @Test
    public void listTwoBlocksOneNotMeetsTarget() throws Exception{
        Block genesis = Block.getGenesisBlock();
        Entry entry1 = EntryTest.generateEntry1();
        List<Entry> entries1 = Arrays.asList(entry1);
        int target = 0;
        int nonce = 0;

        Block block1 = new Block(genesis,target,nonce,entries1);

        assertFalse(Block.verifyChain(Arrays.asList(genesis,block1), bus));
        assertFalse(block1.setEntriesList(entries1));
    }

    @Test
    public void listTwoInvalidBlocks() throws Exception{
        Block genesis = Block.getGenesisBlock();
        Entry entry1 = EntryTest.generateEntry1();
        Entry entry2 = EntryTest.generateEntry2();
        List<Entry> entries1 = Arrays.asList(entry1);
        List<Entry> entries2 = Arrays.asList(entry2);
        int target = Integer.MAX_VALUE;
        int nonce = 0;

        Block block1 = new Block(genesis,target,nonce,entries1);
        Block block2 = new Block(block1,target,nonce,entries2);

        assertFalse(Block.verifyChain(Arrays.asList(genesis,block2), bus));
    }

    @Test
    public void listTwoInvalidTimeStamps() throws Exception{
        Block genesis = Block.getGenesisBlock();
        Entry entry1 = EntryTest.generateEntry1();
        List<Entry> entries1 = Arrays.asList(entry1);
        int target = Integer.MAX_VALUE;
        int nonce = 0;

        Block block1 = new Block(genesis,1455745984017l,target,nonce,entries1);

        assertFalse(Block.verifyChain(Arrays.asList(genesis,block1), bus));
    }

    @Test
    public void setOneEntryValid() throws IOException{
        Block genesis = Block.getGenesisBlock();
        Entry entry1 = EntryTest.generateEntry1();

        List<Entry> entries = Arrays.asList(entry1);
        int target = Integer.MAX_VALUE;
        int nonce = 0;
        Block firstBlock = new Block(genesis,target,nonce,entries);
        byte[] serialBlock = firstBlock.serializeHeader();
        Block deserializedBlock = Block.deserialize(serialBlock);
        assertTrue(deserializedBlock.setEntriesList(Arrays.asList(entry1)));
    }

    @Test
    public void setOneEntryInvalid() throws IOException{
        Block genesis = Block.getGenesisBlock();
        Entry entry1 = EntryTest.generateEntry1();
        Entry entry2 = EntryTest.generateEntry2();

        List<Entry> entries = Arrays.asList(entry1);
        int target = Integer.MAX_VALUE;
        int nonce = 0;
        Block firstBlock = new Block(genesis,target,nonce,entries);
        byte[] serialBlock = firstBlock.serializeHeader();
        Block deserializedBlock = Block.deserialize(serialBlock);
        assertFalse(deserializedBlock.setEntriesList(Arrays.asList(entry2)));

    }



}
