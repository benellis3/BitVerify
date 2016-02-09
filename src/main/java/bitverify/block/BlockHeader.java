package bitverify.block;

import bitverify.crypto.Hash;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;

import java.io.*;
import java.util.*;

public class BlockHeader {
    // N.B. hashes are 32 bytes long. Refer to constant Hash.HASH_LENGTH.
    @DatabaseField(dataType = DataType.BYTE_ARRAY)
    private byte[] prevHeaderHash;
    @DatabaseField(dataType = DataType.BYTE_ARRAY)
    private byte[] entriesHash;
    @DatabaseField
    private long timeStamp;
    @DatabaseField
    private int bitsTarget;
    @DatabaseField
    private int nonce = 0;
    
    
    public BlockHeader(byte[] prevBlockHeaderHash,byte[] entriesHash, int target){
        this.prevHeaderHash = prevBlockHeaderHash;
        this.entriesHash = entriesHash;
        this.timeStamp = System.currentTimeMillis();
        this.bitsTarget = target;                  //need to read into how this is done over time and agreed upon by all
    }
    
    public BlockHeader(byte[] prevHash, byte[] entriesHash, long timeStamp, int bitsTarget, int nonce){
        this.prevHeaderHash = prevHash;
        this.entriesHash = entriesHash;
        this.timeStamp = timeStamp;
        this.bitsTarget = bitsTarget;
        this.nonce = nonce;
    }
    
    public static BlockHeader deserialize(byte[] data) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        return BlockHeader.deserialize(in);
    }
    
    private static BlockHeader deserialize(InputStream in) throws IOException {
        // DataInputStream allows us to read in primitives in binary form.
        try (DataInputStream d = new DataInputStream(in)) {
            // establish a pair of 32-byte buffers to read in our hashes as byte arrays
            byte[] prevHeaderHash = new byte[Hash.HASH_LENGTH];
            d.readFully(prevHeaderHash);
            byte[] entriesHash = new byte[Hash.HASH_LENGTH];
            d.readFully(entriesHash);
            // instantiate while reading in the remaining fields - timestamp, bitsTarget , nonce
            return new BlockHeader(prevHeaderHash, entriesHash, d.readLong(), d.readInt(), d.readInt());
        }
    }
    
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serialize(out);
        return out.toByteArray();
    }
    
    private void serialize(OutputStream out) throws IOException {
        // DataOutputStream allows us to write primitives in binary form.
        try (DataOutputStream d = new DataOutputStream(out)) {
            // write out each field in binary form, in declaration order.
            d.write(prevHeaderHash);
            d.write(entriesHash);
            d.writeLong(timeStamp);
            d.writeInt(bitsTarget);
            d.writeInt(nonce);
        }
    }

    /**
     * Compute the hash of this block header.
     */
    public byte[] hash() {
        ByteArrayOutputStream b = new ByteArrayOutputStream(Hash.HASH_LENGTH);
        try {
            serialize(b);
        } catch (IOException e) {
            // we are in control of creating the stream here so this will never happen, but just in case...
            e.printStackTrace();
        }
        return Hash.hashBytes(b.toByteArray());
    }

    
    
//    Speak with Alex about how this should be done to prevent concurrency issues.
    public Boolean incrementNonce(){
        nonce += 1;
        return true;
    }
    
    //fill in code to check the number of bits with the target number of bits after hashing the nonce value
    //move this method to the Block module maybe to make it easier?
//    TODO: 
    public Boolean checkNonce(){
        Boolean check = 1>0;
        if(check){
            return true;
        }else{
            return false;
        }
    }
    
    public static Boolean verifyHeaders(List<BlockHeader> headerList) throws Exception{
        int listLen = headerList.size();
        Boolean isValid = true;
        if(headerList.isEmpty()){
            Exception e = new IllegalStateException();
            throw e;
        }else if(listLen == 1){
            return isValid;
        }else{
            BlockHeader prevBlock = headerList.get(0);
            BlockHeader currentBlock;
            byte[] prevBlockHash = prevBlock.hash();
            byte[] currentBlockPrevHash;
            for(int i = 1; i < listLen; i++){
                currentBlock = headerList.get(i);
                currentBlockPrevHash = currentBlock.getPrevHeaderHash();
                if(!Arrays.equals(currentBlockPrevHash, prevBlockHash)){
                    isValid = false;
                }
                
                prevBlock = currentBlock;
                prevBlockHash = prevBlock.hash();
            }
        }
        return isValid;
    }

    
//    GETTER METHODS
    
    public byte[] getPrevHeaderHash(){
        return prevHeaderHash;
    }

    public byte[] getEntriesHash() { return entriesHash; }

    public long getTimeStamp(){
        return timeStamp;
    }
    
    public int getTarget(){
        return bitsTarget;
    }
    
    public int getNonce(){
        return nonce;
    }
}
