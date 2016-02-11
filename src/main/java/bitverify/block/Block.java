package bitverify.block;

import bitverify.entries.*;
import bitverify.crypto.Hash;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.util.Arrays;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;

public class Block {
    @DatabaseField(dataType = DataType.BYTE_ARRAY)
    private byte[] blockID;
    // N.B. hashes are 32 bytes long. Refer to constant Hash.HASH_LENGTH.
    @DatabaseField(dataType = DataType.BYTE_ARRAY)
    private byte[] prevBlockHash;
    @DatabaseField(dataType = DataType.BYTE_ARRAY)
    private byte[] entriesHash;
    @DatabaseField
    private long timeStamp;
    @DatabaseField
    private int bitsTarget;
    @DatabaseField
    private int nonce = 0;
    @DatabaseField
    private long height;
    @DatabaseField
    private byte[] ID;
    private List<Entry> entries;
    private boolean verifiedEntries;
    
    
    public Block(){}
    
    public Block(Block prevBlock,int target,int nonce, List<Entry> entriesList){
        this.prevBlockHash = prevBlock.hash();
        this.bitsTarget = target;
        this.timeStamp = System.currentTimeMillis();
        this.nonce = nonce; 
        this.entries = entriesList;
        this.verifiedEntries = true;
        this.blockID = this.hash();
    }
    
//    constructor that is used when a block is deserialized 
    public Block(byte[] prevHash, byte[] entriesHash, long timeStamp, int bitsTarget, int nonce){
        this.prevBlockHash = prevHash;
        this.entriesHash = entriesHash;
        this.timeStamp = timeStamp;
        this.bitsTarget = bitsTarget;
        this.nonce = nonce;
        this.verifiedEntries = false;
    }
    
//    TODO: make this constructor for the genesis block to use
    private Block(byte[] prevHeaderHash,int target){
        this.entries = new ArrayList<Entry>();
//        createHeader(prevHeaderHash, target);
    }
    
    public static Block simpleGenesisBlock(){
        String mythology = "ARNOLD";
        byte[] prevHeadHash = Hash.hashString(mythology);
        Block resultBlock = new Block(prevHeadHash,5);
        return resultBlock;
    }
    
    public boolean setEntriesList(List<Entry> entryList){
        entries = entryList;
        try {
            byte[] entriesSerial = serializeEntries();
            byte[] calculatedHash = Hash.hashBytes(entriesSerial);
            if(Arrays.areEqual(this.entriesHash, calculatedHash)){
                this.verifiedEntries = true;
                return this.verifiedEntries;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }
    
    public void incrementNonce(){
        nonce += 1;
    }
    
    public Boolean checkNonce(){
        Boolean check = 1>0;
        if(check){
            return true;
        }else{
            return false;
        }
    }
    
    public static Boolean verifyChain(List<Block> blockList) throws Exception{
        int listLen = blockList.size();
        if(blockList.isEmpty()){
            Exception e = new IllegalStateException();
            throw e;
        }else if(listLen == 1){
            return true;
        }else{
            Block prevBlock = blockList.get(0);
            Block currentBlock;
            byte[] prevBlockHash = prevBlock.hash();
            byte[] currentBlockPrevHash;
            for(int i = 1; i < listLen; i++){
                currentBlock = blockList.get(i);
                currentBlockPrevHash = currentBlock.getPrevBlockHash();
                if(!Arrays.areEqual(prevBlockHash, currentBlockPrevHash)){
                    return false;
                }
                prevBlock = currentBlock;
                prevBlockHash = prevBlock.hash();
            }
        }
        return true;
    }
    
    public byte[] hash() {
        ByteArrayOutputStream b = new ByteArrayOutputStream(Hash.HASH_LENGTH);
        try {
            serialize(b);
        } catch (IOException e) {
            // we are in control of creating the stream here so this will never happen, but just in case...
            e.printStackTrace();
        }
        return Hash.hashBytes(b.toByteArray());
//        TODO: hash twice
    }
    
    public static Block deserialize(byte[] data) throws IOException {
//        ByteArrayInputStream in = new ByteArrayInputStream(data);
//        return BlockHeader.deserialize(in);
        return new Block();
    }
    
    private static Block deserialize(InputStream in) throws IOException {
        // DataInputStream allows us to read in primitives in binary form.
        try (DataInputStream d = new DataInputStream(in)) {
            // establish a pair of 32-byte buffers to read in our hashes as byte arrays
            byte[] previousBlockHash = new byte[Hash.HASH_LENGTH];
            d.readFully(previousBlockHash);
            byte[] entriesHash = new byte[Hash.HASH_LENGTH];
            d.readFully(entriesHash);
            // instantiate while reading in the remaining fields - timestamp, bitsTarget , nonce
            return new Block(previousBlockHash, entriesHash, d.readLong(), d.readInt(), d.readInt());
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
    
    public byte[] serialize() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            serialize(out);
            return out.toByteArray();
        }
      }
    
    private void serialize(OutputStream out) throws IOException{
      try(DataOutputStream d = new DataOutputStream(out)){
          d.write(prevBlockHash);
          d.write(entriesHash);
          d.writeLong(timeStamp);
          d.writeInt(bitsTarget);
          d.writeInt(nonce);
      }
    }
    
    public void setHeight(long height){
        this.height = height;
    }
    
    public void setNonce(int nonce){
        this.nonce = nonce;
    }
    
    
//  GETTER METHODS

      public byte[] getBlockID() { return blockID; }
    
      public byte[] getPrevBlockHash(){
          return prevBlockHash;
      }
    
      public byte[] getEntriesHash() { return entriesHash; }
    
      public long getTimeStamp(){
          return timeStamp;
      }
      
      public long getHeight(){
          return height;
      }
    
      public int getTarget(){
          return bitsTarget;
      }
    
      public int getNonce(){
          return nonce;
      }    
}
