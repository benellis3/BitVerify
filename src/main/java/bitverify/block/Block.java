package bitverify.block;

import bitverify.entries.*;
import bitverify.crypto.Hash;

import java.util.List;

//import bitverify.crypto;

public class Block {
    public Integer blockSize;
    public BlockHeader header;
    public List<Entry> entries;
    
    
    
    
    public Block(Block PrevBlock, List<Entry> entries){
        this.entries = entries;             //ask Laszlo to make a method or something to get the number of entries or just abandon these
                                        //from the block header
        
        System.out.println("okay, block has been made.");
        
    }
    
    public Block(byte[] serialized){
        int size = calculateSize();
        byte[] totasize = new byte[size];
//        byte[] serialeEntry = entry.serialize();      Ask if this can become a method or if there is any way 
        
    }
    
    public Block(){
        System.out.println("okay, block has been made.");
    }
    
    public String hashBlock(){
        byte[] serialized = this.serializeBlock();
        return Hash.hashBytesToString(serialized);
    }
    
    public byte[] serializeBlock(){
        return null;
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
