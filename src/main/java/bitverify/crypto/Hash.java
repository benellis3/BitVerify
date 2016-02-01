package bitverify.crypto;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.util.encoders.Hex;

public class Hash {
	
	private static SHA256Digest digest = null;
	
	public static String hashBytes(byte[] data){
		if (digest==null){ digest = new SHA256Digest(); }
		byte[] dataOut = new byte[256 / 8]; // sha256
		digest.update(data,0,data.length);
		digest.doFinal(dataOut, 0);
		String msgDigest = Hex.toHexString(dataOut);
		return msgDigest;
	}
	
	public static String hashString(String data){
		return hashBytes( toByteArray(data) );
	}
	
	protected static byte[] toByteArray(String input){
        byte[] bytes = new byte[input.length()];
        for (int i = 0; i != bytes.length; i++){
            bytes[i] = (byte)input.charAt(i);
        }
        return bytes;
    }
	
	public static void main(String args[]){
		
	}
	
}
