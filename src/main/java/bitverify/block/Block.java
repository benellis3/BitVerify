package bitverify.block;

import bitverify.entries.*;
import bitverify.crypto.Hash;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.util.Arrays;

public class Block {
    private BlockHeader header;
    private List<Entry> entries;

    public Block(BlockHeader header, List<Entry> entries) {
        this.header = header;
        this.entries = entries;
    }
    
    public Block(Block prevBlock,int target){
        byte[] prevHeaderHash = prevBlock.header.hash();
        this.entries = new ArrayList<Entry>();
        createHeader(prevHeaderHash, target);
    }
    
    private Block(byte[] prevHeaderHash,int target){
        this.entries = new ArrayList<Entry>();
        createHeader(prevHeaderHash, target);
    }
    
    public static Block simpleGenesisBlock(){
        String mythology = "ARNOLD";
        byte[] prevHeadHash = Hash.hashString(mythology);
        Block resultBlock = new Block(prevHeadHash,7);
        return resultBlock;
    }
    
    public static Boolean validateBlock(Block prevBlock, BlockHeader currentHeader, List<Entry> entries){
//        pull target hash from proposed header since it's the only way to extract that information
        int targetHash = currentHeader.getTarget();
        Block createdBlock = new Block(prevBlock,targetHash);
        createdBlock.setEntriesList(entries);
        byte[] createdHeaderHash = createdBlock.header.hash();
        byte[] actualHeaderHash = currentHeader.hash();
        if(Arrays.areEqual(createdHeaderHash, actualHeaderHash)){
            return true;
        }
        return false;
    }
    
    public void setEntriesList(List<Entry> entryList){
        entries = entryList;
        createHeader();
    }
    
    public void addSingleEntry(Entry entry){
        entries.add(entry);
        createHeader();
    }

    /**
     * Recreate the header, retrieving our previous header hash and target from the old header.
     */
    private void createHeader() {
        createHeader(this.header.getPrevHeaderHash(), this.header.getTarget());
    }
    
    private void createHeader(byte[] prevHeaderHash, int target){
        byte[] entriesSerial;
        try {
            entriesSerial = serializeEntries();
            header = new BlockHeader(prevHeaderHash,entriesSerial,target);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public byte[] hashBlock() throws IOException{
        byte[] serialized = this.serialize();
        byte[] hashedBlockOnce = Hash.hashBytes(serialized);
        byte[] hashedBlockTwice = Hash.hashBytes(hashedBlockOnce);
        return hashedBlockTwice;
    }
    
    public byte[] serialize() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            serialize(out);
            return out.toByteArray();
        }
    }
    
    private byte[] serializeEntries() throws IOException{
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             DataOutputStream d = new DataOutputStream(out)){
            for(Entry e : entries){
                d.write(e.serialize());
            }
            return out.toByteArray();
        }
    }
    
    private void serialize(OutputStream out) throws IOException{
        try(DataOutputStream d = new DataOutputStream(out)){
            d.write(header.serialize());
            for(Entry e : entries){
                d.write(e.serialize());
            }
        }
    }

    public BlockHeader getHeader() {
        return header;
    }
}
