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

//import bitverify.crypto;

public class Block {
    public Integer blockSize;
    public BlockHeader header;
    public List<Entry> entries;
    public int target;
    public byte[] prevHeaderHash;
    
    public Block(Block prevBlock,int target){
        prevHeaderHash = prevBlock.header.hash();
        this.entries = new ArrayList<Entry>();
        this.target = target;
        createHeader();
    }
    
    private Block(byte[] prevHeaderHash,int target){
        this.prevHeaderHash = prevHeaderHash;
        this.target = target;
        this.entries = new ArrayList<Entry>();
        createHeader();
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
    
    private void createHeader(){
        byte[] entriesSerial;
        try {
            entriesSerial = serializeEntries();
            header = new BlockHeader(this.prevHeaderHash,entriesSerial,this.target);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public String hashBlock() throws IOException{
        byte[] serialized = this.serialize();
        String hashedBlockOnce = Hash.hashBytesToString(serialized);
        String hashedBlockTwice = Hash.hashStringToString(hashedBlockOnce);
        return hashedBlockTwice;
    }
    
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serialize(out);
        return out.toByteArray();
    }
    
    private byte[] serializeEntries() throws IOException{
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try(DataOutputStream d = new DataOutputStream(out)){
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
}
