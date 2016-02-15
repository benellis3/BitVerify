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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.util.Arrays;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;


/**
 * This class is responsible for creating a block in our chain. Given another block, it can create its own hashes based on
 * these prior dependencies. There are also static methods to check the chain and create a block from a byte array.
 * 
 * @author Dominiquo Santistevan
 */
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
    
/**
 * The empty constructor is required for the database.
 */
    public Block(){}
    
    /**
     * 
     * @param prevBlock A block object that is the last known mined block in the chain to which this block will be attached.
     * @param target The integer value that mining calculates should be the number of zeros required to 'mine' this block.
     * @param nonce The changing value that is the free parameter input value to our hash that must reach the target zeros. 
     * @param entriesList List of Entry types that this block be the container for.  
     * @throws IOException Requires the list of entries to be serializable.
     */
    public Block(Block prevBlock,int target,int nonce, List<Entry> entriesList) throws IOException{
        this.prevBlockHash = prevBlock.hash();
        this.bitsTarget = target;
        this.timeStamp = System.currentTimeMillis();
        this.nonce = nonce; 
        this.entries = entriesList;
        this.verifiedEntries = true;
        this.entriesHash = Hash.hashBytes(serializeEntries());
//        this.blockID = this.hash();
    }
    
/**
 * 
 * This constructor will only be used by the deserialize method. That is why the timestamp is manually entered as well as
 * the hashes for the block and entries. When using this method, the block is not verified until it is given a list of 
 * entries that agrees with the initial hash that was deserialized. 
 * 
 * @param prevHash byte array storing the hash of the previous block to the block being deserialized.
 * @param entriesHash byte array storing the hash of the entries list corresponding to this block.
 * @param timeStamp time since epoch that was originally created by the miner.
 * @param bitsTarget The integer value that mining calculates should be the number of zeros required to 'mine' this block.
 * @param nonce The changing value that is the free parameter input value to our hash that must reach the target zeros. 
 */
        
    private Block(byte[] prevHash, byte[] entriesHash, long timeStamp, int bitsTarget, int nonce){
        this.prevBlockHash = prevHash;
        this.entriesHash = entriesHash;
        this.timeStamp = timeStamp;
        this.bitsTarget = bitsTarget;
        this.nonce = nonce;
//        this.entries = new ArrayList<Entry>();
        this.verifiedEntries = false;
    }
    /**
     * 
     * @return Static block that has no previous block. This will be the genesis block. 
     */
    public static Block simpleGenesisBlock(){
        String mythology = "ARNOLD";
        String itsComing = "You hear that, Mr. Anderson. That is the sound inevitability.";
        byte[] prevHash = Hash.hashString(mythology);
        byte[] entryHash = Hash.hashString(itsComing);
        long timeStamp = System.currentTimeMillis();
        int target = 3;
        int nonce = 58;
        Block resultBlock = new Block(prevHash,entryHash,timeStamp,target,nonce);
        resultBlock.verifiedEntries = true;
        return resultBlock;
    }
    
/**
 * This method is used after a block is received and deserialized but the corresponding entries have yet to be recieved. 
 * When a node receives, what is believed to be the correct entries, this method will set their values and also check
 * to make sure they haven't been tampered with throughout the sending process. 
 * 
 * @param entryList List of entries that should correspond to the current block
 * @return a boolean to indicate if the entered entries are the same as the ones indicated in the hash of the deserialized block.
 */
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
    
    /**
     * adds one to the nonce value for the miner.
     */
    public void incrementNonce(){
        nonce += 1;
    }
    
    /**
     * 
     * @param blockList List of blocks that represents a subchain in the total blockchain therefore order should be preserved
     * @return boolean to indicate whether the given subchain is valid or not
     * @throws Exception Method is expecting a list of Entries to verify, so the list should have size > 0. 
     */
    public static boolean verifyChain(List<Block> blockList) throws Exception{
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
    
    /**
     * Creates a array hash for the current block. 
     * @return 32 byte array hash
     */
    public byte[] hash() {
        ByteArrayOutputStream b = new ByteArrayOutputStream(Hash.HASH_LENGTH);
        try {
            serialize(b);
        } catch (IOException e) {
            // we are in control of creating the stream here so this will never happen, but just in case...
            e.printStackTrace();
        }
        byte[] firstHash = Hash.hashBytes(b.toByteArray());
        byte[] secondHash = Hash.hashBytes(firstHash);
        return secondHash;
    }
    
    /**
     * 
     * @param the byte array that should contain the data for a valid block.
     * @return the unverified block corresponding to the given byte array
     * @throws IOException if there is some data in the byte array that does not fit the format of the block serialization
     */
    public static Block deserialize(byte[] data) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        return Block.deserialize(in);
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
    
    /**
     * 
     * @return byte array to be sent over the network and later unpacked
     * @throws IOException 
     */
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
      
      public boolean isVerified(){
          return this.verifiedEntries;
      }
}
