package bitverify.crypto;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;

public class Hash {
	public static final int HASH_LENGTH = 256/8;
	
	public static byte[] hashBytes(byte[] data){
		SHA256Digest digest = new SHA256Digest();
		byte[] dataOut = new byte[HASH_LENGTH]; // sha256
		digest.update(data,0,data.length);
		digest.doFinal(dataOut, 0);
		return dataOut;
	}
	
	public static byte[] hashString(String data){
		return hashBytes(data.getBytes(StandardCharsets.UTF_8));
	}
	
	public static String hashBytesToString(byte[] data){
		return Hex.toHexString(hashBytes(data));
	}
	
	public static String hashStringToString(String data){
		return Hex.toHexString(hashString(data));
	}

}
