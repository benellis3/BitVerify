package bitverify.block;
//import bitverify.crypto;

public class Block {
    public Integer blockSize;
    public BlockHeader header;
    
    
    public Block(Block PrevBlock){
        System.out.println("okay, block has been made.");
        
    }
    
    public Block(byte[] serialized){
        
    }
    
    public Block(){
        System.out.println("okay, block has been made.");
    }
    
    public String hashBlock(){
        return null;
    }
    
    public byte[] serializeBlock(){
        return null;
    }
    
    public BlockHeader unpackBlock(Block prevBlock){
//         unparse a block of raw bytes
        BlockHeader head = new BlockHeader(prevBlock);
        return head;
    }
    
}
