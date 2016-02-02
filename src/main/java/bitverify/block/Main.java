package bitverify.block;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import bitverify.crypto.Hash;

public class Main {

    public static void main(String[] args) throws UnsupportedEncodingException {
//        Block block = new Block();
        String output;
        
        String hey = "hello what's going on with you today";
        String prevHash = Hash.hashString(hey);
        String timeStamp = BlockHeader.getTime();
        int entries = 1;
        int target = 4;
        int nonce = 12;
        
        BlockHeader initialBlock = new BlockHeader(prevHash,timeStamp,entries,target,nonce,false);
        
        byte[] serial = initialBlock.serialize();
        
        System.out.println(serial);
        
        BlockHeader newBlock = BlockHeader.desearlize(serial);
        
        System.out.println(newBlock.getPrevBlockHash());
        System.out.println(newBlock.getTimeStamp());
        System.out.println(newBlock.getEntries());
        System.out.println(newBlock.getTarget());
        System.out.println(newBlock.getNonce()); 
        
        }

}
