package bitverify.block;

import java.nio.ByteBuffer;

import bitverify.entries.*;
import bitverify.crypto.Hash;

//import bitverify.crypto;

public class Block {
    public Integer blockSize;
    public BlockHeader header;
    public Entry entry;
    
    
    
    
    public Block(Block PrevBlock,Entry entry){
        this.entry = entry;             //ask Laszlo to make a method or something to get the number of entries or just abandon these 
                                        //from the block header
        
        System.out.println("okay, block has been made.");
        
    }
    
    public Block(byte[] serialized){
        int headerSize = header.HEADER_SIZE;
        int 
    }
    
    public Block(){
        System.out.println("okay, block has been made.");
    }
    
    public String hashBlock(){
        byte[] serialized = this.serializeBlock();
        return Hash.hashBytes(serialized);
    }
    
    public byte[] serializeBlock(){
        int size = calculateSize();
        byte[] finalArray = new byte[size];
        ByteBuffer byteBuffer = ByteBuffer.wrap(finalArray);
//        byte[] serialeEntry = entry.serialize();      Ask if this can become a method or if there is any way
        byte[]  headerSerial = header.serialize();
        
        byteBuffer.put(headerSerial);
//        byteBuffer.put(serialEntry);
        
        return finalArray;
    }
    
    
    //talk about this method with Laszo on how to get serialized size of entry
    private int calculateSize(){
        return (Integer) null;
    }
    
    public BlockHeader unpackBlock(Block prevBlock){
//         unparse a block of raw bytes
//        BlockHeader head = new BlockHeader(prevBlock);
//        return head;
        return null;
    }
    
}
