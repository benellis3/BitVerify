package bitverify.block;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class Converter {
    
//    make sure UTF-8 is what we're always going to be using later in the future when things are hashed
    public static byte[] stringToByteArray(String input){
        byte[] out = input.getBytes(Charset.forName("UTF-8"));
        return out;
    }
    
//    make sure UTF-8 is what we're always going to be using later in the future when things are hashed
    public static String byteToString(byte[] buff){
        String result = new String( buff, Charset.forName("UTF-8") );
        return result;
    }
    
    public static byte[] intToByteArray(int val){
        ByteBuffer b = ByteBuffer.allocate(4);
        //b.order(ByteOrder.BIG_ENDIAN); // optional, the initial order of a byte buffer is always BIG_ENDIAN.
        b.putInt(val);
        byte[] result = b.array();
        return result;
    }
    
    //make tests to see what happens if input buffer is not correct size
    public static int byteToInt(byte[] buff){
        ByteBuffer wrapper = ByteBuffer.wrap(buff);
        int result = wrapper.getInt();
        return result;
    }
    

}
