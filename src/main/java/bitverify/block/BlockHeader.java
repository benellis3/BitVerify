package bitverify.block;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class BlockHeader {
    private static final int HEADER_SIZE = 95; 
    private static final int[] lengths = {64,19,4,4,4};
    private String prevBlockHash;   //previous block hash is always 64 bytes  
    private String timeStamp = getTime();       //defined as 19 character long
    private int entries;            //4 bytes
    private int currentBitsTarget;  //4 bytes
    private int nonce = 0;          //4 bytes
    
    
    public BlockHeader(Block prevBlock){
        timeStamp = getTime();                  
        prevBlockHash = prevBlock.hashBlock();  
        currentBitsTarget = 1;                  //need to read into how this is done over time and agreed upon by all
    }
    
    public BlockHeader(String prevHash, String time, int entries, int bitTarget, int nonce, Boolean resetTimer){
        if(!resetTimer){
            timeStamp = time;
        }
        
        this.prevBlockHash = prevHash;
        this.entries = entries;
        this.currentBitsTarget = bitTarget;
        this.nonce = nonce;
        
        
    }
    
    public static BlockHeader desearlize(byte[] buff){
        int length =  buff.length;
        List<byte[]> paramsList = new ArrayList<byte[]>();
        if(length != HEADER_SIZE){
//            throw some type of error
            return null;
        }
        
        int prevVal = 0;
        int val = 0;
        for(int i = 0; i<lengths.length; i++){
            val += lengths[i];
            byte[] param = Arrays.copyOfRange(buff, prevVal, val);
            paramsList.add(param);
            prevVal = val;
        }
        
//        maybe think of a way to do this a little more cleanly
        String prevHash = Converter.byteToString(paramsList.get(0));
        String time = Converter.byteToString(paramsList.get(1));
        int entries = Converter.byteToInt(paramsList.get(2));
        int target = Converter.byteToInt(paramsList.get(3));
        int nonce = Converter.byteToInt(paramsList.get(4));
        
        BlockHeader block = new BlockHeader(prevHash,time,entries,target,nonce,false);
        
        return block;
    }
    
    //wrap this in some sort of exception handler to prevent Unsupported Encoding Exceptions.
    public byte[] serialize(){
        byte[] finalArray = new byte[HEADER_SIZE];
        ByteBuffer bytebuff = ByteBuffer.wrap(finalArray);
        
        bytebuff.put(Converter.stringToByteArray(prevBlockHash));
        bytebuff.put(Converter.stringToByteArray(timeStamp));
        bytebuff.put(Converter.intToByteArray(entries));
        bytebuff.put(Converter.intToByteArray(currentBitsTarget));
        
        return finalArray;
    }
    
    public String getPrevBlockHash(){
        return prevBlockHash;
    }
    
    public int getEntries(){
        return entries;
    }
    
    public String getTimeStamp(){
        return timeStamp;
    }
    
    public String restTimeStamp(){
        timeStamp = getTimeStamp();
        return timeStamp;
    }
    
    public int getTarget(){
        return currentBitsTarget;
    }
    
//    Speak with Alex about how this should be done to prevent concurrency issues.
    public Boolean incrementNonce(){
        nonce += 1;
        return true;
    }
    
    //fill in code to check the number of bits with the target number of bits after hashing the nonce value
    public Boolean checkNonce(){
        Boolean check = 1>0;
        if(check){
            return true;
        }else{
            return false;
        }
    }
    
    public int getNonce(){
        return nonce;
    }
    
    static public String getTime(){
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/mm/yyyy HH:mm:ss");
        return sdf.format(cal.getTime());
    }
}
